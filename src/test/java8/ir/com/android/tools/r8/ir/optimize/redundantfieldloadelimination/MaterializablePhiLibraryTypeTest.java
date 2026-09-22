// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MaterializablePhiLibraryTypeTest extends TestBase {

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
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "true");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject storeSuperFirstMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("storeSuperFirst");
    MethodSubject storeSubFirstMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("storeSubFirst");

    long superFirstGets =
        storeSuperFirstMethod.streamInstructions().filter(InstructionSubject::isStaticGet).count();
    long subFirstGets =
        storeSubFirstMethod.streamInstructions().filter(InstructionSubject::isStaticGet).count();

    long superFirstCasts =
        storeSuperFirstMethod.streamInstructions().filter(InstructionSubject::isCheckCast).count();
    long subFirstCasts =
        storeSubFirstMethod.streamInstructions().filter(InstructionSubject::isCheckCast).count();

    // Both methods have the same logic with operands reversed.
    // Due to the bug in MaterializablePhi.addOperand, storeSubFirst unsoundly eliminates the load
    // and cast, while storeSuperFirst correctly bails out and retains them.
    assertEquals(superFirstGets, subFirstGets);
    assertEquals(superFirstCasts, subFirstCasts);
  }

  static class Main {

    static Object f;
    static Object g;

    public static void main(String[] args) {
      boolean c = args.length == 0;
      storeSubFirst(c, new BufferedReader(new StringReader("a")), new StringReader("b"));
      System.out.println(f != null);
      storeSuperFirst(c, new BufferedReader(new StringReader("a")), new StringReader("b"));
      System.out.println(g != null);
      f = "keepObject";
      g = "keepObject";
    }

    @NeverInline
    static void storeSubFirst(boolean c, BufferedReader br, Reader r) {
      if (c) {
        f = br;
      } else {
        f = r;
      }
      Reader x = (Reader) f;
      x.hashCode();
    }

    @NeverInline
    static void storeSuperFirst(boolean c, BufferedReader br, Reader r) {
      if (c) {
        g = r;
      } else {
        g = br;
      }
      Reader x = (Reader) g;
      x.hashCode();
    }
  }
}
