// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerStaticFieldShadowingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep interface " + Defaults.class.getTypeName() + " { *; }")
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(BaseChecker.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject allow =
                  inspector.clazz(Gate.class).uniqueMethodWithOriginalName("allow");
              assertThat(allow, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  @NoVerticalClassMerging
  public static class Permissions {
    @NeverPropagateValue public static int requiredLevel = 100;
  }

  public abstract static class BaseChecker extends Permissions {}

  @NoVerticalClassMerging
  public interface Defaults {
    @NeverPropagateValue int requiredLevel = 0;
  }

  @NeverClassInline
  public static final class Gate extends BaseChecker implements Defaults {
    @NeverInline
    public static boolean allow(int level) {
      return level >= BaseChecker.requiredLevel;
    }
  }

  public static final class Main {
    public static void main(String[] args) {
      System.out.println(Gate.allow(7));
    }
  }
}
