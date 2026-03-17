// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup.exceptions;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.outliner.bottomup.BottomUpOutlinerTestBase;
import com.android.tools.r8.ir.optimize.outliner.bottomup.Outline;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

public class ThrowBlockOutlinerKeepLocalsTest extends BottomUpOutlinerTestBase {

  @Test
  public void test() throws Exception {
    assumeDebug();
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addInnerClasses(getClass())
            .apply(this::configure)
            .compile()
            .inspectWithSyntheticItems(this::inspectOutput);
    compileResult
        .run(parameters.getRuntime(), Main.class, "0")
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
    compileResult.run(parameters.getRuntime(), Main.class, "1").assertSuccessWithEmptyOutput();
  }

  @Override
  public void inspectOutlines(Collection<Outline> outlines, DexItemFactory factory) {
    // Verify that we have a single outline with one argument.
    assertEquals(1, outlines.size());
    Outline outline = outlines.iterator().next();
    assertEquals(1, outline.getArguments().size());
    assertTrue(outline.getArguments().get(0).isUnknown());
  }

  private void inspectOutput(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems)
      throws Exception {
    assertEquals(2, inspector.allClasses().size());

    MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());

    ClassSubject outlineClassSubject =
        inspector.clazz(syntheticItems.syntheticBottomUpOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());
    assertEquals(1, outlineClassSubject.allMethods().size());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertThat(mainMethodSubject, invokesMethod(outlineMethodSubject));

    // Check that the local variable "x" is found in the debug info.
    DexCode mainCode = mainMethodSubject.getMethod().getCode().asDexCode();
    List<DexDebugEntry> mainDebugEntries =
        mainCode
            .getDebugInfo()
            .asEventBasedInfo()
            .computeEntries(mainMethodSubject.getMethod().getReference(), false);
    assertTrue(
        mainDebugEntries.stream()
            .flatMap(mainDebugEntry -> mainDebugEntry.locals.values().stream())
            .anyMatch(local -> local.name.isEqualTo("x")));
  }

  @Override
  public boolean shouldOutline(Outline outline) {
    return true;
  }

  static class Main {

    public static void main(String[] args) {
      int i = Integer.parseInt(args[0]);
      if (i == 0) {
        String x = "Hello, world!";
        throw new IllegalArgumentException(x);
      }
    }
  }
}
