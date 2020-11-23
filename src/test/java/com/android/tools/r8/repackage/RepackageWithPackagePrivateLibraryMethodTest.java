// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithPackagePrivateLibraryMethodTest extends RepackageTestBase {

  private final String[] EXPECTED = new String[] {"Library::foo", "Program::bar"};

  public RepackageWithPackagePrivateLibraryMethodTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addLibraryClasses(Library.class)
        .addProgramClasses(Program.class, Main.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addLibraryClasses(Library.class)
            .addDefaultRuntimeLibrary(parameters)
            .addProgramClasses(Program.class, Main.class)
            .apply(this::configureRepackaging)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class))
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion() == Version.V8_1_0) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class);
    }
  }

  public static class Library {

    static void foo() {
      System.out.println("Library::foo");
    }
  }

  public static class Program {

    @NeverInline
    public static void bar() {
      Library.foo();
      System.out.println("Program::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Program.bar();
    }
  }
}
