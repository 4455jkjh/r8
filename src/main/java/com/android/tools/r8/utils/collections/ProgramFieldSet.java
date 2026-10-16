// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProgramFieldSet extends DexClassAndFieldSetBase<ProgramField> {

  private static final ProgramFieldSet EMPTY = new ProgramFieldSet(ImmutableMap.of());

  private ProgramFieldSet(Map<DexField, ProgramField> backing) {
    super(backing);
  }

  public static ProgramFieldSet create() {
    return new ProgramFieldSet(new IdentityHashMap<>());
  }

  public static ProgramFieldSet createConcurrent() {
    return new ProgramFieldSet(new ConcurrentHashMap<>());
  }

  public static ProgramFieldSet empty() {
    return EMPTY;
  }

  @Override
  public boolean add(ProgramField field) {
    ProgramField existing = backing.put(field.getReference(), field);
    assert existing == null || existing.isStructurallyEqualTo(field);
    return existing == null;
  }

  public void addAll(Iterable<ProgramField> fields) {
    fields.forEach(this::add);
  }

  public void addAll(ProgramFieldSet fields) {
    backing.putAll(fields.backing);
  }

  public boolean createAndAdd(DexProgramClass clazz, DexEncodedField definition) {
    return add(new ProgramField(clazz, definition));
  }
}
