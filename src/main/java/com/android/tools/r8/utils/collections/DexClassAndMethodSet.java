// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DexClassAndMethodSet extends DexClassAndMethodSetBase<DexClassAndMethod> {

  private static final DexClassAndMethodSet EMPTY = new DexClassAndMethodSet(ImmutableMap.of());

  private DexClassAndMethodSet(Map<DexMethod, DexClassAndMethod> backing) {
    super(backing);
  }

  public static DexClassAndMethodSet create() {
    return new DexClassAndMethodSet(new IdentityHashMap<>());
  }

  public static DexClassAndMethodSet createConcurrent() {
    return new DexClassAndMethodSet(new ConcurrentHashMap<>());
  }

  public static DexClassAndMethodSet createLinked() {
    return new DexClassAndMethodSet(new LinkedHashMap<>());
  }

  public static DexClassAndMethodSet empty() {
    return EMPTY;
  }

  public void addAll(DexClassAndMethodSet methods) {
    backing.putAll(methods.backing);
  }
}
