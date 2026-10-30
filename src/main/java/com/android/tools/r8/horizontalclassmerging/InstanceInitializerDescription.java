// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.proto.ArgumentPermutation;
import com.android.tools.r8.horizontalclassmerging.InstanceInitializerAnalysis.InstanceInitializerAnalysisResult;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.ObjectUtils;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A simple abstraction of an instance initializer's code, which allows a parent constructor call
 * followed by a sequence of instance-put instructions.
 */
public class InstanceInitializerDescription {

  // Field assignments that happen prior to the parent constructor call.
  //
  // Most fields are generally assigned after the parent constructor call, but both javac and
  // kotlinc may assign instance fields prior to the parent constructor call. For example, the
  // synthetic this$0 field for non-static inner classes is typically assigned prior to the parent
  // constructor call.
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre;

  // Field assignments that happens after the parent constructor call.
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost;

  // The parent constructor method and the arguments passed to it.
  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  // The constructor parameters, where reference types have been mapped to java.lang.Object, to
  // ensure we don't group constructors such as <init>(int) and <init>(Object), since this would
  // lead to type errors.
  private final DexTypeList relaxedParameters;

  InstanceInitializerDescription(
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments,
      DexTypeList relaxedParameters) {
    this.instanceFieldAssignmentsPre = instanceFieldAssignmentsPre;
    this.instanceFieldAssignmentsPost = instanceFieldAssignmentsPost;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
    this.relaxedParameters = relaxedParameters;
  }

  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexMethod instanceInitializer) {
    return new Builder(appView, instanceInitializer);
  }

  public IncompleteMergedInstanceInitializerCode createCode(
      HorizontalMergeGroup group, boolean hasClassId, int numberOfUnusedArguments) {
    return new IncompleteMergedInstanceInitializerCode(
        hasClassId ? group.getClassIdField() : null,
        numberOfUnusedArguments,
        instanceFieldAssignmentsPre,
        instanceFieldAssignmentsPost,
        parentConstructor,
        parentConstructorArguments);
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InstanceInitializerDescription description = (InstanceInitializerDescription) obj;
    return instanceFieldAssignmentsPre.equals(description.instanceFieldAssignmentsPre)
        && instanceFieldAssignmentsPost.equals(description.instanceFieldAssignmentsPost)
        && parentConstructor == description.parentConstructor
        && parentConstructorArguments.equals(description.parentConstructorArguments)
        && relaxedParameters.equals(description.relaxedParameters);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashLLLLL(
        instanceFieldAssignmentsPre,
        instanceFieldAssignmentsPost,
        parentConstructor,
        parentConstructorArguments,
        relaxedParameters);
  }

  public static class Builder {

    private final DexItemFactory dexItemFactory;
    private final InstanceFieldInitializationInfoFactory infoFactory;
    private final DexTypeList parameters;

    // Use TreeMap so that field assignments are ordered canonically by target DexField rather than
    // by the order in which instance-put instructions appear in the original constructor.
    private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre =
        new TreeMap<>();
    private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost =
        new TreeMap<>();
    private DexMethod parentConstructor;
    private List<InstanceFieldInitializationInfo> parentConstructorArguments;

    Builder(AppView<? extends AppInfoWithClassHierarchy> appView, DexMethod methodReference) {
      this(
          appView.dexItemFactory(),
          methodReference,
          appView.instanceFieldInitializationInfoFactory());
    }

    Builder(
        DexItemFactory dexItemFactory,
        DexMethod methodReference,
        InstanceFieldInitializationInfoFactory infoFactory) {
      this.dexItemFactory = dexItemFactory;
      this.infoFactory = infoFactory;
      this.parameters = methodReference.getParameters();
    }

    private DexType getRelaxedType(DexType type) {
      return type.isPrimitiveType() ? type : dexItemFactory.objectType;
    }

    @SuppressWarnings("ReferenceEquality")
    public void addInstancePut(DexField field, InstanceFieldInitializationInfo value) {
      if (parentConstructor == null) {
        instanceFieldAssignmentsPre.put(field, value);
        return;
      }

      // If the parent constructor is java.lang.Object.<init>() then group all the field assignments
      // before the parent constructor call to allow more sharing.
      //
      // Note that field assignments that store the receiver cannot be hoisted to before the
      // Object.<init>() call, since this would lead to an illegal use of the uninitialized 'this'.
      if (dexItemFactory.objectMembers.constructor.isIdenticalTo(parentConstructor)) {
        if (!value.isArgumentInitializationInfo()
            || value.asArgumentInitializationInfo().getArgumentIndex() != 0) {
          instanceFieldAssignmentsPre.put(field, value);
          return;
        }
      }

      instanceFieldAssignmentsPost.put(field, value);
    }

    public boolean addInvokeConstructor(
        DexMethod method, List<InstanceFieldInitializationInfo> arguments) {
      if (parentConstructor == null) {
        parentConstructor = method;
        parentConstructorArguments = arguments;
        return true;
      }
      return false;
    }

    public InstanceInitializerAnalysisResult build() {
      assert isValid();
      int arity = parameters.size();
      if (arity == 0) {
        return new InstanceInitializerAnalysisResult(
            new InstanceInitializerDescription(
                instanceFieldAssignmentsPre,
                instanceFieldAssignmentsPost,
                parentConstructor,
                parentConstructorArguments,
                DexTypeList.empty()),
            ArgumentPermutation.getDefault());
      }

      // Assign new 1-based argument indices to used parameters in the canonical order in which
      // they are first referenced across pre-super field assignments, parent constructor arguments,
      // and post-super field assignments. This allows constructors that take the same parameters in
      // different orders to canonicalize to the same InstanceInitializerDescription.
      int[] oldToNewArgumentIndex = new int[arity + 1];
      IntBox nextNewArgumentIndex = new IntBox(1);
      Consumer<InstanceFieldInitializationInfo> recordUse =
          info -> {
            if (info.isArgumentInitializationInfo()) {
              int oldArgumentIndex = info.asArgumentInitializationInfo().getArgumentIndex();
              if (oldArgumentIndex > 0 && oldToNewArgumentIndex[oldArgumentIndex] == 0) {
                oldToNewArgumentIndex[oldArgumentIndex] = nextNewArgumentIndex.getAndIncrement();
              }
            }
          };

      instanceFieldAssignmentsPre.values().forEach(recordUse);
      parentConstructorArguments.forEach(recordUse);
      instanceFieldAssignmentsPost.values().forEach(recordUse);

      for (int oldArgumentIndex = 1; oldArgumentIndex <= arity; oldArgumentIndex++) {
        if (oldToNewArgumentIndex[oldArgumentIndex] == 0) {
          oldToNewArgumentIndex[oldArgumentIndex] = nextNewArgumentIndex.getAndIncrement();
        }
      }

      // Build the ArgumentPermutation and the canonicalized relaxed parameter list.
      ArgumentPermutation.Builder permutationBuilder = ArgumentPermutation.builder(arity + 1);
      DexType[] relaxedParameters = new DexType[arity];
      for (int oldArgumentIndex = 1; oldArgumentIndex <= arity; oldArgumentIndex++) {
        int newArgumentIndex = oldToNewArgumentIndex[oldArgumentIndex];
        DexType parameter = parameters.get(oldArgumentIndex - 1);
        permutationBuilder.setNewArgumentIndex(oldArgumentIndex, newArgumentIndex);
        relaxedParameters[newArgumentIndex - 1] = getRelaxedType(parameter);
      }
      ArgumentPermutation argumentPermutation = permutationBuilder.build();

      // Rewrite argument references in field assignments and parent constructor arguments to use
      // the new canonical argument indices.
      if (!argumentPermutation.isDefault()) {
        instanceFieldAssignmentsPre.replaceAll(
            (field, info) -> rewriteInfo(info, oldToNewArgumentIndex));
        instanceFieldAssignmentsPost.replaceAll(
            (field, info) -> rewriteInfo(info, oldToNewArgumentIndex));
        parentConstructorArguments =
            ListUtils.map(
                parentConstructorArguments, info -> rewriteInfo(info, oldToNewArgumentIndex));
      }

      return new InstanceInitializerAnalysisResult(
          new InstanceInitializerDescription(
              instanceFieldAssignmentsPre,
              instanceFieldAssignmentsPost,
              parentConstructor,
              parentConstructorArguments,
              DexTypeList.create(relaxedParameters)),
          argumentPermutation);
    }

    private InstanceFieldInitializationInfo rewriteInfo(
        InstanceFieldInitializationInfo info, int[] oldToNewArgumentIndex) {
      if (info.isArgumentInitializationInfo()) {
        int oldArgumentIndex = info.asArgumentInitializationInfo().getArgumentIndex();
        if (oldArgumentIndex > 0) {
          int newArgumentIndex = oldToNewArgumentIndex[oldArgumentIndex];
          if (newArgumentIndex != oldArgumentIndex) {
            return infoFactory.createArgumentInitializationInfo(newArgumentIndex);
          }
        }
      }
      return info;
    }

    public boolean isValid() {
      return parentConstructor != null;
    }
  }
}
