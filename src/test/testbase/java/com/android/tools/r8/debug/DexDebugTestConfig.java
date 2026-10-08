// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.DexRuntime;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Base test configuration with DEX version of JDWP. */
public class DexDebugTestConfig extends DebugTestConfig {

  private final DexRuntime runtime;

  public DexDebugTestConfig(DexRuntime runtime, Path... paths) {
    this(runtime, Arrays.asList(paths));
  }

  public DexDebugTestConfig(TestRuntime.DexRuntime runtime, List<Path> paths) {
    this.runtime = runtime;
    addPaths(paths);
  }

  @Override
  public TestRuntime getRuntime() {
    return runtime;
  }
}
