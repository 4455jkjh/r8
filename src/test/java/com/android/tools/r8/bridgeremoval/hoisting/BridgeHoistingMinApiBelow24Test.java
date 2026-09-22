// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BridgeHoistingMinApiBelow24Test extends TestBase {

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
        .assertSuccessWithOutputLines("Caught UOE");
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            rr -> rr.assertSuccessWithOutputLines("Caught UOE"),
            rr -> rr.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class A {
    @NeverInline
    @NoMethodStaticizing
    public void doRemove() {
      System.out.println("A.doRemove");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class B1 extends A {
    @NeverInline
    @NoMethodStaticizing
    public void remove() {
      doRemove();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class B2 extends A {}

  @NeverClassInline
  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public static class D extends B2 implements Iterator<Object> {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      return null;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      if (args.length == 42) {
        new B1().remove();
      }
      if (args.length == 43) {
        new B2();
      }
      Iterator<Object> it = new D();
      try {
        it.remove();
      } catch (UnsupportedOperationException e) {
        System.out.println("Caught UOE");
      }
    }
  }
}
