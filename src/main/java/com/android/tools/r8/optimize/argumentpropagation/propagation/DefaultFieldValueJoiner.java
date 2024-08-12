// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReferenceTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class DefaultFieldValueJoiner {

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexProgramClass> classesWithSingleCallerInlinedInstanceInitializers;
  private final FieldStateCollection fieldStates;
  private final List<FlowGraph> flowGraphs;

  public DefaultFieldValueJoiner(
      AppView<AppInfoWithLiveness> appView,
      Set<DexProgramClass> classesWithSingleCallerInlinedInstanceInitializers,
      FieldStateCollection fieldStates,
      List<FlowGraph> flowGraphs) {
    this.appView = appView;
    this.classesWithSingleCallerInlinedInstanceInitializers =
        classesWithSingleCallerInlinedInstanceInitializers;
    this.fieldStates = fieldStates;
    this.flowGraphs = flowGraphs;
  }

  public Map<FlowGraph, Deque<FlowGraphNode>> joinDefaultFieldValuesForFieldsWithReadBeforeWrite(
      ExecutorService executorService) throws ExecutionException {
    // Find all the fields where we need to determine if each field read is guaranteed to be
    // dominated by a write.
    Map<DexProgramClass, List<ProgramField>> fieldsOfInterest = getFieldsOfInterest();

    // If constructor inlining is disabled, then we focus on whether each instance initializer
    // definitely assigns the given field before it is read. We do the same for final and static
    // fields.
    Map<DexType, ProgramFieldSet> fieldsNotSubjectToInitializerAnalysis =
        removeFieldsNotSubjectToInitializerAnalysis(fieldsOfInterest);
    ProgramFieldSet fieldsWithLiveDefaultValue = ProgramFieldSet.createConcurrent();
    analyzeInitializers(
        fieldsOfInterest,
        field -> {
          if (!field.getAccessFlags().isFinal() && !field.getAccessFlags().isStatic()) {
            fieldsNotSubjectToInitializerAnalysis
                .computeIfAbsent(
                    field.getHolderType(), ignoreKey(ProgramFieldSet::createConcurrent))
                .add(field);
          } else {
            fieldsWithLiveDefaultValue.add(field);
          }
        },
        executorService);

    // For non-final fields where writes in instance initializers may have been subject to
    // constructor inlining, we find all new-instance instructions (including subtype allocations)
    // and check if the field is written on each allocation before it is possibly read.
    analyzeNewInstanceInstructions(
        fieldsNotSubjectToInitializerAnalysis, fieldsWithLiveDefaultValue::add);

    return updateFlowGraphs(fieldsWithLiveDefaultValue, executorService);
  }

  private Map<DexProgramClass, List<ProgramField>> getFieldsOfInterest() {
    Map<DexProgramClass, List<ProgramField>> fieldsOfInterest = new IdentityHashMap<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramField(
          field -> {
            // We only need to include the fields where including the default value would make a
            // difference. Then we can assert below in updateFlowGraphs() that adding the default
            // value changes the field state.
            // TODO(b/296030319): Implement this for primitive fields.
            ValueState state = fieldStates.get(field);
            if (state.isUnknown()) {
              return;
            }
            if (state.isReferenceState()) {
              ConcreteReferenceTypeValueState referenceState = state.asReferenceState();
              if (referenceState.getDynamicType().getNullability().isNullable()
                  && referenceState.getAbstractValue(appView).isUnknown()) {
                return;
              }
            }
            fieldsOfInterest
                .computeIfAbsent(field.getHolder(), ignoreKey(ArrayList::new))
                .add(field);
          });
    }
    return fieldsOfInterest;
  }

  private Map<DexType, ProgramFieldSet> removeFieldsNotSubjectToInitializerAnalysis(
      Map<DexProgramClass, List<ProgramField>> fieldsSubjectToInitializerAnalysis) {
    // When there is no constructor inlining, we can always analyze the initializers.
    Map<DexType, ProgramFieldSet> fieldsNotSubjectToInitializerAnalysis = new ConcurrentHashMap<>();
    if (!appView.options().canInitNewInstanceUsingSuperclassConstructor()) {
      return fieldsNotSubjectToInitializerAnalysis;
    }
    if (classesWithSingleCallerInlinedInstanceInitializers != null
        && classesWithSingleCallerInlinedInstanceInitializers.isEmpty()) {
      return fieldsNotSubjectToInitializerAnalysis;
    }

    // When constructor inlining is enabled, we can still limit the analysis to the instance
    // initializers for final fields. We can do the same for static fields as <clinit> is not
    // subject to inlining.
    MapUtils.removeIf(
        fieldsSubjectToInitializerAnalysis,
        (holder, fields) -> {
          if (classesWithSingleCallerInlinedInstanceInitializers != null
              && !classesWithSingleCallerInlinedInstanceInitializers.contains(holder)) {
            return false;
          }

          fields.removeIf(
              field -> {
                if (!field.getAccessFlags().isFinal() && !field.getAccessFlags().isStatic()) {
                  fieldsNotSubjectToInitializerAnalysis
                      .computeIfAbsent(
                          holder.getType(), ignoreKey(ProgramFieldSet::createConcurrent))
                      .add(field);
                  return true;
                }
                return false;
              });
          return fields.isEmpty();
        });
    return fieldsNotSubjectToInitializerAnalysis;
  }

  private void analyzeInitializers(
      Map<DexProgramClass, List<ProgramField>> fieldsOfInterest,
      Consumer<ProgramField> concurrentLiveDefaultValueConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        fieldsOfInterest,
        (clazz, fields) -> {
          ProgramFieldSet instanceFieldsWithLiveDefaultValue = ProgramFieldSet.create();
          ProgramFieldSet staticFieldsWithLiveDefaultValue = ProgramFieldSet.create();
          partitionFields(
              fields, instanceFieldsWithLiveDefaultValue, staticFieldsWithLiveDefaultValue);
          analyzeClassInitializerAssignments(
              clazz, staticFieldsWithLiveDefaultValue, concurrentLiveDefaultValueConsumer);
          analyzeInstanceInitializerAssignments(
              clazz, instanceFieldsWithLiveDefaultValue, concurrentLiveDefaultValueConsumer);
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void partitionFields(
      Collection<ProgramField> fields,
      ProgramFieldSet instanceFieldsWithLiveDefaultValue,
      ProgramFieldSet staticFieldsWithLiveDefaultValue) {
    for (ProgramField field : fields) {
      if (field.getAccessFlags().isStatic()) {
        staticFieldsWithLiveDefaultValue.add(field);
      } else {
        instanceFieldsWithLiveDefaultValue.add(field);
      }
    }
  }

  private void analyzeClassInitializerAssignments(
      DexProgramClass clazz,
      ProgramFieldSet staticFieldsWithLiveDefaultValue,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    if (staticFieldsWithLiveDefaultValue.isEmpty()) {
      return;
    }
    if (clazz.hasClassInitializer()) {
      IRCode code =
          clazz
              .getProgramClassInitializer()
              .buildIR(appView, MethodConversionOptions.nonConverting());
      FieldReadBeforeWriteAnalysis analysis = new FieldReadBeforeWriteAnalysis(appView, code);
      staticFieldsWithLiveDefaultValue.removeIf(analysis::isStaticFieldNeverReadBeforeWrite);
    }
    staticFieldsWithLiveDefaultValue.forEach(liveDefaultValueConsumer);
  }

  private void analyzeInstanceInitializerAssignments(
      DexProgramClass clazz,
      ProgramFieldSet instanceFieldsWithoutDefaultValue,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    if (instanceFieldsWithoutDefaultValue.isEmpty()) {
      return;
    }
    ProgramFieldSet instanceFieldsWithDefaultValue = ProgramFieldSet.create();
    for (ProgramMethod instanceInitializer : clazz.programInstanceInitializers()) {
      IRCode code = instanceInitializer.buildIR(appView, MethodConversionOptions.nonConverting());
      FieldReadBeforeWriteAnalysis analysis = new FieldReadBeforeWriteAnalysis(appView, code);
      instanceFieldsWithoutDefaultValue.removeIf(
          field -> {
            if (analysis.isInstanceFieldMaybeReadBeforeWrite(field)
                && instanceFieldsWithDefaultValue.add(field)) {
              liveDefaultValueConsumer.accept(field);
              return true;
            }
            return false;
          });
    }
  }

  private void analyzeNewInstanceInstructions(
      Map<DexType, ProgramFieldSet> nonFinalInstanceFields,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    // Conservatively treat all fields as maybe read before written.
    // TODO(b/296030319): Implement analysis by building IR for all methods that instantiate the
    //  relevant classes and analyzing the puts to the newly created instances.
    for (ProgramField field : IterableUtils.flatten(nonFinalInstanceFields.values())) {
      liveDefaultValueConsumer.accept(field);
    }
  }

  private Map<FlowGraph, Deque<FlowGraphNode>> updateFlowGraphs(
      ProgramFieldSet fieldsWithLiveDefaultValue, ExecutorService executorService)
      throws ExecutionException {
    Collection<Pair<FlowGraph, Deque<FlowGraphNode>>> worklists =
        ThreadUtils.processItemsWithResultsThatMatches(
            flowGraphs,
            flowGraph -> {
              Deque<FlowGraphNode> worklist = new ArrayDeque<>();
              flowGraph.forEachFieldNode(
                  node -> {
                    ProgramField field = node.getField();
                    if (fieldsWithLiveDefaultValue.remove(field)) {
                      DexType inStaticType = null;
                      node.addState(
                          appView,
                          getDefaultValueState(field),
                          inStaticType,
                          () -> {
                            if (node.isUnknown()) {
                              node.clearPredecessors();
                            }
                            node.addToWorkList(worklist);
                          });
                    }
                  });
              return new Pair<>(flowGraph, worklist);
            },
            pair -> !pair.getSecond().isEmpty(),
            appView.options().getThreadingModule(),
            executorService);
    // Unseen fields are not added to any flow graphs, since they are not needed for flow
    // propagation. Update these fields directly in the field state collection.
    for (ProgramField field : fieldsWithLiveDefaultValue) {
      fieldStates.addTemporaryFieldState(
          appView, field, () -> getDefaultValueState(field), Timing.empty());
    }
    return MapUtils.newIdentityHashMap(
        builder -> worklists.forEach(pair -> builder.put(pair.getFirst(), pair.getSecond())));
  }

  private ConcreteValueState getDefaultValueState(ProgramField field) {
    AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
    AbstractValue defaultValue;
    if (field.getAccessFlags().isStatic() && field.getDefinition().hasExplicitStaticValue()) {
      defaultValue = field.getDefinition().getStaticValue().toAbstractValue(abstractValueFactory);
    } else if (field.getType().isPrimitiveType()) {
      defaultValue = abstractValueFactory.createZeroValue();
    } else {
      defaultValue = abstractValueFactory.createUncheckedNullValue();
    }
    NonEmptyValueState fieldStateToAdd;
    if (field.getType().isArrayType()) {
      Nullability defaultNullability = Nullability.definitelyNull();
      fieldStateToAdd = ConcreteArrayTypeValueState.create(defaultNullability);
    } else if (field.getType().isClassType()) {
      assert defaultValue.isNull()
          || defaultValue.isSingleStringValue()
          || defaultValue.isSingleDexItemBasedStringValue();
      DynamicType dynamicType =
          defaultValue.isNull()
              ? DynamicType.definitelyNull()
              : DynamicType.createExact(
                  TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
      fieldStateToAdd = ConcreteClassTypeValueState.create(defaultValue, dynamicType);
    } else {
      assert field.getType().isPrimitiveType();
      fieldStateToAdd = ConcretePrimitiveTypeValueState.create(defaultValue);
    }
    // We should always be able to map static field values to an unknown abstract value.
    if (fieldStateToAdd.isUnknown()) {
      throw new Unreachable();
    }
    return fieldStateToAdd.asConcrete();
  }
}
