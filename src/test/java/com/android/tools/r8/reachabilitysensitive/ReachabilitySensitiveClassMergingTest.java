// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.reachabilitysensitive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import dalvik.annotation.optimization.ReachabilitySensitive;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReachabilitySensitiveClassMergingTest extends TestBase {

  // When @ReachabilitySensitive is honored, local variable live ranges are extended to the end of
  // their scope, preventing register reuse and resulting in 8 registers allocated for method m().
  // Without @ReachabilitySensitive, release register allocation reuses registers and shrinks the
  // register count to 3 (or 4 pre-M), overwriting `obj` before GC runs.
  private static final int EXPECTED_SENSITIVE_REGISTER_SIZE = 8;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private R8TestBuilder<?, R8TestRunResult, ?> configureCommonR8() {
    return testForR8(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClasses(Main.class, A.class, B.class, ReachabilitySensitive.class)
        .release()
        .compile()
        .inspect(inspector -> inspect(inspector, B.class, EXPECTED_SENSITIVE_REGISTER_SIZE))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "5", "java.lang.Object");
  }

  @Test
  public void testHorizontalClassMerging() throws Exception {
    configureCommonR8()
        .addProgramClasses(Main.class, A.class, B.class, ReachabilitySensitive.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(inspector -> inspect(inspector, B.class, EXPECTED_SENSITIVE_REGISTER_SIZE))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "5", "java.lang.Object");
  }

  @Test
  public void testVerticalClassMerging() throws Exception {
    configureCommonR8()
        .addProgramClasses(VcmMain.class, VcmSuper.class, VcmSub.class, ReachabilitySensitive.class)
        .addKeepMainRule(VcmMain.class)
        .enableNoHorizontalClassMergingAnnotations()
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(inspector -> inspect(inspector, VcmSuper.class, EXPECTED_SENSITIVE_REGISTER_SIZE))
        .run(parameters.getRuntime(), VcmMain.class)
        .assertSuccessWithOutputLines("sub", "5", "java.lang.Object");
  }

  private void inspect(CodeInspector inspector, Class<?> holder, int expectedRegisters) {
    ClassSubject clazz = inspector.clazz(holder);
    assertTrue(clazz.isPresent());
    MethodSubject mMethod = clazz.uniqueMethodWithOriginalName("m");
    assertTrue(mMethod.isPresent());
    DexCode code = mMethod.getMethod().getCode().asDexCode();
    assertEquals(expectedRegisters, code.registerSize);
  }

  // Non-sensitive class A.
  @NeverClassInline
  public static class A {

    @NeverInline
    public A() {}

    @NeverInline
    @NoMethodStaticizing
    public void other() {
      System.out.println("a");
    }
  }

  // Sensitive class B.
  @NeverClassInline
  public static class B {

    @NeverInline
    public B() {}

    @ReachabilitySensitive
    @NeverInline
    @NoMethodStaticizing
    public void m() {
      Object obj = new Object();
      WeakReference<Object> ref = new WeakReference<>(obj);
      int i = 2;
      int j = i + 1;
      int k = j + 2;
      System.out.println(k);
      Runtime.getRuntime().gc();
      System.runFinalization();
      Runtime.getRuntime().gc();
      System.out.println(ref.get().getClass().getName());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().other();
      new B().m();
    }
  }

  @NoHorizontalClassMerging
  public static class VcmSuper {

    @NeverInline
    public VcmSuper() {}

    @ReachabilitySensitive
    @NeverInline
    @NoMethodStaticizing
    public void m() {
      Object obj = new Object();
      WeakReference<Object> ref = new WeakReference<>(obj);
      int i = 2;
      int j = i + 1;
      int k = j + 2;
      System.out.println(k);
      Runtime.getRuntime().gc();
      System.runFinalization();
      Runtime.getRuntime().gc();
      System.out.println(ref.get().getClass().getName());
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class VcmSub extends VcmSuper {

    @NeverInline
    public VcmSub() {}

    @NeverInline
    @NoMethodStaticizing
    public void other() {
      System.out.println("sub");
    }
  }

  public static class VcmMain {

    public static void main(String[] args) {
      VcmSub sub = new VcmSub();
      sub.other();
      sub.m();
    }
  }
}
