// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.DefaultMixedSectionLayoutStrategy;
import com.android.tools.r8.dex.MixedSectionLayoutStrategy;
import com.android.tools.r8.dex.StartupMixedSectionLayoutStrategy;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.utils.MixedSectionLayoutInspector;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupMixedSectionLayoutStrategyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    MixedSectionLayoutInspectorImpl mixedSectionLayoutInspector =
        new MixedSectionLayoutInspectorImpl();
    Collection<ExternalStartupItem> startupProfile =
        ImmutableList.of(
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(A.class))
                .build(),
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(B.class))
                .build());
    testForR8(Backend.DEX)
        .addProgramClasses(A.class, B.class, C.class)
        .addKeepAllClassesRule()
        .addOptionsModification(
            options -> {
              options.getTestingOptions().limitNumberOfClassesPerDex = 1;
              options
                  .getTestingOptions()
                  .setMixedSectionLayoutStrategyInspector(mixedSectionLayoutInspector);
            })
        .allowDiagnosticMessages()
        .apply(testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, startupProfile))
        .setMinApi(AndroidApiLevel.N)
        .compile()
        .inspectMultiDex(
            // classes.dex is a startup dex file.
            inspector -> assertThat(inspector.clazz(A.class), isPresent()),
            // classes2.dex is a startup dex file.
            inspector -> assertThat(inspector.clazz(B.class), isPresent()),
            // classes3.dex is a non-startup dex file.
            inspector -> assertThat(inspector.clazz(C.class), isPresent()));
    assertEquals(new IntArraySet(new int[] {0, 1, 2}), mixedSectionLayoutInspector.seen);
  }

  static class A {}

  static class B {}

  static class C {}

  private static class MixedSectionLayoutInspectorImpl extends MixedSectionLayoutInspector {

    final IntSet seen = new IntArraySet();

    @Override
    public void inspectClassDataLayout(
        int virtualFile,
        Collection<DexProgramClass> layout,
        MixedSectionLayoutStrategy layoutStrategy) {
      seen.add(virtualFile);
      if (virtualFile == 0 || virtualFile == 1) {
        assertTrue(layoutStrategy instanceof StartupMixedSectionLayoutStrategy);
      } else {
        assertEquals(2, virtualFile);
        assertTrue(layoutStrategy instanceof DefaultMixedSectionLayoutStrategy);
      }
    }
  }
}
