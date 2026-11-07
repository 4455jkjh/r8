// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.CancelCompilationException;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Function;
import java.util.function.IntFunction;

public class TimingWithCancellation extends TimingDelegate {

  private final InternalOptions options;

  public TimingWithCancellation(InternalOptions options, Timing timing) {
    super(timing);
    assert options.cancelCompilationChecker != null;
    this.options = options;
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    if (getDelegate().isEmpty()) {
      return this;
    }
    return new TimingWithCancellation(options, getDelegate().createThreadTiming(title, options));
  }

  @Override
  public <T> Timing createThreadTiming(
      T context, Function<T, String> titleSupplier, InternalOptions options) {
    if (getDelegate().isEmpty()) {
      return this;
    }
    return new TimingWithCancellation(
        options, getDelegate().createThreadTiming(context, titleSupplier, options));
  }

  @Override
  public Timing createThreadTiming(
      int value, IntFunction<String> titleSupplier, InternalOptions options) {
    if (getDelegate().isEmpty()) {
      return this;
    }
    return new TimingWithCancellation(
        options, getDelegate().createThreadTiming(value, titleSupplier, options));
  }

  @Override
  public Timing begin(String title) {
    if (options.checkIfCancelled()) {
      throw new CancelCompilationException();
    }
    return super.begin(title);
  }

  @Override
  public <T> Timing begin(T context, Function<T, String> titleSupplier) {
    if (options.checkIfCancelled()) {
      throw new CancelCompilationException();
    }
    return super.begin(context, titleSupplier);
  }

  @Override
  public Timing begin(int value, IntFunction<String> titleSupplier) {
    if (options.checkIfCancelled()) {
      throw new CancelCompilationException();
    }
    return super.begin(value, titleSupplier);
  }
}
