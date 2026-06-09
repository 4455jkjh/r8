// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.retrace.classes.SynthesizeLineNumber;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalMergingWithMergedClassesMappingTest extends TestBase {

  private static final String FILENAME = "SynthesizeLineNumber.java";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  private static final StackTrace expectedStackTrace =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.A.class))
                  .setMethodName("foo")
                  .setFileName(FILENAME)
                  .setLineNumber(14)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.Main.class))
                  .setMethodName("call")
                  .setFileName(FILENAME)
                  .setLineNumber(34)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.Main.class))
                  .setMethodName("main")
                  .setFileName(FILENAME)
                  .setLineNumber(28)
                  .build())
          .build();

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(SynthesizeLineNumber.class)
        .run(parameters.getRuntime(), SynthesizeLineNumber.Main.class, "normal")
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResultBase<?> compilationResult =
        testForR8(parameters)
            .addInnerClasses(SynthesizeLineNumber.class)
            .addKeepMainRule(SynthesizeLineNumber.Main.class)
            .enableInliningAnnotations()
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector.assertIsCompleteMergeGroup(
                        SynthesizeLineNumber.A.class, SynthesizeLineNumber.B.class))
            .compile();

    // Check that emitted map is as expected.
    String proguardMap = compilationResult.getProguardMap();
    assertThat(
        proguardMap,
        containsString("# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.2\"}"));
    String optimizedLineNumberRange = canDiscardResidualDebugInfo(parameters) ? "0:7" : "1:8";
    assertThat(
        proguardMap,
        containsString(
            StringUtils.lines(
                "com.android.tools.r8.retrace.classes.SynthesizeLineNumber$A -> a:",
                "# {\"id\":\"sourceFile\",\"fileName\":\"SynthesizeLineNumber.java\"}",
                "    "
                    + optimizedLineNumberRange
                    + ":void"
                    + " com.android.tools.r8.retrace.classes.SynthesizeLineNumber$B.bar():21:21 ->"
                    + " a",
                "    " + optimizedLineNumberRange + ":void foo():14:14 -> b")));

    // Mock changes to proguard map to simulate what R8 could emit.
    String proguardMapWithMergedClasses =
        proguardMap
            .replace(
                "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.2\"}",
                "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.3\"}")
            .replace(
                "com.android.tools.r8.retrace.classes.SynthesizeLineNumber$A -> a:",
                StringUtils.joinLines(
                    "com.android.tools.r8.retrace.classes.SynthesizeLineNumber$A -> a:",
                    "# {\"id\":\"com.android.tools.r8.mergedClasses\", \"kind\": \"horizontal\","
                        + " \"class_id_field\": \"$r8$classId\", \"merged_classes\": [{ \"name\":"
                        + " \"com.android.tools.r8.retrace.classes.SynthesizeLineNumber$A\","
                        + " \"class_id\": \"0\" }, { \"name\":"
                        + " \"com.android.tools.r8.retrace.classes.SynthesizeLineNumber$B\","
                        + " \"class_id\": \"1\" }] }"));
    compilationResult
        .run(parameters.getRuntime(), SynthesizeLineNumber.Main.class, "normal")
        .inspectOriginalStackTrace(
            originalStackTrace -> {
              StackTrace retraced = originalStackTrace.retrace(proguardMapWithMergedClasses);
              assertThat(retraced, isSame(expectedStackTrace));
            });
  }
}
