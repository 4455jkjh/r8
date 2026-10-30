// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingAnnotationClassValueTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Handles.class)
        .addKeepAttributeAnnotationDefault()
        .addKeepRuntimeVisibleAnnotations()
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertNotUnboxed(
                    Tag.class, TagDefault.class, TagArray.class, TagArrayDefault.class))
        .compile();
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Handles {
    Class<?> value();

    Class<?> valueDefault() default TagDefault.class;

    Class<?>[] arrayValue();

    Class<?>[] arrayValueDefault() default {TagArrayDefault.class};
  }

  public enum Tag {
    A,
    B
  }

  public enum TagDefault {
    A,
    B
  }

  public enum TagArray {
    A,
    B
  }

  public enum TagArrayDefault {
    A,
    B
  }

  @Handles(
      value = Tag.class,
      arrayValue = {TagArray.class})
  public static class Main {
    public static void main(String[] args) {}
  }
}
