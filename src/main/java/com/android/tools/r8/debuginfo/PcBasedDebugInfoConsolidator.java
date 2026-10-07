// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo.PcBasedDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.ObjObjIntConsumer;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PcBasedDebugInfoConsolidator {

  private final InternalOptions options;

  private PcBasedDebugInfoConsolidator(AppView<?> appView) {
    this.options = appView.options();
  }

  public static void run(
      AppView<?> appView,
      List<VirtualFile> virtualFiles,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    new PcBasedDebugInfoConsolidator(appView).consolidate(virtualFiles, executorService, timing);
  }

  private void consolidate(
      List<VirtualFile> virtualFiles, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Consolidate PcEncodedDebugInfo");
    ThreadUtils.processItems(
        virtualFiles, this::consolidate, options.getThreadingModule(), executorService);
    timing.end();
  }

  @SuppressWarnings("ReferenceEquality")
  private void consolidate(VirtualFile virtualFile) {
    Int2ReferenceMap<PcBasedDebugInfoGroup> groupsByParamCount = new Int2ReferenceOpenHashMap<>();
    forEachMethodWithPcBasedDebugInfo(
        virtualFile,
        (code, debugInfo, methodMaxPc) ->
            groupsByParamCount
                .computeIfAbsent(debugInfo.getParameterCount(), PcBasedDebugInfoGroup::new)
                .record(debugInfo, methodMaxPc));
    if (groupsByParamCount.isEmpty()) {
      return;
    }
    int threshold = options.testing.pcBasedDebugEncodingOverheadThreshold;
    for (PcBasedDebugInfoGroup group : groupsByParamCount.values()) {
      group.computeSharedDebugInfo(threshold);
    }
    forEachMethodWithPcBasedDebugInfo(
        virtualFile,
        (code, debugInfo, methodMaxPc) -> {
          PcBasedDebugInfo consolidatedDebugInfo =
              groupsByParamCount.get(debugInfo.getParameterCount()).getSharedDebugInfo(methodMaxPc);
          assert consolidatedDebugInfo != null;
          assert DebugRepresentation.verifyLastExecutableInstructionWithinBound(
              code, consolidatedDebugInfo.getMaxPc());
          if (consolidatedDebugInfo != debugInfo) {
            code.setDebugInfo(consolidatedDebugInfo);
          }
        });
  }

  private static void forEachMethodWithPcBasedDebugInfo(
      VirtualFile virtualFile, ObjObjIntConsumer<DexCode, PcBasedDebugInfo> consumer) {
    for (DexProgramClass clazz : virtualFile.classes()) {
      for (DexEncodedMethod method : clazz.methods(DexEncodedMethod::hasDexCode)) {
        DexCode code = method.getDexCode();
        if (code.hasDebugInfo() && code.getDebugInfo().isPcBasedInfo()) {
          PcBasedDebugInfo debugInfo = code.getDebugInfo().asPcBasedInfo();
          DexInstruction lastInstruction = DebugRepresentation.getLastExecutableInstruction(code);
          int methodMaxPc =
              lastInstruction != null ? lastInstruction.getOffset() : debugInfo.getMaxPc();
          consumer.accept(code, debugInfo, methodMaxPc);
        }
      }
    }
  }

  private static class PcBasedDebugInfoGroup {

    private final int paramCount;
    private final Int2IntOpenHashMap countByMaxPc = new Int2IntOpenHashMap();
    private PcBasedDebugInfo existingMaxPcInfo;
    private int maxPc = -1;
    private int totalMethods = 0;
    private int totalUnconsolidatedCost = 0;

    private PcBasedDebugInfo singleSharedInfo;
    private Int2ReferenceMap<PcBasedDebugInfo> sharedByMethodMaxPc;

    PcBasedDebugInfoGroup(int paramCount) {
      this.paramCount = paramCount;
    }

    void record(PcBasedDebugInfo debugInfo, int methodMaxPc) {
      countByMaxPc.addTo(methodMaxPc, 1);
      if (existingMaxPcInfo == null || debugInfo.getMaxPc() > existingMaxPcInfo.getMaxPc()) {
        existingMaxPcInfo = debugInfo;
      }
      maxPc = Math.max(maxPc, methodMaxPc);
      totalMethods++;
      totalUnconsolidatedCost += DebugRepresentation.pcEventCount(methodMaxPc);
    }

    void computeSharedDebugInfo(int threshold) {
      if (DebugRepresentation.isWithinExpansionThreshold(
          threshold, maxPc, totalMethods, totalUnconsolidatedCost)) {
        singleSharedInfo = createOrReuseSharedInfo(maxPc);
        return;
      }
      int[] sortedPcs = countByMaxPc.keySet().toIntArray();
      Arrays.sort(sortedPcs);
      sharedByMethodMaxPc = new Int2ReferenceOpenHashMap<>(sortedPcs.length);
      int bucketStart = 0;
      int bucketMethods = 0;
      int bucketUnconsolidatedCost = 0;
      for (int i = 0; i < sortedPcs.length; i++) {
        int currentPc = sortedPcs[i];
        int methods = countByMaxPc.get(currentPc);
        int unconsolidatedCost = methods * DebugRepresentation.pcEventCount(currentPc);
        if (bucketMethods > 0
            && !DebugRepresentation.isWithinExpansionThreshold(
                threshold,
                currentPc,
                bucketMethods + methods,
                bucketUnconsolidatedCost + unconsolidatedCost)) {
          flushBucket(sortedPcs, bucketStart, i - 1);
          bucketStart = i;
          bucketMethods = 0;
          bucketUnconsolidatedCost = 0;
        }
        bucketMethods += methods;
        bucketUnconsolidatedCost += unconsolidatedCost;
      }
      flushBucket(sortedPcs, bucketStart, sortedPcs.length - 1);
    }

    private void flushBucket(int[] sortedPcs, int start, int end) {
      int bucketMaxPc = sortedPcs[end];
      PcBasedDebugInfo shared = createOrReuseSharedInfo(bucketMaxPc);
      for (int i = start; i <= end; i++) {
        sharedByMethodMaxPc.put(sortedPcs[i], shared);
      }
    }

    private PcBasedDebugInfo createOrReuseSharedInfo(int bucketMaxPc) {
      if (existingMaxPcInfo != null && existingMaxPcInfo.getMaxPc() == bucketMaxPc) {
        return existingMaxPcInfo;
      }
      return new PcBasedDebugInfo(paramCount, bucketMaxPc);
    }

    PcBasedDebugInfo getSharedDebugInfo(int methodMaxPc) {
      if (singleSharedInfo != null) {
        return singleSharedInfo;
      }
      return sharedByMethodMaxPc.get(methodMaxPc);
    }
  }
}
