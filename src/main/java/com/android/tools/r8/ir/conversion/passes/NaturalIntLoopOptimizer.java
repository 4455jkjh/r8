// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.optimize.AssumeInserter.findDominatedPredecessorIndexesInPhi;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArithmeticBinop;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.AssumeIntRange;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.internal.collections.WorkList;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The NaturalIntLoopRemover detects natural loops on an integer iterator and computes the exact
 * number of iterations if possible. If the number of iterations is known to be 1, it transforms the
 * loop into a straight-line single iteration of the loop body.
 *
 * <p>This relies on the CodeRewriter to rewrite known array length upfront. Generally this can
 * pattern match fori and for loops with any initial value and increment, but this should be
 * extended for while loop support.
 */
public class NaturalIntLoopOptimizer extends CodeRewriterPass<AppInfo> {

  public NaturalIntLoopOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "NaturalIntLoopRemover";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean loopRemoved = false;
    AffectedValues affectedValues = new AffectedValues();
    Map<Value, Value> replacements = new IdentityHashMap<>();
    for (BasicBlock comparisonBlockCandidate : code.blocks) {
      if (isComparisonBlock(comparisonBlockCandidate)) {
        loopRemoved |=
            tryRemoveLoop(
                code, comparisonBlockCandidate.exit().asIf(), affectedValues, replacements);
      }
    }
    if (!replacements.isEmpty()) {
      updateDominatedUsers(code, replacements);
    }
    if (loopRemoved) {
      code.removeAllDeadAndTrivialPhis(affectedValues);
      affectedValues.narrowingWithAssumeRemoval(appView, code);
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(loopRemoved);
  }

  private void updateDominatedUsers(IRCode code, Map<Value, Value> replacements) {
    DominatorTree dominatorTree = new DominatorTree(code);
    replacements.forEach(
        (loopPhi, assumedValue) -> {
          BasicBlock insertionBlock = assumedValue.getBlock();
          Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
          Map<Phi, IntList> dominatedPhiUsers = new IdentityHashMap<>();
          for (Instruction user : loopPhi.uniqueUsers()) {
            if (user != assumedValue.getDefinition()
                && dominatorTree.dominatedBy(user.getBlock(), insertionBlock)) {
              dominatedUsers.add(user);
            }
          }
          for (Phi user : loopPhi.uniquePhiUsers()) {
            IntList dominatedPredecessorIndices =
                findDominatedPredecessorIndexesInPhi(
                    user, loopPhi, block -> dominatorTree.dominatedBy(block, insertionBlock));
            if (!dominatedPredecessorIndices.isEmpty()) {
              dominatedPhiUsers.put(user, dominatedPredecessorIndices);
            }
          }
          loopPhi.replaceSelectiveUsers(assumedValue, dominatedUsers, dominatedPhiUsers);
        });
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    // This is relevant only if a loop may be present, which implies at least 4 blocks.
    return appView.options().enableLoopUnrolling
        && code.metadata().mayHaveIf()
        && code.getBlocks().size() >= 4;
  }

  @SuppressWarnings("UnnecessaryParentheses")
  private boolean isComparisonBlock(BasicBlock comparisonBlockCandidate) {
    if (!comparisonBlockCandidate.exit().isIf()) {
      return false;
    }
    for (Instruction instruction : comparisonBlockCandidate.getInstructions()) {
      if (instruction.isIf()) {
        return true;
      }
      if (!instruction.isConstNumber()) {
        return false;
      }
    }
    throw new Unreachable();
  }

  private boolean tryRemoveLoop(
      IRCode code, If comparison, AffectedValues affectedValues, Map<Value, Value> replacements) {
    Phi loopPhi = computeLoopPhi(comparison);
    if (loopPhi == null) {
      return false;
    }

    ConstNumber comparisonValue = null;
    if (!comparison.isZeroTest()) {
      comparisonValue =
          comparison
              .getOperand(1 - comparison.inValues().indexOf(loopPhi))
              .getDefinition()
              .asConstNumber();
      assert comparisonValue != null;
    }

    NaturalIntLoopWithKnowIterations.Builder builder =
        NaturalIntLoopWithKnowIterations.builder(comparison, comparisonValue, loopPhi);
    if (!analyzeLoopIterator(comparison, loopPhi, builder)) {
      return false;
    }

    Set<BasicBlock> loopBody = computeLoopBody(builder.getBackPredecessor(), comparison.getBlock());
    if (loopBody == null) {
      return false;
    }
    if (loopBody.contains(builder.getLoopEntry())) {
      assert false;
      return false;
    }
    builder.setLoopBody(loopBody);

    if (!analyzeLoopExit(loopBody, comparison, builder)) {
      return false;
    }
    if (!analyzePhiUses(loopBody, comparison, builder)) {
      return false;
    }

    NaturalIntLoopWithKnowIterations loop = builder.build();

    if (loop.has1Iteration()) {
      loop.remove1IterationLoop(affectedValues);
      return true;
    }
    if (tryOptimizeUnboxedEnumValuesLoop(code, loop)) {
      return true;
    }
    if (appView.hasClassHierarchy()) {
      tryInsertAssumeRangeInstruction(code, loop, replacements);
    }
    return false;
  }

