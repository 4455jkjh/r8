// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.debuginfo.DebugRepresentation.getParameterCountForPcBasedDebugInfo;
import static com.android.tools.r8.debuginfo.DebugRepresentation.verifyLastExecutableInstructionWithinBound;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface PositionToMappedRangeMapper {

  List<MappedPosition> getMappedPositions(
      ProgramMethod method,
      MethodPositionRemapper positionRemapper,
      boolean hasOverloads,
      boolean canUseDexPc,
      int pcEncodingCutoff,
      Timing timing);

  static PositionToMappedRangeMapper create(AppView<?> appView) {
    return appView.options().isGeneratingClassFiles()
        ? new ClassFilePositionToMappedRangeMapper(appView)
        : new DexPositionToMappedRangeMapper(appView);
  }

  class DexPositionToMappedRangeMapper implements PositionToMappedRangeMapper {

    private final DexPositionToNoPcMappedRangeMapper noPcMapper;
    private final DexPositionToPcMappedRangeMapper pcMapper;

    private DexPositionToMappedRangeMapper(AppView<?> appView) {
      PcBasedDebugInfoRecorder pcBasedDebugInfoRecorder =
          appView.options().canUseNativeDexPcInsteadOfDebugInfo()
              ? new NativePcSupport()
              : new Pc2PcMappingSupport(appView.options());
      noPcMapper = new DexPositionToNoPcMappedRangeMapper(appView);
      pcMapper = new DexPositionToPcMappedRangeMapper(appView, pcBasedDebugInfoRecorder);
    }

    @Override
    public List<MappedPosition> getMappedPositions(
        ProgramMethod method,
        MethodPositionRemapper positionRemapper,
        boolean hasOverloads,
        boolean canUseDexPc,
        int pcEncodingCutoff,
        Timing timing) {
      return canUseDexPc
          ? pcMapper.optimizeDexCodePositionsForPc(
              method, positionRemapper, pcEncodingCutoff, timing)
          : noPcMapper.optimizeDexCodePositions(method, positionRemapper, timing);
    }
  }

  interface PcBasedDebugInfoRecorder {
    /** Callback to record a code object with a given max instruction PC and parameter count. */
    void recordPcMappingFor(ProgramMethod method, int maxEncodingPc);

    int getPcEncoding(int pc);
  }

  class Pc2PcMappingSupport implements PcBasedDebugInfoRecorder {

    private static class Key {
      final int paramCount;
      final int maxEncodingPc;

      Key(int paramCount, int maxEncodingPc) {
        this.paramCount = paramCount;
        this.maxEncodingPc = maxEncodingPc;
      }

      @Override
      @SuppressWarnings("EqualsUnsafeCast")
      public boolean equals(Object o) {
        Key that = (Key) o;
        return paramCount == that.paramCount && maxEncodingPc == that.maxEncodingPc;
      }

      @Override
      public int hashCode() {
        return ObjectUtils.hashII(paramCount, maxEncodingPc);
      }
    }

    private final Map<Key, DexDebugInfo> debugInfos = new ConcurrentHashMap<>();
    private final InternalOptions options;

    public Pc2PcMappingSupport(InternalOptions options) {
      this.options = options;
    }

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc + 1;
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      assert method.getDefinition().getCode().isDexCode();
      int parameterCount = getParameterCountForPcBasedDebugInfo(method, options);
      DexCode code = method.getDefinition().getDexCode();
      assert verifyLastExecutableInstructionWithinBound(code, maxEncodingPc);
      Key key = new Key(parameterCount, maxEncodingPc);
      DexDebugInfo debugInfo =
          debugInfos.computeIfAbsent(
              key, k -> new DexDebugInfo.PcBasedDebugInfo(k.paramCount, k.maxEncodingPc));
      assert debugInfo.asPcBasedInfo().getMaxPc() == maxEncodingPc;
      code.setDebugInfo(debugInfo);
    }
  }

  class NativePcSupport implements PcBasedDebugInfoRecorder {

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc;
    }

    private void clearDebugInfo(ProgramMethod method) {
      // Always strip the info in full as the runtime will emit the PC directly.
      method.getDefinition().getCode().asDexCode().setDebugInfo(null);
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      clearDebugInfo(method);
    }
  }
}
