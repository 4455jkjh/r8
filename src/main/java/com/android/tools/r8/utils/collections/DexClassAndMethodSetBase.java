// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.collections.KeyedSetBase;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public abstract class DexClassAndMethodSetBase<T extends DexClassAndMethod>
    extends KeyedSetBase<DexMethod, T> {

  DexClassAndMethodSetBase(Map<DexMethod, T> backing) {
    super(backing);
  }

  @Override
  protected DexMethod getKey(T method) {
    return method.getReference();
  }

  @Override
  protected boolean isElement(Object o) {
    return o instanceof DexClassAndMethod;
  }

  public boolean contains(DexMethod method) {
    return containsKey(method);
  }

  public boolean contains(DexEncodedMethod method) {
    return containsKey(method.getReference());
  }

  public boolean contains(T method) {
    return containsKey(method.getReference());
  }

  public boolean remove(DexMethod method) {
    return removeKey(method);
  }

  public boolean remove(DexEncodedMethod method) {
    return removeKey(method.getReference());
  }

  public Set<DexEncodedMethod> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    return toDefinitionSet(SetUtils::newIdentityHashSet);
  }

  public Set<DexEncodedMethod> toDefinitionSet(IntFunction<Set<DexEncodedMethod>> factory) {
    Set<DexEncodedMethod> definitions = factory.apply(size());
    forEach(method -> definitions.add(method.getDefinition()));
    return definitions;
  }
}
