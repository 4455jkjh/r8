// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodeling;

import static com.android.tools.r8.apimodeling.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodeling.ApiModelingTestHelper.verifyThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
public class ApiModelingNoInliningOfHigherApiLevelVirtualTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ApiModelingNoInliningOfHigherApiLevelVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("callVirtualMethod");
    Method apiCallerCaller = A.class.getDeclaredMethod("noApiCall");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .compile()
        .inspect(
            inspector -> {
              if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)) {
                verifyThat(parameters, apiCaller)
                    .inlinedIntoFromApiLevel(apiCallerCaller, AndroidApiLevel.L_MR1)
                    .accept(inspector);
              } else {
                // TODO(b/188388130): Should only inline on minApi >= 22.
                assertThrows(
                    AssertionError.class,
                    () ->
                        verifyThat(parameters, apiCaller)
                            .inlinedIntoFromApiLevel(apiCallerCaller, AndroidApiLevel.L_MR1)
                            .accept(inspector));
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "A::noApiCall", "ApiCaller::callVirtualMethod", "Api::apiLevel22");
  }

  public static class Api {

    public void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  public static class ApiCaller {
    public void callVirtualMethod() {
      System.out.println("ApiCaller::callVirtualMethod");
      new Api().apiLevel22();
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void noApiCall() {
      System.out.println("A::noApiCall");
      new ApiCaller().callVirtualMethod();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.noApiCall();
    }
  }
}
