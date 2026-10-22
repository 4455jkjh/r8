// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.pc2pc;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SharedPc2PcDebugInfo extends TestBase {

  static final List<String> METHODS = Arrays.asList("m1", "m2", "m3", "m4");
  static final String EXPECTED = StringUtils.lines(METHODS);

  @Parameter(0)
  public boolean enableShareDebugInfoForDifferentParameterSizes;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, enableShareDebugInfoForDifferentParameterSizes: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDefaultDexRuntime()
            .withApiLevel(AndroidApiLevel.B)
            .withApiLevel(AndroidApiLevel.N)
            .build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepAttributeLineNumberTable()
        .addOptionsModification(
            options ->
                options.getTestingOptions().enableShareDebugInfoForDifferentParameterSizes =
                    enableShareDebugInfoForDifferentParameterSizes)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              // The mX methods can share the pc encoding which is smaller than the sum of the
              // normal encodings.
              DexDebugInfo shared = null;
              for (String name : METHODS) {
                DexEncodedMethod method = clazz.uniqueMethodWithOriginalName(name).getMethod();
                DexDebugInfo debugInfo = method.getCode().asDexCode().getDebugInfo();
                assertTrue(debugInfo.isPcBasedInfo());
                // The DEX parser should allocate the same shared instance to each method.
                assertTrue(shared == null || debugInfo == shared);
                shared = debugInfo;
              }
              DexEncodedMethod mainMethod = clazz.mainMethod().getMethod();
              DexDebugInfo mainDebugInfo = mainMethod.getCode().asDexCode().getDebugInfo();
              if (enableShareDebugInfoForDifferentParameterSizes
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
                assertSame(shared, mainDebugInfo);
              } else {
                // The main method debug info is smallest using normal line increments.
                assertTrue(mainDebugInfo.isEventBasedInfo());
              }
            });
  }

  static class TestClass {

    public static void m1() {
      System.out.print("m");
      System.out.println("1");
    }

    public static void m2() {
      System.out.print("m");
      System.out.print("2");
      System.out.println();
    }

    public static void m3() {
      PrintStream out = System.out;
      out.println("m3");
    }

    public static void m4() {
      String m = "m";
      PrintStream out = System.out;
      out.print(m);
      String s = "4";
      PrintStream out1 = System.out;
      out1.print(s);
      PrintStream out2 = System.out;
      out2.println();
    }

    public static void main(String[] args) {
      m1();
      m2();
      m3();
      m4();
    }
  }
}
