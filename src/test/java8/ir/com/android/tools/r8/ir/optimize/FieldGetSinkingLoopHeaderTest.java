// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldGetSinkingLoopHeaderTest extends TestBase {

  private static final String[] EXPECTED_EDGE_CASE_LINES = {
    "0:3", "0:3", "0:3", "2:0", "1:1", "3", "3"
  };

  // TODO(b/584819109): The first three cases should output "0:3" instead of "3:3".
  private static final String[] BUGGY_EDGE_CASE_LINES = {
    "3:3", "3:3", "3:3", "2:0", "1:1", "3", "3"
  };

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "3");
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .debug()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "3");
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/584819109): Should output "0", "3".
        .assertSuccessWithOutputLines("3", "3")
        .inspect(FieldGetSinkingLoopHeaderTest::inspectMain);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/584819109): Should output "0", "3".
        .assertSuccessWithOutputLines("3", "3")
        .inspect(FieldGetSinkingLoopHeaderTest::inspectMain);
  }

  @Test
  public void testEdgeCasesJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), EdgeCasesMain.class)
        .assertSuccessWithOutputLines(EXPECTED_EDGE_CASE_LINES);
  }

  @Test
  public void testEdgeCasesD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .debug()
        .run(parameters.getRuntime(), EdgeCasesMain.class)
        .assertSuccessWithOutputLines(EXPECTED_EDGE_CASE_LINES);
  }

  @Test
  public void testEdgeCasesD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), EdgeCasesMain.class)
        // TODO(b/584819109): Should output EXPECTED_EDGE_CASE_LINES.
        .assertSuccessWithOutputLines(BUGGY_EDGE_CASE_LINES)
        .inspect(FieldGetSinkingLoopHeaderTest::inspectEdgeCases);
  }

  @Test
  public void testEdgeCasesR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(EdgeCasesMain.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), EdgeCasesMain.class)
        // TODO(b/584819109): Should output EXPECTED_EDGE_CASE_LINES.
        .assertSuccessWithOutputLines(BUGGY_EDGE_CASE_LINES)
        .inspect(FieldGetSinkingLoopHeaderTest::inspectEdgeCases);
  }

  private static void inspectMain(CodeInspector inspector) {
    MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethod, isPresent());
    // TODO(b/584819109): Should be 2 once sinking into loop header is prevented.
    assertEquals(
        1, mainMethod.streamInstructions().filter(InstructionSubject::isInstanceGet).count());
  }

  private static void inspectEdgeCases(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(EdgeCasesMain.class);
    assertThat(clazz, isPresent());
    // TODO(b/584819109): Should be 2 once sinking into loop header is prevented.
    assertEquals(1, countStaticGets(clazz, "staticGetPreheaderUser"));
    // TODO(b/584819109): Should be 2 once sinking into loop header is prevented.
    assertEquals(1, countInstanceGets(clazz, "instanceGetLoopExitPhiUser"));
    // TODO(b/584819109): Should be 2 once sinking into loop header is prevented.
    assertEquals(1, countStaticGets(clazz, "staticGetLoopExitPhiUser"));
    assertEquals(2, countInstanceGets(clazz, "secondPredecessorExtraPhi"));
    assertEquals(1, countInstanceGets(clazz, "sinkableInstanceGetLoop"));
    assertEquals(1, countStaticGets(clazz, "sinkableStaticGetLoop"));
  }

  private static long countInstanceGets(ClassSubject clazz, String methodName) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(methodName);
    assertThat(method, isPresent());
    return method.streamInstructions().filter(InstructionSubject::isInstanceGet).count();
  }

  private static long countStaticGets(ClassSubject clazz, String methodName) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(methodName);
    assertThat(method, isPresent());
    return method.streamInstructions().filter(InstructionSubject::isStaticGet).count();
  }

  static class Main {
    int index;

    @NeverInline
    void advance() {
      index++;
    }

    public static void main(String[] args) {
      Main m = new Main();
      if (args.length > 0) {
        m.advance();
      }
      int pos = m.index;
      int start = pos;
      while (pos < 3) {
        m.advance();
        pos = m.index;
      }
      System.out.println(start);
      System.out.println(pos);
    }
  }

  @NeverClassInline
  static class Counter {
    int index;

    @NeverInline
    void advance() {
      index++;
    }
  }

  static class EdgeCasesMain {
    @NeverPropagateValue static int staticPreheaderIndex;
    @NeverPropagateValue static int staticLoopExitPhiIndex;
    @NeverPropagateValue static int staticSinkableIndex;

    @NeverInline
    static void advanceStaticPreheader() {
      staticPreheaderIndex++;
    }

    @NeverInline
    static void advanceStaticLoopExitPhi() {
      staticLoopExitPhiIndex++;
    }

    @NeverInline
    static void advanceStaticSinkable() {
      staticSinkableIndex++;
    }

    @NeverInline
    static void printPair(int a, int b) {
      System.out.println(a + ":" + b);
    }

    @NeverInline
    static void staticGetPreheaderUser(String[] args) {
      if (args.length > 0) {
        advanceStaticPreheader();
      }
      int pos = staticPreheaderIndex;
      int start = pos;
      while (pos < 3) {
        advanceStaticPreheader();
        pos = staticPreheaderIndex;
      }
      printPair(start, pos);
    }

    @NeverInline
    static void instanceGetLoopExitPhiUser(String[] args) {
      Counter c = new Counter();
      if (args.length > 0) {
        c.advance();
      }
      int pos = c.index;
      int start = pos;
      while (pos < 3) {
        if (args.length > 0) {
          start = 42;
          break;
        }
        c.advance();
        pos = c.index;
      }
      printPair(start, pos);
    }

    @NeverInline
    static void staticGetLoopExitPhiUser(String[] args) {
      if (args.length > 0) {
        advanceStaticLoopExitPhi();
      }
      int pos = staticLoopExitPhiIndex;
      int start = pos;
      while (pos < 3) {
        if (args.length > 0) {
          start = 42;
          break;
        }
        advanceStaticLoopExitPhi();
        pos = staticLoopExitPhiIndex;
      }
      printPair(start, pos);
    }

    @NeverInline
    static void secondPredecessorExtraPhi(boolean cond) {
      Counter c = new Counter();
      c.advance();
      int pos;
      int extra = 0;
      if (cond) {
        c.advance();
        pos = c.index;
      } else {
        pos = c.index;
        extra = pos;
      }
      printPair(pos, extra);
    }

    @NeverInline
    static int sinkableInstanceGetLoop(String[] args) {
      Counter c = new Counter();
      if (args.length > 0) {
        c.advance();
      }
      int pos = c.index;
      while (pos < 3) {
        c.advance();
        pos = c.index;
      }
      return pos;
    }

    @NeverInline
    static int sinkableStaticGetLoop(String[] args) {
      if (args.length > 0) {
        advanceStaticSinkable();
      }
      int pos = staticSinkableIndex;
      while (pos < 3) {
        advanceStaticSinkable();
        pos = staticSinkableIndex;
      }
      return pos;
    }

    public static void main(String[] args) {
      staticGetPreheaderUser(args);
      instanceGetLoopExitPhiUser(args);
      staticGetLoopExitPhiUser(args);
      secondPredecessorExtraPhi(args.length == 0);
      secondPredecessorExtraPhi(args.length > 0);
      System.out.println(sinkableInstanceGetLoop(args));
      System.out.println(sinkableStaticGetLoop(args));
    }
  }
}
