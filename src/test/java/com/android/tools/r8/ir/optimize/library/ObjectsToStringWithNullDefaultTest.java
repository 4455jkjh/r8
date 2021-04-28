// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsToStringWithNullDefaultTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .build();
  }

  public ObjectsToStringWithNullDefaultTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testNonNullArgumentMethodSubject =
                  mainClassSubject.uniqueMethodWithName("testNonNullArgument");
              assertThat(testNonNullArgumentMethodSubject, isPresent());
              assertThat(testNonNullArgumentMethodSubject, not(invokesMethodWithName("toString")));

              MethodSubject testNullArgumentMethodSubject =
                  mainClassSubject.uniqueMethodWithName("testNullArgument");
              assertThat(testNullArgumentMethodSubject, isPresent());
              assertThat(testNullArgumentMethodSubject, not(invokesMethodWithName("toString")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar");
  }

  static class Main {

    public static void main(String[] args) {
      testNonNullArgument();
      testNullArgument();
    }

    @NeverInline
    static void testNonNullArgument() {
      System.out.println(Objects.toString("Foo", ":-("));
    }

    @NeverInline
    static void testNullArgument() {
      System.out.println(Objects.toString(null, "Bar"));
    }
  }
}
