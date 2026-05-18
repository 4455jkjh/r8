// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CliParserUtilsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CliParserUtilsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testParsePositiveIntValid() {
    AtomicInteger parsedValue = new AtomicInteger(-1);
    List<String> errors = new ArrayList<>();
    CliParserUtils.parsePositiveInt("10", parsedValue::set, errors::add);
    assertTrue(errors.isEmpty());
    assertEquals(10, parsedValue.get());
  }

  @Test
  public void testParsePositiveIntZero() {
    AtomicInteger parsedValue = new AtomicInteger(-1);
    List<String> errors = new ArrayList<>();
    CliParserUtils.parsePositiveInt("0", parsedValue::set, errors::add);
    assertEquals(1, errors.size());
    assertEquals("0 is not a positive integer", errors.get(0));
    assertEquals(-1, parsedValue.get());
  }

  @Test
  public void testParsePositiveIntNegative() {
    AtomicInteger parsedValue = new AtomicInteger(-1);
    List<String> errors = new ArrayList<>();
    CliParserUtils.parsePositiveInt("-5", parsedValue::set, errors::add);
    assertEquals(1, errors.size());
    assertEquals("-5 is not a positive integer", errors.get(0));
    assertEquals(-1, parsedValue.get());
  }

  @Test
  public void testParsePositiveIntNotAnInteger() {
    AtomicInteger parsedValue = new AtomicInteger(-1);
    List<String> errors = new ArrayList<>();
    CliParserUtils.parsePositiveInt("abc", parsedValue::set, errors::add);
    assertEquals(1, errors.size());
    assertEquals("abc is not an integer", errors.get(0));
    assertEquals(-1, parsedValue.get());
  }
}
