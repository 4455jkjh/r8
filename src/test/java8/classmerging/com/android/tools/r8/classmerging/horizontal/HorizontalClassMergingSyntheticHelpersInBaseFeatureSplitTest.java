// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.RepackagingInspector;
import com.android.tools.r8.utils.internal.Box;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/583992687.
public class HorizontalClassMergingSyntheticHelpersInBaseFeatureSplitTest
    extends HorizontalClassMergingTestBase {

  public HorizontalClassMergingSyntheticHelpersInBaseFeatureSplitTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    Box<RepackagingInspector> repackagingBox = new Box<>();
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(BaseMain.class, Base1.class, Base2.class)
            .addFeatureSplit(AFeatureMain.class, AFeature1.class, AFeature2.class)
            .addKeepMainRule(BaseMain.class)
            .addKeepClassAndMembersRules(AFeatureMain.class)
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertIsCompleteMergeGroup(AFeature1.class, AFeature2.class)
                        .assertIsCompleteMergeGroup(Base1.class, Base2.class)
                        .assertNoOtherClassesMerged())
            .addRepackagingInspector(repackagingBox::set)
            .collectSyntheticItems()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .compile();

    SyntheticItemsTestUtils syntheticItems = compileResult.getSyntheticItems();
    compileResult
        .inspect(
            baseInspector -> {
              assertEquals(4, baseInspector.allClasses().size());
              ClassReference repackagedAFeature1 =
                  repackagingBox.get().getTarget(Reference.classFromClass(AFeature1.class));
              assertThat(
                  baseInspector.clazz(syntheticItems.syntheticIsEqualClass(repackagedAFeature1, 0)),
                  isPresent());
              assertThat(
                  baseInspector.clazz(
                      syntheticItems.syntheticThrowCCEIfNotEqualsClass(repackagedAFeature1, 1)),
                  isPresent());
            },
            featureInspector -> assertEquals(2, featureInspector.allClasses().size()))
        .run(parameters.getRuntime(), BaseMain.class, "no-feature")
        .assertSuccessWithOutputLines(
            "Base1", "CCE", "CCE", "Base2", "true", "false", "false", "true");

    compileResult
        .addFeatureSplitsToRunClasspathFiles()
        .run(parameters.getRuntime(), BaseMain.class)
        .assertSuccessWithOutputLines(
            "Base1",
            "CCE",
            "CCE",
            "Base2",
            "true",
            "false",
            "false",
            "true",
            "AFeature1",
            "AFeature2");
  }

  @NeverClassInline
  public static class AFeature1 {
    @NeverInline
    public void foo() {
      System.out.println("AFeature1");
    }
  }

  @NeverClassInline
  public static class AFeature2 {
    @NeverInline
    public void bar() {
      System.out.println("AFeature2");
    }
  }

  public static class AFeatureMain {
    public static void main(String[] args) {
      new AFeature1().foo();
      new AFeature2().bar();
    }
  }

  @NeverClassInline
  public static class Base1 {
    @NeverInline
    public void foo() {
      System.out.println("Base1");
    }
  }

  @NeverClassInline
  public static class Base2 {
    @NeverInline
    public void bar() {
      System.out.println("Base2");
    }
  }

  public static class BaseMain {
    @NeverInline
    public static void castBase1(Object o) {
      ((Base1) o).foo();
    }

    @NeverInline
    public static void castBase2(Object o) {
      ((Base2) o).bar();
    }

    @NeverInline
    public static void instanceOfBase1(Object o) {
      System.out.println(o instanceof Base1);
    }

    @NeverInline
    public static void instanceOfBase2(Object o) {
      System.out.println(o instanceof Base2);
    }

    public static void main(String[] args) {
      Object b1 = new Base1();
      Object b2 = new Base2();
      try {
        castBase1(b1);
      } catch (ClassCastException e) {
        System.out.println("CCE");
      }
      try {
        castBase1(b2);
      } catch (ClassCastException e) {
        System.out.println("CCE");
      }
      try {
        castBase2(b1);
      } catch (ClassCastException e) {
        System.out.println("CCE");
      }
      try {
        castBase2(b2);
      } catch (ClassCastException e) {
        System.out.println("CCE");
      }
      instanceOfBase1(b1);
      instanceOfBase1(b2);
      instanceOfBase2(b1);
      instanceOfBase2(b2);
      if (args.length == 0) {
        AFeatureMain.main(args);
      }
    }
  }
}
