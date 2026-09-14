// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
    return getTestParameters()
        .withDefaultCfRuntime()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .build();
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
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(AndroidApiLevel.L))
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557271579): At min-api < 24, canUseDefaultAndStaticInterfaceMethods() is false,
        //  so the default interface methods check is skipped and B1.remove() is hoisted to
        //  A.remove(). On API >= 24 runtimes, D.remove() dispatches to A.doRemove() instead of
        //  Iterator.remove().
        .assertSuccessWithOutputLines("A.doRemove");
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
