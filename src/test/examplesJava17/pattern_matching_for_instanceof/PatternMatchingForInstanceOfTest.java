// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package pattern_matching_for_instanceof;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PatternMatchingForInstanceOfTest extends TestBase {

  @Parameter public TestParameters parameters;

  private static List<String> EXPECTED = ImmutableList.of("Hello, world!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(Main.class);
    if (parameters.getBackend().isDex()) {
      builder.run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines(EXPECTED);
    } else {
      testForJvm(parameters)
          .addRunClasspathFiles(builder.compile().writeToZip())
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines(EXPECTED);
    }
  }

  public static final class Main {
    public static void main(String[] args) {
      Object obj = "Hello, world!";
      if (obj instanceof String s) {
        System.out.println(s);
      }
    }
  }
}
