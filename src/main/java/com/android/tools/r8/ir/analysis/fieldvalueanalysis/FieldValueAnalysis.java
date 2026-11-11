// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.DominatorTree.Assumption;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.UnknownInstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexClassAndFieldMap;
import com.android.tools.r8.utils.internal.DequeUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.OptionalBool;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class FieldValueAnalysis {

  static class FieldInitializationInfo {

    private final AnalyzedCode analyzedCode;
    final Instruction instruction;
    private final InstanceFieldInitializationInfo instanceFieldInitializationInfo;

    FieldInitializationInfo(
        AnalyzedCode analyzedCode,
        Instruction instruction,
        InstanceFieldInitializationInfo instanceFieldInitializationInfo) {
      this.analyzedCode = analyzedCode;
      this.instruction = instruction;
      this.instanceFieldInitializationInfo = instanceFieldInitializationInfo;
    }

    boolean dominatesNormalExits() {
      return analyzedCode.dominatesNormalExits(instruction);
    }

    boolean isInInitializer() {
      return analyzedCode.isInitializer();
    }
  }

  /**
   * The code of the initializer, or of a helper method that is only called from the initializer
   * (directly or through other helpers). The code of a helper is analyzed as if it was inlined at
   * its call site.
   */
  static class AnalyzedCode {

    private final IRCode code;
    private final AnalyzedCode parent;
    private final InvokeMethod callSite;

    private DominatorTree dominatorTree;
    private Map<BasicBlock, AbstractFieldSet> fieldsMaybeReadBeforeBlockInclusiveCache;
    private OptionalBool isStraightLineCode = OptionalBool.unknown();
    private List<BasicBlock> normalExitBlocks;
    private AbstractFieldSet readSet;

    AnalyzedCode(IRCode code, AnalyzedCode parent, InvokeMethod callSite) {
      this.code = code;
      this.parent = parent;
      this.callSite = callSite;
    }

    boolean isInitializer() {
      return parent == null;
    }

    boolean isStraightLineCode() {
      if (isStraightLineCode.isUnknown()) {
        isStraightLineCode =
            OptionalBool.of(
                Iterables.all(code.getBlocks(), block -> block.getSuccessors().size() <= 1));
      }
      return isStraightLineCode.isTrue();
    }

    List<BasicBlock> getOrComputeNormalExitBlocks() {
      if (normalExitBlocks == null) {
        normalExitBlocks = code.computeNormalExitBlocks();
      }
      return normalExitBlocks;
    }

    DominatorTree getOrCreateDominatorTree() {
      if (dominatorTree == null) {
        dominatorTree = new DominatorTree(code, Assumption.NO_UNREACHABLE_BLOCKS);
      }
      return dominatorTree;
    }

    /** Returns true if the instruction is executed on all normal exits from the initializer. */
    boolean dominatesNormalExits(Instruction instruction) {
      if (!isStraightLineCode()
          && !getOrCreateDominatorTree()
              .dominatesAllOf(instruction.getBlock(), getOrComputeNormalExitBlocks())) {
        return false;
      }
      return isInitializer() || parent.dominatesNormalExits(callSite);
    }
  }

  final AppView<AppInfoWithLiveness> appView;
  final IRCode code;
  final ProgramMethod context;
  final OptimizationFeedback feedback;

  private final AnalyzedCode root;

  // The analyzed helper methods, indexed by their call site.
  private final Map<InvokeMethod, AnalyzedCode> helpers = new IdentityHashMap<>();

  final DexClassAndFieldMap<List<FieldInitializationInfo>> putsPerField =
      DexClassAndFieldMap.create();

  FieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView, IRCode code, OptimizationFeedback feedback) {
    this.appView = appView;
    this.code = code;
    this.feedback = feedback;
    this.context = code.context();
    this.root = new AnalyzedCode(code, null, null);
  }

  DominatorTree getOrCreateDominatorTree() {
    return root.getOrCreateDominatorTree();
  }

  private Map<BasicBlock, AbstractFieldSet> getOrCreateFieldsMaybeReadBeforeBlockInclusive(
      AnalyzedCode analyzedCode) {
    if (analyzedCode.fieldsMaybeReadBeforeBlockInclusiveCache == null) {
      analyzedCode.fieldsMaybeReadBeforeBlockInclusiveCache =
          createFieldsMaybeReadBeforeBlockInclusive(analyzedCode);
    }
    return analyzedCode.fieldsMaybeReadBeforeBlockInclusiveCache;
  }

  boolean isInstanceFieldValueAnalysis() {
    return false;
  }

  InstanceFieldValueAnalysis asInstanceFieldValueAnalysis() {
    return null;
  }

  boolean isStaticFieldValueAnalysis() {
    return false;
  }

  StaticFieldValueAnalysis asStaticFieldValueAnalysis() {
    return null;
  }

  abstract boolean isSubjectToOptimizationIgnoringPinning(DexClassAndField field);

  abstract boolean isSubjectToOptimization(DexClassAndField field, ProgramMethod context);

  /**
   * Returns the code of the target of the given invoke if the target is a helper method that is
   * only called from the given caller and should be analyzed as if it was inlined, or null.
   */
  IRCode getEffectivelyClinitOutline(InvokeMethod invoke, ProgramMethod caller) {
    return null;
  }

  private void recordFieldPut(
      ProgramField field, Instruction instruction, AnalyzedCode analyzedCode) {
    recordFieldPut(
        field, instruction, UnknownInstanceFieldInitializationInfo.getInstance(), analyzedCode);
  }

  void recordFieldPut(
      DexClassAndField field, Instruction instruction, InstanceFieldInitializationInfo info) {
    recordFieldPut(field, instruction, info, root);
  }

  private void recordFieldPut(
      DexClassAndField field,
      Instruction instruction,
      InstanceFieldInitializationInfo info,
      AnalyzedCode analyzedCode) {
    putsPerField
        .computeIfAbsent(field, ignore -> new ArrayList<>())
        .add(new FieldInitializationInfo(analyzedCode, instruction, info));
  }

  /** This method analyzes initializers with the purpose of computing field optimization info. */
  void computeFieldOptimizationInfo(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    // Find all the static-put instructions that assign a field in the enclosing class which is
    // guaranteed to be assigned only in the current initializer.
    collectFieldPuts(root);

    putsPerField.forEach(
        (field, fieldPuts) -> {
          if (fieldPuts.size() > 1) {
            return;
          }
          FieldInitializationInfo info = ListUtils.first(fieldPuts);
          Instruction instruction = info.instruction;
          if (instruction.isInvokeDirect()) {
            asInstanceFieldValueAnalysis()
                .recordInstanceFieldIsInitializedWithInfo(
                    field, info.instanceFieldInitializationInfo);
            return;
          }
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          if (!info.dominatesNormalExits()) {
            return;
          }
          boolean priorReadsWillReadSameValue =
              !classInitializerDefaultsResult.hasStaticValue(field) && fieldPut.value().isZero();
          if (!priorReadsWillReadSameValue && fieldMaybeReadBeforeInstruction(field, info)) {
            // TODO(b/172528424): Generalize to InstanceFieldValueAnalysis.
            if (isStaticFieldValueAnalysis()) {
              // At this point the value read in the field can be only the default static value, if
              // read prior to the put, or the value put, if read after the put. We still want to
              // record it because the default static value is typically null/0, so code present
              // after a null/0 check can take advantage of the optimization.
              DexValue valueBeforePut = classInitializerDefaultsResult.getStaticValue(field);
              asStaticFieldValueAnalysis()
                  .updateFieldOptimizationInfoWith2Values(
                      field.asProgramField(), fieldPut.value(), valueBeforePut);
            }
            return;
          }
          updateFieldOptimizationInfo(field, fieldPut, fieldPut.value());
        });
  }

  private void collectFieldPuts(AnalyzedCode analyzedCode) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    for (BasicBlock block : analyzedCode.code.getBlocks()) {
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.isFieldPut()) {
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          DexField fieldReference = fieldPut.getField();
          ProgramField field = appInfo.resolveField(fieldReference).getProgramField();
          if (field != null) {
            if (isSubjectToOptimization(field, analyzedCode.code.context())) {
              recordFieldPut(field, fieldPut, analyzedCode);
            } else if (analyzedCode.isInitializer()
                && isStaticFieldValueAnalysis()
                && field.getHolder().isEnum()
                && isSubjectToOptimizationIgnoringPinning(field)) {
              recordFieldPut(field, fieldPut, analyzedCode);
            }
          }
        } else if (isInstanceFieldValueAnalysis()
            && instruction.isInvokeConstructor(appView.dexItemFactory())) {
          InvokeDirect invoke = instruction.asInvokeDirect();
          asInstanceFieldValueAnalysis().analyzeForwardingConstructorCall(invoke, code.getThis());
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          IRCode helperCode = getEffectivelyClinitOutline(invoke, analyzedCode.code.context());
          if (helperCode != null) {
            AnalyzedCode helper = new AnalyzedCode(helperCode, analyzedCode, invoke);
            helpers.put(invoke, helper);
            collectFieldPuts(helper);
          }
        }
      }
    }
  }

  boolean fieldMaybeReadBeforeInstruction(DexClassAndField field, FieldInitializationInfo info) {
    return fieldMaybeReadBeforeInstruction(field, info.analyzedCode, info.instruction);
  }

  /**
   * Returns true if the field may be read before the given instruction in the given code, or before
   * the call to the given code if it is the code of a helper method.
   */
  @SuppressWarnings("ReferenceEquality")
  private boolean fieldMaybeReadBeforeInstruction(
      DexClassAndField field, AnalyzedCode analyzedCode, Instruction instruction) {
    if (!getOrComputeReadSet(root).contains(field)) {
      return false;
    }

    if (getOrComputeReadSet(analyzedCode).contains(field)) {
      BasicBlock block = instruction.getBlock();

      // First check if the field may be read in any of the (transitive) predecessor blocks.
      if (fieldMaybeReadBeforeBlock(field, analyzedCode, block)) {
        return true;
      }

      // Then check if any of the instructions that precede the given instruction in the current
      // block may read the field.
      InstructionIterator instructionIterator = block.iterator();
      while (instructionIterator.hasNext()) {
        Instruction current = instructionIterator.next();
        if (current == instruction) {
          break;
        }
        if (readSet(current).contains(field)) {
          return true;
        }
      }
    }

    // Otherwise, the field is not read prior to the given instruction, unless it is read prior to
    // the call to this helper method.
    return !analyzedCode.isInitializer()
        && fieldMaybeReadBeforeInstruction(field, analyzedCode.parent, analyzedCode.callSite);
  }

  private boolean fieldMaybeReadBeforeBlock(
      DexClassAndField field, AnalyzedCode analyzedCode, BasicBlock block) {
    for (BasicBlock predecessor : block.getPredecessors()) {
      if (fieldMaybeReadBeforeBlockInclusive(field, analyzedCode, predecessor)) {
        return true;
      }
    }
    return false;
  }

  private boolean fieldMaybeReadBeforeBlockInclusive(
      DexClassAndField field, AnalyzedCode analyzedCode, BasicBlock block) {
    return getOrCreateFieldsMaybeReadBeforeBlockInclusive(analyzedCode).get(block).contains(field);
  }

  /**
   * Eagerly creates a mapping from each block to the set of fields that may be read in that block
   * and its transitive predecessors.
   */
  @SuppressWarnings("ReferenceEquality")
  private Map<BasicBlock, AbstractFieldSet> createFieldsMaybeReadBeforeBlockInclusive(
      AnalyzedCode analyzedCode) {
    Map<BasicBlock, AbstractFieldSet> result = new IdentityHashMap<>();
    Deque<BasicBlock> worklist = DequeUtils.newArrayDeque(analyzedCode.code.entryBlock());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.removeFirst();
      boolean seenBefore = result.containsKey(block);
      AbstractFieldSet readSet =
          result.computeIfAbsent(block, ignore -> EmptyFieldSet.getInstance());
      if (readSet.isTop()) {
        // We already have unknown information for this block.
        continue;
      }

      assert readSet.isKnownFieldSet();
      KnownFieldSet knownReadSet = readSet.asKnownFieldSet();
      int oldSize = seenBefore ? knownReadSet.size() : -1;

      // Everything that is read in the predecessor blocks should also be included in the read set
      // for the current block, so here we join the information from the predecessor blocks into the
      // current read set.
      boolean blockOrPredecessorMaybeReadAnyField = false;
      for (BasicBlock predecessor : block.getPredecessors()) {
        AbstractFieldSet predecessorReadSet =
            result.getOrDefault(predecessor, EmptyFieldSet.getInstance());
        if (predecessorReadSet.isBottom()) {
          continue;
        }
        if (predecessorReadSet.isTop()) {
          blockOrPredecessorMaybeReadAnyField = true;
          break;
        }
        assert predecessorReadSet.isConcreteFieldSet();
        if (!knownReadSet.isConcreteFieldSet()) {
          knownReadSet = new ConcreteMutableFieldSet();
        }
        knownReadSet.asConcreteFieldSet().addAll(predecessorReadSet.asConcreteFieldSet());
      }

      if (!blockOrPredecessorMaybeReadAnyField) {
        // Finally, we update the read set with the fields that are read by the instructions in the
        // current block. This can be skipped if the block has already been processed.
        if (seenBefore) {
          assert verifyFieldSetContainsAllFieldReadsInBlock(knownReadSet, block);
        } else {
          for (Instruction instruction : block.getInstructions()) {
            AbstractFieldSet instructionReadSet = readSet(instruction);
            if (instructionReadSet.isBottom()) {
              continue;
            }
            if (instructionReadSet.isTop()) {
              blockOrPredecessorMaybeReadAnyField = true;
              break;
            }
            if (!knownReadSet.isConcreteFieldSet()) {
              knownReadSet = new ConcreteMutableFieldSet();
            }
            knownReadSet.asConcreteFieldSet().addAll(instructionReadSet.asConcreteFieldSet());
          }
        }
      }

      boolean changed = false;
      if (blockOrPredecessorMaybeReadAnyField) {
        // Record that this block reads all fields.
        result.put(block, UnknownFieldSet.getInstance());
        changed = true;
      } else {
        if (knownReadSet != readSet) {
          result.put(block, knownReadSet.asConcreteFieldSet());
        }
        if (knownReadSet.size() != oldSize) {
          assert knownReadSet.size() > oldSize;
          changed = true;
        }
      }

      if (changed) {
        // Rerun the analysis for all successors because the state of the current block changed.
        worklist.addAll(block.getSuccessors());
      }
    }
    return result;
  }

  private boolean verifyFieldSetContainsAllFieldReadsInBlock(
      KnownFieldSet readSet, BasicBlock block) {
    for (Instruction instruction : block.getInstructions()) {
      AbstractFieldSet instructionReadSet = readSet(instruction);
      assert !instructionReadSet.isTop();
      if (instructionReadSet.isBottom()) {
        continue;
      }
      for (DexEncodedField field : instructionReadSet.asConcreteFieldSet().getFields()) {
        assert readSet.contains(field);
      }
    }
    return true;
  }

  /**
   * Returns the code of the helper called directly from the initializer by the given invoke, if it
   * has been analyzed.
   */
  IRCode getAnalyzedHelperCode(InvokeMethod invoke) {
    AnalyzedCode helper = helpers.get(invoke);
    if (helper == null || !helper.parent.isInitializer()) {
      return null;
    }
    return helper.code;
  }

  /**
   * Returns the fields that may be read by the given instruction. For calls to analyzed helpers
   * this is the set of fields read by the helper, instead of the unknown read set of the invoke.
   */
  private AbstractFieldSet readSet(Instruction instruction) {
    if (instruction.isInvokeMethod()) {
      AnalyzedCode helper = helpers.get(instruction.asInvokeMethod());
      if (helper != null) {
        return getOrComputeReadSet(helper);
      }
    }
    return instruction.readSet(appView, context);
  }

  private AbstractFieldSet getOrComputeReadSet(AnalyzedCode analyzedCode) {
    if (analyzedCode.readSet == null) {
      ConcreteMutableFieldSet result = new ConcreteMutableFieldSet();
      for (Instruction instruction : analyzedCode.code.instructions()) {
        AbstractFieldSet instructionReadSet = readSet(instruction);
        if (instructionReadSet.isTop()) {
          analyzedCode.readSet = UnknownFieldSet.getInstance();
          return analyzedCode.readSet;
        }
        if (!instructionReadSet.isBottom()) {
          result.addAll(instructionReadSet.asConcreteFieldSet());
        }
      }
      analyzedCode.readSet = result.isEmpty() ? EmptyFieldSet.getInstance() : result;
    }
    return analyzedCode.readSet;
  }

  abstract void updateFieldOptimizationInfo(
      DexClassAndField field, FieldInstruction fieldPut, Value value);
}
