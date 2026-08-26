// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.files;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ZipUtils;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArchiveTimestampTest extends TestBase {

  static final long EXPECTED_CONSTANT_TIME_FOR_ZIP_ENTRIES =
      LocalDate.of(1980, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testConstant() throws Exception {
    assertEquals(
        EXPECTED_CONSTANT_TIME_FOR_ZIP_ENTRIES, InternalOptions.CONSTANT_TIME_FOR_ZIP_ENTRIES);
  }

  @Test
  public void testD8() throws Exception {
    ZipUtils.iter(
        testForD8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .setMinApi(AndroidApiLevel.L)
            .compile()
            .writeToZip(),
        entry -> assertEquals(EXPECTED_CONSTANT_TIME_FOR_ZIP_ENTRIES, entry.getTime()));
  }

  @Test
  public void testR8Dex() throws Exception {
    ZipUtils.iter(
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .setMinApi(AndroidApiLevel.L)
            .addKeepMainRule(TestClass.class)
            .compile()
            .writeToZip(),
        entry -> assertEquals(EXPECTED_CONSTANT_TIME_FOR_ZIP_ENTRIES, entry.getTime()));
  }

  @Test
  public void testR8Cf() throws Exception {
    ZipUtils.iter(
        testForR8(Backend.CF)
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .compile()
            .writeToZip(),
        entry -> assertEquals(EXPECTED_CONSTANT_TIME_FOR_ZIP_ENTRIES, entry.getTime()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
