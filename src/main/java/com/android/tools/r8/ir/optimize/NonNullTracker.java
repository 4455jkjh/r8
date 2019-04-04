// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.OptimizationFeedback;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class NonNullTracker {

  private final AppView<? extends AppInfo> appView;

  public NonNullTracker(AppView<? extends AppInfo> appView) {
    this.appView = appView;
  }

  @VisibleForTesting
  static boolean throwsOnNullInput(Instruction instruction) {
    return (instruction.isInvokeMethodWithReceiver() && !instruction.isInvokeDirect())
        || instruction.isInstanceGet()
        || instruction.isInstancePut()
        || instruction.isArrayGet()
        || instruction.isArrayPut()
        || instruction.isArrayLength()
        || instruction.isMonitor();
  }

  private Value getNonNullInput(Instruction instruction) {
    if (instruction.isInvokeMethodWithReceiver()) {
      return instruction.asInvokeMethodWithReceiver().getReceiver();
    } else if (instruction.isInstanceGet()) {
      return instruction.asInstanceGet().object();
    } else if (instruction.isInstancePut()) {
      return instruction.asInstancePut().object();
    } else if (instruction.isArrayGet()) {
      return instruction.asArrayGet().array();
    } else if (instruction.isArrayPut()) {
      return instruction.asArrayPut().array();
    } else if (instruction.isArrayLength()) {
      return instruction.asArrayLength().array();
    } else if (instruction.isMonitor()) {
      return instruction.asMonitor().object();
    }
    throw new Unreachable("Should conform to throwsOnNullInput.");
  }

  public void addNonNull(IRCode code) {
    addNonNullInPart(code, code.blocks.listIterator(), b -> true);
  }

  public void addNonNullInPart(
      IRCode code, ListIterator<BasicBlock> blockIterator, Predicate<BasicBlock> blockTester) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Set<Value> knownToBeNonNullValues = Sets.newIdentityHashSet();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (!blockTester.test(block)) {
        continue;
      }
      // Add non-null after
      // 1) invocations that call non-overridable library methods that are known to return non null.
      // 2) instructions that implicitly indicate receiver/array is not null.
      // 3) parameters that are not null after the invocation.
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isInvokeMethod()
            && appView
                .dexItemFactory()
                .libraryMethodsReturningNonNull
                .contains(current.asInvokeMethod().getInvokedMethod())) {
          Value knownToBeNonNullValue = current.outValue();
          // Avoid adding redundant non-null instruction.
          // Otherwise, we will have something like:
          // non_null_rcv <- non-null(rcv)
          // ...
          // another_rcv <- non-null(non_null_rcv)
          if (knownToBeNonNullValue != null && isNonNullCandidate(knownToBeNonNullValue)) {
            knownToBeNonNullValues.add(knownToBeNonNullValue);
          }
        }
        if (throwsOnNullInput(current)) {
          Value knownToBeNonNullValue = getNonNullInput(current);
          if (isNonNullCandidate(knownToBeNonNullValue)) {
            knownToBeNonNullValues.add(knownToBeNonNullValue);
          }
        }
        if (current.isInvokeMethod() && !current.isInvokePolymorphic()) {
          DexEncodedMethod singleTarget = null;
          if (appView.enableWholeProgramOptimizations()) {
            assert appView.appInfo().hasLiveness();
            singleTarget =
                current
                    .asInvokeMethod()
                    .lookupSingleTarget(
                        appView.appInfo().withLiveness(), code.method.method.holder);
          } else {
            // Even in D8, invoke-{direct|static} can be resolved without liveness.
            // Due to the incremental compilation, though, it is allowed only if the holder of the
            // invoked method is same as that of the method we are processing now.
            DexMethod invokedMethod = current.asInvokeMethod().getInvokedMethod();
            if (invokedMethod.holder == code.method.method.holder) {
              if (current.isInvokeDirect()) {
                singleTarget = appView.appInfo().lookupDirectTarget(invokedMethod);
              } else if (current.isInvokeStatic()) {
                singleTarget = appView.appInfo().lookupStaticTarget(invokedMethod);
              }
            }
          }
          if (singleTarget != null
              && singleTarget.getOptimizationInfo().getNonNullParamOnNormalExits() != null) {
            BitSet facts = singleTarget.getOptimizationInfo().getNonNullParamOnNormalExits();
            for (int i = 0; i < current.inValues().size(); i++) {
              if (facts.get(i)) {
                Value knownToBeNonNullValue = current.inValues().get(i);
                if (isNonNullCandidate(knownToBeNonNullValue)) {
                  knownToBeNonNullValues.add(knownToBeNonNullValue);
                }
              }
            }
          }
        }
        if (!knownToBeNonNullValues.isEmpty()) {
          addNonNullForValues(
              code,
              blockIterator,
              block,
              iterator,
              current,
              knownToBeNonNullValues,
              affectedValues);
          knownToBeNonNullValues.clear();
        }
      }

      // Add non-null on top of the successor block if the current block ends with a null check.
      if (block.exit().isIf() && block.exit().asIf().isZeroTest()) {
        // if v EQ blockX
        // ... (fallthrough)
        // blockX: ...
        //
        //   ~>
        //
        // if v EQ blockX
        // non_null_value <- non-null(v)
        // ...
        // blockX: ...
        //
        // or
        //
        // if v NE blockY
        // ...
        // blockY: ...
        //
        //   ~>
        //
        // blockY: non_null_value <- non-null(v)
        // ...
        If theIf = block.exit().asIf();
        Value knownToBeNonNullValue = theIf.inValues().get(0);
        // Avoid adding redundant non-null instruction.
        if (isNonNullCandidate(knownToBeNonNullValue)) {
          BasicBlock target = theIf.targetFromNonNullObject();
          // Ignore uncommon empty blocks.
          if (!target.isEmpty()) {
            DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
            // Make sure there are no paths to the target block without passing the current block.
            if (dominatorTree.dominatedBy(target, block)) {
              // Collect users of the original value that are dominated by the target block.
              Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
              Map<Phi, IntList> dominatedPhiUsersWithPositions = new IdentityHashMap<>();
              Set<BasicBlock> dominatedBlocks =
                  Sets.newHashSet(dominatorTree.dominatedBlocks(target));
              for (Instruction user : knownToBeNonNullValue.uniqueUsers()) {
                if (dominatedBlocks.contains(user.getBlock())) {
                  dominatedUsers.add(user);
                }
              }
              for (Phi user : knownToBeNonNullValue.uniquePhiUsers()) {
                IntList dominatedPredecessorIndexes = findDominatedPredecessorIndexesInPhi(
                    user, knownToBeNonNullValue, dominatedBlocks);
                if (!dominatedPredecessorIndexes.isEmpty()) {
                  dominatedPhiUsersWithPositions.put(user, dominatedPredecessorIndexes);
                }
              }
              // Avoid adding a non-null for the value without meaningful users.
              if (!dominatedUsers.isEmpty() || !dominatedPhiUsersWithPositions.isEmpty()) {
                TypeLatticeElement typeLattice = knownToBeNonNullValue.getTypeLattice();
                Value nonNullValue =
                    code.createValue(
                        typeLattice.asReferenceTypeLatticeElement().asNotNull(),
                        knownToBeNonNullValue.getLocalInfo());
                affectedValues.addAll(knownToBeNonNullValue.affectedValues());
                NonNull nonNull = new NonNull(nonNullValue, knownToBeNonNullValue, theIf);
                InstructionListIterator targetIterator = target.listIterator();
                nonNull.setPosition(targetIterator.next().getPosition());
                targetIterator.previous();
                targetIterator.add(nonNull);
                knownToBeNonNullValue.replaceSelectiveUsers(
                    nonNullValue, dominatedUsers, dominatedPhiUsersWithPositions);
              }
            }
          }
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView, code.method).narrowing(affectedValues);
    }
  }

  private void addNonNullForValues(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      BasicBlock block,
      InstructionListIterator iterator,
      Instruction current,
      Set<Value> knownToBeNonNullValues,
      Set<Value> affectedValues) {
    // First, if the current block has catch handler, split into two blocks, e.g.,
    //
    // ...x
    // invoke(rcv, ...)
    // ...y
    //
    //   ~>
    //
    // ...x
    // invoke(rcv, ...)
    // goto A
    //
    // A: ...y // blockWithNonNullInstruction
    boolean split = block.hasCatchHandlers();
    BasicBlock blockWithNonNullInstruction = split ? iterator.split(code, blockIterator) : block;
    DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);

    for (Value knownToBeNonNullValue : knownToBeNonNullValues) {
      // Find all users of the original value that are dominated by either the current block
      // or the new split-off block. Since NPE can be explicitly caught, nullness should be
      // propagated through dominance.
      Set<Instruction> users = knownToBeNonNullValue.uniqueUsers();
      Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
      Map<Phi, IntList> dominatedPhiUsersWithPositions = new IdentityHashMap<>();
      Set<BasicBlock> dominatedBlocks = Sets.newIdentityHashSet();
      for (BasicBlock dominatee : dominatorTree.dominatedBlocks(blockWithNonNullInstruction)) {
        dominatedBlocks.add(dominatee);
        InstructionListIterator dominateeIterator = dominatee.listIterator();
        if (dominatee == blockWithNonNullInstruction && !split) {
          // In the block where the non null instruction will be inserted, skip instructions up to
          // and including the insertion point.
          dominateeIterator.nextUntil(instruction -> instruction == current);
        }
        while (dominateeIterator.hasNext()) {
          Instruction potentialUser = dominateeIterator.next();
          if (users.contains(potentialUser)) {
            dominatedUsers.add(potentialUser);
          }
        }
      }
      for (Phi user : knownToBeNonNullValue.uniquePhiUsers()) {
        IntList dominatedPredecessorIndexes =
            findDominatedPredecessorIndexesInPhi(user, knownToBeNonNullValue, dominatedBlocks);
        if (!dominatedPredecessorIndexes.isEmpty()) {
          dominatedPhiUsersWithPositions.put(user, dominatedPredecessorIndexes);
        }
      }

      // Only insert non-null instruction if it is ever used.
      // Exception: if it is an argument, non-null IR can be used to compute non-null parameter.
      if (knownToBeNonNullValue.isArgument()
          || !dominatedUsers.isEmpty()
          || !dominatedPhiUsersWithPositions.isEmpty()) {
        // Add non-null fake IR, e.g.,
        // ...x
        // invoke(rcv, ...)
        // goto A
        // ...
        // A: non_null_rcv <- non-null(rcv)
        // ...y
        TypeLatticeElement typeLattice = knownToBeNonNullValue.getTypeLattice();
        assert typeLattice.isReference();
        Value nonNullValue =
            code.createValue(
                typeLattice.asReferenceTypeLatticeElement().asNotNull(),
                knownToBeNonNullValue.getLocalInfo());
        affectedValues.addAll(knownToBeNonNullValue.affectedValues());
        NonNull nonNull = new NonNull(nonNullValue, knownToBeNonNullValue, current);
        nonNull.setPosition(current.getPosition());
        if (blockWithNonNullInstruction != block) {
          // If we split, add non-null IR on top of the new split block.
          blockWithNonNullInstruction.listIterator().add(nonNull);
        } else {
          // Otherwise, just add it to the current block at the position of the iterator.
          iterator.add(nonNull);
        }

        // Replace all users of the original value that are dominated by either the current block
        // or the new split-off block.
        knownToBeNonNullValue.replaceSelectiveUsers(
            nonNullValue, dominatedUsers, dominatedPhiUsersWithPositions);
      }
    }
  }

  private IntList findDominatedPredecessorIndexesInPhi(
      Phi user, Value knownToBeNonNullValue, Set<BasicBlock> dominatedBlocks) {
    assert user.getOperands().contains(knownToBeNonNullValue);
    List<Value> operands = user.getOperands();
    List<BasicBlock> predecessors = user.getBlock().getPredecessors();
    assert operands.size() == predecessors.size();

    IntList predecessorIndexes = new IntArrayList();
    int index = 0;
    Iterator<Value> operandIterator = operands.iterator();
    Iterator<BasicBlock> predecessorIterator = predecessors.iterator();
    while (operandIterator.hasNext() && predecessorIterator.hasNext()) {
      Value operand = operandIterator.next();
      BasicBlock predecessor = predecessorIterator.next();
      // When this phi is chosen to be known-to-be-non-null value,
      // check if the corresponding predecessor is dominated by the block where non-null is added.
      if (operand == knownToBeNonNullValue && dominatedBlocks.contains(predecessor)) {
        predecessorIndexes.add(index);
      }

      index++;
    }
    return predecessorIndexes;
  }

  private boolean isNonNullCandidate(Value knownToBeNonNullValue) {
    TypeLatticeElement typeLattice = knownToBeNonNullValue.getTypeLattice();
    return typeLattice.isReference() && !typeLattice.isNullType() && typeLattice.isNullable();
  }

  public void computeNonNullParamOnNormalExits(OptimizationFeedback feedback, IRCode code) {
    Set<BasicBlock> normalExits = Sets.newIdentityHashSet();
    normalExits.addAll(code.computeNormalExitBlocks());
    DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
    List<Value> arguments = code.collectArguments();
    BitSet facts = new BitSet();
    Set<BasicBlock> nullCheckedBlocks = Sets.newIdentityHashSet();
    for (int index = 0; index < arguments.size(); index++) {
      Value argument = arguments.get(index);
      // Consider reference-type parameter only.
      if (!argument.getTypeLattice().isReference()) {
        continue;
      }
      // The receiver is always non-null on normal exits.
      if (argument.isThis()) {
        facts.set(index);
        continue;
      }
      // Collect basic blocks that check nullability of the parameter.
      nullCheckedBlocks.clear();
      for (Instruction user : argument.uniqueUsers()) {
        if (user.isNonNull()) {
          nullCheckedBlocks.add(user.asNonNull().getBlock());
        }
        if (user.isIf()
            && user.asIf().isZeroTest()
            && (user.asIf().getType() == Type.EQ || user.asIf().getType() == Type.NE)) {
          nullCheckedBlocks.add(user.asIf().targetFromNonNullObject());
        }
      }
      if (!nullCheckedBlocks.isEmpty()) {
        boolean allExitsCovered = true;
        for (BasicBlock normalExit : normalExits) {
          if (!isNormalExitDominated(normalExit, code, dominatorTree, nullCheckedBlocks)) {
            allExitsCovered = false;
            break;
          }
        }
        if (allExitsCovered) {
          facts.set(index);
        }
      }
    }
    if (facts.length() > 0) {
      feedback.setNonNullParamOnNormalExits(code.method, facts);
    }
  }

  private boolean isNormalExitDominated(
      BasicBlock normalExit,
      IRCode code,
      DominatorTree dominatorTree,
      Set<BasicBlock> nullCheckedBlocks) {
    // Each normal exit should be...
    for (BasicBlock nullCheckedBlock : nullCheckedBlocks) {
      // A) ...directly dominated by any null-checked block.
      if (dominatorTree.dominatedBy(normalExit, nullCheckedBlock)) {
        return true;
      }
    }
    // B) ...or indirectly dominated by null-checked blocks.
    // Although the normal exit is not dominated by any of null-checked blocks (because of other
    // paths to the exit), it could be still the case that all possible paths to that exit should
    // pass some of null-checked blocks.
    Set<BasicBlock> visited = Sets.newIdentityHashSet();
    // Initial fan-out of predecessors.
    Deque<BasicBlock> uncoveredPaths = new ArrayDeque<>(normalExit.getPredecessors());
    while (!uncoveredPaths.isEmpty()) {
      BasicBlock uncoveredPath = uncoveredPaths.poll();
      // Stop traversing upwards if we hit the entry block: if the entry block has an non-null,
      // this case should be handled already by A) because the entry block surely dominates all
      // normal exits.
      if (uncoveredPath == code.entryBlock()) {
        return false;
      }
      // Make sure we're not visiting the same block over and over again.
      if (!visited.add(uncoveredPath)) {
        // But, if that block is the last one in the queue, the normal exit is not fully covered.
        if (uncoveredPaths.isEmpty()) {
          return false;
        } else {
          continue;
        }
      }
      boolean pathCovered = false;
      for (BasicBlock nullCheckedBlock : nullCheckedBlocks) {
        if (dominatorTree.dominatedBy(uncoveredPath, nullCheckedBlock)) {
          pathCovered = true;
          break;
        }
      }
      if (!pathCovered) {
        // Fan out predecessors one more level.
        // Note that remaining, unmatched null-checked blocks should cover newly added paths.
        uncoveredPaths.addAll(uncoveredPath.getPredecessors());
      }
    }
    // Reaching here means that every path to the given normal exit is covered by the set of
    // null-checked blocks.
    assert uncoveredPaths.isEmpty();
    return true;
  }

  public void cleanupNonNull(IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();

    InstructionIterator it = code.instructionIterator();
    boolean needToCheckTrivialPhis = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      // non_null_rcv <- non-null(rcv)  // deleted
      // ...
      // non_null_rcv#foo
      //
      //  ~>
      //
      // rcv#foo
      if (instruction.isNonNull()) {
        NonNull nonNull = instruction.asNonNull();
        Value src = nonNull.src();
        Value dest = nonNull.dest();
        affectedValues.addAll(dest.affectedValues());

        // Replace `dest` by `src`.
        needToCheckTrivialPhis = needToCheckTrivialPhis || dest.uniquePhiUsers().size() != 0;
        dest.replaceUsers(src);
        it.remove();
      }
    }
    // non-null might introduce a phi, e.g.,
    // non_null_rcv <- non-null(rcv)
    // ...
    // v <- phi(rcv, non_null_rcv)
    //
    // Cleaning up that non-null may result in a trivial phi:
    // v <- phi(rcv, rcv)
    if (needToCheckTrivialPhis) {
      code.removeAllTrivialPhis();
    }

    // We need to update the types of all values whose definitions depend on a non-null value.
    // This is needed to preserve soundness of the types after the NonNull instructions have been
    // removed.
    //
    // As an example, consider a check-cast instruction on the form "z = (T) y". If y used to be
    // defined by a NonNull instruction, then the type analysis could have used this information
    // to mark z as non-null. However, cleanupNonNull() have now replaced y by a nullable value x.
    // Since z is defined as "z = (T) x", and x is nullable, it is no longer sound to have that z
    // is not nullable. This is fixed by rerunning the type analysis for the affected values.
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView, code.method).widening(affectedValues);
    }
  }

}
