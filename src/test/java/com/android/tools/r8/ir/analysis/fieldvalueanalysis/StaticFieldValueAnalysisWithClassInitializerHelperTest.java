// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticFieldValueAnalysisWithClassInitializerHelperTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresent());
              // The field written in the helper called from <clinit> has a known value.
              assertThat(aClass.uniqueFieldWithOriginalName("f"), isAbsent());
              // The field read before the call to the helper does not.
              assertThat(aClass.uniqueFieldWithOriginalName("g"), isPresent());
              // The field written in a helper that is called inside a try block does not, since
              // the helper may throw before the field is written.
              ClassSubject bClass = inspector.clazz(B.class);
              assertThat(bClass, isPresent());
              assertThat(bClass.uniqueFieldWithOriginalName("f"), isPresent());
              // For sequential helpers readG(); writeFG();, f is not read by readG() so it is
              // folded, whereas g is read by readG() before writeFG() so it is not.
              ClassSubject cClass = inspector.clazz(C.class);
              assertThat(cClass, isPresent());
              assertThat(cClass.uniqueFieldWithOriginalName("f"), isAbsent());
              assertThat(cClass.uniqueFieldWithOriginalName("g"), isPresent());
              // A conditional write in a helper does not dominate normal exits.
              ClassSubject dClass = inspector.clazz(D.class);
              assertThat(dClass, isPresent());
              assertThat(dClass.uniqueFieldWithOriginalName("f"), isPresent());
              // A helper called outside <clinit> has multiple call sites and is not analyzed.
              ClassSubject eClass = inspector.clazz(E.class);
              assertThat(eClass, isPresent());
              assertThat(eClass.uniqueFieldWithOriginalName("f"), isPresent());
              // Nested helpers <clinit> -> init1() -> init2() propagate dominance and read sets.
              ClassSubject fClass = inspector.clazz(F.class);
              assertThat(fClass, isPresent());
              assertThat(fClass.uniqueFieldWithOriginalName("f"), isAbsent());
              assertThat(fClass.uniqueFieldWithOriginalName("g"), isPresent());
              // Multiple writes across <clinit> and init(), or multiple writes inside init(), are
              // not folded.
              ClassSubject gClass = inspector.clazz(G.class);
              assertThat(gClass, isPresent());
              assertThat(gClass.uniqueFieldWithOriginalName("f"), isPresent());
              assertThat(gClass.uniqueFieldWithOriginalName("g"), isPresent());
              // Inside a helper with an internal try/catch, a write inside the try block after a
              // throwing instruction does not dominate normal exits, while a write after the
              // try/catch does.
              ClassSubject hClass = inspector.clazz(H.class);
              assertThat(hClass, isPresent());
              assertThat(hClass.uniqueFieldWithOriginalName("f"), isPresent());
              assertThat(hClass.uniqueFieldWithOriginalName("g"), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "42", "0", "0", "51", "0", "0", "77", "77", "81", "0", "92", "93", "0", "102");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(A.f);
      System.out.println(A.copyOfG);
      B.g = args.length;
      System.out.println(B.f);
      System.out.println(C.f);
      System.out.println(C.copyOfG);
      D.flag = args.length;
      System.out.println(D.f);
      System.out.println(E.f);
      E.init();
      System.out.println(E.f);
      System.out.println(F.f);
      System.out.println(F.copyOfG);
      G.flag = args.length + 1;
      System.out.println(G.f);
      System.out.println(G.g);
      H.flag = args.length + 1;
      System.out.println(H.f);
      System.out.println(H.g);
    }
  }

  static class A {

    static int f;
    static int g;
    static int copyOfG;

    static {
      copyOfG = g;
      init();
    }

    @NeverInline
    static void init() {
      f = 42;
      g = 43;
    }
  }

  static class B {

    static int f;
    static int g;

    static {
      try {
        init();
      } catch (RuntimeException e) {
        // Ignore.
      }
    }

    @NeverInline
    static void init() {
      // Always throws, since g is only written after class initialization.
      if (g == 0) {
        throw new RuntimeException();
      }
      f = 42;
    }
  }

  static class C {

    static int f;
    static int g;
    static int copyOfG;

    static {
      readG();
      writeFG();
    }

    @NeverInline
    static void readG() {
      copyOfG = g;
    }

    @NeverInline
    static void writeFG() {
      f = 51;
      g = 52;
    }
  }

  static class D {

    static int flag;
    static int f;

    static {
      init();
    }

    @NeverInline
    static void init() {
      if (flag != 0) {
        f = 61;
      }
    }
  }

  static class E {

    static int f;

    static {
      init();
    }

    @NeverInline
    static void init() {
      f = 77;
    }
  }

  static class F {

    static int f;
    static int g;
    static int copyOfG;

    static {
      init1();
    }

    @NeverInline
    static void init1() {
      copyOfG = g;
      init2();
    }

    @NeverInline
    static void init2() {
      f = 81;
      g = 82;
    }
  }

  static class G {

    static int flag;
    static int f;
    static int g;

    static {
      f = 91;
      init();
    }

    @NeverInline
    static void init() {
      f = 92;
      g = 93;
      if (flag != 0) {
        g = 94;
      }
    }
  }

  static class H {

    static int flag;
    static int sink;
    static int f;
    static int g;

    static {
      init();
    }

    @NeverInline
    static void init() {
      try {
        sink = 1 / flag;
        f = 101;
      } catch (ArithmeticException e) {
        // Ignore.
      }
      g = 102;
    }
  }
}
