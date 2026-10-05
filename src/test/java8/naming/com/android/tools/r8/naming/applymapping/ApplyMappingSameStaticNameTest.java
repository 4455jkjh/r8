// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test reproduces b/131532229 on d8-1.4. This test is basically just testing that we are not
 * throwing up on the mapping file that has static methods and fields that go to different names.
 */
@RunWith(Parameterized.class)
public class ApplyMappingSameStaticNameTest extends TestBase {

  public static final String pgMap =
      StringUtils.lines(
          I.class.getTypeName() + " -> " + I.class.getTypeName() + ":",
          "  boolean[] jacocoInit() -> a",
          "  int f1 -> c",
          A.class.getTypeName() + " -> " + A.class.getTypeName() + ":",
          "  boolean[] jacocoInit() -> a",
          "  int f1 -> c",
          B.class.getTypeName() + " -> " + B.class.getTypeName() + ":",
          "  boolean[] jacocoInit() -> b",
          "  int f1 -> d");

  public interface I {
    static boolean[] jacocoInit() {
      return null;
    }

    int f1 = 0;
  }

  public static class A {
    public static boolean[] jacocoInit() {
      return null;
    }

    public static int f1 = 1;
  }

  // B is abstract and implements I so ProguardMapMinifier propagates non-private interface
  // mappings from I onto B when B is on the classpath, testing that B's own static method and
  // field mappings are not overwritten by I's differently-named static method and field mappings.
  public abstract static class B extends A implements I {
    public static boolean[] jacocoInit() {
      return null;
    }

    public static int f1 = 2;
  }

  public static class C extends B {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void test_b131532229() throws Exception {
    testForR8(parameters.getBackend())
        .addDontShrink()
        .addClasspathClasses(I.class, A.class, B.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addProgramClasses(C.class)
        .addApplyMapping(pgMap)
        .compile();
  }
}
