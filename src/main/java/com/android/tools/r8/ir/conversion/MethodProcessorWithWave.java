// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.internal.ThrowingFunction;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class MethodProcessorWithWave extends MethodProcessor {

  private static final int MAX_SEQUENTIAL_WAVE_SIZE = 16;
  private static final int MAX_SEQUENTIAL_WAVE_INSTRUCTIONS = 256;

  protected ProgramMethodSet wave;
  protected ProgramMethodSet waveExtension = ProgramMethodSet.createConcurrent();

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return CallSiteInformation.empty();
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    return wave != null && wave.contains(method);
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    waveExtension.add(method);
  }

  protected void prepareForWaveExtensionProcessing() {
    if (waveExtension.isEmpty()) {
      wave = ProgramMethodSet.empty();
    } else {
      wave = waveExtension;
      waveExtension = ProgramMethodSet.createConcurrent();
    }
  }

  protected boolean shouldProcessWaveSequentially() {
    int size = wave.size();
    if (size <= 1) {
      return true;
    }
    if (size > MAX_SEQUENTIAL_WAVE_SIZE) {
      return false;
    }
    int totalInstructions = 0;
    for (ProgramMethod method : wave) {
      DexEncodedMethod definition = method.getDefinition();
      if (definition.hasLirCode()) {
        totalInstructions += definition.getLirCode().getInstructionCount();
      } else if (definition.hasCfCode()) {
        totalInstructions += definition.getCfCode().getInstructions().size();
      } else {
        return false;
      }
      if (totalInstructions > MAX_SEQUENTIAL_WAVE_INSTRUCTIONS) {
        return false;
      }
    }
    return true;
  }

  protected <E extends Exception> Collection<Timing> processWave(
      ThrowingFunction<ProgramMethod, Timing, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    if (shouldProcessWaveSequentially()) {
      List<Timing> timings = new ArrayList<>(wave.size());
      try {
        for (ProgramMethod method : wave) {
          timings.add(consumer.apply(method));
        }
      } catch (Throwable t) {
        throw new ExecutionException(t);
      }
      return timings;
    }
    return ThreadUtils.processItemsWithResults(wave, consumer, threadingModule, executorService);
  }
}
