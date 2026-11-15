// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.RESOURCE_CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.google.common.base.Predicates.not;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.ResourceConstNumber;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.optimize.compose.ComposeReferences;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;

/**
 * Simplifies phis for defaulted parameters in {@code @Composable} functions when the default value
 * branch is unconditionally taken on initial composition and the parameter itself is only used in
 * phis along {@code Composer.skipToGroupEnd()} paths.
 *
 * <p>For a {@code @Composable} function with a defaulted parameter {@code param}, the Compose
 * compiler generates code with the following structure:
 *
 * <pre>
 *   if ((defaultFlags & mask) != 0) {
 *     dirty |= ...;
 *   } else if ((changed & ...) == 0) {
 *     dirty |= composer.changed(param) ? ... : ...;
 *   }
 *   if (composer.shouldExecute(...)) {
 *     composer.startDefaults();
 *     if ((changed & 1) == 0 || composer.getDefaultsInvalid()) {
 *       if ((defaultFlags & mask) != 0) {
 *         param = defaultValue;
 *       }
 *     } else {
 *       composer.skipToGroupEnd();
 *     }
 *     // phi1 = phi(defaultValue, param)
 *     composer.endDefaults();
 *     ... use phi1 ...
 *   } else {
 *     composer.skipToGroupEnd();
 *   }
 *   // phi2 = phi(phi1, param)
 *   ScopeUpdateScope scope = composer.endRestartGroup();
 *   if (scope != null) {
 *     scope.updateScope((composer, force) -> Composable(..., phi2, ..., changed | 1, defaultFlags));
 *   }
 * </pre>
 *
 * <p>When all external callers omit {@code param} (passing a dummy constant such as {@code 0} or
 * {@code null} and setting {@code mask} in {@code defaultFlags}), the restart-group lambda still
 * passes {@code phi2} back as {@code param} on recomposition. Because of this cycle ({@code param
 * -> phi1 -> phi2 -> RecomposeLambda -> param}), {@code ArgumentPropagator} cannot prove that
 * {@code param} is constant across all call sites.
 *
 * <p>However, {@code defaultFlags} is invariant across both external callers and the restart-group
 * lambda. Once {@code ArgumentPropagator} propagates {@code defaultFlags} and {@code
 * BranchSimplifier} folds {@code (defaultFlags & mask) != 0} to {@code true}, the {@code
 * composer.changed(param)} check is dead and the initial-composition branch unconditionally assigns
 * {@code defaultValue}. At that point, {@code param} is only used as a phi operand on paths
 * dominated by {@code composer.skipToGroupEnd()}.
 *
 * <p>Since {@code skipToGroupEnd()} is only executed on recomposition—after an initial composition
 * has already taken the default-value branch and captured {@code defaultValue} into the
 * restart-group lambda—{@code param} is guaranteed to equal {@code defaultValue} whenever {@code
 * skipToGroupEnd()} is reached. Replacing {@code param} with {@code defaultValue} collapses {@code
 * phi1} and {@code phi2} into trivial phis and leaves {@code param} unused so that {@code
 * EffectivelyUnusedArgumentsAnalysis} can remove it from the method signature.
 */
