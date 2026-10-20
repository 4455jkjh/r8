// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.collections.KeyedSetBase;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public abstract class DexClassAndFieldSetBase<T extends DexClassAndField>
    extends KeyedSetBase<DexField, T> {

  DexClassAndFieldSetBase(Map<DexField, T> backing) {
    super(backing);
  }

  @Override
  protected DexField getKey(T field) {
    return field.getReference();
  }

  @Override
  protected boolean isElement(Object o) {
    return o instanceof DexClassAndField;
  }

  public boolean contains(DexField field) {
    return containsKey(field);
  }

  public boolean contains(DexEncodedField field) {
    return containsKey(field.getReference());
  }

  public boolean contains(T field) {
    return containsKey(field.getReference());
  }

  public boolean remove(DexField field) {
    return removeKey(field);
  }

  public boolean remove(DexEncodedField field) {
    return removeKey(field.getReference());
  }

  public boolean remove(T field) {
    return removeKey(field.getReference());
  }

  public Set<DexEncodedField> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    return toDefinitionSet(SetUtils::newIdentityHashSet);
  }

  public Set<DexEncodedField> toDefinitionSet(IntFunction<Set<DexEncodedField>> factory) {
    Set<DexEncodedField> definitions = factory.apply(size());
    forEach(field -> definitions.add(field.getDefinition()));
    return definitions;
  }
}
