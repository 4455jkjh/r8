// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexField;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DexClassAndFieldSet extends DexClassAndFieldSetBase<DexClassAndField> {

  private static final DexClassAndFieldSet EMPTY = new DexClassAndFieldSet(ImmutableMap.of());

  private DexClassAndFieldSet(Map<DexField, DexClassAndField> backing) {
    super(backing);
  }

  public static DexClassAndFieldSet create() {
    return new DexClassAndFieldSet(new IdentityHashMap<>());
  }

  public static DexClassAndFieldSet createConcurrent() {
    return new DexClassAndFieldSet(new ConcurrentHashMap<>());
  }

  public static DexClassAndFieldSet createLinked() {
    return new DexClassAndFieldSet(new LinkedHashMap<>());
  }

  public static DexClassAndFieldSet empty() {
    return EMPTY;
  }

  public void addAll(DexClassAndFieldSet fields) {
    backing.putAll(fields.backing);
  }
}
