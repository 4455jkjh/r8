// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForType;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelInlineMethodWithApiTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelInlineMethodWithApiTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiCallerApiLevel1 = ApiCaller.class.getDeclaredMethod("apiLevel22");
    Method apiCallerCallerApiLevel1 = ApiCallerCaller.class.getDeclaredMethod("apiLevel22");
    Method otherCallerApiLevel1 = OtherCaller.class.getDeclaredMethod("apiLevel1");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, ApiCallerCaller.class, OtherCaller.class, Main.class)
        .addLibraryClasses(ApiType.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForType(ApiType.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .compile()
        .addRunClasspathClasses(ApiType.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(ApiType.class.getName())
        .inspect(verifyThat(parameters, apiCallerApiLevel1).inlinedInto(apiCallerCallerApiLevel1))
        // TODO(b/138781768): Should not be inlined
        .inspect(
            verifyThat(parameters, apiCallerCallerApiLevel1).inlinedInto(otherCallerApiLevel1));
  }

  public static class ApiType {}

  @NoHorizontalClassMerging
  public static class ApiCaller {
    public static ApiType apiLevel22() throws Exception {
      // The reflective call here is to ensure that the setting of A's api level is not based on
      // a method reference to `Api` and only because of the type reference in the field `api`.
      Class<?> reflectiveCall =
          Class.forName(
              "com.android.tools.r8.apimodel.ApiModelInlineMethodWithApiTypeTest_ApiType"
                  .replace("_", "$"));
      return (ApiType) reflectiveCall.getDeclaredConstructor().newInstance();
    }
  }

  @NoHorizontalClassMerging
  public static class ApiCallerCaller {

    public static void apiLevel22() throws Exception {
      // This is referencing the proto of ApiCaller.foo and thus have a reference to ApiType. It is
      // therefore OK to inline ApiCaller.foo() into ApiCallerCaller.bar().
      System.out.println(ApiCaller.apiLevel22().getClass().getName());
    }
  }

  public static class OtherCaller {

    public static void apiLevel1() throws Exception {
      // ApiCallerCaller.apiLevel22 should never be inlined here.
      ApiCallerCaller.apiLevel22();
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      OtherCaller.apiLevel1();
    }
  }
}
