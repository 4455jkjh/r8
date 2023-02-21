// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.frames;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PrimitiveAndObjectIncompatibleTypesOnStackTest extends TestBase implements Opcodes {

  @Parameter(0)
  public boolean insertFrame;

  @Parameter(1)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, insert frame: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            insertFrame,
            runResult ->
                runResult.applyIf(
                    parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9),
                    ignore -> runResult.assertSuccessWithEmptyOutput(),
                    ignore ->
                        runResult
                            .assertFailureWithErrorThatThrows(VerifyError.class)
                            .assertFailureWithErrorThatMatches(
                                containsString(
                                    "Type top (current frame, stack[0]) is not assignable to"
                                        + " category1 type"))),
            runResult ->
                runResult
                    .assertFailureWithErrorThatThrows(VerifyError.class)
                    .assertFailureWithErrorThatMatches(containsString("Mismatched stack types")));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    AssertUtils.assertFailsCompilation(
        () ->
            testForD8()
                .addProgramClassFileData(getTransformedMain())
                .setMinApi(parameters)
                .compile());
  }

  @Test
  public void testR8() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addProgramClassFileData(getTransformedMain())
                .addKeepMainRule(Main.class)
                .setMinApi(parameters)
                .compile());
  }

  static class Main {

    public static void main(String[] args) {
      test(args.length, new Object());
    }

    public static void test(int i, Object obj) {
      stub();
      // Code added by transformer:
      //   if (i != 0) {
      //     push 0
      //   } else {
      //     push obj
      //   }
      //   pop
    }

    private static void stub() {}
  }

  private byte[] getTransformedMain() throws Exception {
    return transformer(Main.class)
        .applyIf(!insertFrame, transformer -> transformer.setVersion(CfVersion.V1_5))
        .setMaxs(MethodPredicate.onName("test"), 1, 2)
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              Label elseLabel = new Label();
              Label exitLabel = new Label();
              visitor.visitVarInsn(ILOAD, 0);
              visitor.visitJumpInsn(IFEQ, elseLabel);
              visitor.visitInsn(ICONST_0);
              visitor.visitJumpInsn(GOTO, exitLabel);
              visitor.visitLabel(elseLabel);
              if (insertFrame) {
                visitor.visitFrame(
                    Opcodes.F_FULL,
                    // Locals
                    2,
                    new Object[] {Opcodes.INTEGER, binaryName(Object.class)},
                    // Stack
                    0,
                    new Object[0]);
              }
              visitor.visitVarInsn(ALOAD, 1);
              visitor.visitLabel(exitLabel);
              if (insertFrame) {
                visitor.visitFrame(
                    Opcodes.F_FULL,
                    // Locals
                    2,
                    new Object[] {Opcodes.INTEGER, binaryName(Object.class)},
                    // Stack
                    1,
                    new Object[] {Opcodes.TOP});
              }
              visitor.visitInsn(POP);
              visitor.visitInsn(RETURN);
              if (insertFrame) {
                visitor.visitFrame(
                    Opcodes.F_FULL,
                    // Locals
                    2,
                    new Object[] {Opcodes.INTEGER, binaryName(Object.class)},
                    // Stack
                    0,
                    new Object[] {});
              }
            })
        .transform();
  }
}
