// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.graph.DexReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class StartupItem {

  public abstract void accept(
      Consumer<StartupClass> classConsumer, Consumer<StartupMethod> methodConsumer);

  public abstract <T> T apply(
      Function<StartupClass, T> classFunction, Function<StartupMethod, T> methodFunction);

  public abstract DexReference getReference();

  public boolean isStartupClass() {
    return false;
  }

  public StartupClass asStartupClass() {
    assert false;
    return null;
  }

  public boolean isStartupMethod() {
    return false;
  }

  public StartupMethod asStartupMethod() {
    assert false;
    return null;
  }

  public abstract String serializeToString();
}
