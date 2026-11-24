// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class DexMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexReference implements NamingLensComparable<R> {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexMember<?, ?> t1, DexMember<?, ?> t2) {
    return t1 == t2;
  }

  public final DexType holder;
  public final DexString name;

  private static final int NOT_COMPUTED_HASH_VALUE = -1;
  private static final int SENTINEL_HASH_VALUE = 0;
  private volatile int hash = NOT_COMPUTED_HASH_VALUE;

  public DexMember(DexType holder, DexString name) {
    assert holder != null;
    this.holder = holder;
    assert name != null;
    this.name = name;
  }

  // TODO: Remove this by making DexField and DexMethod use reference equality and identity hash
  //  code.
  protected abstract int computeHashCode();

  protected abstract boolean computeEquals(Object other);

  @Override
  public final int hashCode() {
    int cache = hash;
    if (cache == NOT_COMPUTED_HASH_VALUE) {
      cache = computeHashCode();
      if (cache == NOT_COMPUTED_HASH_VALUE) {
        cache = SENTINEL_HASH_VALUE;
      }
      hash = cache;
    }
    assert verifyHashCodeConsistent(cache);
    return cache;
  }

  private boolean verifyHashCodeConsistent(int computedValue) {
    int newComputedValue = computeHashCode();
    if (newComputedValue == NOT_COMPUTED_HASH_VALUE) {
      newComputedValue = SENTINEL_HASH_VALUE;
    }
    assert computedValue == newComputedValue
        : "Hash code for " + this + " has changed from " + hash + " to " + newComputedValue;
    return true;
  }

  @Override
  public final boolean equals(Object other) {
    return this == other || computeEquals(other);
  }

  public abstract <T> T apply(
      Function<DexField, T> fieldConsumer, Function<DexMethod, T> methodConsumer);

  public final boolean isDefinedOnClass(DexClass clazz) {
    return lookupOnClass(clazz) != null;
  }

  public abstract DexEncodedMember<?, ?> lookupOnClass(DexClass clazz);

  public abstract DexClassAndMember<?, ?> lookupMemberOnClass(DexClass clazz);

  public abstract ProgramMember<?, ?> lookupOnProgramClass(DexProgramClass clazz);

  public abstract boolean match(R entry);

  public abstract boolean match(D entry);

  @Override
  public DexType getContextType() {
    return holder;
  }

  public DexType getHolderType() {
    return holder;
  }

  public DexString getName() {
    return name;
  }

  @Override
  public boolean isDexMember() {
    return true;
  }

  @Override
  public DexMember<D, R> asDexMember() {
    return this;
  }

  public abstract Iterable<DexType> getReferencedTypes();

  public Iterable<DexType> getReferencedBaseTypes(DexItemFactory dexItemFactory) {
    return Iterables.transform(getReferencedTypes(), type -> type.getBaseType());
  }

  public boolean verifyReferencedBaseTypesMatches(
      Predicate<DexType> predicate, DexItemFactory dexItemFactory) {
    assert Streams.stream(getReferencedBaseTypes(dexItemFactory)).allMatch(predicate);
    return true;
  }

  public abstract DexMember<D, R> withHolder(DexReference holder, DexItemFactory dexItemFactory);
}
