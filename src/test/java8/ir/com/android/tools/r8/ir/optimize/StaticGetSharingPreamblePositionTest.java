// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for hoisting a static-get into a block whose exit has the preamble position.
@RunWith(Parameterized.class)
public class StaticGetSharingPreamblePositionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Throwable {
    // Remove the line of the `if` in test(), so that it has the preamble position, and number the
    // lines of the two branches 100 and 200.
    testForR8(parameters)
        .addProgramClassFileData(
            transformer(Main.class)
                .setPredictiveLineNumbering(MethodPredicate.onName("test"), -1)
                .transform())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .enableInliningAnnotations()
        .compile()
        .inspectProguardMap(
            map -> {
              // The System.out read hoisted before the `if` must not be attributed to line 200
              // ahead of the preamble.
              List<String> testMappings =
                  StringUtils.splitLines(map).stream()
                      .map(String::trim)
                      .filter(line -> line.contains("void test(boolean)"))
                      .collect(Collectors.toList());
              List<String> expectedMappings;
              if (parameters.isCfRuntime()) {
                expectedMappings = ImmutableList.of("1:101:void test(boolean):100:200 -> a");
              } else if (canDiscardResidualDebugInfo(parameters)) {
                // The mapping is on native pcs, the hoisted read and the `if` at pcs 0-3 have none.
                expectedMappings =
                    ImmutableList.of(
                        "4:9:void test(boolean):100:100 -> a",
                        "10:15:void test(boolean):200:200 -> a");
              } else {
                expectedMappings =
                    ImmutableList.of(
                        "1:1:void test(boolean):100:100 -> a",
                        "2:2:void test(boolean):200:200 -> a");
              }
              assertEquals(expectedMappings, testMappings);
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  static class Main {

    @NeverInline
    static void test(boolean b) {
      if (b) {
        System.out.println("true");
      } else {
        System.out.println("false");
      }
    }

    public static void main(String[] args) {
      test(args.length == 0);
    }
  }
}
