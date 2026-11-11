// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dagger;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DaggerFlagGatedProvidersTest extends DaggerTestBase {

  private static final String MAIN_CLASS = "flaggated.Main";

  private static final List<String> EXPECTED_OUTPUT = ImmutableList.of("used", "used");

  private static final Set<String> UNUSED_MARKERS =
      ImmutableSet.of(
          "MARKER_FlaggedSingleton", "MARKER_FlaggedReusable", "MARKER_FlaggedLazySingleton");

  private static Map<Boolean, Path> compiledJars;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean fastInit;

  @Parameter(2)
  public boolean enableAssertions;

  @Parameters(name = "{0}, fastInit: {1}, enableAssertions: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @BeforeClass
  public static void setUp() throws Exception {
    compiledJars = compileExampleWithAndWithoutFastInit("flaggated/Main.java");
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(enableAssertions);
    testForJvm(parameters)
        .addProgramFiles(getProgramFiles(compiledJars.get(fastInit)))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramFiles(getProgramFiles(compiledJars.get(fastInit)))
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules(
            "-neverinline class flaggated.BannerModule { static java.lang.String"
                + " provideBanner(...); }",
            "-neverinline class flaggated.DaggerAppComponent* { <init>(...); void initialize(...);"
                + " java.lang.String banner(); }")
        .enableProguardTestOptions()
        // DoubleCheck.<init> and SingleCheck.<init> contain `assert provider != null;`. With
        // compile-time assertion removal (enableAssertions = false, the DEX default), those checks
        // and $assertionsDisabled are stripped, making the constructors smaller and free of
        // AssertionError side effects. Without removal (enableAssertions = true), eliminating dead
        // DoubleCheck/SingleCheck-wrapped providers after unused-argument removal also requires
        // proving `provider != null` across the retained throw branch. Explicitly testing both also
        // covers both modes uniformly on CF (which defaults to passthrough) and DEX.
        .apply(b -> configureAssertions(b, enableAssertions))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz("dagger.internal.DoubleCheck"), isPresent());
              // TODO(b/581317183, b/503184789): Should be empty.
              assertEquals(UNUSED_MARKERS, presentConstStrings(inspector, UNUSED_MARKERS));
            });
  }
}
