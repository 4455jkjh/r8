// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.List;

public interface AtomicUpdaterMethodProcessorEventConsumer {

  void acceptUnsafeInstanceContext(
      List<DexMethod> initializationMethods, DexType unsafeClass, ProgramDefinition context);

  void acceptUnsafeGetAndSetContext(DexMethod method, ProgramMethod context);
}
