// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.experimental.startup.StartupClass;
import com.android.tools.r8.experimental.startup.StartupProfile;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergingWithStartupClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean includeStartupClasses;

  @Parameters(name = "{0}, include startup classes: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private List<Class<?>> getStartupClasses() {
    return includeStartupClasses
        ? Collections.emptyList()
        : ImmutableList.of(StartupA.class, StartupB.class);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addOptionsModification(
            options -> {
              DexItemFactory dexItemFactory = options.dexItemFactory();
              StartupProfile startupProfile =
                  StartupProfile.builder()
                      .apply(
                          builder ->
                              getStartupClasses()
                                  .forEach(
                                      startupClass ->
                                          builder.addStartupClass(
                                              StartupClass.dexBuilder()
                                                  .setClassReference(
                                                      toDexType(startupClass, dexItemFactory))
                                                  .build())))
                      .build();
              StartupProfileProvider startupProfileProvider = startupProfile::serializeToString;
              options.getStartupOptions().setStartupProfileProvider(startupProfileProvider);
            })
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        getStartupClasses().isEmpty(),
                        i ->
                            i.assertIsCompleteMergeGroup(
                                StartupA.class,
                                StartupB.class,
                                OnClickHandlerA.class,
                                OnClickHandlerB.class),
                        i ->
                            i.assertIsCompleteMergeGroup(StartupA.class, StartupB.class)
                                .assertIsCompleteMergeGroup(
                                    OnClickHandlerA.class, OnClickHandlerB.class))
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("StartupA", "StartupB");
  }

  static class Main {

    public static void main(String[] args) {
      StartupA.foo();
      StartupB.bar();
    }

    // @Keep
    public void onClick() {
      OnClickHandlerA.baz();
      OnClickHandlerB.qux();
    }
  }

  static class StartupA {

    @NeverInline
    static void foo() {
      System.out.println("StartupA");
    }
  }

  static class StartupB {

    @NeverInline
    static void bar() {
      System.out.println("StartupB");
    }
  }

  static class OnClickHandlerA {

    @NeverInline
    static void baz() {
      System.out.println("IdleA");
    }
  }

  static class OnClickHandlerB {

    @NeverInline
    static void qux() {
      System.out.println("IdleB");
    }
  }
}
