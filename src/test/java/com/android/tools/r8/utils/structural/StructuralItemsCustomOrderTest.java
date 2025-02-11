// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.structural;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StructuralItemsCustomOrderTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public StructuralItemsCustomOrderTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  final B b1 = new B(1);
  final B b2 = new B(2);
  final B b2_copy = new B(2);

  final A a1b1 = new A(1, b1);
  final A a2b1 = new A(2, b1);
  final A a1b2 = new A(1, b2);
  final A a2b2 = new A(2, b2);
  final A a1b2_copy = new A(1, b2_copy);
  final A a2b2_copy = new A(2, b2_copy);

  @Test
  public void testOrder() {
    assertFalse(b1.isLessThan(b2));
    assertTrue(b2.isEqualTo(b2_copy));
    assertTrue(b2.isLessThan(b1));

    assertFalse(a1b1.isLessThan(a1b2));
    assertTrue(a1b2.isLessThan(a2b1));
    assertFalse(a2b1.isLessThan(a1b2_copy));
  }

  @Test
  public void testOrderWithIdentityEquivalence() {
    // These mirror the above exactly but using the compare result.
    RepresentativeMap<DexType> map = t -> t;
    RepresentativeMap<DexMethod> mmap = m -> m;

    assertFalse(b1.compareWithSyntheticEquivalenceTo(b2, map, mmap) < 0);
    assertTrue(b2.compareWithSyntheticEquivalenceTo(b2_copy, map, mmap) == 0);
    assertTrue(b2.compareWithSyntheticEquivalenceTo(b1, map, mmap) < 0);

    assertFalse(a1b1.compareWithSyntheticEquivalenceTo(a1b2, map, mmap) < 0);
    assertTrue(a1b2.compareWithSyntheticEquivalenceTo(a2b1, map, mmap) < 0);
    assertFalse(a2b1.compareWithSyntheticEquivalenceTo(a1b2_copy, map, mmap) < 0);
  }

  @Test
  public void testEquals() {
    assertFalse(b1.isEqualTo(b2));
    assertTrue(b2.isEqualTo(b2_copy));
    assertEquals(b2, b2_copy);

    assertFalse(a2b2.isEqualTo(a2b1));
    assertTrue(a1b2.isEqualTo(a1b2_copy));
    assertEquals(a2b2, a2b2_copy);

    // Type incompatible check should still work.
    assertNotEquals(b1, a1b1);
  }

  @Test
  public void testHashCode() {
    Set<B> bs = new HashSet<>(ImmutableList.of(b1, b2, b2_copy));
    assertEquals(ImmutableSet.of(b1, b2), bs);

    Set<A> as = new HashSet<>(ImmutableList.of(a1b1, a1b2, a2b1, a2b2, a1b2_copy, a2b2_copy));
    assertEquals(ImmutableSet.of(a1b1, a1b2, a2b1, a2b2), as);

    // If these collide it is a poor hashing algorithm...
    assertNotEquals(b1.hashCode(), b2.hashCode());
  }

  private static class A implements StructuralItem<A> {

    private final int x;
    private final B b;

    private static void specify(StructuralSpecification<A, ?> spec) {
      spec.withInt(a -> a.x).withItem(a -> a.b);
    }

    public A(int x, B b) {
      this.x = x;
      this.b = b;
    }

    @Override
    public StructuralMapping<A> getStructuralMapping() {
      return A::specify;
    }

    @Override
    public A self() {
      return this;
    }

    @Override
    public final boolean equals(Object other) {
      return Equatable.equalsImpl(this, other);
    }

    @Override
    public final int hashCode() {
      return HashCodeVisitor.run(this, A::specify);
    }
  }

  private static class B implements StructuralItem<B> {

    private final int y;

    private static void specify(StructuralSpecification<B, ?> spec) {
      spec.withInt(b -> b.y);
    }

    public B(int y) {
      this.y = y;
    }

    @Override
    public StructuralMapping<B> getStructuralMapping() {
      return B::specify;
    }

    @Override
    public B self() {
      return this;
    }

    @Override
    public final boolean equals(Object other) {
      return Equatable.equalsImpl(this, other);
    }

    @Override
    public final int hashCode() {
      return HashCodeVisitor.run(this, B::specify);
    }

    // Override allowing a change to the order of any type of compare-to visitation, e.g., with
    // and without a type equivalence map.
    @Override
    public int acceptCompareTo(B other, CompareToVisitor visitor) {
      return visitor.visit(other, this, B::specify);
    }
  }
}