public class UnusedComposableArgumentSimplifier
    extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  public UnusedComposableArgumentSimplifier(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "UnusedComposableArgumentSimplifier";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.getComposeOptions().isUnusedComposableArgumentSimplifierEnabled()
        && appView.getComposeReferences().isComposable(code.context());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator entryBlockIterator = null;
    Set<BasicBlock> skipToGroupEndBlocks = null;
    boolean hasChanged = false;
    for (Value rawArgumentValue : code.collectArguments()) {
      // Unwrap an Assume instruction (e.g., DynamicTypeAssumption or NonNullAssumption) on the
      // argument if the Assume is its only user.
      Value argumentValue = rawArgumentValue;
      if (!argumentValue.hasPhiUsers()
          && argumentValue.hasSingleUniqueUser(Instruction::isAssume)) {
        Assume argumentAssume = argumentValue.singleUniqueUser().asAssume();
        argumentValue = argumentAssume.outValue();
      }
      // The argument must have no non-phi users; any uses in composer.changed(param) or
      // conditional default assignments must already have been simplified away.
      if (argumentValue.hasUsers() || !argumentValue.hasPhiUsers()) {
        continue;
      }
      if (skipToGroupEndBlocks == null) {
        skipToGroupEndBlocks = computeSkipToGroupEndBlocks(code);
        if (skipToGroupEndBlocks.isEmpty() || skipToGroupEndBlocks.contains(code.entryBlock())) {
          return noChange();
        }
      }
      Value defaultValue = findDefaultValue(argumentValue, skipToGroupEndBlocks);
      if (defaultValue == null) {
        continue;
      }
      if (entryBlockIterator == null) {
        entryBlockIterator = code.entryBlock().listIterator();
        entryBlockIterator.positionBeforeNextInstructionThatMatches(not(Instruction::isArgument));
      }
      defaultValue =
          hoistDefaultValueToEntryBlockIfNeeded(
              code, defaultValue, entryBlockIterator, affectedValues);
      if (defaultValue == null) {
        continue;
      }
      // Replace the argument with the hoisted default value.
      argumentValue.replaceUsers(defaultValue, affectedValues);
      hasChanged = true;
    }
    if (hasChanged) {
      code.removeAllDeadAndTrivialPhis(affectedValues);
      affectedValues.narrowingWithAssumeRemoval(appView, code);
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  /**
   * Returns all basic blocks dominated by an invoke to {@code Composer.skipToGroupEnd()}. Any phi
   * predecessor in this set is only reachable when skipping a group during recomposition.
   */
  private Set<BasicBlock> computeSkipToGroupEndBlocks(IRCode code) {
    Set<BasicBlock> skipToGroupEndBlocks = new ReferenceOpenHashSet<>();
    DominatorTree dominatorTree = null;
    ComposeReferences references = appView.getComposeReferences();
    for (InvokeMethodWithReceiver invoke :
        code.<InvokeMethodWithReceiver>instructions(
            Instruction::isInvokeMethodWithDynamicDispatch)) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod.getName().isIdenticalTo(references.skipToGroupEndName)
          && invokedMethod.getArity() == 0
          && invokedMethod.getReturnType().isVoidType()
          && invoke
              .getFirstArgument()
              .getAliasedValue(AssumeAndCheckCastAliasedValueConfiguration.getInstance())
              .isArgument()) {
        if (dominatorTree == null) {
          dominatorTree = new DominatorTree(code);
        }
        dominatorTree.dominatedBlocks(invoke.getBlock(), skipToGroupEndBlocks);
      }
    }
    return skipToGroupEndBlocks;
  }

  /**
   * Checks whether all phi users of {@code argumentValue} merge {@code argumentValue} (exclusively
   * from predecessors in {@code skipToGroupEndBlocks}) with a single non-phi {@code defaultValue},
   * and returns that {@code defaultValue} (or {@code null} if the pattern does not match).
   */
  @SuppressWarnings("ReferenceEquality")
  private Value findDefaultValue(Value argumentValue, Set<BasicBlock> skipToGroupEndBlocks) {
    Set<Phi> phiUsers = argumentValue.uniquePhiUsers();
    Value defaultValue = null;
    for (Phi phi : phiUsers) {
      for (int operandIndex = 0; operandIndex < phi.getOperands().size(); operandIndex++) {
        Value operand = phi.getOperand(operandIndex);
        if (operand == argumentValue) {
          // Every incoming edge carrying the parameter itself must come from a skipToGroupEnd()
          // path.
          if (!skipToGroupEndBlocks.contains(phi.getBlock().getPredecessor(operandIndex))) {
            return null;
          }
        } else if (!phiUsers.contains(operand)) {
          // Any incoming edge not coming from another phi in phiUsers (e.g., phi1 flowing into
          // phi2) must carry the unique defaultValue assigned on the initial-composition path.
          if (defaultValue == null) {
            defaultValue = operand;
          } else if (defaultValue != operand) {
            return null;
          }
        }
      }
    }
    if (defaultValue == null || defaultValue.isPhi()) {
      return null;
    }
    // Verify that the subgraph of phis in phiUsers is a DAG rooted at defaultValue and
    // argumentValue (e.g., phi1 = phi(defaultValue, argumentValue) feeding into
    // phi2 = phi(phi1, argumentValue)), ensuring all phis in phiUsers become trivial once
    // argumentValue is replaced with defaultValue.
    if (!verifyPhiUsersFormDag(phiUsers, argumentValue, defaultValue)) {
      return null;
    }
    return defaultValue;
  }

  private static boolean verifyPhiUsersFormDag(
      Set<Phi> phiUsers, Value argumentValue, Value defaultValue) {
    Set<Phi> verified = new ReferenceOpenHashSet<>();
    Set<Phi> visiting = new ReferenceOpenHashSet<>();
    for (Phi phi : phiUsers) {
      if (!verifyPhiAcyclic(phi, phiUsers, argumentValue, defaultValue, visiting, verified)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean verifyPhiAcyclic(
      Phi phi,
      Set<Phi> phiUsers,
      Value argumentValue,
      Value defaultValue,
      Set<Phi> visiting,
      Set<Phi> verified) {
    if (verified.contains(phi)) {
      return true;
    }
    if (!visiting.add(phi)) {
      return false;
    }
    for (Value operand : phi.getOperands()) {
      if (operand == argumentValue || operand == defaultValue) {
        continue;
      }
      assert operand.isPhi() && phiUsers.contains(operand.asPhi());
      if (!verifyPhiAcyclic(
          operand.asPhi(), phiUsers, argumentValue, defaultValue, visiting, verified)) {
        return false;
      }
    }
    visiting.remove(phi);
    verified.add(phi);
    return true;
  }

  /**
   * Hoists {@code defaultValue} (and its optional {@link Assume} instruction) to the entry block if
   * it is not already defined there.
   *
   * <p>The default value is originally materialized inside the {@code composer.shouldExecute(...)}
   * / {@code startDefaults()} branch, which does not dominate the outer {@code skipToGroupEnd()}
   * branch. Hoisting it to the entry block ensures that {@code defaultValue} dominates all phi
   * predecessors where {@code argumentValue} is replaced.
   */
  @SuppressWarnings("ReferenceEquality")
  private Value hoistDefaultValueToEntryBlockIfNeeded(
      IRCode code,
      Value defaultValue,
      InstructionListIterator entryBlockIterator,
      AffectedValues affectedValues) {
    if (defaultValue.getBlock().isEntry()) {
      return defaultValue;
    }
    // If defaultValue is defined by an Assume (such as a non-null assumption on a StaticGet),
    // hoist both the underlying root instruction and a fresh Assume to the entry block.
    Assume assume = defaultValue.getDefinition().asAssume();
    Value rootValue;
    if (assume != null) {
      rootValue = assume.src();
      // Check that the root value is defined in the same block and that it is only used by the
      // assume.
      if (rootValue.isPhi()
          || rootValue.getBlock() != assume.getBlock()
          || rootValue.hasPhiUsers()
          || !rootValue.hasSingleUniqueUser(assume)) {
        return null;
      }
    } else {
      rootValue = defaultValue;
    }
    Instruction rootDefinition = rootValue.getDefinition();
    if (!canHoistToEntryBlock(code, rootDefinition)) {
      return null;
    }
    // Hoist root instruction.
    Instruction hoistedRoot = createMaterializingInstruction(code, rootDefinition);
    hoistedRoot.setPosition(code.getEntryPosition());
    entryBlockIterator.add(hoistedRoot);
    rootValue.replaceUsers(hoistedRoot.outValue(), affectedValues);
    rootDefinition.removeOrReplaceByDebugLocalRead();
    Value hoistedRootValue = hoistedRoot.outValue();

    // Hoist assume instruction.
    if (assume == null) {
      return hoistedRootValue;
    }
    Value hoistedAssumeValue = code.createValue(assume.outValue().getType());
    Assume hoistedAssume =
        Assume.create(
            assume.getDynamicType(),
            hoistedAssumeValue,
            hoistedRootValue,
            hoistedRoot,
            appView,
            code.context());
    hoistedAssume.setPosition(hoistedRoot.getPosition());
    entryBlockIterator.add(hoistedAssume);
    defaultValue.replaceUsers(hoistedAssumeValue, affectedValues);
    assume.removeOrReplaceByDebugLocalRead();
    return hoistedAssumeValue;
  }

  private boolean canHoistToEntryBlock(IRCode code, Instruction instruction) {
    if (instruction.getBlock().hasCatchHandlers() || code.entryBlock().hasCatchHandlers()) {
      return false;
    }
    if (instruction.instructionTypeCanThrow() && code.metadata().mayHaveMonitorInstruction()) {
      return false;
    }
    // TODO(b/451417434): Evaluate how many default arguments fall outside this handling.
    switch (instruction.opcode()) {
      case CONST_CLASS:
        return !instruction.instructionMayHaveSideEffects(appView, code.context());
      case CONST_NUMBER:
      case RESOURCE_CONST_NUMBER:
      case CONST_STRING:
      case DEX_ITEM_BASED_CONST_STRING:
        return true;
      case STATIC_GET:
        return canHoistStaticGetToEntryBlock(code.context(), instruction.asStaticGet());
      default:
        return false;
    }
  }

  // TODO(b/451417434): Evaluate soundness. How often do we have static-gets that are not provably
  // side effect free?
  //  Should we add -assumenosideeffects rules for the most common ones, or model them?
  private boolean canHoistStaticGetToEntryBlock(ProgramMethod context, StaticGet staticGet) {
    // Default values in Compose are often companion/singleton field reads (e.g.,
    // Modifier.Companion.$$INSTANCE, Color.Unspecified, TextUnit.Unspecified) whose holder <clinit>
    // is not provably side-effect free. Because the non-skipping default branch is always executed
    // on initial composition before any recomposition can take a skipToGroupEnd() branch, the
    // holder class is already initialized on recomposition and hoisting the StaticGet within this
    // @Composable method is safe under CLASS_ALREADY_INITIALIZED.
    if (staticGet.instructionInstanceCanThrow(
        appView, context, SideEffectAssumption.CLASS_ALREADY_INITIALIZED)) {
      return false;
    }
    if (!AndroidApiLevelUtils.isApiSafeForReference(staticGet.getField().getType(), appView)) {
      return false;
    }
    SingleFieldResolutionResult<?> resolutionResult =
        appInfo().resolveField(staticGet.getField()).asSingleFieldResolutionResult();
    if (resolutionResult == null
        || !resolutionResult.isSingleProgramFieldResolutionResult()
        || !resolutionResult.getInitialResolutionHolder().isResolvable(appView)) {
      return false;
    }
    ProgramField field = resolutionResult.getSingleProgramField();
    return !field.getAccessFlags().isVolatile()
        && (appView.hasLiveness()
            ? field.isEffectivelyFinal(appView.withLiveness())
            : field.getAccessFlags().isFinal())
        && !appView.getKeepInfo(field).isPinned(options);
  }

  private static Instruction createMaterializingInstruction(IRCode code, Instruction instruction) {
    switch (instruction.opcode()) {
      case CONST_CLASS:
        return ConstClass.copyOf(code, instruction.asConstClass());
      case CONST_NUMBER:
        return ConstNumber.copyOf(code, instruction.asConstNumber());
      case RESOURCE_CONST_NUMBER:
        return ResourceConstNumber.copyOf(code, instruction.asResourceConstNumber());
      case CONST_STRING:
        return ConstString.copyOf(code, instruction.asConstString());
      case DEX_ITEM_BASED_CONST_STRING:
        return DexItemBasedConstString.copyOf(code, instruction.asDexItemBasedConstString());
      case STATIC_GET:
        return StaticGet.copyOf(code, instruction.asStaticGet());
      default:
        throw new Unreachable();
    }
  }
}
