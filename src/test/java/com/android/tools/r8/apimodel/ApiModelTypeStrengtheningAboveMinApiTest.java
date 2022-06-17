// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelTypeStrengtheningAboveMinApiTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    int sdkInt = parameters.isCfRuntime() ? 0 : parameters.getApiLevel().getLevel();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Version.class)
        .addLibraryClasses(ApiLevel22.class, ApiLevel23.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + Version.class.getTypeName() + " {",
            "  public static int getSdkInt(int) return " + sdkInt + "..42;",
            "}")
        .apply(setMockApiLevelForClass(ApiLevel22.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForClass(ApiLevel23.class, AndroidApiLevel.M))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              FieldSubject fieldSubject = inspector.clazz(Main.class).uniqueFieldWithName("FIELD");
              assertThat(fieldSubject, isPresent());
              assertEquals(
                  ApiLevel22.class.getTypeName(), fieldSubject.getField().getType().getTypeName());
            })
        .addRunClasspathClasses(ApiLevel22.class, ApiLevel23.class)
        .run(parameters.getRuntime(), Main.class, Integer.toString(sdkInt))
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1),
            runResult -> runResult.assertSuccessWithOutputLines("ApiLevel22"),
            runResult -> runResult.assertSuccessWithOutputLines("null"));
  }

  public static class ApiLevel23 {}

  public static class ApiLevel22 extends ApiLevel23 {

    @Override
    public String toString() {
      return "ApiLevel22";
    }
  }

  public static class Main {

    public static ApiLevel23 FIELD;

    public static void main(String[] args) {
      int sdk = Integer.parseInt(args[0]);
      if (Version.getSdkInt(sdk) >= 22) {
        FIELD = new ApiLevel22();
      }
      System.out.println(FIELD);
    }
  }

  public static class Version {

    // -assumevalues ...
    public static int getSdkInt(int sdk) {
      return sdk;
    }
  }
}
