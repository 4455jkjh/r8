// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation.readbeforewrite;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * A library constructor may call a method that the program overrides, as {@code
 * android.view.ViewGroup.<init>} does with {@code requestLayout()}. The override then runs before
 * the program subclass has assigned its fields, so a null check on such a field must be kept.
 */
@RunWith(Parameterized.class)
public class ReadBeforeWriteLibraryParentConstructorCallbackTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addLibraryClasses(LibraryView.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class, ProgramView.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject programViewClass = inspector.clazz(ProgramView.class);
              assertThat(programViewClass, isPresent());
              assertThat(programViewClass.uniqueFieldWithOriginalName("callback"), isPresent());
            })
        .addRunClasspathClasses(LibraryView.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("callback is null", "callback is set");
  }

  // Library.
  public static class LibraryView {

    public LibraryView() {
      requestLayout();
    }

    public void requestLayout() {}
  }

  // Program.
  @NeverClassInline
  static class ProgramView extends LibraryView {

    private final Object callback = new Object();

    @NeverInline
    @Override
    public void requestLayout() {
      if (callback != null) {
        System.out.println("callback is set");
      } else {
        System.out.println("callback is null");
      }
    }
  }

  static class Main {

    public static void main(String[] args) {
      new ProgramView().requestLayout();
    }
  }
}
