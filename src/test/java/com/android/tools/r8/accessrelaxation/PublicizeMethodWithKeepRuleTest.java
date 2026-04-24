// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PublicizeMethodWithKeepRuleTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  @Test
  public void testDefault() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers class * { <methods>; }")
                .addOptionsModification(
                    options -> assertFalse(options.testing.allowAccessModificationInKeepRule)),
        // By default the constructor is not publicized when kept.
        false);
  }

  @Test
  public void testPublicizedWithAllowAccessModification() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers,allowaccessmodification class * { <methods>; }")
                .addOptionsModification(
                    options -> assertFalse(options.testing.allowAccessModificationInKeepRule)),
        // When setting ,allowaccessmodification, the method should be publicized.
        true);
  }

  @Test
  public void testPublicizedWithoutAllowAccessModification() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers class * { <methods>; }")
                .addOptionsModification(
                    options -> options.testing.allowAccessModificationInKeepRule = true),
        // When ,allowaccessmodification is implicitly enabled, the method should be publicized.
        true);
  }

  @Test
  public void testNotInlinedWithKeepDefault() throws Exception {
    runTest(
        builder ->
            builder
                .addKeepRules("-keepclassmembers,disallowaccessmodification class * { <methods>; }")
                .addOptionsModification(
                    options -> options.testing.allowAccessModificationInKeepRule = true),
        // When ,allowaccessmodification is implicitly enabled, the method should not be publicized
        // if ,disallowaccessmodification is explicitly set.
        false);
  }

  private void runTest(
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> configuration, boolean expectPublic)
      throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(configuration)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertEquals(
                  expectPublic, mainClassSubject.uniqueMethodWithOriginalName("test").isPublic());
            });
  }

  static class Main {

    public static void main(String[] args) {
      test();
    }

    private static void test() {
      System.out.println("Hello, world!");
    }
  }
}
