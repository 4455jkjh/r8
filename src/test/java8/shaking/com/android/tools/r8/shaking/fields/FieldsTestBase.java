// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.fields;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class FieldsTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().withDexRuntimes().build();
  }

  public abstract Collection<Class<?>> getClasses();

  public abstract Class<?> getMainClass();

  public void testOnR8(
      List<String> keepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      String expected)
      throws Throwable {
    testForR8(Backend.DEX)
        .enableNoVerticalClassMergingAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void testOnProguard(
      List<String> keepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      String expected)
      throws Throwable {
    testForProguard()
        .addProgramClasses(getClasses())
        .addProgramClasses(NoVerticalClassMerging.class)
        .addKeepRules(keepRules)
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void runTest(
      List<String> keepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      String expected)
      throws Throwable {
    if (parameters.isCfRuntime()) {
      testOnProguard(keepRules, inspector, expected);
    } else {
      testOnR8(keepRules, inspector, expected);
    }
  }

  public void runTest(
      String keepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      String expected)
      throws Throwable {
    runTest(
        ImmutableList.of(
            keepRules,
            "-dontobfuscate",
            "-keep class " + getMainClass().getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}"),
        inspector,
        expected);
  }

  public String allFieldsOutput() {
    return StringUtils.lines("Super.f1 found", "Sub.f2 found", "SubSub.f3 found");
  }

  public String onlyF1Output() {
    return StringUtils.lines("Super.f1 found", "Sub.f2 not found", "SubSub.f3 not found");
  }

  public String onlyF2Output() {
    return StringUtils.lines("Super.f1 not found", "Sub.f2 found", "SubSub.f3 not found");
  }

  public String onlyF3Output() {
    return StringUtils.lines("Super.f1 not found", "Sub.f2 not found", "SubSub.f3 found");
  }
}
