// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinimalStartupDexFromStartupMethodRuleTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters.getBackend()));
  }

  @Test
  public void testR8() throws Exception {
    runTest(testForR8(parameters.getBackend()).addKeepAllClassesRule());
  }

  private void runTest(TestCompilerBuilder<?, ?, ?, ?, ?> testCompilerBuilder) throws Exception {
    testCompilerBuilder
        .addInnerClasses(getClass())
        .apply(this::configureStartupConfiguration)
        .setMinApi(parameters)
        .compile()
        .inspectMultiDex(
            primaryDexInspector -> {
              ClassSubject mainClassSubject = primaryDexInspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // TODO(b/274591979): Should be in classes2.dex.
              ClassSubject postStartupClassSubject =
                  primaryDexInspector.clazz(PostStartupClass.class);
              assertThat(postStartupClassSubject, isPresent());
            },
            secondaryDexInspector -> {
              // TODO(b/274591979): Should be present.
              ClassSubject postStartupClassSubject =
                  secondaryDexInspector.clazz(PostStartupClass.class);
              assertThat(postStartupClassSubject, isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void configureStartupConfiguration(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    StartupTestingUtils.setStartupConfiguration(
        testBuilder,
        ImmutableList.of(
            ExternalStartupMethod.builder()
                .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
                .build()));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  static class PostStartupClass {}
}
