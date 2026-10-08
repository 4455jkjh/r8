// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress63598979 extends JasminTestBase {

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
  public void testSimplifyIf() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod("test1", ImmutableList.of("Z"), "Z",
        ".limit stack 2",
        ".limit locals 2",
        "  iload 0",
        "  ifne L2",
        "L1:",
        "  iconst_1",
        "  goto L3",
        "L2:",
        "  iconst_0",
        "L3:",
        "  ireturn");

    clazz.addStaticMethod("test2", ImmutableList.of("Z"), "Z",
        ".limit stack 2",
        ".limit locals 2",
        "  iload 0",
        "  ifne L2",
        "L1:",
        "  iconst_0",
        "  goto L3",
        "L2:",
        "  iconst_1",
        "L3:",
        "  ireturn");

    clazz.addStaticMethod("test3", ImmutableList.of("Z"), "Z",
        ".limit stack 2",
        ".limit locals 2",
        "  iload 0",
        "  ifeq L2",
        "L1:",
        "  iconst_0",
        "  goto L3",
        "L2:",
        "  iconst_1",
        "L3:",
        "  ireturn");


    clazz.addStaticMethod("test4", ImmutableList.of("Z"), "Z",
        ".limit stack 2",
        ".limit locals 2",
        "  iload 0",
        "  ifeq L2",
        "L1:",
        "  iconst_1",
        "  goto L3",
        "L2:",
        "  iconst_0",
        "L3:",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_1",
        "  invokestatic Test/test1(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test2(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_1",
        "  invokestatic Test/test2(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test3(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_1",
        "  invokestatic Test/test3(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test4(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_1",
        "  invokestatic Test/test4(Z)Z",
        "  invokestatic java/lang/Boolean/toString(Z)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    String expected = runOnJava(builder, clazz.name);
    String artResult = runOnArtD8(builder, clazz.name);
    assertEquals(expected, artResult);
  }
}
