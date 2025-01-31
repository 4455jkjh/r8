// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import com.android.tools.r8.graph.DexString;
import java.util.function.Predicate;

public interface R8PartialPredicate extends Predicate<DexString> {

  String serializeToString();
}
