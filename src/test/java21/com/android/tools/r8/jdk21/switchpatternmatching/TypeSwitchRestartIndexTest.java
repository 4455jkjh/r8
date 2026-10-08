// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk21.switchpatternmatching;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class TypeSwitchRestartIndexTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  public static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null",
          "String: a",
          "Integer: 1",
          "Other",
          "null",
          "Long String: abc",
          "Short String: a",
          "Other",
          "Custom String: hello",
          "Custom Other",
          "Expected IOOBE",
          "Expected IOOBE");

  private byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
        .clearNest()
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitVarInsn(int opcode, int var) {
                if (getContext().getReference().getMethodName().equals("customRestartSwitch")
                    && opcode == Opcodes.ILOAD) {
                  // Replace the load of the synthetic restart local with parameter 1 (int restart).
                  super.visitVarInsn(Opcodes.ILOAD, 1);
                  return;
                }
                super.visitVarInsn(opcode, var);
              }
            })
        .transform();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .compile()
        // With R8 partial the synthetic switch helper classes are merged and renamed.
        .inspectIf(
            parameters.getPartialCompilationTestParameters().isNone(),
            inspector -> {
              List<MethodSubject> switchDispatchMethods =
                  inspector.allClasses().stream()
                      .map(c -> c.uniqueMethodWithOriginalName("switchDispatch"))
                      .filter(MethodSubject::isPresent)
                      .collect(Collectors.toList());
              assertEquals(3, switchDispatchMethods.size());
              long methodsWithoutCheckIndex =
                  switchDispatchMethods.stream()
                      .filter(
                          m -> m.streamInstructions().noneMatch(InstructionSubject::isInvokeStatic))
                      .count();
              // unguardedSwitch has restart == 0 always and therefore omits Objects.checkIndex,
              // the restart switch, and the restart parameter; guardedSwitch and
              // customRestartSwitch keep them.
              assertEquals(1, methodsWithoutCheckIndex);
              long methodsWithoutSwitch =
                  switchDispatchMethods.stream()
                      .filter(m -> m.streamInstructions().noneMatch(InstructionSubject::isSwitch))
                      .count();
              assertEquals(1, methodsWithoutSwitch);
              long oneArgMethods =
                  switchDispatchMethods.stream().filter(m -> m.getParameters().size() == 1).count();
              assertEquals(1, oneArgMethods);
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addProgramClassFileData(getTransformedMain())
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {

    static void unguardedSwitch(Object obj) {
      switch (obj) {
        case null -> System.out.println("null");
        case String s -> System.out.println("String: " + s);
        case Integer i -> System.out.println("Integer: " + i);
        default -> System.out.println("Other");
      }
    }

    static void guardedSwitch(Object obj) {
      switch (obj) {
        case null -> System.out.println("null");
        case String s when s.length() > 2 -> System.out.println("Long String: " + s);
        case String s -> System.out.println("Short String: " + s);
        default -> System.out.println("Other");
      }
    }

    static void customRestartSwitch(Object obj, int restart) {
      switch (obj) {
        case String s -> System.out.println("Custom String: " + s);
        default -> System.out.println("Custom Other");
      }
    }

    public static void main(String[] args) {
      unguardedSwitch(null);
      unguardedSwitch("a");
      unguardedSwitch(1);
      unguardedSwitch(new Object());

      guardedSwitch(null);
      guardedSwitch("abc");
      guardedSwitch("a");
      guardedSwitch(new Object());

      customRestartSwitch("hello", 0);
      customRestartSwitch("hello", 1);
      try {
        customRestartSwitch("hello", -1);
        System.out.println("Unexpected");
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Expected IOOBE");
      }
      try {
        customRestartSwitch("hello", 2);
        System.out.println("Unexpected");
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Expected IOOBE");
      }
    }
  }
}
