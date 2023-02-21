// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dagger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DaggerBasicSingletonUsingBindsTest extends DaggerBasicTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public String target;

  @Parameters(name = "{0}, javac -target {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
            .withAllApiLevels()
            .build(),
        javacTargets);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    DaggerBasicTestBase.compileWithSingleton();
  }

  public static final String MAIN_CLASS = "basic.MainUsingBinds";
  public static final List<String> EXPECTED_OUTPUT =
      ImmutableList.of("true", "true", "true", "I1Impl1", "I2Impl1", "I3Impl1");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(getProgramFiles(target))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramFiles(getProgramFiles(target))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    assertEquals(
        ImmutableSet.of(
            "basic.I1Impl1",
            "basic.I2Impl1",
            "basic.I3Impl1",
            "basic.MainUsingBinds",
            "basic.DaggerMainComponentUsingBinds",
            "dagger.internal.DoubleCheck",
            "javax.inject.Provider"),
        inspector.allClasses().stream()
            .map(FoundClassSubject::getOriginalName)
            .filter(name -> !name.contains("_Factory"))
            .collect(Collectors.toSet()));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles(target))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              inspector
                  .applyIf(
                      target.equals("1.8") || parameters.isDexRuntime(),
                      i ->
                          i.assertIsCompleteMergeGroup(
                              "basic.I1Impl1_Factory",
                              "basic.I2Impl1_Factory",
                              "basic.I3Impl1_Factory"))
                  .applyIf(
                      target.equals("1.8") || parameters.isDexRuntime(),
                      i ->
                          i.assertIsCompleteMergeGroup(
                              "basic.I1Impl1_Factory$InstanceHolder",
                              "basic.I2Impl1_Factory$InstanceHolder",
                              "basic.I3Impl1_Factory$InstanceHolder"))
                  .assertNoOtherClassesMerged();
            })
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype("basic.I1", "basic.I2", "basic.I3"))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
