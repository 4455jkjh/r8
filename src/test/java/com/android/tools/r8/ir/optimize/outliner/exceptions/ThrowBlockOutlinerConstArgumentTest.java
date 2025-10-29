// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.getSyntheticItemsTestUtils;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.isInvokeWithTarget;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutlinerArrayUseTypeTest.Main;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ThrowBlockOutlinerConstArgumentTest extends ThrowBlockOutlinerTestBase {

  @Test
  public void testD8() throws Exception {
    runTest(testForD8(parameters));
  }

  @Test
  public void testR8() throws Exception {
    assumeRelease();
    runTest(testForR8(parameters).addKeepMainRule(Main.class));
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> testBuilder)
      throws Exception {
    TestCompileResult<?, ?> compileResult =
        testBuilder
            .addInnerClasses(getClass())
            .apply(this::configure)
            .compile()
            .inspect(inspector -> inspectOutput(inspector, testBuilder.isR8TestBuilder()));
    for (int i = 0; i < 3; i++) {
      compileResult
          .run(parameters.getRuntime(), Main.class, Integer.toString(i))
          .assertFailureWithErrorThatThrows(IllegalArgumentException.class)
          .assertFailureWithErrorThatMatches(containsString("Unexpected " + i));
    }
    compileResult
        .run(parameters.getRuntime(), Main.class, Integer.toString(3))
        .assertFailureWithErrorThatThrows(RuntimeException.class);
    compileResult
        .run(parameters.getRuntime(), Main.class, Integer.toString(42))
        .assertSuccessWithEmptyOutput();
  }

  @Override
  public void inspectOutlines(Collection<ThrowBlockOutline> outlines, DexItemFactory factory) {
    // Verify that we have two throw block outlines with one and three users, respectively.
    List<ThrowBlockOutline> throwOutlines =
        outlines.stream().filter(ThrowBlockOutline::isThrowOutline).collect(Collectors.toList());
    assertEquals(2, throwOutlines.size());
    IntSet numberOfUsers = new IntArraySet();
    for (ThrowBlockOutline outline : throwOutlines) {
      numberOfUsers.add(outline.getNumberOfUsers());
    }
    assertTrue(numberOfUsers.contains(1));
    assertTrue(numberOfUsers.contains(3));
  }

  private void inspectOutput(CodeInspector inspector, boolean isR8) {
    assertEquals(2, inspector.allClasses().size());

    ClassSubject outlineClassSubject =
        inspector.clazz(
            getSyntheticItemsTestUtils(isR8).syntheticThrowBlockOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        3,
        mainMethodSubject
            .streamInstructions()
            .filter(isInvokeWithTarget(outlineMethodSubject))
            .count());
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      if (i == 0) {
        throw new IllegalArgumentException("Unexpected 0");
      }
      if (i == 1) {
        throw new IllegalArgumentException("Unexpected 1");
      }
      String arg0 = "Unexpected " + args[0];
      if (i == 2) {
        throw new IllegalArgumentException(arg0);
      }
      if (i == 3) {
        throw new RuntimeException();
      }
    }
  }
}
