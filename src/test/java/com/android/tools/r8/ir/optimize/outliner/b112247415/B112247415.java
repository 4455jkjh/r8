// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b112247415;

import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  interface Act {
    default String get(StringBuilder builder, String arg) {
      builder.append(arg).append(arg).append(arg);
      return builder.toString();
    }
  }

  public static void main(String[] args) {
    System.out.println(get(new TestClass().toActOverridden(), new StringBuilder(), "a"));
    System.out.println(get(new TestClass().toActDefault(), new StringBuilder(), "b"));
  }

  static String get(Act act, StringBuilder builder, String arg) {
    act.get(builder, arg);
    return builder.toString();
  }

  Act toActOverridden() {
    return new Act() {
      @Override
      public String get(StringBuilder builder, String arg) {
        builder.append(arg).append(arg).append(arg);
        return builder.toString();
      }
    };
  }

  Act toActDefault() {
    return new Act() {
    };
  }
}

@RunWith(Parameterized.class)
public class B112247415 extends TestBase {

  private static final String EXPECTED = StringUtils.lines("aaa", "bbb");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addDontObfuscate()
            .setMinApi(parameters)
            .addProgramClassesAndInnerClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(
                options -> {
                  // To trigger outliner, set # of expected outline candidate as threshold.
                  options.outline.threshold = 2;
                  options.inlinerOptions().enableInlining = false;
                  // Disable minimize synthetic names for robust detection of synthetic kinds.
                  options.desugarSpecificOptions().minimizeSyntheticNames = false;
                })
            .noHorizontalClassMergingOfSynthetics()
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED)
            .inspector();

    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (!SyntheticItemsTestUtils.isExternalOutlineClass(clazz.getFinalReference())) {
        clazz.forAllMethods(
            method -> {
              if (method.hasCode()) {
                verifyAbsenceOfStringBuilderAppend(method.streamInstructions());
              }
            });
      }
    }
  }

  private void verifyAbsenceOfStringBuilderAppend(Stream<InstructionSubject> instructions) {
    instructions
        .filter(InstructionSubject::isInvokeVirtual)
        .forEach(instr -> {
          DexMethod invokedMethod = instr.getMethod();
          if (invokedMethod.holder.getName().endsWith("StringBuilder")) {
            assertNotEquals("append", invokedMethod.name.toString());
          }
        });
  }
}
