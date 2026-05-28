// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedvalues;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValueClassBoxingUnboxingTest extends TestBase {

  @Parameter(0)
  public boolean inlineConstructor;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, inline constructor: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, Sinks.class)
        .addProgramClassFileData(
            transformer(ValueClass.class)
                .replaceClassDescriptorInAnnotations(
                    descriptor(MyJvmInline.class), "Lkotlin/jvm/JvmInline;")
                .transform(),
            transformer(MyJvmInline.class).setClassDescriptor("Lkotlin/jvm/JvmInline;").transform())
        .addKeepClassAndMembersRules(Main.class, Sinks.class)
        .applyIf(
            inlineConstructor,
            b ->
                b.addKeepRules(
                    "-alwaysinline class " + ValueClass.class.getTypeName() + " {",
                    "  void <init>(int);",
                    "}"),
            b ->
                b.addKeepRules(
                        "-neverinline class " + ValueClass.class.getTypeName() + " {",
                        "  void <init>(int);",
                        "}")
                    .enableProguardTestOptions())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "42");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Main.class);

    MethodSubject testBoxUnboxMethod = classSubject.uniqueMethodWithOriginalName("testBoxUnbox");
    assertThat(testBoxUnboxMethod, isPresent());
    assertTrue(
        testBoxUnboxMethod.streamInstructions().noneMatch(InstructionSubject::isNewInstance));

    MethodSubject testUnboxBoxMethod = classSubject.uniqueMethodWithOriginalName("testUnboxBox");
    assertThat(testUnboxBoxMethod, isPresent());
    assertThat(testUnboxBoxMethod, invokesMethodWithName("getClass"));
    // Boxing should have been removed.
    assertTrue(
        testUnboxBoxMethod.streamInstructions().noneMatch(InstructionSubject::isNewInstance));
    // Unboxing should have been removed.
    assertTrue(
        testUnboxBoxMethod.streamInstructions().noneMatch(InstructionSubject::isInstanceGet));
  }

  public static class Main {

    public static void main(String[] args) {
      testBoxUnbox(42);
      testUnboxBox(new ValueClass(42));
    }

    static void testBoxUnbox(int f) {
      // Should be optimized into intSink(f).
      ValueClass vc = new ValueClass(f);
      Sinks.intSink(vc.f);
    }

    static void testUnboxBox(ValueClass in) {
      // Should be optimized into in.getClass(); Sinks.sink(in).
      Sinks.sink(new ValueClass(in.f));
    }
  }

  static class Sinks {

    static void sink(Object o) {
      System.out.println(o);
    }

    static void intSink(int i) {
      System.out.println(i);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface MyJvmInline {}

  @MyJvmInline
  public static class ValueClass {

    final int f;

    public ValueClass(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return Integer.toString(f);
    }
  }
}
