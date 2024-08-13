// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Reproduction for b/196345511.
@RunWith(Parameterized.class)
public class StaticInterfaceMethodTest extends TestBase {

  public static final String OUTPUT = "Called LibraryInterface::foo";
  public static final String EXPECTED = StringUtils.lines(OUTPUT);

  public interface LibraryInterface {
    static void foo() {
      System.out.println(OUTPUT);
    }
  }

  public static class ProgramClass implements LibraryInterface {

    public static void main(String[] args) {
      LibraryInterface.foo();
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StaticInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testFullProgram() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testLibraryLinkedWithProgram() throws Throwable {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibraryInterface.class)
            .addKeepClassAndMembersRules(LibraryInterface.class)
            .setMinApi(parameters)
            .compile();
    CodeInspector inspector = libraryResult.inspector();
    ClassSubject libraryInterface = inspector.clazz(LibraryInterface.class);
    assertThat(libraryInterface, isPresent());
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      assertThat(libraryInterface.method(LibraryInterface.class.getMethod("foo")), isPresent());
    } else {
      // Desugaring must remove the static on the interface.
      assertThat(libraryInterface.method(LibraryInterface.class.getMethod("foo")), isAbsent());
      // Check that we included the companion class and method.
      ClassSubject companion =
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(LibraryInterface.class));
      assertThat(companion, isPresent());
      assertEquals(1, companion.allMethods().size());
    }

    testForR8(parameters.getBackend())
        .addDontShrink()
        .addProgramClasses(ProgramClass.class)
        .addClasspathClasses(LibraryInterface.class)
        .addApplyMapping(libraryResult.getProguardMap())
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
