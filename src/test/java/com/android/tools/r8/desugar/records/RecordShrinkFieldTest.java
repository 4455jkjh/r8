// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordShrinkFieldTest extends TestBase {

  private static final String RECORD_NAME = "RecordShrinkField";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);

  private static final String EXPECTED_RESULT_D8 =
      StringUtils.lines(
          "Person[unused=-1, name=Jane Doe, age=42]", "Person[unused=-1, name=Bob, age=42]");
  private static final String EXPECTED_RESULT_R8 = StringUtils.lines("a[a=Jane Doe]", "a[a=Bob]");
  private static final String EXPECTED_RESULT_R8_NO_MINIFICATION =
      StringUtils.lines(
          "RecordShrinkField$Person[name=Jane Doe]", "RecordShrinkField$Person[name=Bob]");

  private final TestParameters parameters;
  private final boolean minifying;

  public RecordShrinkFieldTest(TestParameters parameters, boolean minifying) {
    this.parameters = parameters;
    this.minifying = minifying;
  }

  @Parameterized.Parameters(name = "{0}, minifying: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue("Only valid in R8", minifying);
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .minification(minifying)
        .compile()
        .inspect(this::assertSingleField)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(
            minifying ? EXPECTED_RESULT_R8 : EXPECTED_RESULT_R8_NO_MINIFICATION);
  }

  @Test
  public void testR8CfThenDex() throws Exception {
    Path desugared =
        testForR8(Backend.CF)
            .addProgramClassFileData(PROGRAM_DATA)
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(MAIN_TYPE)
            .minification(minifying)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .compile()
            .writeToZip();
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_TYPE)
        .minification(minifying)
        .compile()
        .inspect(this::assertSingleField)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(
            minifying ? EXPECTED_RESULT_R8 : EXPECTED_RESULT_R8_NO_MINIFICATION);
  }

  private void assertSingleField(CodeInspector inspector) {
    ClassSubject recordClass =
        inspector.clazz(minifying ? "records.a" : "records.RecordShrinkField$Person");
    assertEquals(1, recordClass.allInstanceFields().size());
    assertEquals(
        "java.lang.String", recordClass.allInstanceFields().get(0).getField().type().toString());
  }
}
