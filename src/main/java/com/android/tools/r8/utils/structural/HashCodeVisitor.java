// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import com.android.tools.r8.utils.structural.StructuralItem.CompareToAccept;
import com.android.tools.r8.utils.structural.StructuralItem.HashingAccept;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Simple hash code implementation.
 *
 * <p>This visitor relies on the specification of hashCode on all object types. Thus it does not
 * have a call-back structure that requires the spec implementation as well as a visitor for the
 * recursive decent. There is also no support for overriding the visitation apart from the usual
 * override of hashCode().
 */
public class HashCodeVisitor<T> extends StructuralSpecification<T, HashCodeVisitor<T>> {

  public static <T> int run(T item, StructuralAccept<T> visit) {
    HashCodeVisitor<T> visitor = new HashCodeVisitor<>(item);
    visit.accept(visitor);
    return visitor.hashCode;
  }

  private final T item;

  private int hashCode = 0;

  private HashCodeVisitor(T item) {
    this.item = item;
  }

  private HashCodeVisitor<T> amend(int value) {
    // This mirrors the behavior of Objects.hash(values...) / Arrays.hashCode(array).
    hashCode = 31 * hashCode + value;
    return this;
  }

  @Override
  public HashCodeVisitor<T> withAssert(Predicate<T> predicate) {
    assert predicate.test(item);
    return this;
  }

  @Override
  public HashCodeVisitor<T> withBool(Predicate<T> getter) {
    return amend(Boolean.hashCode(getter.test(item)));
  }

  @Override
  public HashCodeVisitor<T> withInt(ToIntFunction<T> getter) {
    return amend(Integer.hashCode(getter.applyAsInt(item)));
  }

  @Override
  public <S> HashCodeVisitor<T> withCustomItem(
      Function<T, S> getter, CompareToAccept<S> compare, HashingAccept<S> hasher) {
    S member = getter.apply(item);
    // Use the value 1 for the null-member case such that a different hash is obtained for
    // {null, null} and {null}.
    return amend(member == null ? 1 : member.hashCode());
  }
}
