// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexBuilderReturnDuplicationTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT = new String[] {"0", "1", "2", "300", "900"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    // The gotos to the return fit in one code unit, so they are not replaced by returns.
    MethodSubject shortGoto =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testShortGotoToReturn");
    assertTrue(shortGoto.isPresent());
    assertEquals(
        1, shortGoto.streamInstructions().filter(InstructionSubject::isReturnVoid).count());
    assertEquals(2, shortGoto.streamInstructions().filter(InstructionSubject::isGoto).count());

    // The goto to the return does not fit in one code unit, so it is replaced by a return.
    MethodSubject longGoto =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testLongGotoToReturn");
    assertTrue(longGoto.isPresent());
    assertEquals(2, longGoto.streamInstructions().filter(InstructionSubject::isReturnVoid).count());
    assertTrue(longGoto.streamInstructions().noneMatch(InstructionSubject::isGoto));
  }

  static class TestClass {

    @NeverInline
    static void mightThrow(int a) {
      if (a < 0) {
        throw new RuntimeException();
      }
      System.out.println(a);
    }

    @NeverInline
    static void sideEffect(int v) {
      System.out.println(v);
    }

    @NeverInline
    static void testShortGotoToReturn(int a, int b) {
      try {
        mightThrow(a);
      } catch (RuntimeException e) {
        if (b == 0) {
          sideEffect(0);
        } else if (b == 1) {
          sideEffect(1);
        } else {
          sideEffect(2);
        }
        return;
      }
      throw new IllegalStateException();
    }

    static int sum;

    @NeverInline
    static void add(int v) {
      sum += v;
    }

    @NeverInline
    static void testLongGotoToReturn(int a, boolean cond) {
      try {
        mightThrow(a);
      } catch (RuntimeException e) {
        if (cond) {
          add(100001);
          add(200002);
          add(300003);
          add(400004);
          add(500005);
          add(600006);
          add(700007);
          add(800008);
          add(900009);
          add(1000010);
          add(1100011);
          add(1200012);
          add(1300013);
          add(1400014);
          add(1500015);
          add(1600016);
          add(1700017);
          add(1800018);
          add(1900019);
          add(2000020);
          add(2100021);
          add(2200022);
          add(2300023);
          add(2400024);
        } else {
          add(100002);
          add(200004);
          add(300006);
          add(400008);
          add(500010);
          add(600012);
          add(700014);
          add(800016);
          add(900018);
          add(1000020);
          add(1100022);
          add(1200024);
          add(1300026);
          add(1400028);
          add(1500030);
          add(1600032);
          add(1700034);
          add(1800036);
          add(1900038);
          add(2000040);
          add(2100042);
          add(2200044);
          add(2300046);
          add(2400048);
        }
        return;
      }
      throw new IllegalStateException();
    }

    public static void main(String[] args) {
      testShortGotoToReturn(-1, 0);
      testShortGotoToReturn(-1, 1);
      testShortGotoToReturn(-1, 2);
      testLongGotoToReturn(-1, args.length == 0);
      System.out.println(sum % 1000);
      testLongGotoToReturn(-1, args.length != 0);
      System.out.println(sum % 1000);
    }
  }
}
