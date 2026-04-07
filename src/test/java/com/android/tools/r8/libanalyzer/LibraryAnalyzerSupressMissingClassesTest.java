// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerSupressMissingClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  static class Main {

    public static void main(String[] args) {
      A.m();
    }
  }

  public static class A {

    public static void m() {
      System.out.println("Hello, world!");
    }
  }
}
