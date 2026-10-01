// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.softverificationerrorremoval;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import java.util.function.Supplier;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SoftVerificationErrorRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public SoftVerificationErrorRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testWithoutJavaStub() throws Exception {
    testForD8()
        .addInnerClasses(SoftVerificationErrorRemovalTest.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIfDexRuntime(
            version -> version.isOlderThanOrEqual(Version.V4_4_4),
            r -> assertVerificationErrorsPresent(r, true),
            r -> assertVerificationErrorsPresent(r, false));
  }

  private void assertVerificationErrorsPresent(TestRunResult<?> run, boolean present) {
    Matcher<String> matcher1 =
        containsString(
            "VFY: unable to find class referenced in signature (Ljava/util/function/Supplier;)");
    Matcher<String> matcher2 =
        containsString(
            "VFY: unable to resolve interface method 7: Ljava/util/function/Supplier;.get"
                + " ()Ljava/lang/Object;");
    run.assertStderrMatches(present ? matcher1 : not(matcher1));
    run.assertStderrMatches(present ? matcher2 : not(matcher2));
  }

  @Test
  public void testWithJavaStub() throws Exception {
    TestRunResult<?> run =
        testForD8()
            .addInnerClasses(SoftVerificationErrorRemovalTest.class)
            .addProgramClassFileData(LibraryFilesHelper.getSupplier())
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class);
    assertVerificationErrorsPresent(run, false);
  }

  static class TestClass {

    public static void main(String[] args) {
      ExampleClass exampleClass = new ExampleClass();
      exampleClass.hello();
    }
  }

  static class ExampleClass {
    void hello() {
      System.out.println("hello");
    }

    void hello(Supplier<String> stringSupplier) {
      System.out.println(stringSupplier.get());
    }
  }
}
