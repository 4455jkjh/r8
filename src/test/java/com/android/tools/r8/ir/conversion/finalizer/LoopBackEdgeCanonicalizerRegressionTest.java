// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.ir.conversion.finalizer.LoopBackEdgeCanonicalizerTest.countBackwardGotos;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexGoto16;
import com.android.tools.r8.dex.code.DexGoto32;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Label;

/** Regression test for b/577294978. */
@RunWith(Parameterized.class)
public class LoopBackEdgeCanonicalizerRegressionTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT =
      new String[] {"reloaded", "reloaded", "a", "0", "b", "1", "2"};

  private static final String[] RESUMABLE_EXPECTED_OUTPUT =
      new String[] {"3", "103", "100", "3", "103", "100"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  // Strip line numbers so the two throwing resume blocks share the same position, matching
  // kotlinc's coroutine state machine where all resume blocks share the method entry line.
  private ClassFileTransformer stripLineNumberTransformer(Class<?> clazz) throws Exception {
    return transformer(clazz).removeLineNumberTable(MethodPredicate.all());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(stripLineNumberTransformer(TestClass.class).transform())
        .release()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(
            stripLineNumberTransformer(TestClass.class)
                // Strip the local `unused`, such that the outer loop headers are only phis and a
                // goto, and the phis of the other locals have local info.
                .addMethodTransformer(
                    new MethodTransformer() {
                      @Override
                      public void visitLocalVariable(
                          String name,
                          String descriptor,
                          String signature,
                          Label start,
                          Label end,
                          int index) {
                        if (!name.equals("unused")) {
                          super.visitLocalVariable(name, descriptor, signature, start, end, index);
                        }
                      }
                    })
                .transform())
        .debug()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(stripLineNumberTransformer(TestClass.class).transform())
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    MethodSubject collectSubject = testClassSubject.uniqueMethodWithOriginalName("collect");
    assertThat(collectSubject, isPresent());
    // On API < L, the Dalvik tracing JIT workaround (b/117907456) rewrites the trailing throw in
    // the finally handler to `goto +2; throw; goto -1`, adding one backward goto.
    int expectedBackwardGotos = parameters.getApiLevel().isLessThan(AndroidApiLevel.L) ? 2 : 1;
    assertEquals(expectedBackwardGotos, countBackwardGotos(testClassSubject, "collect"));
    MethodSubject throwOnFailureSubject =
        testClassSubject.uniqueMethodWithOriginalName("throwOnFailure");
    assertThat(throwOnFailureSubject, isPresent());
    assertEquals(
        1,
        collectSubject
            .streamInstructions()
            .filter(CodeMatchers.isInvokeWithTarget(throwOnFailureSubject))
            .count());
    // The phis of the outer loop header are used directly in the inner loop, so the outer loop
    // header is not merged into the inner loop header.
    assertEquals(2, countBackwardGotos(testClassSubject, "readOuterInInner"));
  }

  @Test
  public void testResumableD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(buildResumableClass())
        .release()
        .compile()
        .inspect(this::inspectResumable)
        .run(parameters.getRuntime(), "Resumable")
        .assertSuccessWithOutputLines(RESUMABLE_EXPECTED_OUTPUT);
  }

  @Test
  public void testResumableD8Debug() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(buildResumableClass())
        .debug()
        .compile()
        .inspect(this::inspectResumable)
        .run(parameters.getRuntime(), "Resumable")
        .assertSuccessWithOutputLines(RESUMABLE_EXPECTED_OUTPUT);
  }

  @Test
  public void testResumableR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(buildResumableClass())
        .addKeepClassAndMembersRules("Resumable")
        .compile()
        .inspect(this::inspectResumable)
        .run(parameters.getRuntime(), "Resumable")
        .assertSuccessWithOutputLines(RESUMABLE_EXPECTED_OUTPUT);
  }

  private void inspectResumable(CodeInspector inspector) {
    ClassSubject resumableSubject = inspector.clazz("Resumable");
    for (String methodName : new String[] {"resume", "resumeFirst"}) {
      assertEquals(1, countBackwardGotos(resumableSubject, methodName));
      // The resume path jumps directly to the loop header, not through the latch.
      assertEquals(0, countGotosToGotos(resumableSubject, methodName));
    }
  }

  private static long countGotosToGotos(ClassSubject classSubject, String methodName) {
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    DexInstruction[] instructions = methodSubject.getMethod().getCode().asDexCode().instructions;
    Map<Integer, DexInstruction> instructionsByOffset = new HashMap<>();
    for (DexInstruction instruction : instructions) {
      instructionsByOffset.put(instruction.getOffset(), instruction);
    }
    return Arrays.stream(instructions)
        .filter(LoopBackEdgeCanonicalizerRegressionTest::isGoto)
        .filter(
            instruction ->
                isGoto(
                    instructionsByOffset.get(
                        instruction.getOffset() + instruction.getTargets()[0])))
        .count();
  }

  private static boolean isGoto(DexInstruction instruction) {
    return instruction instanceof DexGoto
        || instruction instanceof DexGoto16
        || instruction instanceof DexGoto32;
  }

  // Builds a coroutine-like state machine, where the paths that resume and continue the loop join
  // in a block that only has phis and a goto to the loop header. As the control flow is
  // irreducible, the IR has the redundant phis k_header = phi(k, k_join) and
  // k_join = phi(k, k_header), and k_header becomes trivial when the join is merged into the
  // header.
  private static byte[] buildResumableClass() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Resumable");
    // static int resume(int n, int state, int k) {
    //   int x = 0;
    //   if (state == 0) goto Header;
    //   x += 100;
    //   goto Join;
    //   Header: if (n <= 0) return x; n--; x += Math.abs(k);
    //   Join: goto Header;
    // }
    addResumeMethod(
        clazz,
        "resume",
        "  ifeq Header",
        "  iinc 3 100",
        "  goto Join",
        "Header:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 0 -1",
        "  iload 3",
        "  iload 2",
        "  invokestatic java/lang/Math/abs(I)I",
        "  iadd",
        "  istore 3",
        "Join:");
    // Same as resume, but the resume path is visited first in the reverse postorder, such that the
    // join is the loop header instead of its target.
    addResumeMethod(
        clazz,
        "resumeFirst",
        "  ifne Resume",
        "Header:",
        "  iload 0",
        "  ifle Exit",
        "  iinc 0 -1",
        "  iload 3",
        "  iload 2",
        "  invokestatic java/lang/Math/abs(I)I",
        "  iadd",
        "  istore 3",
        "  goto Join",
        "Resume:",
        "  iinc 3 100",
        "Join:");
    List<String> mainLines = new ArrayList<>();
    mainLines.add(".limit stack 4");
    mainLines.add(".limit locals 1");
    for (String methodName : new String[] {"resume", "resumeFirst"}) {
      addPrintCall(mainLines, methodName, 3, 0);
      addPrintCall(mainLines, methodName, 3, 1);
      addPrintCall(mainLines, methodName, 0, 1);
    }
    mainLines.add("  return");
    clazz.addMainMethod(mainLines);
    return builder.buildClasses().get(0);
  }

  private static void addResumeMethod(
      JasminBuilder.ClassBuilder clazz, String name, String... bodyUntilJoin) {
    List<String> lines = new ArrayList<>();
    lines.add(".limit stack 2");
    lines.add(".limit locals 4");
    lines.add("  iconst_0");
    lines.add("  istore 3");
    lines.add("  iload 1");
    lines.addAll(Arrays.asList(bodyUntilJoin));
    lines.add("  goto Header");
    lines.add("Exit:");
    lines.add("  iload 3");
    lines.add("  ireturn");
    clazz.addStaticMethod(name, ImmutableList.of("I", "I", "I"), "I", lines.toArray(new String[0]));
  }

  private static void addPrintCall(List<String> lines, String methodName, int n, int state) {
    lines.add("  getstatic java/lang/System/out Ljava/io/PrintStream;");
    lines.add("  ldc " + n);
    lines.add("  ldc " + state);
    lines.add("  ldc 1");
    lines.add("  invokestatic Resumable/" + methodName + "(III)I");
    lines.add("  invokevirtual java/io/PrintStream/println(I)V");
  }

  static class TestClass {

    static int stepIndex = 0;
    static final int[] STEPS = new int[] {1, 2, 3, 0, 1, 2};

    @NeverInline
    static Object init(Object s) {
      return System.currentTimeMillis() > 0 ? s : null;
    }

    @NeverInline
    static Object reload(Object r) {
      return System.currentTimeMillis() > 0 ? r : null;
    }

    @KeepConstantArguments
    @NeverInline
    static void throwOnFailure(Object r) {
      if (r == null) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    static void freeSlot(Object s) {
      System.out.println(s);
    }

    @NeverInline
    static int step(Object s) {
      return s != null ? STEPS[stepIndex++] : 0;
    }

    @KeepConstantArguments
    @NeverInline
    static void collect(Object s, Object r, Object fail) {
      s = init(s);
      try {
        outer:
        while (true) {
          // Dead local store keeps `outer` and `inner` as separate labels in javac, producing an
          // empty phi-only outer loop header in IR before register allocation.
          Object unused = s;
          inner:
          while (true) {
            switch (step(s)) {
              case 0:
                return;
              case 1:
                s = reload(r);
                throwOnFailure(fail);
                continue inner;
              case 2:
                s = reload(r);
                throwOnFailure(fail);
                continue outer;
              case 3:
                s = init(s);
                continue inner;
              default:
                throw new IllegalStateException();
            }
          }
        }
      } finally {
        freeSlot(s);
      }
    }

    @NeverInline
    static int readOuterInInner(int[] steps) {
      int outerCount = 0;
      CharSequence label = "a";
      int i = 0;
      outer:
      while (true) {
        // Dead local store keeps `outer` and `inner` as separate labels, see collect.
        int unused = outerCount;
        while (true) {
          int step = steps[i++];
          if (step == 0) {
            return outerCount;
          }
          if (step == 1) {
            outerCount++;
            label = new StringBuilder("b");
            continue outer;
          }
          // The phis for label and outerCount in the outer loop header are used directly in the
          // inner loop.
          System.out.println(label);
          System.out.println(outerCount);
        }
      }
    }

    public static void main(String[] args) {
      collect("initial", "reloaded", "ok");
      try {
        collect("initial", "reloaded", null);
      } catch (RuntimeException ignored) {
      }
      System.out.println(readOuterInInner(new int[] {2, 1, 2, 1, 0}));
    }
  }
}
