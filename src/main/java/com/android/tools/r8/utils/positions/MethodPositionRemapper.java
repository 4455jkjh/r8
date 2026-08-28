// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.internal.collections.Pair;

/** Stateful remapper that maps positions for a single method. */
public interface MethodPositionRemapper {

  /** Returns {@code (original, mapped)} positions. */
  Pair<Position, Position> createRemappedPosition(Position position);

  /** Sets the next line number to allocate when assigning remapped positions. */
  void setNextOptimizedLineNumber(int nextOptimizedLineNumber);
}

