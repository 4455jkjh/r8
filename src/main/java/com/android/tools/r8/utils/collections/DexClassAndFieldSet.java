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

public abstract class DexClassAndFieldSet extends DexClassAndFieldSetBase<DexClassAndField> {

  private static final DexClassAndFieldSet EMPTY = new EmptyDexClassAndFieldSet();

  DexClassAndFieldSet() {
    super();
  }

  public static DexClassAndFieldSet create() {
    return new IdentityDexClassAndFieldSet();
  }

  public static DexClassAndFieldSet createConcurrent() {
    return new ConcurrentDexClassAndFieldSet();
  }

  public static DexClassAndFieldSet createLinked() {
    return new LinkedDexClassAndFieldSet();
  }

  public static DexClassAndFieldSet empty() {
    return EMPTY;
  }

  public void addAll(DexClassAndFieldSet fields) {
    backing.putAll(fields.backing);
  }

  private static class ConcurrentDexClassAndFieldSet extends DexClassAndFieldSet {

    @Override
    Map<DexField, DexClassAndField> createBacking() {
      return new ConcurrentHashMap<>();
    }

    @Override
    Map<DexField, DexClassAndField> createBacking(int capacity) {
      return new ConcurrentHashMap<>(capacity);
    }
  }

  private static class EmptyDexClassAndFieldSet extends DexClassAndFieldSet {

    @Override
    Map<DexField, DexClassAndField> createBacking() {
      return ImmutableMap.of();
    }

    @Override
    Map<DexField, DexClassAndField> createBacking(int capacity) {
      return ImmutableMap.of();
    }
  }

  private static class IdentityDexClassAndFieldSet extends DexClassAndFieldSet {

    @Override
    Map<DexField, DexClassAndField> createBacking() {
      return new IdentityHashMap<>();
    }

    @Override
    Map<DexField, DexClassAndField> createBacking(int capacity) {
      return new IdentityHashMap<>(capacity);
    }
  }

  private static class LinkedDexClassAndFieldSet extends DexClassAndFieldSet {

    @Override
    Map<DexField, DexClassAndField> createBacking() {
      return new LinkedHashMap<>();
    }

    @Override
    Map<DexField, DexClassAndField> createBacking(int capacity) {
      return new LinkedHashMap<>(capacity);
    }
  }
}
