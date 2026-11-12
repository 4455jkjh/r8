// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk11.string;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringConcatValueOfSideEffectsTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("10", "state=before");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withDexRuntime(Version.MAIN)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withAllApiLevels()
        .withoutCollapsedDexRuntimes()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addOptionsModification(opt -> opt.enableStringConcatInstruction = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        // Default is JDK-8 rt.jar which has no definition of StringConcatFactory.
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.LATEST),
            b -> b.setMinApi(AndroidApiLevel.LATEST))
        .applyIf(
            canUseStringConcatFactoryOnArt(parameters),
            b ->
                b.addOptionsModification(
                    options -> options.getTestingOptions().enableStringConcatFactoryForDex = true))
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .addOptionsModification(options -> options.enableStringConcatInstruction = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class ToStringThatCounts {
    static int count = 0;

    @Override
    public String toString() {
      return Integer.toString(count++);
    }
  }

  static class ToStringReadsMutableState {
    static String state;

    @Override
    public String toString() {
      return state;
    }
  }

  static class TestClass {

    @NeverInline
    static void testReorderedSideEffects() {
      ToStringThatCounts.count = 0;
      String first = String.valueOf(new ToStringThatCounts());
      String second = String.valueOf(new ToStringThatCounts());
      // Removing String.valueOf here would defer toString() into the concat and print "01".
      System.out.println(second + first);
    }

    @NeverInline
    static void testDeferredStateRead() {
      ToStringReadsMutableState.state = "before";
      String captured = String.valueOf(new ToStringReadsMutableState());
      ToStringReadsMutableState.state = "after";
      // Removing String.valueOf here would defer toString() past the state update and print
      // "state=after".
      System.out.println("state=" + captured);
    }

    public static void main(String[] args) {
      testReorderedSideEffects();
      testDeferredStateRead();
    }
  }
}
