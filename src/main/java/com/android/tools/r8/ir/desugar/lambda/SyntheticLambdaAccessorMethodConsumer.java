// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;

public interface SyntheticLambdaAccessorMethodConsumer {

  void acceptSyntheticLambdaAccessorMethod(ProgramMethod accessor, DexMethod target);

  static SyntheticLambdaAccessorMethodConsumer emptySyntheticLambdaAccessorMethodConsumer() {
    return (accessor, target) -> {};
  }
}
