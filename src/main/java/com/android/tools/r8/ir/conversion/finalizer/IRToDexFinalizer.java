// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.finalizer.passes.BasicBlockReorderer;
import com.android.tools.r8.ir.conversion.finalizer.passes.BranchDiamondInverter;
import com.android.tools.r8.ir.conversion.finalizer.passes.DebugLocalUpdater;
import com.android.tools.r8.ir.conversion.finalizer.passes.IdenticalBlockPrefixSharer;
import com.android.tools.r8.ir.conversion.finalizer.passes.IdenticalBlockRemover;
import com.android.tools.r8.ir.conversion.finalizer.passes.IdenticalBlockSuffixSharer;
import com.android.tools.r8.ir.conversion.finalizer.passes.RedundantInstructionsRemover;
import com.android.tools.r8.ir.conversion.finalizer.passes.TrivialGotosCollapser;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.RuntimeWorkaroundCodeRewriter;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;

public class IRToDexFinalizer extends IRFinalizer<DexCode> {

  private final DeadCodeRemover deadCodeRemover;
  private final InternalOptions options;
  private final TrivialGotosCollapser trivialGotosCollapser;
  private final IdenticalBlockRemover identicalBlockRemover;
  private final RedundantInstructionsRemover redundantInstructionsRemover;
  private final IdenticalBlockPrefixSharer identicalBlockPrefixSharer;
  private final IdenticalBlockSuffixSharer identicalBlockSuffixSharer;
  private final DebugLocalUpdater debugLocalUpdater;
  private final BranchDiamondInverter branchDiamondInverter;
  private final BasicBlockReorderer basicBlockReorderer;

  public IRToDexFinalizer(AppView<?> appView, DeadCodeRemover deadCodeRemover) {
    super(appView);
    this.deadCodeRemover = deadCodeRemover;
    this.options = appView.options();
    this.trivialGotosCollapser = new TrivialGotosCollapser(this.appView);
    identicalBlockRemover = new IdenticalBlockRemover(appView);
    redundantInstructionsRemover = new RedundantInstructionsRemover(appView);
    identicalBlockPrefixSharer = new IdenticalBlockPrefixSharer(appView);
    identicalBlockSuffixSharer = new IdenticalBlockSuffixSharer(appView);
    debugLocalUpdater = new DebugLocalUpdater(appView);
    branchDiamondInverter = new BranchDiamondInverter(appView);
    this.basicBlockReorderer = new BasicBlockReorderer(this.appView);
  }

  @Override
  public DexCode finalizeCode(
      IRCode code,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing,
      String previousPrintString) {
    if (options.canUseNestBasedAccess()) {
      D8NestBasedAccessDesugaring.checkAndFailOnIncompleteNests(appView);
    }
    DexEncodedMethod method = code.method();
    workaroundBugs(code, timing);
    code.traceBlocks();
    // Perform register allocation.
    LinearScanRegisterAllocator registerAllocator = performRegisterAllocation(code, method, timing);
    return new DexBuilder(code, bytecodeMetadataProvider, registerAllocator, options).build();
  }

  private void workaroundBugs(IRCode code, Timing timing) {
    RuntimeWorkaroundCodeRewriter.workaroundNumberConversionRegisterAllocationBug(appView, code);
    RuntimeWorkaroundCodeRewriter.workaroundDex2OatInliningIssue(appView, code);
    if (RuntimeWorkaroundCodeRewriter.workaroundInstanceOfTypeWeakeningInVerifier(appView, code)) {
      deadCodeRemover.run(code, timing);
    }
    RuntimeWorkaroundCodeRewriter.workaroundSwitchMaxIntBug(code, appView);
    RuntimeWorkaroundCodeRewriter.workaroundDex2OatLinkedListBug(code, options);
    RuntimeWorkaroundCodeRewriter.workaroundForwardingInitializerBug(code, options);
    RuntimeWorkaroundCodeRewriter.workaroundExceptionTargetingLoopHeaderBug(code, options);
    RuntimeWorkaroundCodeRewriter.rewriteJdk8272564Fix(code, options, appView);
    RuntimeWorkaroundCodeRewriter.reportInvokeSuperToInterfaceOnDalvik(code, options, appView);
    assert code.isConsistentSSA(appView);
  }

  @SuppressWarnings("UnusedVariable")
  private LinearScanRegisterAllocator performRegisterAllocation(
      IRCode code, DexEncodedMethod method, Timing timing) {
    // Always perform dead code elimination before register allocation. The register allocator
    // does not allow dead code (to make sure that we do not waste registers for unneeded values).
    assert deadCodeRemover.verifyNoDeadCode(code);
    timing.begin("Allocate registers");
    LinearScanRegisterAllocator registerAllocator =
        new LinearScanRegisterAllocator(appView, code, timing);
    registerAllocator.allocateRegisters();
    timing.end();
    trivialGotosCollapser.run(code, registerAllocator, timing);
    debugLocalUpdater.run(code, registerAllocator, timing);
    redundantInstructionsRemover.run(code, registerAllocator, timing);
    boolean changed =
        identicalBlockRemover.run(code, registerAllocator, timing).hasChanged().isTrue();
    changed |=
        identicalBlockPrefixSharer.run(code, registerAllocator, timing).hasChanged().isTrue();
    changed |=
        identicalBlockSuffixSharer.run(code, registerAllocator, timing).hasChanged().isTrue();
    changed |= branchDiamondInverter.run(code, registerAllocator, timing).hasChanged().isTrue();
    if (changed) {
      trivialGotosCollapser.run(code, registerAllocator, timing);
      identicalBlockRemover.run(code, registerAllocator, timing);
      identicalBlockPrefixSharer.run(code, registerAllocator, timing);
      identicalBlockSuffixSharer.run(code, registerAllocator, timing);
      branchDiamondInverter.run(code, registerAllocator, timing);
    }
    // BasicBlockReorderer should be run near the end because other optimizations may change block
    // ordering.
    basicBlockReorderer.run(code, registerAllocator, timing);
    trivialGotosCollapser.run(code, registerAllocator, timing);
    return registerAllocator;
  }
}
