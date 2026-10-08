// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk21.switchpatternmatching;

import static com.android.tools.r8.desugar.switchpatternmatching.SwitchTestHelper.hasJdk21TypeSwitch;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeSwitchStringSwitchTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null",
          "AaAa",
          "AaBB",
          "Other: BBAa",
          "CCCC",
          "Other: other",
          "null",
          "X",
          "Y",
          "String: z",
          "null",
          "Hello world!",
          "Hello world, ish!",
          "_",
          "Hello world, ish2!",
          "Other: Hello worlc@",
          "Other: Hello");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .inspect(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testHashCodeCollisions() {
    parameters.assumeJvmTestParameters();
    // The string cases of Main.stringSwitch share hash codes.
    assertEquals("AaAa".hashCode(), "AaBB".hashCode());
    assertEquals("AaAa".hashCode(), "BBAa".hashCode());
    assertNotEquals("AaAa".hashCode(), "CCCC".hashCode());
    // The string cases of Main.stringSwitchWithHashCollisions share hash codes, interleaved with a
    // string case with a different hash code.
    assertEquals("Hello world!".hashCode(), "Hello worla~".hashCode());
    assertEquals("Hello world!".hashCode(), "Hello worlb_".hashCode());
    assertEquals("Hello world!".hashCode(), "Hello worlc@".hashCode());
    assertNotEquals("Hello world!".hashCode(), "_".hashCode());
  }

  private static List<FoundMethodSubject> getSwitchDispatchMethods(CodeInspector inspector) {
    return inspector.allClasses().stream()
        .flatMap(clazz -> clazz.allMethods().stream())
        .filter(method -> method.getOriginalMethodName().equals("switchDispatch"))
        .collect(Collectors.toList());
  }

  private void inspectD8(CodeInspector inspector) {
    // All switchDispatch methods dispatch on String.hashCode() for the leading string cases.
    List<FoundMethodSubject> switchDispatchMethods = getSwitchDispatchMethods(inspector);
    assertEquals(3, switchDispatchMethods.size());
    for (MethodSubject method : switchDispatchMethods) {
      assertThat(method, invokesMethodWithHolderAndName(String.class, "hashCode"));
    }
    // Strings with colliding hash codes share a single switch key, and are then distinguished
    // using String.equals().
    MethodSubject collisions =
        switchDispatchMethods.stream()
            .filter(
                method ->
                    method
                        .streamInstructions()
                        .anyMatch(instruction -> instruction.isConstString("Hello worla~")))
            .findFirst()
            .get();
    assertEquals(
        2,
        collisions
            .streamInstructions()
            .filter(InstructionSubject::isSwitch)
            .mapToInt(instruction -> instruction.asSwitch().getKeys().size())
            .sum());
    assertEquals(
        4,
        collisions
            .streamInstructions()
            .filter(
                instruction ->
                    instruction.isInvokeVirtual()
                        && instruction.getMethod().getName().toString().equals("equals"))
            .count());
  }

  private void inspectR8(CodeInspector inspector) {
    // The switches with at least three leading string cases still dispatch on String.hashCode()
    // when desugared to DEX (R8 CF output does not desugar typeSwitch).
    for (String name : new String[] {"stringSwitch", "stringSwitchWithHashCollisions"}) {
      MethodSubject method = inspector.clazz(Main.class).uniqueMethodWithOriginalName(name);
      if (parameters.isCfRuntime()) {
        assertTrue(hasJdk21TypeSwitch(method));
      } else {
        assertThat(method, invokesMethodWithHolderAndName(String.class, "hashCode"));
      }
    }
  }

  static class Main {

    @NeverInline
    static void stringSwitch(String s) {
      switch (s) {
        case null -> System.out.println("null");
        case "AaAa" -> System.out.println("AaAa");
        case "AaBB" -> System.out.println("AaBB");
        case "CCCC" -> System.out.println("CCCC");
        default -> System.out.println("Other: " + s);
      }
    }

    @NeverInline
    static void stringSwitchWithTrailingType(String s) {
      switch (s) {
        case null -> System.out.println("null");
        case "X" -> System.out.println("X");
        case "Y" -> System.out.println("Y");
        case String other -> System.out.println("String: " + other);
      }
    }

    @NeverInline
    static void stringSwitchWithHashCollisions(String s) {
      switch (s) {
        case null -> System.out.println("null");
        case "Hello world!" -> System.out.println("Hello world!");
        case "Hello worla~" -> System.out.println("Hello world, ish!");
        case "_" -> System.out.println("_");
        case "Hello worlb_" -> System.out.println("Hello world, ish2!");
        default -> System.out.println("Other: " + s);
      }
    }

    public static void main(String[] args) {
      stringSwitch(null);
      stringSwitch("AaAa");
      stringSwitch("AaBB");
      stringSwitch("BBAa");
      stringSwitch("CCCC");
      stringSwitch("other");

      stringSwitchWithTrailingType(null);
      stringSwitchWithTrailingType("X");
      stringSwitchWithTrailingType("Y");
      stringSwitchWithTrailingType("z");

      stringSwitchWithHashCollisions(null);
      stringSwitchWithHashCollisions("Hello world!");
      stringSwitchWithHashCollisions("Hello worla~");
      stringSwitchWithHashCollisions("_");
      stringSwitchWithHashCollisions("Hello worlb_");
      stringSwitchWithHashCollisions("Hello worlc@");
      stringSwitchWithHashCollisions("Hello");
    }
  }
}
