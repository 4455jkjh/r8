// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;

import java.nio.file.Path;
import java.util.function.Supplier;

public enum ProguardVersion {
  V7_0_0("7.0.0", TestDeps::getProguard7_0_0Dir),
  V7_7_0("7.7.0", TestDeps::getProguard7_7_0Dir);

  private final String version;
  private final Supplier<Path> dir;

  ProguardVersion(String version, Supplier<Path> dir) {
    this.version = version;
    this.dir = dir;
  }

  public static ProguardVersion getLatest() {
    return V7_7_0;
  }

  public Path getProguardScript() {
    return isWindows()
        ? getScriptDirectory().resolve("proguard.bat")
        : getScriptDirectory().resolve("proguard.sh");
  }

  public Path getRetraceScript() {
    return isWindows()
        ? getScriptDirectory().resolve("retrace.bat")
        : getScriptDirectory().resolve("retrace.sh");
  }

  private Path getScriptDirectory() {
    return dir.get().resolve("bin");
  }

  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "Proguard " + version;
  }
}
