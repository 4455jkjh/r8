// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationRemovalNoShrinkingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableProGuardCompatibilityMode;

  @Parameter(2)
  public boolean keepRuntimeVisibleAnnotations;

  @Parameters(name = "{0}, compat: {1}, keep: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), enableProGuardCompatibilityMode)
        .addInnerClasses(getClass())
        .addDontObfuscate()
        .addDontOptimize()
        .addDontShrink()
        .applyIf(
            keepRuntimeVisibleAnnotations, TestShrinkerBuilder::addKeepRuntimeVisibleAnnotations)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              // TODO(b/530133774): The runtime invisible annotation should be stripped in full
              //  mode.
              assertThat(mainClass.annotation(RuntimeInvisibleAnnotation.class), isPresent());
              assertThat(mainClass.annotation(RuntimeVisibleAnnotation.class), isPresent());
            });
  }

  @RuntimeInvisibleAnnotation
  @RuntimeVisibleAnnotation
  public static class Main {}

  @Retention(RetentionPolicy.CLASS)
  @interface RuntimeInvisibleAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface RuntimeVisibleAnnotation {}
}
