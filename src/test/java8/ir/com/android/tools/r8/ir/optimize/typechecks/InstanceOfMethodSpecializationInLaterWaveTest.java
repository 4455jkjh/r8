// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.typechecks;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.containsInstanceOf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Deque;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for instanceof specialization of a candidate seen in a later primary wave. */
@RunWith(Parameterized.class)
public class InstanceOfMethodSpecializationInLaterWaveTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.getTestingOptions().waveModifier = this::waveModifier)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("false", "true", "false", "false", "true", "true");
  }

  // Moves C.isC() and D.isD() to the second wave (with TestClass.callIsCOnNewA), so that their
  // candidates are only seen after B.isB() of the first wave has been specialized. A.isC() is
  // single-caller inlined in the same wave that C.isC() is specialized into A.isC(). D.isD() is
  // called through an A-typed receiver, so it is only removed if it is specialized into A.isD().
  private void waveModifier(Deque<ProgramMethodSet> waves) {
    assertTrue(waves.size() >= 3);
    ProgramMethodSet initialWave = waves.removeFirst();
    moveToNextWave(initialWave, waves.getFirst(), C.class, "isC");
    moveToNextWave(initialWave, waves.getFirst(), D.class, "isD");
    waves.addFirst(initialWave);
  }

  private static void moveToNextWave(
      ProgramMethodSet wave, ProgramMethodSet nextWave, Class<?> holder, String name) {
    ProgramMethod method =
        wave.stream()
            .filter(
                m ->
                    m.getHolderType().getTypeName().equals(holder.getTypeName())
                        && m.getName().isEqualTo(name))
            .findFirst()
            .orElse(null);
    assertNotNull(method);
    wave.remove(method.getDefinition());
    nextWave.add(method);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("isB"), isAbsent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    assertThat(cClassSubject.uniqueMethodWithOriginalName("isC"), isAbsent());

    ClassSubject dClassSubject = inspector.clazz(D.class);
    assertThat(dClassSubject, isPresent());
    assertThat(dClassSubject.uniqueMethodWithOriginalName("isD"), isAbsent());

    MethodSubject isDMethodSubject = inspector.clazz(A.class).uniqueMethodWithOriginalName("isD");
    assertThat(isDMethodSubject, isPresent());
    assertThat(isDMethodSubject, containsInstanceOf(dClassSubject.getFinalReference()));
  }

  public static class TestClass {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      A b = System.currentTimeMillis() > 0 ? new B() : new A();
      A c = System.currentTimeMillis() > 0 ? new C() : new A();
      C exactC = System.currentTimeMillis() > 0 ? new C() : null;
      A d = System.currentTimeMillis() > 0 ? new D() : new A();
      System.out.println(a.isB());
      System.out.println(b.isB());
      System.out.println(c.isB());
      System.out.println(callIsCOnNewA());
      if (exactC != null) {
        System.out.println(exactC.isC());
      }
      System.out.println(d.isD());
    }

    @NeverInline
    static boolean callIsCOnNewA() {
      return new A().isC();
    }
  }

  public static class A {

    boolean isB() {
      return false;
    }

    boolean isC() {
      return false;
    }

    @NeverInline
    boolean isD() {
      return false;
    }
  }

  public static class B extends A {

    @Override
    boolean isB() {
      return true;
    }
  }

  @NoHorizontalClassMerging
  public static class C extends A {

    @Override
    boolean isC() {
      return true;
    }
  }

  @NoHorizontalClassMerging
  public static class D extends A {

    @Override
    boolean isD() {
      return true;
    }
  }
}
