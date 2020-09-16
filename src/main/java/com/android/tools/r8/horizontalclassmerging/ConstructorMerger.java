// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConstructorMerger {
  private final AppView<?> appView;
  private final DexProgramClass target;
  private final Collection<DexEncodedMethod> constructors;
  private final DexItemFactory dexItemFactory;
  private final DexField classIdField;

  ConstructorMerger(
      AppView<?> appView,
      DexProgramClass target,
      Collection<DexEncodedMethod> constructors,
      DexField classIdField) {
    this.appView = appView;
    this.target = target;
    this.constructors = constructors;
    this.classIdField = classIdField;

    // Constructors should not be empty and all constructors should have the same prototype.
    assert !constructors.isEmpty();
    assert constructors.stream().map(constructor -> constructor.proto()).distinct().count() == 1;

    this.dexItemFactory = appView.dexItemFactory();
  }

  public static class Builder {
    private final Collection<DexEncodedMethod> constructors;

    public Builder() {
      constructors = new ArrayList<>();
    }

    public Builder add(DexEncodedMethod constructor) {
      constructors.add(constructor);
      return this;
    }

    public ConstructorMerger build(
        AppView<?> appView, DexProgramClass target, DexField classIdField) {
      return new ConstructorMerger(appView, target, constructors, classIdField);
    }
  }

  private boolean isTrivialMerge() {
    return constructors.size() == 1;
  }

  private DexMethod moveConstructor(DexEncodedMethod constructor) {
    DexMethod method =
        dexItemFactory.createFreshMethodName(
            "constructor",
            constructor.holder(),
            constructor.proto(),
            target.type,
            tryMethod -> target.lookupMethod(tryMethod) == null);

    if (constructor.holder() == target.type) {
      target.removeMethod(constructor.toReference());
    }

    DexEncodedMethod encodedMethod = constructor.toTypeSubstitutedMethod(method);
    encodedMethod.getMutableOptimizationInfo().markForceInline();
    target.addDirectMethod(encodedMethod);
    return method;
  }

  private DexProto getNewConstructorProto(SyntheticArgumentClass syntheticArgumentClass) {
    DexEncodedMethod firstConstructor = constructors.stream().findFirst().get();
    DexProto oldProto = firstConstructor.getProto();

    if (isTrivialMerge()) {
      return oldProto;
    }

    List<DexType> parameters = new ArrayList<>();
    Collections.addAll(parameters, oldProto.parameters.values);
    parameters.add(syntheticArgumentClass.getArgumentClass());
    parameters.add(dexItemFactory.intType);
    // TODO(b/165783587): add synthesised class to prevent constructor merge conflict
    return dexItemFactory.createProto(oldProto.returnType, parameters);
  }

  private MethodAccessFlags getAccessFlags() {
    // TODO(b/164998929): ensure this behaviour is correct, should probably calculate upper bound
    return MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true);
  }

  /** Synthesize a new method which selects the constructor based on a parameter type. */
  void merge(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Reference2IntMap<DexType> classIdentifiers,
      SyntheticArgumentClass syntheticArgumentClass) {
    // Tree map as must be sorted.
    Int2ReferenceSortedMap<DexMethod> typeConstructorClassMap = new Int2ReferenceAVLTreeMap<>();

    int classFileVersion = -1;
    for (DexEncodedMethod constructor : constructors) {
      if (constructor.hasClassFileVersion()) {
        classFileVersion = Integer.max(classFileVersion, constructor.getClassFileVersion());
      }
      DexMethod movedConstructor = moveConstructor(constructor);
      lensBuilder.recordOriginalSignature(constructor.method, movedConstructor);
      typeConstructorClassMap.put(
          classIdentifiers.getInt(constructor.getHolderType()), movedConstructor);
    }

    DexProto newProto = getNewConstructorProto(syntheticArgumentClass);

    DexMethod originalConstructorReference =
        appView.graphLens().getOriginalMethodSignature(constructors.iterator().next().method);
    DexMethod newConstructorReference =
        appView.dexItemFactory().createMethod(target.type, newProto, dexItemFactory.initMethodName);
    ConstructorEntryPointSynthesizedCode synthesizedCode =
        new ConstructorEntryPointSynthesizedCode(
            typeConstructorClassMap,
            newConstructorReference,
            classIdField,
            originalConstructorReference);
    DexEncodedMethod newConstructor =
        new DexEncodedMethod(
            newConstructorReference,
            getAccessFlags(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode,
            classFileVersion,
            true);

    if (isTrivialMerge()) {
      // The constructor does not require the additional argument, just map it like a regular
      // method.
      lensBuilder.mapMethod(constructors.iterator().next().method, newConstructorReference);
    } else {
      // Map each old constructor to the newly synthesized constructor in the graph lens.
      for (DexEncodedMethod oldConstructor : constructors) {
        lensBuilder.mapMergedConstructor(
            oldConstructor.method,
            newConstructorReference,
            classIdentifiers.getInt(oldConstructor.getHolderType()));
      }
    }
    // Map the first constructor to the newly synthesized constructor.
    lensBuilder.recordExtraOriginalSignature(originalConstructorReference, newConstructorReference);

    target.addDirectMethod(newConstructor);

    fieldAccessChangesBuilder.fieldWrittenByMethod(classIdField, newConstructorReference);
  }
}
