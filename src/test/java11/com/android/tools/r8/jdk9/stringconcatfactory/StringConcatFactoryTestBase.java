// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.stringconcatfactory;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.nio.file.Path;
import java.util.List;

public abstract class StringConcatFactoryTestBase extends TestBase {

  protected static List<Object[]> getArtTestParameters() {
    return buildParameters(
        getTestParameters()
            .withDexRuntime(Version.MAIN)
            .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
            .withApiLevel(AndroidApiLevel.CINNAMON_BUN_2)
            .build(),
        BooleanUtils.values());
  }

  protected static Path compileWithMakeConcat(Class<?> clazz) throws Exception {
    return javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(ToolHelper.getSourceFileForTestClassFromResources(clazz))
        .addOptions("-XDstringConcat=indy")
        .compile();
  }

  protected void enableStringConcatFactoryForDex(TestCompilerBuilder<?, ?, ?, ?, ?> builder) {
    builder.addOptionsModification(
        opt -> {
          opt.enableStringConcatInstruction = true;
          opt.getTestingOptions().enableStringConcatFactoryForDex = true;
        });
  }

  protected void checkNoInvokeDynamic(
      CodeInspector inspector, Class<?> clazz, String... methodNames) {
    assertTrue(methodNames.length > 0);
    for (String methodName : methodNames) {
      MethodSubject method = inspector.clazz(clazz).uniqueMethodWithOriginalName(methodName);
      assertThat(method, isPresent());
      assertFalse(method.streamInstructions().anyMatch(InstructionSubject::isInvokeDynamic));
    }
  }

  protected void checkInvokeDynamic(
      CodeInspector inspector,
      Class<?> clazz,
      boolean expectMakeConcatWithConstants,
      String... methodNames) {
    assertTrue(methodNames.length > 0);
    DexString expectedMethodName =
        expectMakeConcatWithConstants
            ? inspector.getFactory().stringConcatFactoryMembers.makeConcatWithConstants.getName()
            : inspector.getFactory().stringConcatFactoryMembers.makeConcat.getName();
    for (String methodName : methodNames) {
      MethodSubject method = inspector.clazz(clazz).uniqueMethodWithOriginalName(methodName);
      assertThat(method, isPresent());
      assertTrue(method.streamInstructions().anyMatch(InstructionSubject::isInvokeDynamic));
      assertTrue(
          method
              .streamInstructions()
              .filter(InstructionSubject::isInvokeDynamic)
              .map(InstructionSubject::asInvokeDynamic)
              .allMatch(
                  instruction ->
                      instruction.getCallSite().methodName.isIdenticalTo(expectedMethodName)));
    }
  }

  protected void checkValueOf(
      CodeInspector inspector, Class<?> clazz, String methodName, long expected) {
    MethodSubject method = inspector.clazz(clazz).uniqueMethodWithOriginalName(methodName);
    assertThat(method, isPresent());
    assertEquals(
        expected,
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .map(InstructionSubject::getMethod)
            .filter(
                dexMethod ->
                    dexMethod.getHolderType().isIdenticalTo(inspector.getFactory().stringType)
                        || inspector.getFactory().getPrimitiveFromBoxed(dexMethod.getHolderType())
                            != null)
            .filter(
                dexMethod ->
                    dexMethod.getName().isIdenticalTo(inspector.getFactory().valueOfMethodName))
            .count());
  }
}
