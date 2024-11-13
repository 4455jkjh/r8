// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class ForNameTest extends CompatProguardSmaliTestBase {

  private final String CLASS_NAME = "Example";
  private final static String BOO = "Boo";

  @Test
  public void forName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        1,
        "const-string v0, \"" + BOO + "\"",
        "invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;",
        "move-result-object v0",
        "return-void");
    builder.addClass(BOO);

    CodeInspector inspector =
        runCompatProguard(
            builder,
            testBuilder ->
                testBuilder
                    .addKeepMainRule(CLASS_NAME)
                    // Add main dex rule to disable Class.forName() optimization.
                    .addMainDexRules("-keep class " + CLASS_NAME)
                    .addDontOptimize()
                    .addDontShrink()
                    .setMinApi(AndroidApiLevel.B));

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(CodeInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstString);
    DexConstString constString = (DexConstString) code.instructions[0];
    assertNotEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[1] instanceof DexInvokeStatic);
    assertTrue(code.instructions[2] instanceof DexReturnVoid);
  }

  @Test
  public void forName_noMinification() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        1,
        "const-string v0, \"" + BOO + "\"",
        "invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;",
        "move-result-object v0",
        "return-void");
    builder.addClass(BOO);

    CodeInspector inspector =
        runCompatProguard(
            builder,
            testBuilder ->
                testBuilder
                    // Add main dex rule to disable Class.forName() optimization.
                    .addMainDexRules("-keep class " + CLASS_NAME)
                    .addDontOptimize()
                    .addDontObfuscate()
                    .addDontShrink()
                    .setMinApi(AndroidApiLevel.B));

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(CodeInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstString);
    DexConstString constString = (DexConstString) code.instructions[0];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[1] instanceof DexInvokeStatic);
    assertTrue(code.instructions[2] instanceof DexReturnVoid);
  }

}