  /**
   * Looks for loops on the form `for (int i = 0; i < N; i++) { int j = values[i]; ... }`, where
   * `values` is the unboxed $VALUES array that results from calling the helper method `int[]
   * EnumUnboxingSharedUtility.values(int size)`.
   *
   * <p>Since this $VALUES array is defined as [1, 2, 3, 4, ..., N-1], such loops can be optimized
   * into: `for (int j = 1; j < N + 1, j++) { ... }`. This avoids the need for calling
   * EnumUnboxingSharedUtility#values, which saves an array allocation.
   */
  private boolean tryOptimizeUnboxedEnumValuesLoop(
      IRCode code, NaturalIntLoopWithKnowIterations loop) {
    // Check that the loop has `int i = 0` and `i++`.
    if (loop.initCounter.getIntValue() != 0 || loop.counterIncrement != 1) {
      return false;
    }
    // Check that the back edge goes to the loop body entry.
    if (loop.loopBodyEntry != loop.backPredecessor) {
      return false;
    }
    // Check that the If isntruction is on the form `ifge <phi>, <const>`.
    if (loop.comparison.getType() != IfType.GE
        || loop.comparison.rhs().getConstIntValueIfNonNegative() < 0) {
      return false;
    }
    // Check if the loop phi is only used by an ArrayGet instruction (other than the known If and
    // and Add users).
    Value loopIndexValue = loop.comparison.lhs();
    if (loopIndexValue.hasDebugUsers() || loopIndexValue.hasPhiUsers()) {
      return false;
    }
    Instruction singleLoopIndexUser = null;
    for (Instruction loopIndexUser : loopIndexValue.aliasedUsers()) {
      if (loopIndexUser.isAssumeIntRange()) {
        if (loopIndexUser.outValue().hasDebugUsers() || loopIndexUser.outValue().hasPhiUsers()) {
          return false;
        } else {
          continue;
        }
      } else if (loopIndexUser == loop.comparison
          || loopIndexUser == loop.counterIncrementInstruction) {
        continue;
      }
      if (singleLoopIndexUser == null) {
        singleLoopIndexUser = loopIndexUser;
      } else {
        return false;
      }
    }
    if (singleLoopIndexUser == null || !singleLoopIndexUser.isArrayGet()) {
      return false;
    }
    // Check if the array is defined by a call to EnumUnboxingSharedUtility#values.
    ArrayGet arrayGet = singleLoopIndexUser.asArrayGet();
    Value array = arrayGet.array().getAliasedValue();
    if (!array.isDefinedByInstructionSatisfying(Instruction::isInvokeStatic)) {
      return false;
    }
    InvokeStatic arrayDefinition = array.getDefinition().asInvokeStatic();
    DexMethod invokedMethod = arrayDefinition.getInvokedMethod();
    if (!invokedMethod.getHolderType().isClassType()) {
      return false;
    }
    DexProgramClass holderClass =
        asProgramClassOrNull(appView.definitionFor(invokedMethod.getHolderType()));
    if (holderClass == null
        || !holderClass.getAccessFlags().isSynthetic()
        || !appView.getSyntheticItems().isSynthetic(holderClass)
        || !appView
            .getSyntheticItems()
            .hasKindThatMatches(
                holderClass,
                (kind, naming) -> kind.equals(naming.ENUM_UNBOXING_SHARED_UTILITY_CLASS))
        || !invokedMethod.match(
            dexItemFactory.enumUnboxingSharedUtilityMembers.valuesMethodSignature)) {
      return false;
    }
    int size = arrayDefinition.getFirstOperand().getConstIntValueIfNonNegative();
    if (size < 0) {
      return false;
    }
    // Optimize the loop.
    optimizeUnboxedEnumValuesLoop(code, loop, arrayGet, arrayDefinition, size);
    return true;
  }

