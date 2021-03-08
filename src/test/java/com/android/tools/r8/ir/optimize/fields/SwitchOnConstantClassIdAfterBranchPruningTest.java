// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.fields;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchOnConstantClassIdAfterBranchPruningTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchOnConstantClassIdAfterBranchPruningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertIsCompleteMergeGroup(A.class, B.class, C.class))
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .noMinification()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals(0, aClassSubject.allInstanceFields().size());

              MethodSubject mMethodSubject =
                  aClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
              assertThat(mMethodSubject, isPresent());
              assertTrue(
                  mMethodSubject.streamInstructions().noneMatch(x -> x.isIf() || x.isSwitch()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      if (alwaysFalse()) {
        new B().m();
        new C().m();
      }
    }

    static boolean alwaysFalse() {
      return false;
    }
  }

  @NeverClassInline
  static class A {

    void m() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  static class B {

    void m() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  static class C {

    void m() {
      System.out.println("C");
    }
  }
}
