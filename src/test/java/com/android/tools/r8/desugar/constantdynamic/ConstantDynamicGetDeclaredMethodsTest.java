// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantDynamicGetDeclaredMethodsTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Hello, world!",
          "myConstant",
          "3",
          "java.lang.invoke.MethodHandles$Lookup",
          "java.lang.String",
          "java.lang.Class");
  private static final String EXPECTED_OUTPUT_R8 =
      StringUtils.lines("Hello, world!", "No myConstant method");

  private static final Class<?> MAIN_CLASS = A.class;

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm()
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        // TODO(b/210485236): This should never fail.
        .applyIf(
            // When not desugaring the CF code requires JDK 11.
            DesugarTestConfiguration::isNotDesugared,
            r -> {
              if (parameters.isCfRuntime()
                  && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)) {
                r.assertSuccessWithOutput(EXPECTED_OUTPUT);
              } else {
                r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class);
              }
            },
            c ->
                DesugarTestConfiguration.isDesugared(c)
                    && parameters.isDexRuntime()
                    && parameters
                        .getRuntime()
                        .asDex()
                        .getVm()
                        .getVersion()
                        .isOlderThan(Version.V8_1_0),
            r -> r.assertFailureWithErrorThatThrows(ClassNotFoundException.class),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_CLASS)
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn(MethodHandles.Lookup.class))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT_R8);
  }

  @Test
  public void testR8KeepBootstrapMethod() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(MAIN_CLASS)
        .addKeepMethodRules(MAIN_CLASS, "myConstant(...)")
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn(MethodHandles.Lookup.class))
        .run(parameters.getRuntime(), MAIN_CLASS)
        // TODO(b/210485236): This should never fail.
        .applyIf(
            parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0),
            b -> b.assertFailureWithErrorThatThrows(ClassNotFoundException.class),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  private byte[] getTransformedClasses() throws IOException {
    return transformer(A.class)
        .setVersion(CfVersion.V11)
        .transformConstStringToConstantDynamic(
            "condy", A.class, "myConstant", "constantName", Object.class)
        .transform();
  }

  public static class A {

    public static Object f() {
      return "condy"; // Will be transformed to Constant_DYNAMIC.
    }

    public static void main(String[] args) {
      System.out.println(f());
      Method[] methods = A.class.getDeclaredMethods();
      for (Method method : methods) {
        if (method.getName().equals("myConstant")) {
          System.out.println(method.getName());
          System.out.println(method.getParameterTypes().length);
          for (int j = 0; j < method.getParameterTypes().length; j++) {
            System.out.println(method.getParameterTypes()[j].getName());
          }
          return;
        }
      }
      System.out.println("No myConstant method");
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return "Hello, world!";
    }
  }
}
