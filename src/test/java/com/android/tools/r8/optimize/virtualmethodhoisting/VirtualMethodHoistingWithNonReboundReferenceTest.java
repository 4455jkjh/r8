// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.virtualmethodhoisting;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.verticalclassmerging.ClassMergerMode;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that VirtualMethodHoister does not introduce IllegalAccessErrors from rewriting invokes to
 * methods higher up in the class hierarchy that are inaccessible to callers. Instead, the
 * VirtualMethodHoister should leave such invokes unchanged, meaning the invokes no longer point
 * directly to a method definition.
 */
@RunWith(Parameterized.class)
public class VirtualMethodHoistingWithNonReboundReferenceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Base.class, ASuper.class, B.class, S.class, T.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Base.class, ASuper.class, B.class, S.class, T.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            o ->
                o.getVerticalClassMergerOptions()
                    .setModePredicate(mode -> mode == ClassMergerMode.FINAL))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableUnusedArgumentAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3");
  }

  private List<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(A.class), "Lpkg/A;")
            .transform(),
        transformer(A.class).setClassDescriptor("Lpkg/A;").transform());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject baseClass = inspector.clazz(Base.class);
    assertThat(baseClass, isPresent());

    // S should be merged into T.
    ClassSubject sClass = inspector.clazz(S.class);
    assertThat(sClass, isAbsent());

    ClassSubject tClass = inspector.clazz(T.class);
    assertThat(tClass, isPresent());

    // Base#method should no longer be abstract
    MethodSubject baseMethod = baseClass.uniqueMethodWithOriginalName("method");
    assertThat(baseMethod, isPresent());
    assertThat(baseMethod, not(isAbstract()));
    assertTrue(
        baseMethod.streamInstructions().anyMatch(instruction -> instruction.isConstNumber(1)));

    // A#method should be hoisted to Base, so A should no longer have it.
    ClassSubject aClass = inspector.clazz("pkg.A");
    assertThat(aClass, isPresent());
    assertThat(aClass.uniqueMethodWithOriginalName("method"), isAbsent());

    // A#callMethod should NOT be updated to call Base#method, since it doesn't have access to Base.
    // Moreover, the non-rebound method reference that A#method(S) should be correctly rewritten
    // to A#method(T) when the final round of the vertical class merger merges S into T.
    MethodSubject callMethodSubject = aClass.uniqueMethodWithOriginalName("callMethod");
    assertThat(callMethodSubject, isPresent());
    assertThat(
        callMethodSubject,
        invokesMethod(
            null,
            aClass.getFinalName(),
            baseMethod.getFinalName(),
            ImmutableList.of(tClass.getFinalName())));

    // B#method should still be present.
    ClassSubject bClass = inspector.clazz(B.class);
    assertThat(bClass, isPresent());
    assertThat(bClass.uniqueMethodWithOriginalName("method"), isPresent());
  }

  public static class Main {

    public static void main(String[] args) {
      Base a = System.currentTimeMillis() > 0 ? new A() : new B();
      Base b = System.currentTimeMillis() > 0 ? new B() : new A();
      System.out.println(a.method(new T()));
      System.out.println(b.method(new T()));
      System.out.println(new A().callMethod(new T()));
    }
  }

  @NoAccessModification
  @NoVerticalClassMerging
  abstract static class Base {

    @NoParameterTypeStrengthening
    public abstract int method(S s);
  }

  @NoVerticalClassMerging
  public abstract static class ASuper extends Base {}

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class /*pkg.*/ A extends ASuper {

    @KeepUnusedArguments
    @NeverInline
    @NeverPropagateValue
    @NoParameterTypeStrengthening
    @Override
    public int method(S s) {
      return 1;
    }

    @KeepUnusedArguments
    @NeverInline
    @NeverPropagateValue
    @NoParameterTypeStrengthening
    public int callMethod(S s) {
      return method(s) + 2;
    }
  }

  @NoHorizontalClassMerging
  public static class B extends Base {

    @KeepUnusedArguments
    @NoParameterTypeStrengthening
    @Override
    public int method(S s) {
      return 2;
    }
  }

  public static class S {}

  @NeverClassInline
  public static class T extends S {}
}
