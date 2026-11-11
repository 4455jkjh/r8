// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.internal.ThrowingAction;
import com.android.tools.r8.utils.internal.ThrowingSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;

abstract class TimingImplBase extends Timing {

  @Override
  public final <T> Timing begin(T context, Function<T, String> titleSupplier) {
    return begin(titleSupplier.apply(context));
  }

  @Override
  public final Timing begin(int value, IntFunction<String> titleSupplier) {
    return begin(titleSupplier.apply(value));
  }

  @Override
  public final <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
    begin(title);
    try {
      action.execute();
    } finally {
      end();
    }
  }

  @Override
  public final <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier)
      throws E {
    begin(title);
    try {
      return supplier.get();
    } finally {
      end();
    }
  }
}
