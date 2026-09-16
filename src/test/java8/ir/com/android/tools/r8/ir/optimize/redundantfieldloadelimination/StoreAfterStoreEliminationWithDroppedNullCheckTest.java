// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StoreAfterStoreEliminationWithDroppedNullCheckTest extends TestBase {

  private static List<String> EXPECTED = ImmutableList.of("Armed: false");
  private static List<String> UNEXPECTED = ImmutableList.of("Armed: true");

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
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557274976): R8 drops the implicit null check on t, causing Service.armed to be set
        // to true.
        .assertSuccessWithOutputLines(UNEXPECTED);
  }

  public static class Target {
    @NeverPropagateValue public Object state;
  }

  public static class Service {
    @NeverPropagateValue public static boolean armed = false;

    @NeverInline
    public static void apply(Target t) {
      t.state = "PENDING";
      Service.armed = true;
      t.state = "DONE";
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Target target = args.length == 42 ? new Target() : null;
      try {
        Service.apply(target);
      } catch (NullPointerException e) {
        // Expected
      }
      System.out.println("Armed: " + Service.armed);
      if (target != null) {
        System.out.println("State: " + target.state);
      }
    }
  }
}
