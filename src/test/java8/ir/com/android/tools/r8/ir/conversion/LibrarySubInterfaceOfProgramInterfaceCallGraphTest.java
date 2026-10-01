// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibrarySubInterfaceOfProgramInterfaceCallGraphTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(ProgramSuperInterface.class, Main.class)
        .addLibraryClasses(LibrarySubInterface.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ProgramSuperInterface.class)
        .addDontWarn(LibrarySubInterface.class)
        .compile()
        .addRunClasspathClasses(LibrarySubInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello!");
  }

  public interface ProgramSuperInterface {

    void foo();
  }

  public interface LibrarySubInterface extends ProgramSuperInterface {}

  public static class Main {

    public static void callLibrary(LibrarySubInterface sub) {
      sub.foo();
    }

    public static void main(String[] args) {
      if (args.length > 0) {
        callLibrary(null);
      }
      System.out.println("Hello!");
    }
  }
}
