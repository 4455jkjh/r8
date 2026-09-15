// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithUnrelatedKeptMethodCollisionTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {
        "ParentAdapter.insertItems: 0, 2",
        "ParentAdapter.insertItems: 1, 3",
        "ChildAdapter.insertItems: 2, 0",
        "ChildAdapter.insertItems: 3, 1"
      };

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keep;

  @Parameters(name = "{0}, keepUnrelated: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(keep);
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(keep, b -> b.addKeepClassAndMembersRules(VideoPageViewModel.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject parentClassSubject = inspector.clazz(ParentAdapter.class);
              assertThat(parentClassSubject, isPresent());

              MethodSubject parentMethodSubject =
                  parentClassSubject.uniqueMethodWithOriginalName("insertItems");
              assertThat(parentMethodSubject, isPresent());
              assertThat(parentMethodSubject, isFinal());

              ClassSubject childClassSubject = inspector.clazz(ChildAdapter.class);
              assertThat(childClassSubject, isPresent());

              MethodSubject childMethodSubject =
                  childClassSubject.uniqueMethodWithOriginalName("insertItems");
              assertThat(childMethodSubject, isPresent());

              if (keep) {
                // TODO(b/561828141): Should not have the same final signature as the final method
                //  in ParentAdapter.
                assertEquals(
                    parentMethodSubject.getFinalSignature(),
                    childMethodSubject.getFinalSignature());
              } else {
                assertNotEquals(
                    parentMethodSubject.getFinalSignature(),
                    childMethodSubject.getFinalSignature());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/561828141): Should succeed with expected output.
        .applyIf(
            keep
                && parameters.isCfRuntime()
                && parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            rr -> rr.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            keep && parameters.isCfRuntime(),
            rr -> rr.assertFailureWithErrorThatThrows(VerifyError.class),
            keep && parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V5_1_1),
            rr -> rr.assertFailureWithErrorThatThrows(LinkageError.class),
            keep,
            rr -> rr.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            rr -> rr.assertSuccessWithOutputLines(EXPECTED));
  }

  @NoVerticalClassMerging
  public static class ParentAdapter {

    @NeverInline
    public synchronized void insertItems(List<String> items, int position) {
      System.out.println("ParentAdapter.insertItems: " + items.size() + ", " + position);
    }
  }

  @NoVerticalClassMerging
  public interface Adapter {

    void insertItems(int position, List<String> items);
  }

  @NeverClassInline
  public static class ChildAdapter extends ParentAdapter implements Adapter {

    @NeverInline
    @Override
    public void insertItems(int position, List<String> items) {
      System.out.println("ChildAdapter.insertItems: " + position + ", " + items.size());
    }
  }

  public static class VideoPageViewModel {

    public void insertItems(int position, List<String> items) {
      System.out.println("VideoPageViewModel.insertItems");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ChildAdapter child = new ChildAdapter();
      child.insertItems(Collections.emptyList(), 2);
      child.insertItems(Collections.singletonList("a"), 3);
      Adapter adapter = child;
      adapter.insertItems(2, Collections.emptyList());
      adapter.insertItems(3, Collections.singletonList("a"));
    }
  }
}
