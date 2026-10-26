// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaTestRunner extends TestBase {

  private static final Class<?> CLASS = LambdaTest.class;
  private static final String METHOD = "main";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public LambdaTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Test that the InvokeDynamic instruction in LambdaTest.main()
    // is not modified by the R8 compilation.
    // First, extract the InvokeDynamic instruction from the input class.
    CodeInspector inputInspector =
        testForJvm(parameters)
            .addProgramClasses(CLASS)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutputLines("42")
            .inspector();
    CfInvokeDynamic insnInput = findFirstInMethod(inputInspector);
    assertNotNull("No CfInvokeDynamic found in input", insnInput);
    // Compile with R8 and extract the InvokeDynamic instruction from the output class.
    CodeInspector outputInspector =
        testForR8(parameters)
            .addProgramClasses(CLASS)
            .debug()
            .addDontShrink()
            .addDontObfuscate()
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutputLines("42")
            .inspector();
    CfInvokeDynamic insnOutput = findFirstInMethod(outputInspector);
    assertNotNull("No CfInvokeDynamic found in output", insnOutput);
    // Check that the InvokeDynamic instruction is not modified.
    assertEquals(print(insnInput), print(insnOutput));
  }

  private static CfInvokeDynamic findFirstInMethod(CodeInspector inspector) {
    String returnType = "void";
    List<String> args = Collections.singletonList(String[].class.getTypeName());
    DexEncodedMethod method = inspector.clazz(CLASS).method(returnType, METHOD, args).getMethod();
    CfCode code = method.getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction instanceof CfInvokeDynamic) {
        return (CfInvokeDynamic) instruction;
      }
    }
    return null;
  }

  private static String print(CfInstruction instruction) {
    CfPrinter printer = new CfPrinter();
    instruction.print(printer);
    return printer.toString();
  }

}
