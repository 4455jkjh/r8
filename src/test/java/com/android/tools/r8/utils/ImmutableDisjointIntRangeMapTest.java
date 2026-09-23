// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.collections.ImmutableDisjointIntRangeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImmutableDisjointIntRangeMapTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ImmutableDisjointIntRangeMapTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testSingleRange() {
    ImmutableDisjointIntRangeMap<String> map =
        ImmutableDisjointIntRangeMap.<String>builder().add(10, 20, "A").build();
    assertEquals(1, map.size());

    assertNull(map.getEntry(9));
    assertEntry(map.getEntry(10), 10, "A");
    assertEntry(map.getEntry(15), 10, "A");
    assertEntry(map.getEntry(20), 10, "A");
    assertNull(map.getEntry(21));
  }

  @Test
  public void testMultipleRangesOutOfOrder() {
    ImmutableDisjointIntRangeMap<Character> map =
        ImmutableDisjointIntRangeMap.<Character>builder()
            .add(31, 100, 'a')
            .add(1, 10, 'b')
            .add(20, 30, 'c')
            .build();
    assertEquals(3, map.size());

    assertEntry(map.getEntry(5), 1, 'b');
    assertEntry(map.getEntry(25), 20, 'c');
    assertEntry(map.getEntry(50), 31, 'a');
  }

  @Test
  public void testAddUncovered() {
    ImmutableDisjointIntRangeMap<String> map =
        ImmutableDisjointIntRangeMap.<String>builder()
            .add(10, 20, "A")
            .addUncoveredSubRanges(10, 20, "B")
            .addUncoveredSubRanges(12, 18, "C")
            .addUncoveredSubRanges(5, 12, "E")
            .addUncoveredSubRanges(18, 25, (start, end) -> "D_" + start + "_" + end)
            .addUncoveredSubRanges(8, 22, "F")
            .addUncoveredSubRanges(30, 40, "G")
            .build();

    assertEquals(4, map.size());
    assertNull(map.getEntry(4));
    assertEntry(map.getEntry(7), 5, "E");
    assertEntry(map.getEntry(15), 10, "A");
    assertEntry(map.getEntry(23), 21, "D_21_25");
    assertNull(map.getEntry(26));
    assertEntry(map.getEntry(35), 30, "G");
    assertNull(map.getEntry(41));
  }

  private <V> void assertEntry(
      ImmutableDisjointIntRangeMap.Entry<V> entry, int expectedStart, V expectedValue) {
    assertNotNull(entry);
    assertEquals(expectedStart, entry.start);
    assertEquals(expectedValue, entry.value);
  }
}