  private void optimizeUnboxedEnumValuesLoop(
      IRCode code,
      NaturalIntLoopWithKnowIterations loop,
      ArrayGet arrayGet,
      InvokeStatic invokeValues,
      int size) {
    assert loop != null;
    assert size >= 0;
    // Optimize the loop from `for (int i = 0; i < N; i++) { int j = values[i]; ... }` to
    // `for (int j = 1; j <= N; j++) { ... }`.
    //
    // First update the loop index initialization value from 0 to 1.
    ConstNumber newInitCounter =
        ConstNumber.builder()
            .setValue(1)
            .setFreshOutValue(code, TypeElement.getInt())
            .setPosition(loop.initCounter)
            .build();
    loop.initCounter.getBlock().listIterator(loop.initCounter).add(newInitCounter);
    loop.loopPhi.replaceOperand(loop.initCounter.outValue(), newInitCounter.outValue());
    loop.initCounter.outValue().removePhiUser(loop.loopPhi);

    // Update the loop index end value from N to N+1.
    Value loopEndValue = loop.comparison.rhs();
    int loopEnd = loopEndValue.getConstIntValueIfNonNegative();
    ConstNumber newLoopEnd =
        ConstNumber.builder()
            .setValue(loopEnd + 1)
            .setFreshOutValue(code, TypeElement.getInt())
            .setPosition(loop.comparison)
            .build();
    loop.comparison.getBlock().listIterator().add(newLoopEnd);
    loop.comparison.replaceValue(loopEndValue, newLoopEnd.outValue());

    // Replace the ArrayGet instruction by the new loop index value.
    arrayGet.outValue().replaceUsers(loop.loopPhi);
    arrayGet.removeOrReplaceByDebugLocalRead();

    // Remove the call to EnumUnboxingSharedUtility.values(size) if it is no longer used.
    if (invokeValues.outValue().hasSingleUniqueUserAndNoOtherUsers()) {
      Instruction invokeValuesUser = invokeValues.outValue().singleUniqueUser();
      if (invokeValuesUser.isAssume() && invokeValuesUser.outValue().isUnused()) {
        invokeValuesUser.removeOrReplaceByDebugLocalRead();
      }
    }
    if (invokeValues.hasUnusedOutValue()) {
      invokeValues.removeOrReplaceByDebugLocalRead();
    }
  }

  private void tryInsertAssumeRangeInstruction(
      IRCode code, NaturalIntLoopWithKnowIterations loop, Map<Value, Value> replacements) {
    long loopStart = loop.initCounter.getIntValue();
    int loopDelta = loop.counterIncrement;
    if (loopDelta == 0) {
      return;
    }
    long bound = loop.comparison.isZeroTest() ? 0 : loop.comparisonValue.getIntValue();
    if (loop.target(loopStart) == loop.loopExit) {
      return;
    }
    // This intentionally uses long to correctly handle loops where the loop index overflows,
    // such as: for (int i = 2147483645; i < 5; i++).
    long minInclusive = loopStart;
    long maxInclusive =
        loop.target(bound) != loop.loopExit
            ? loopStart + ((bound - loopStart) / loopDelta) * loopDelta
            : loopStart + ((bound - loopStart - Integer.signum(loopDelta)) / loopDelta) * loopDelta;
    if (loopDelta < 0) {
      minInclusive = maxInclusive;
      maxInclusive = loopStart;
    }
    if (minInclusive > maxInclusive
        || minInclusive < Integer.MIN_VALUE
        || maxInclusive > Integer.MAX_VALUE) {
      return;
    }

    Value assumedValue = code.createValue(TypeElement.getInt());
    replacements.put(loop.loopPhi, assumedValue);
    AssumeIntRange assumeIntRange =
        new AssumeIntRange(assumedValue, loop.loopPhi, (int) minInclusive, (int) maxInclusive);
    assumeIntRange.setPosition(loop.comparison.getPosition());
    loop.loopBodyEntry.listIterator().add(assumeIntRange);
  }

