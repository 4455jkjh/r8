// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.stringconcatfactory;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringConcatFactoryArtTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Values: true, 0, 1, 2, 3, 4.0, 5.0");
  static final Class<?> MAIN = Main.class;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(Version.MAIN)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withApiLevel(AndroidApiLevel.CINNAMON_BUN_2)
        .build();
  }

  public StringConcatFactoryArtTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkInvokeDynamic(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz(MAIN.getTypeName())
            .uniqueMethodWithOriginalName("testPrimitives")
            .streamInstructions()
            .anyMatch(InstructionSubject::isInvokeDynamic));
    assertTrue(
        inspector
            .clazz(MAIN.getTypeName())
            .uniqueMethodWithOriginalName("testPrimitives")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeDynamic)
            .map(InstructionSubject::asInvokeDynamic)
            .allMatch(
                instruction ->
                    instruction
                        .getCallSite()
                        .methodName
                        .toString()
                        .equals("makeConcatWithConstants")));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), MAIN)
        .inspect(this::checkInvokeDynamic)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addOptionsModification(
            opt -> {
              opt.enableStringConcatInstruction = true;
              opt.getTestingOptions().enableStringConcatFactoryForDex = true;
            })
        .compile()
        .inspect(this::checkInvokeDynamic)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        // Default is JDK-8 rt.jar which has no definition of StringConcatFactory.
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN)
        .addOptionsModification(
            opt -> {
              opt.enableStringConcatInstruction = true;
              opt.getTestingOptions().enableStringConcatFactoryForDex = true;
            })
        .enableInliningAnnotations()
        .compile()
        .inspect(this::checkInvokeDynamic)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class Main {

    @NeverInline
    public static void testPrimitives(
        boolean z, byte b, short s, int i, long l, float f, double d) {
      System.out.println(
          "Values: " + z + ", " + b + ", " + s + ", " + i + ", " + l + ", " + f + ", " + d);
    }

    public static void main(String[] args) {
      testPrimitives(
          args.length == 0,
          (byte) args.length,
          (short) (args.length + 1),
          args.length + 2,
          args.length + 3,
          args.length + 4,
          args.length + 5);
    }
  }
}
