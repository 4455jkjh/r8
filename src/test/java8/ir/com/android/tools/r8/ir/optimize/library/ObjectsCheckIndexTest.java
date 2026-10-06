// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsCheckIndexTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ObjectsCheckIndexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "2", "0", "Expected IOOBE", "Expected IOOBE", "Expected IOOBE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testValidIndexMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testValidIndex");
              assertThat(testValidIndexMethodSubject, isPresent());
              assertThat(testValidIndexMethodSubject, not(invokesMethodWithName("checkIndex")));

              MethodSubject testValidUnusedIndexMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testValidUnusedIndex");
              assertThat(testValidUnusedIndexMethodSubject, isPresent());
              assertThat(
                  testValidUnusedIndexMethodSubject, not(invokesMethodWithName("checkIndex")));
              assertTrue(
                  testValidUnusedIndexMethodSubject
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isIf));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "2", "0", "Expected IOOBE", "Expected IOOBE", "Expected IOOBE");
  }

  private byte[] getProgramClassFileData() throws IOException {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Mock.class), descriptor(Objects.class))
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      testValidIndex();
      testValidUnusedIndex();
      try {
        testNegativeIndex();
        System.out.println("Unexpected");
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Expected IOOBE");
      }
      try {
        testOutOfBoundsIndex();
        System.out.println("Unexpected");
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Expected IOOBE");
      }
      try {
        testNegativeLength();
        System.out.println("Unexpected");
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Expected IOOBE");
      }
    }

    @NeverInline
    static void testValidIndex() {
      System.out.println(Mock.checkIndex(2, 5));
    }

    @NeverInline
    static void testValidUnusedIndex() {
      Mock.checkIndex(0, 5);
      System.out.println(0);
    }

    @NeverInline
    static void testNegativeIndex() {
      System.out.println(Mock.checkIndex(-1, 5));
    }

    @NeverInline
    static void testOutOfBoundsIndex() {
      System.out.println(Mock.checkIndex(5, 5));
    }

    @NeverInline
    static void testNegativeLength() {
      System.out.println(Mock.checkIndex(0, -1));
    }
  }

  // References to this class are rewritten to java.util.Objects by transformation.
  static class Mock {

    public static int checkIndex(int index, int length) {
      throw new RuntimeException();
    }
  }
}
