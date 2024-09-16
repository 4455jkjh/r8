// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public interface NewlyInstantiatedClassEnqueuerAnalysis {

  /** Called when a class is found to be instantiated. */
  default void processNewlyInstantiatedClass(
      DexProgramClass clazz, ProgramMethod context, EnqueuerWorklist worklist) {}
}