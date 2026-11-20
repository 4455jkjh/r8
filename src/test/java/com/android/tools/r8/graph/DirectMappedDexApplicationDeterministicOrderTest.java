// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DirectMappedDexApplicationDeterministicOrderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableShrinking;

  @Parameters(name = "{0}, enableShrinking: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build(),
        BooleanUtils.values());
  }

  @Test
  public void testNoEagerDeterministicOrderBeforeInitialTreePruner() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(!enableShrinking, b -> b.addDontShrink())
        .addOptionsModification(
            options -> options.getArtProfileOptions().setEnableCompletenessCheckForTesting(false))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ok");
  }

  static class Unused {}

  static class Main {
    public static void main(String[] args) {
      System.out.println("ok");
    }
  }
}
