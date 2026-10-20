// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_2_2_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion.JAVA_8;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
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
public class KotlinContextParametersInlineDebugTest extends KotlinDebugTestBase {

  private static final String SOURCE_FILE = "Main.kt";
  private static final String DEBUGGEE_CLASS = "MainKt";

  private static final String KOTLIN_SOURCE =
      StringUtils.lines(
          "class Ctx1",
          "class Ctx2",
          "class Ctx3",
          "context(ctx1: Ctx1, ctx2: Ctx2, ctx3: Ctx3)",
          "inline fun foo() {",
          "  println() // Breakpoint",
          "}",
          "",
          "fun main() {",
          "  with(Ctx1()) {",
          "    with(Ctx2()) {",
          "      with(Ctx3()) {",
          "        foo()",
          "      }",
          "    }",
          "  }",
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
            .withCompilersStartingFromIncluding(KOTLINC_2_2_0)
            .withAllLambdaGenerations()
            .withTargetVersion(JAVA_8)
            .build());
  }

  private static KotlinCompileMemoizer compiledJars;

  @BeforeClass
  public static void setup() throws Exception {
    Path sourceFile = getStaticTemp().newFolder().toPath().resolve(SOURCE_FILE);
    FileUtils.writeTextFile(sourceFile, KOTLIN_SOURCE);
    compiledJars =
        getCompileMemoizer(sourceFile).configure(KotlinCompilerTool::enableContextParameters);
  }

  @Test
  public void testContextParametersOrderInInlinedFunction() throws Throwable {
    runDebugTest(
        testForRuntime(parameters)
            .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
            .debugConfig(parameters.getRuntime()),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, "main", "()V"),
        run(),
        checkLine(SOURCE_FILE, 10),
        stepOver(),
        checkLine(SOURCE_FILE, 11),
        stepOver(),
        checkLine(SOURCE_FILE, 12),
        stepOver(),
        checkLine(SOURCE_FILE, 13),
        stepInto(),
        // Line 19 in the SMAP corresponds to line 6 inside inlined foo().
        checkLine(SOURCE_FILE, 19),
        inspect(
            s -> {
              // The Kotlin debugger in IntelliJ / Android Studio sorts visible variables in an
              // inline frame by their starting code index (LocalVariable.compareTo).
              List<String> contextVariablesByCodeIndex =
                  s.getVisibleVariables().stream()
                      .filter(v -> v.getName().startsWith("ctx"))
                      .sorted(Comparator.comparingLong(Variable::getCodeIndex))
                      .map(Variable::getName)
                      .collect(Collectors.toList());
              if (parameters.isCfRuntime()) {
                assertEquals(
                    ImmutableList.of("ctx1$iv", "ctx2$iv", "ctx3$iv"), contextVariablesByCodeIndex);
              } else {
                // TODO(b/574583375): The order of context variables in DEX is ctx3$iv, ctx1$iv,
                // ctx2$iv instead of ctx1$iv, ctx2$iv, ctx3$iv.
                assertEquals(
                    ImmutableList.of("ctx3$iv", "ctx1$iv", "ctx2$iv"), contextVariablesByCodeIndex);
              }
            }),
        run());
  }
}
