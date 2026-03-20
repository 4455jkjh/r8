// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.ifs;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SplitBranchResourceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public SplitBranchResourceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class, R.fraction.class)
        .build(temp);
  }

  @Test
  public void test() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .enableOptimizedShrinking()
        .addAndroidResources(testResources)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "true", "true", "false", "true", "true", "false", "true", "false", "true", "false");
  }

  private void inspect(CodeInspector inspector) {
    inspector
        .clazz(Main.class)
        .forAllMethods(
            method -> {
              String name = method.getOriginalMethodName();
              if (name.equals("main") || name.equals("clinit")) {
                return;
              }
              long count = method.streamInstructions().filter(InstructionSubject::isIf).count();
              if (name.equals("getResourceIdOrZero")) {
                assertEquals("Wrong count for " + name, 6, count);
              } else if (name.equals("testMultiplePhis")) {
                assertEquals("Wrong count for " + name, 3, count);
              } else if (name.equals("testMixed") || name.equals("testEquality")) {
                assertEquals("Wrong count for " + name, 2, count);
              }
            });
  }

  public static class Main {

    static final int NONE = 0;
    static final int ONE = 1;
    static final int TWO = 2;
    static final int THREE = 3;
    static final int FOUR = 4;

    public static void main(String[] args) {
      System.out.println(getResourceIdOrZero(NONE));
      System.out.println(getResourceIdOrZero(THREE));
      System.out.println(getResourceIdOrZero(5));

      System.out.println(testMultiplePhis(11));
      System.out.println(testMultiplePhis(6));
      System.out.println(testMultiplePhis(1));

      System.out.println(testMixed(1));
      System.out.println(testMixed(0));

      System.out.println(testEquality(1));
      System.out.println(testEquality(0));
    }

    @NeverInline
    static boolean getResourceIdOrZero(int i) {
      return i == NONE || getResId(i) > 0;
    }

    @NeverInline
    static boolean testEquality(int i) {
      int v;
      if (i > 0) v = R.string.foo;
      else v = R.string.bar;
      return v == R.string.foo;
    }

    @NeverInline
    static boolean testMultiplePhis(int i) {
      int x;
      if (i > 10) {
        x = R.string.foo;
      } else if (i > 5) {
        x = R.string.bar;
      } else {
        x = 0;
      }
      return x != 0;
    }

    @NeverInline
    static boolean testMixed(int i) {
      int v;
      if (i > 0) v = R.string.foo;
      else v = 42;
      return v > 100;
    }

    @AlwaysInline
    static int getResId(int zoomIndex) {
      switch (zoomIndex) {
        case ONE:
          return R.fraction.one;
        case TWO:
          return R.fraction.two;
        case THREE:
          return R.fraction.three;
        case FOUR:
          return R.fraction.four;
        default:
          return 0;
      }
    }
  }

  public static class R {

    public static class string {
      public static int bar;
      public static int foo;
    }

    public static class fraction {
      public static int one;
      public static int two;
      public static int three;
      public static int four;
    }
  }
}
