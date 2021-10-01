// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceInvokeWithObjectReceiverInliningTest extends TestBase {

  @Parameter(0)
  public boolean enableInlining;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, inlining: {0}, vertical class merging: {1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(enableInlining);
    assumeFalse(enableVerticalClassMerging);
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        // Keep get() to prevent that we optimize it into having static return type A.
        .addKeepRules("-keepclassmembers class " + Main.class.getTypeName() + " { *** get(...); }")
        .addInliningAnnotations()
        .addNoVerticalClassMergingAnnotations()
        .applyIf(!enableInlining, R8TestBuilder::enableInliningAnnotations)
        .applyIf(
            !enableVerticalClassMerging, R8TestBuilder::enableNoVerticalClassMergingAnnotations)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (enableVerticalClassMerging) {
                inspector.assertMergedIntoSubtype(I.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  private static byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("get")) {
                visitor.visitMethodInsn(opcode, owner, name, "(I)Ljava/lang/Object;", isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .setReturnType(MethodPredicate.onName("get"), Object.class.getTypeName())
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      // Transformed from `I get(int)` to `Object get(int)`.
      get(args.length).m();
    }

    // @Keep
    static /*Object*/ I get(int f) {
      return new A(f);
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m();
  }

  static class A implements I {

    int f;

    A(int f) {
      this.f = f;
    }

    @NeverInline
    @Override
    public void m() {
      System.out.println(f);
    }
  }
}
