// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.util.function.Function;
import java.util.function.IntFunction;

class PerfettoThreadTiming extends TimingImplBase {

  PerfettoThreadTiming(PerfettoThreadStack stack) {
    this.stack = stack;
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    throw new Unreachable();
  }

  @Override
  public <T> Timing createThreadTiming(
      T context, Function<T, String> titleSupplier, InternalOptions options) {
    throw new Unreachable();
  }

  @Override
  public Timing createThreadTiming(
      int value, IntFunction<String> titleSupplier, InternalOptions options) {
    throw new Unreachable();
  }

  @Override
  public void notifyThreadTimingFinished() {
    assert stack.depth() == 0 : stack.depth();
  }

  @Override
  public void report() {
    throw new Unreachable();
  }

  @Override
  public Timing endAll() {
    while (stack.depth() > 0) {
      end();
    }
    return this;
  }
}
