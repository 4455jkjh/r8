// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokestatic;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticOnInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public InvokeStaticOnInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws IOException, CompilationFailedException, ExecutionException {
    assertTrue(parameters.isCfRuntime());
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(I.class)
            .addProgramClassFileData(getClassWithTransformedInvoked())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.getRuntime().asCf().isNewerThan(CfVm.JDK8)) {
      runResult.assertFailureWithErrorThatMatches(
          allOf(
              containsString("java.lang.IncompatibleClassChangeError: Method"),
              // JVM method formatting changed between jdk11 and jdk17
              containsString(I.class.getTypeName() + ".foo()"),
              containsString("must be InterfaceMethodref constant")));
    } else {
      runResult.assertSuccessWithOutputLines("Hello World!");
    }
  }

  @Test(expected = CompilationFailedException.class)
  public void testCfInvokeOnStaticInterfaceMethod_errorNotAllowed()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class);
  }

  @Test
  public void testCfInvokeOnStaticInterfaceMethod_errorAllowed()
      throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class)
            .addProgramClassFileData(getClassWithTransformedInvoked())
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .addOptionsModification(o -> o.testing.allowInvokeErrors = true)
            .addDontObfuscate()
            .addKeepMainRule(Main.class)
            // Without a keep rule, we conclude that I.m() is not called and remove it. That is not
            // true, however, since there is an invalid invoke to I.m(). To preserve the error we
            // could consider rewriting invoke to `throw new ICCE()`, as this would also ensure that
            // the compiled program runs on all runtimes with the same behavior.
            .addKeepClassAndMembersRules(I.class)
            .run(parameters.getRuntime(), Main.class);
    if (parameters.getRuntime().asCf().isNewerThan(CfVm.JDK8)) {
      runResult.assertFailureWithErrorThatMatches(
          allOf(
              containsString("java.lang.IncompatibleClassChangeError: Method"),
              containsString(
                  "com.android.tools.r8.graph.invokestatic.InvokeStaticOnInterfaceTest$I.foo()"),
              containsString("must be InterfaceMethodref constant")));
    } else {
      runResult.assertSuccessWithOutputLines("Hello World!");
    }
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKESTATIC, opcode);
              assertTrue(isInterface);
              continuation.visitMethodInsn(opcode, owner, name, descriptor, false);
            })
        .transform();
  }

  @NoVerticalClassMerging
  public interface I {

    @NeverInline
    static void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      I.foo();
    }
  }
}