  /**
   * The loop unroller removes phis corresponding to the loop backjump. There are three scenarios:
   * (1) The loop has a single exit point analyzed, phis used outside the loop are replaced by the
   *     value at the end of the loop body.
   * (2) The phis are unused outside the loop, and they are simply removed.
   * (3) The loop has multiple exits and the phis are used outside the loop, this would require
   *     dealing with complex merge point and postponing phis after the loop, we bail out.
   */
  private boolean analyzePhiUses(
      Set<BasicBlock> loopBody, If comparison, NaturalIntLoopWithKnowIterations.Builder builder) {
    // Check for single exit scenario.
    Set<BasicBlock> successors = Sets.newIdentityHashSet();
    for (BasicBlock basicBlock : loopBody) {
      successors.addAll(basicBlock.getSuccessors());
    }
    successors.removeAll(loopBody);
    if (successors.size() == 1) {
      assert successors.iterator().next() == builder.getLoopExit();
      return true;
    }
    // Check phis are unused outside the loop.
    for (Phi phi : comparison.getBlock().getPhis()) {
      for (Instruction use : phi.uniqueUsers()) {
        if (!loopBody.contains(use.getBlock())) {
          return false;
        }
      }
      for (Phi phiUse : phi.uniquePhiUsers()) {
        if (!loopBody.contains(phiUse.getBlock())) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Verifies the loop is well formed: the comparison on the int iterator should jump to a loop exit
   * on one side and to the loop body on the other side.
   */
  private boolean analyzeLoopExit(
      Set<BasicBlock> loopBody, If comparison, NaturalIntLoopWithKnowIterations.Builder builder) {
    if (loopBody.contains(comparison.getTrueTarget())) {
      if (loopBody.contains(comparison.fallthroughBlock())) {
        return false;
      }
      builder.setLoop(comparison.fallthroughBlock(), comparison.getTrueTarget());
    } else {
      if (!loopBody.contains(comparison.fallthroughBlock())) {
        return false;
      }
      builder.setLoop(comparison.getTrueTarget(), comparison.fallthroughBlock());
    }
    return true;
  }

  /**
   * Analyze the int iterator so that it is initialized with a constant int value, and each
   * iteration of the loop increment the iterator by one of the following: i + cst, cst + i or i -
   * cst.
   */
  private boolean analyzeLoopIterator(
      If comparison, Phi loopPhi, NaturalIntLoopWithKnowIterations.Builder builder) {
    for (int i = 0; i < loopPhi.getOperands().size(); i++) {
      Value operand = loopPhi.getOperand(i);
      if (operand.isPhi()) {
        return false;
      }
      BasicBlock predecessor = comparison.getBlock().getPredecessors().get(i);
      if (operand.isConstNumber()) {
        // Initial value of the int iterator.
        if (!operand.getType().isInt() || builder.getLoopEntry() != null) {
          return false;
        }
        builder.setLoopEntry(predecessor);
        builder.setInitCounter(operand.definition.asConstNumber());
      } else if (operand.definition.isAdd()) {
        // Increment of the int iterator of type i + cst or cst + i.
        if (builder.getBackPredecessor() != null) {
          return false;
        }
        builder.setBackPredecessor(predecessor);
        boolean metPhiOperand = false;
        for (Value inValue : operand.definition.inValues()) {
          if (inValue.isConstNumber() && inValue.getType().isInt()) {
            int counterIncrement = inValue.definition.asConstNumber().getIntValue();
            if (counterIncrement == 0 || builder.getCounterIncrement() != 0) {
              return false;
            }
            builder.setCounterIncrement(counterIncrement, operand.definition.asAdd());
          } else if (inValue.getAliasedValue() == loopPhi) {
            if (metPhiOperand) {
              return false;
            }
            metPhiOperand = true;
          } else {
            return false;
          }
        }
      } else if (operand.definition.isSub()) {
        // Increment of the int iterator of type i - cst.
        if (builder.getBackPredecessor() != null) {
          return false;
        }
        builder.setBackPredecessor(predecessor);
        Sub sub = operand.definition.asSub();
        if (sub.leftValue().getAliasedValue() != loopPhi) {
          return false;
        }
        Value subValue = sub.rightValue();
        if (subValue.isConstNumber() && subValue.getType().isInt()) {
          assert builder.getCounterIncrement() == 0;
          int counterIncrement = -subValue.definition.asConstNumber().getIntValue();
          if (counterIncrement == 0) {
            return false;
          }
          builder.setCounterIncrement(counterIncrement, sub);
        } else {
          return false;
        }
      } else {
        return false;
      }
    }
    assert builder.getLoopEntry() != null;
    assert builder.getLoopEntry().exit().isGoto();
    assert builder.getBackPredecessor() != null;
    assert builder.getBackPredecessor().exit().isGoto();
    assert builder.getCounterIncrement() != 0;
    return true;
  }

  /**
   * Analyze the loop comparison so that it compares a loopPhi with a constant, else answers null.
   */
  private Phi computeLoopPhi(If comparison) {
    Phi loopPhi = null;
    if (comparison.isZeroTest()) {
      if (comparison.lhs().isPhi()) {
        loopPhi = comparison.lhs().asPhi();
      }
    } else if (comparison.rhs().isConstNumber() && comparison.lhs().isPhi()) {
      loopPhi = comparison.lhs().asPhi();
    } else if (comparison.lhs().isConstNumber() && comparison.rhs().isPhi()) {
      loopPhi = comparison.rhs().asPhi();
    }
    if (loopPhi == null) {
      return null;
    }
    if (loopPhi.getOperands().size() != 2) {
      return null;
    }
    if (loopPhi.getBlock() != comparison.getBlock()) {
      return null;
    }
    return loopPhi;
  }

  /**
   * Natural int loop structure and terminology. <code>
   *         v
   *     Loop Entry
   *     int i = 0;    v < < < < < < < < < < < <
   *         v         v                       ^
   *       Comparison Block                    ^
   *       if (i < constant)                   ^
   *       v               v                   ^
   *   Loop Exit         Loop Body Entry       ^
   *       v             i++;                  ^
   *   Method Exit         v                   ^
   *       v               > > > > > > > > > > ^
   * </code>
   */
  static class NaturalIntLoopWithKnowIterations {

    private final ConstNumber initCounter;
    private final int counterIncrement;
    private final ArithmeticBinop counterIncrementInstruction;
    private final If comparison;
    private final ConstNumber comparisonValue;
    private final BasicBlock loopExit;
    private final BasicBlock loopBodyEntry;
    private final BasicBlock backPredecessor;
    private final Set<BasicBlock> loopBody;
    private final Phi loopPhi;

    NaturalIntLoopWithKnowIterations(
        ConstNumber initCounter,
        int counterIncrement,
        ArithmeticBinop counterIncrementInstruction,
        If comparison,
        ConstNumber comparisonValue,
        BasicBlock loopExit,
        BasicBlock loopBodyEntry,
        BasicBlock backPredecessor,
        Set<BasicBlock> loopBody,
        Phi loopPhi) {
      this.initCounter = initCounter;
      this.counterIncrement = counterIncrement;
      this.counterIncrementInstruction = counterIncrementInstruction;
      this.comparison = comparison;
      this.comparisonValue = comparisonValue;
      this.loopExit = loopExit;
      this.loopBodyEntry = loopBodyEntry;
      this.backPredecessor = backPredecessor;
      this.loopBody = loopBody;
      this.loopPhi = loopPhi;
    }

    static class Builder {

      private final If comparison;
      private final ConstNumber comparisonValue;
      private final Phi loopPhi;

      private ConstNumber initCounter;
      private int counterIncrement;
      private ArithmeticBinop counterIncrementInstruction;
      private BasicBlock loopExit;
      private BasicBlock loopBodyEntry;
      private BasicBlock loopEntry;
      private BasicBlock backPredecessor;
      private Set<BasicBlock> loopBody;

      Builder(If comparison, ConstNumber comparisonValue, Phi loopPhi) {
        this.comparison = comparison;
        this.comparisonValue = comparisonValue;
        this.loopPhi = loopPhi;
      }

      public void setInitCounter(ConstNumber initCounter) {
        this.initCounter = initCounter;
      }

      public int getCounterIncrement() {
        return counterIncrement;
      }

      public void setCounterIncrement(
          int counterIncrement, ArithmeticBinop counterIncrementInstruction) {
        this.counterIncrement = counterIncrement;
        this.counterIncrementInstruction = counterIncrementInstruction;
      }

      public BasicBlock getLoopEntry() {
        return loopEntry;
      }

      public void setLoopEntry(BasicBlock loopEntry) {
        this.loopEntry = loopEntry;
      }

      public BasicBlock getBackPredecessor() {
        return backPredecessor;
      }

      public void setBackPredecessor(BasicBlock backPredecessor) {
        this.backPredecessor = backPredecessor;
      }

      public void setLoop(BasicBlock loopExit, BasicBlock loopBodyEntry) {
        this.loopExit = loopExit;
        this.loopBodyEntry = loopBodyEntry;
      }

      public BasicBlock getLoopExit() {
        return loopExit;
      }

      public void setLoopBody(Set<BasicBlock> loopBody) {
        this.loopBody = loopBody;
      }

      public NaturalIntLoopWithKnowIterations build() {
        return new NaturalIntLoopWithKnowIterations(
            initCounter,
            counterIncrement,
            counterIncrementInstruction,
            comparison,
            comparisonValue,
            loopExit,
            loopBodyEntry,
            backPredecessor,
            loopBody,
            loopPhi);
      }
    }

    static Builder builder(If comparison, ConstNumber comparisonValue, Phi loopPhi) {
      return new Builder(comparison, comparisonValue, loopPhi);
    }

    private BasicBlock target(long phiValue) {
      return target((int) phiValue);
    }

    private BasicBlock target(int phiValue) {
      if (comparison.isZeroTest()) {
        return comparison.targetFromCondition(Integer.signum(phiValue));
      }
      if (comparison.rhs().isConstNumber()) {
        int comp = comparison.rhs().getDefinition().asConstNumber().getIntValue();
        return comparison.targetFromCondition(Integer.signum(phiValue - comp));
      }
      int comp = comparison.lhs().getDefinition().asConstNumber().getIntValue();
      return comparison.targetFromCondition(Integer.signum(comp - phiValue));
    }

    public boolean has1Iteration() {
      return target(initCounter.getIntValue()) == loopBodyEntry
          && target(initCounter.getIntValue() + counterIncrement) == loopExit;
    }

    private void remove1IterationLoop(AffectedValues affectedValues) {
      BasicBlock comparisonBlock = comparison.getBlock();
      updatePhis(comparisonBlock, affectedValues);
      patchControlFlow(comparisonBlock);
    }

    private void patchControlFlow(BasicBlock comparisonBlock) {
      assert loopExit.getPhis().isEmpty(); // Edges should be split.
      comparisonBlock.replaceLastInstruction(new Goto());
      comparisonBlock.removeSuccessor(loopExit);

      backPredecessor.replaceSuccessor(comparisonBlock, loopExit);
      backPredecessor.replaceLastInstruction(new Goto());
      comparisonBlock.removePredecessor(backPredecessor);
      loopExit.replacePredecessor(comparisonBlock, backPredecessor);
    }

    private void updatePhis(BasicBlock comparisonBlock, AffectedValues affectedValues) {
      int backIndex = comparisonBlock.getPredecessors().indexOf(backPredecessor);
      for (Phi phi : comparisonBlock.getPhis()) {
        Value loopEntryValue = phi.getOperand(1 - backIndex);
        Value loopExitValue = phi.getOperand(backIndex);
        if (loopExitValue.isPhi() && comparisonBlock.getPhis().contains(loopExitValue.asPhi())) {
          loopExitValue = loopExitValue.asPhi().getOperand(1 - backIndex);
        }
        assert !loopExitValue.isPhi() || !comparisonBlock.getPhis().contains(loopExitValue.asPhi());
        for (Instruction uniqueUser : phi.uniqueUsers()) {
          if (loopBody.contains(uniqueUser.getBlock())) {
            uniqueUser.replaceValue(phi, loopEntryValue, affectedValues);
          } else {
            uniqueUser.replaceValue(phi, loopExitValue, affectedValues);
          }
        }
        for (Phi phiUser : phi.uniquePhiUsers()) {
          if (loopBody.contains(phiUser.getBlock())) {
            phiUser.replaceOperand(phi, loopEntryValue, affectedValues);
          } else {
            phiUser.replaceOperand(phi, loopExitValue, affectedValues);
          }
        }
      }
    }
  }

  private Set<BasicBlock> computeLoopBody(BasicBlock backPredecessor, BasicBlock comparisonBlock) {
    WorkList<BasicBlock> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(backPredecessor);
    workList.markAsSeen(comparisonBlock);
    while (!workList.isEmpty()) {
      BasicBlock basicBlock = workList.next();
      if (basicBlock.isEntry()) {
        // This can happen in loops with multiple entries (Duff device, etc.).
        // Such loops are not generated by javac so we assume they are uncommon.
        return null;
      }
      for (BasicBlock predecessor : basicBlock.getPredecessors()) {
        workList.addIfNotSeen(predecessor);
      }
    }
    return workList.getSeenSet();
  }
}
