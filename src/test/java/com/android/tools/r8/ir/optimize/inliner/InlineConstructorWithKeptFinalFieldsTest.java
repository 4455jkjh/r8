// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineConstructorWithKeptFinalFieldsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.T)
        .build();
  }

  @Test
  public void testDefault() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers class * { <fields>; }")
                .addOptionsModification(
                    options -> {
                      assertTrue(options.testing.allowFinalModificationInKeepRule);
                      assertFalse(options.testing.decoupleFinalModificationFromOptimization);
                    }),
        // By default the constructor is not inlined since we cannot unset the final flag of the
        // two assigned fields.
        false);
  }

  @Test
  public void testInlinedWithKeepDefault() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers class * { <fields>; }")
                .addOptionsModification(
                    options -> {
                      options.testing.allowFinalModificationInKeepRule = true;
                      options.testing.decoupleFinalModificationFromOptimization = true;
                    }),
        // When allowfinalmodification is implicitly enabled, constructor inlining should happen
        // even when no modifiers are given.
        true);
  }

  @Test
  public void testInlinedWithKeepExplicit() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers,allowfinalmodification class * { <fields>; }")
                .addOptionsModification(
                    options -> {
                      options.testing.allowFinalModificationInKeepRule = false;
                      options.testing.decoupleFinalModificationFromOptimization = true;
                    }),
        true);
  }

  @Test
  public void testNotInlinedWithKeepDefault() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers class * { <fields>; }")
                .addOptionsModification(
                    options -> {
                      options.testing.allowFinalModificationInKeepRule = false;
                      options.testing.decoupleFinalModificationFromOptimization = true;
                    }),
        // When allowfinalmodification is implicitly disabled, constructor inlining would require
        // explicitly setting ,allowfinalmodification.
        false);
  }

  @Test
  public void testNotInlinedWithKeepExplicit() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers,disallowfinalmodification class * { <fields>; }")
                .addOptionsModification(
                    options -> {
                      options.testing.allowFinalModificationInKeepRule = true;
                      options.testing.decoupleFinalModificationFromOptimization = true;
                    }),
        // When allowfinalmodification is implicitly enabled, constructor inlining should be
        // disabled if setting ,disallowfinalmodification explicitly.
        false);
  }

  private void runTest(
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> configuration, boolean expectInlined)
      throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(configuration)
        // Use most recent android.jar so that VarHandle is present.
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .enableAlwaysInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertEquals(
                  expectInlined,
                  mainClassSubject.allMethods().stream()
                      .noneMatch(FoundMethodSubject::isInstanceInitializer));
            })
        .run(parameters.getRuntime(), Main.class, "20", "22")
        .assertSuccessWithOutputLines("42");
  }

  static class Main {

    final int x;
    final int y;

    @AlwaysInline
    Main(int x, int y) {
      this.x = x;
      this.y = y;
    }

    public static void main(String[] args) {
      Main main = new Main(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
      System.out.println(main);
    }

    @Override
    public String toString() {
      return Integer.toString(x + y);
    }
  }
}
