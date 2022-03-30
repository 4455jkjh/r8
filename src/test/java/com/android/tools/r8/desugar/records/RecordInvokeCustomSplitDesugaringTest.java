// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordInvokeCustomSplitDesugaringTest extends TestBase {

  private static final String RECORD_NAME = "RecordInvokeCustom";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "%s[]",
          "true",
          "true",
          "true",
          "true",
          "true",
          "false",
          "true",
          "true",
          "false",
          "false",
          "%s[name=Jane Doe, age=42]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT, "Empty", "Person");
  private static final String EXPECTED_RESULT_R8 = String.format(EXPECTED_RESULT, "a", "b");

  private final TestParameters parameters;

  public RecordInvokeCustomSplitDesugaringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  @Test
  public void testD8() throws Exception {
    Path desugared =
        testForD8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    Path desugared =
        testForD8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }
}
