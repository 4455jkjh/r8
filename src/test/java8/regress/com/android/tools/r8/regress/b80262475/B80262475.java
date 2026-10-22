// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b80262475;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexLongToInt;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  public static void f(long[] a) {
    int i = (int) a[0];
    a[i] = a[0];
  }
}

@RunWith(Parameterized.class)
public class B80262475 extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public B80262475(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private boolean overlappingLongToIntInputAndOutput(DexInstruction instruction) {
    if (instruction instanceof DexLongToInt) {
      DexLongToInt longToInt = (DexLongToInt) instruction;
      return longToInt.A == longToInt.B;
    }
    return false;
  }

  @Test
  public void testLongToIntOverlap()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = getMethodSubject(AndroidApiLevel.L);
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    for (DexInstruction instruction : instructions) {
      assertFalse(overlappingLongToIntInputAndOutput(instruction));
    }
  }

  private MethodSubject getMethodSubject(AndroidApiLevel level)
      throws CompilationFailedException, IOException {
    CodeInspector inspector = testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(level)
        .compile()
        .inspector();
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "f", ImmutableList.of("long[]"));
    assertThat(method, isPresent());
    return method;
  }
}
