// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationSiblingMethodMemberValuePropagationTest extends TestBase {

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
        .assertSuccessWithOutputLines("anno");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(A.class, Target.class)
        .addKeepRuntimeVisibleAnnotations()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557267860): Should succeed with expected output.
        .assertSuccessWithOutputLines("K");
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface I {
    String value();
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class Base {
    @NeverInline
    public String value() {
      return "K";
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class A extends Base implements I {
    @Override
    public Class<? extends Annotation> annotationType() {
      return I.class;
    }
  }

  @I("anno")
  public static class Target {}

  public static class Main {
    public static void main(String[] args) {
      if (args.length == 42) {
        System.out.println(new A().value());
      }
      I i = Target.class.getAnnotation(I.class);
      System.out.println(i.value());
    }
  }
}
