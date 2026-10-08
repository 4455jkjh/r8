// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jasmin;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress72758525 extends JasminTestBase {

  private JasminBuilder buildClass() {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addDefaultConstructor();

    clazz.addMainMethod(
        ".limit stack 25",
        ".limit locals 1",
        "aload 0",
        "dup",
        "lconst_0",
        "dconst_1",
        "fconst_0",
        "lconst_1",
        "iconst_5",
        "fconst_1",
        "dconst_1",
        "new Test",
        "dup",
        "invokespecial Test/<init>()V",
        "lconst_0",
        "new java/lang/Object",
        "dup",
        "invokespecial java/lang/Object/<init>()V",
        "iconst_m1",
        "dup2",
        "dup2_x2",
        "L0:",
        "ineg",
        "new java/lang/Object",
        "dup",
        "invokespecial java/lang/Object/<init>()V",
        "dup2_x2",
        "pop2",
        "pop",
        "aload 0",
        "ifnull L0",
        "i2f",
        "invokestatic java/lang/Float/isNaN(F)Z",
        "return");
    return builder;
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Override
  protected DexVm getVm() {
    return parameters.getDexVm();
  }

  @Test
  public void test() throws Exception {
    JasminBuilder builder = buildClass();
    runOnArtD8(builder, "Test");
  }
}
