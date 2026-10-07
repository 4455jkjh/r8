// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.ir.conversion.finalizer.LoopBackEdgeCanonicalizerTest.countBackwardGotos;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that each loop has a single backward branch, which is a goto, for irreducible control flow.
 *
 * <p>These cases are theoretical: none of the apps in our compile dumps seem to have irreducible
 * control flow. They cannot be written in Java source code and are built with Jasmin.
 */
@RunWith(Parameterized.class)
public class LoopBackEdgeCanonicalizerIrreducibleTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT =
      new String[] {"12", "21", "0", "112", "121", "211", "0"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(buildIrreducibleClass())
        .release()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), "Irreducible")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(buildIrreducibleClass())
        .addKeepClassAndMembersRules("Irreducible")
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), "Irreducible")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject irreducibleClassSubject = inspector.clazz("Irreducible");
    assertEquals(1, countBackwardGotos(irreducibleClassSubject, "twoEntries"));
    assertEquals(1, countBackwardGotos(irreducibleClassSubject, "threeEntries"));
  }

  // Builds a class with loops that can be entered at different blocks.
  private static List<byte[]> buildIrreducibleClass() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Irreducible");
    // static int twoEntries(int n, int entry) {
    //   int sum = 0;
    //   if (entry != 0) goto B;
    //   A: if (n <= 0) return sum; sum += 1; n--;
    //   B: if (n <= 0) return sum; sum += 10; n--; goto A;
    // }
    clazz.addStaticMethod(
        "twoEntries",
        ImmutableList.of("I", "I"),
        "I",
        ".limit stack 1",
        ".limit locals 3",
        "  iconst_0",
        "  istore 2",
        "  iload 1",
        "  ifne B",
        "A:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 2 1",
        "  iinc 0 -1",
        "B:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 2 10",
        "  iinc 0 -1",
        "  goto A",
        "Exit:",
        "  iload 2",
        "  ireturn");
    // Same as twoEntries, but with a cycle A -> B -> C -> A that is entered by a switch.
    clazz.addStaticMethod(
        "threeEntries",
        ImmutableList.of("I", "I"),
        "I",
        ".limit stack 1",
        ".limit locals 3",
        "  iconst_0",
        "  istore 2",
        "  iload 1",
        "  tableswitch 0",
        "    A",
        "    B",
        "    C",
        "    default: Exit",
        "A:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 2 1",
        "  iinc 0 -1",
        "B:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 2 10",
        "  iinc 0 -1",
        "C:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 2 100",
        "  iinc 0 -1",
        "  goto A",
        "Exit:",
        "  iload 2",
        "  ireturn");
    List<String> mainLines = new ArrayList<>();
    mainLines.add(".limit stack 3");
    mainLines.add(".limit locals 1");
    addPrintCall(mainLines, "twoEntries", 3, 0);
    addPrintCall(mainLines, "twoEntries", 3, 1);
    addPrintCall(mainLines, "twoEntries", 0, 1);
    addPrintCall(mainLines, "threeEntries", 4, 0);
    addPrintCall(mainLines, "threeEntries", 4, 1);
    addPrintCall(mainLines, "threeEntries", 4, 2);
    addPrintCall(mainLines, "threeEntries", 4, 3);
    mainLines.add("  return");
    clazz.addMainMethod(mainLines);
    return builder.buildClasses();
  }

  private static void addPrintCall(List<String> lines, String methodName, int n, int entry) {
    lines.add("  getstatic java/lang/System/out Ljava/io/PrintStream;");
    lines.add("  ldc " + n);
    lines.add("  ldc " + entry);
    lines.add("  invokestatic Irreducible/" + methodName + "(II)I");
    lines.add("  invokevirtual java/io/PrintStream/println(I)V");
  }
}
