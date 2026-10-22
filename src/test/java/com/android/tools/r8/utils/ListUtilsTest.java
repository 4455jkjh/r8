// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.ListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ListUtilsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ListUtilsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private List<Integer> createInputData(int size) {
    List<Integer> input = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      input.add(i);
    }
    return input;
  }

  @Test
  public void lastIndexOf_outOfRange() {
    List<Integer> input = createInputData(3);
    Predicate<Integer> tester = x -> x * x == -1;
    assertEquals(-1, ListUtils.lastIndexMatching(input, tester));
  }

  @Test
  public void lastIndexOf_first() {
    List<Integer> input = createInputData(3);
    Predicate<Integer> tester = x -> x * x == 0;
    assertEquals(0, ListUtils.lastIndexMatching(input, tester));
  }

  @Test
  public void lastIndexOf_middle() {
    List<Integer> input = createInputData(4);
    Predicate<Integer> tester = x -> x * x == 4;
    assertEquals(2, ListUtils.lastIndexMatching(input, tester));
  }

  @Test
  public void lastIndexOf_last() {
    List<Integer> input = createInputData(2);
    Predicate<Integer> tester = x -> x * x == 1;
    assertEquals(1, ListUtils.lastIndexMatching(input, tester));
  }
}
