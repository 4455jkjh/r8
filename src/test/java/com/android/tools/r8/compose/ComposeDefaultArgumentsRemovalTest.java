// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compose;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposeDefaultArgumentsRemovalTest extends TestBase {

  @Parameter(0)
  public boolean enableSimplifyDefaults;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, simplify defaults: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Composer.class)
        .addOptionsModification(
            options -> {
              assertFalse(options.getComposeOptions().enableSimplifyDefaults);
              options.getComposeOptions().enableSimplifyDefaults = enableSimplifyDefaults;
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());

              MethodSubject unusedDefaultMethod =
                  mainClass.uniqueMethodWithOriginalName("composableWithUnusedDefault");
              assertThat(unusedDefaultMethod, isPresent());
              assertThat(
                  unusedDefaultMethod,
                  notIf(invokesMethodWithName("startDefaults"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultMethod,
                  notIf(invokesMethodWithName("getDefaultsInvalid"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultMethod,
                  notIf(invokesMethodWithName("skipToGroupEnd"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultMethod,
                  notIf(invokesMethodWithName("endDefaults"), enableSimplifyDefaults));
              assertEquals(
                  enableSimplifyDefaults,
                  unusedDefaultMethod.streamInstructions().noneMatch(InstructionSubject::isIf));

              MethodSubject unusedDefaultChangedAlwaysOneMethod =
                  mainClass.uniqueMethodWithOriginalName(
                      "composableWithUnusedDefaultChangedAlwaysOne");
              assertThat(unusedDefaultChangedAlwaysOneMethod, isPresent());
              assertThat(
                  unusedDefaultChangedAlwaysOneMethod,
                  notIf(invokesMethodWithName("startDefaults"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultChangedAlwaysOneMethod,
                  notIf(invokesMethodWithName("getDefaultsInvalid"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultChangedAlwaysOneMethod,
                  notIf(invokesMethodWithName("skipToGroupEnd"), enableSimplifyDefaults));
              assertThat(
                  unusedDefaultChangedAlwaysOneMethod,
                  notIf(invokesMethodWithName("endDefaults"), enableSimplifyDefaults));
              assertEquals(
                  enableSimplifyDefaults,
                  unusedDefaultChangedAlwaysOneMethod
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isIf));

              MethodSubject usedDefaultMethod =
                  mainClass.uniqueMethodWithOriginalName("composableWithUsedDefault");
              assertThat(usedDefaultMethod, isPresent());
              assertTrue(usedDefaultMethod.streamInstructions().anyMatch(InstructionSubject::isIf));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines());
  }

  private List<String> getExpectedOutputLines() {
    if (enableSimplifyDefaults) {
      return ImmutableList.of(
          "10",
          "20",
          "30",
          "startDefaults",
          "endDefaults",
          "42",
          "startDefaults",
          "getDefaultsInvalid",
          "skipToGroupEnd",
          "endDefaults",
          "50");
    }
    return ImmutableList.of(
        "startDefaults",
        "endDefaults",
        "10",
        "startDefaults",
        "getDefaultsInvalid",
        "skipToGroupEnd",
        "endDefaults",
        "20",
        "startDefaults",
        "getDefaultsInvalid",
        "skipToGroupEnd",
        "endDefaults",
        "30",
        "startDefaults",
        "endDefaults",
        "42",
        "startDefaults",
        "getDefaultsInvalid",
        "skipToGroupEnd",
        "endDefaults",
        "50");
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  public interface Composer {
    void startDefaults();

    boolean getDefaultsInvalid();

    void skipToGroupEnd();

    void endDefaults();
  }

  @NeverClassInline
  public static class ComposerImpl implements Composer {

    private final boolean defaultsInvalid;

    public ComposerImpl(boolean defaultsInvalid) {
      this.defaultsInvalid = defaultsInvalid;
    }

    @NeverInline
    @Override
    public void startDefaults() {
      System.out.println("startDefaults");
    }

    @NeverInline
    @Override
    public boolean getDefaultsInvalid() {
      System.out.println("getDefaultsInvalid");
      return defaultsInvalid;
    }

    @NeverInline
    @Override
    public void skipToGroupEnd() {
      System.out.println("skipToGroupEnd");
    }

    @NeverInline
    @Override
    public void endDefaults() {
      System.out.println("endDefaults");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Composer composer = new ComposerImpl(args.length > 0);
      composableWithUnusedDefault(10, composer, 0, 0);
      composableWithUnusedDefault(20, composer, 1, 0);
      composableWithUnusedDefaultChangedAlwaysOne(30, composer, 1, 0);
      composableWithUsedDefault(0, composer, 0, 1);
      composableWithUsedDefault(50, composer, 1, 0);
    }

    @NeverInline
    static void composableWithUnusedDefault(
        int x, Composer composer, int changed, int defaultFlags) {
      composer.startDefaults();
      if ((changed & 1) != 0 && !composer.getDefaultsInvalid()) {
        composer.skipToGroupEnd();
      } else if ((defaultFlags & 1) != 0) {
        x = 42;
      }
      composer.endDefaults();
      System.out.println(x);
    }

    @NeverInline
    static void composableWithUnusedDefaultChangedAlwaysOne(
        int x, Composer composer, int changed, int defaultFlags) {
      composer.startDefaults();
      if ((changed & 1) != 0 && !composer.getDefaultsInvalid()) {
        composer.skipToGroupEnd();
      } else if ((defaultFlags & 1) != 0) {
        x = 42;
      }
      composer.endDefaults();
      System.out.println(x);
    }

    @NeverInline
    static void composableWithUsedDefault(int x, Composer composer, int changed, int defaultFlags) {
      composer.startDefaults();
      if ((changed & 1) != 0 && !composer.getDefaultsInvalid()) {
        composer.skipToGroupEnd();
      } else if ((defaultFlags & 1) != 0) {
        x = 42;
      }
      composer.endDefaults();
      System.out.println(x);
    }
  }
}
