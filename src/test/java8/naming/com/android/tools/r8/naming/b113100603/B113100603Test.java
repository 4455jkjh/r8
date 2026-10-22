// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b113100603;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {
  interface Action {
    void perform();
  }

  private void doAction(Action action) {
    action.perform();
  }

  private void m() {
    doAction(() -> System.out.println("And action"));
  }

  public static void main(String[] args) {
    new A().m();
  }
}

@RunWith(Parameterized.class)
public class B113100603Test extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public B113100603Test(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    DiagnosticsChecker checker = new DiagnosticsChecker();
    ToolHelper.runR8(
        ToolHelper.prepareR8CommandBuilder(
                readClasses(A.class, A.Action.class), emptyConsumer(Backend.DEX), checker)
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(A.class)), Origin.unknown())
            .build());
    assertEquals(0, checker.warnings.size());
  }
}
