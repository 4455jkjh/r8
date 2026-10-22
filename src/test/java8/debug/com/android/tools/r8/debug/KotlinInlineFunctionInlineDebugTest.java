// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion.JAVA_8;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// See b/574583375 for context.
@RunWith(Parameterized.class)
public class KotlinInlineFunctionInlineDebugTest extends KotlinDebugTestBase {

  private static final String SOURCE_FILE = "Main.kt";
  private static final String DEBUGGEE_CLASS = "MainKt";

  private static final String KOTLIN_SOURCE =
      StringUtils.lines(
          "class A",
          "class B",
          "class C",
          "inline fun foo(a: A, b: B, c: C) {",
          "  println() // Breakpoint",
          "}",
          "",
          "fun main() {",
          "  val a = A()",
          "  val b = B()",
          "  val c = C()",
          "  foo(a, b, c)",
          "}");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public KotlinTestParameters kotlinParameters;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withCfRuntimes()
            .withApiLevel(AndroidApiLevel.B)
            .build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withAllLambdaGenerations()
            .withTargetVersion(JAVA_8)
            .build());
  }

  private static KotlinCompileMemoizer compiledJars;

  @BeforeClass
  public static void setup() throws Exception {
    Path sourceFile = getStaticTemp().newFolder().toPath().resolve(SOURCE_FILE);
    FileUtils.writeTextFile(sourceFile, KOTLIN_SOURCE);
    compiledJars = getCompileMemoizer(sourceFile);
  }

  @Test
  public void testInlineFunctionParametersOrder() throws Throwable {
    runDebugTest(
        testForRuntime(parameters)
            .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
            .debugConfig(parameters.getRuntime()),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, "main", "()V"),
        run(),
        checkLine(SOURCE_FILE, 9),
        stepOver(),
        checkLine(SOURCE_FILE, 10),
        stepOver(),
        checkLine(SOURCE_FILE, 11),
        stepOver(),
        checkLine(SOURCE_FILE, 12),
        stepInto(),
        // Line 15 in the SMAP corresponds to line 5 inside inlined foo().
        checkLine(SOURCE_FILE, 15),
        inspect(
            s -> {
              // The Kotlin debugger in IntelliJ / Android Studio sorts visible variables in an
              // inline frame by their starting code index (LocalVariable.compareTo).
              List<String> inlinedParametersByCodeIndex =
                  s.getVisibleVariables().stream()
                      .filter(v -> v.getName().endsWith("$iv"))
                      .sorted(Comparator.comparingLong(Variable::getCodeIndex))
                      .map(Variable::getName)
                      .collect(Collectors.toList());
              assertEquals(
                  kotlinParameters
                          .getCompilerVersion()
                          .isGreaterThanOrEqualTo(KotlinCompilerVersion.KOTLINC_2_2_0)
                      ? ImmutableList.of("a$iv", "b$iv", "c$iv")
                      : Collections.emptyList(),
                  inlinedParametersByCodeIndex);
            }),
        run());
  }
}
