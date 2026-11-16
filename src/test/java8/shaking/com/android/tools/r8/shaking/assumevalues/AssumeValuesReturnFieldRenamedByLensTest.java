// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests an -assumevalues rule that returns a static field that is moved by class merging.
 *
 * <p>When generating DEX, assume rules on invokes are already applied during tree shaking (before
 * class merging), so only class file output exercises rewriting the assumed value with the lens.
 */
@RunWith(Parameterized.class)
public class AssumeValuesReturnFieldRenamedByLensTest extends TestBase {

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
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  static java.lang.Object get() return " + Holder.class.getTypeName() + ".field;",
            "}")
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(Holder.class))
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(Main.class).uniqueMethodWithOriginalName("get"), isAbsent()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  static class Main {

    public static void main(String[] args) {
      Holder.field = new Sub();
      System.out.println(get() == Holder.field);
    }

    @NeverInline
    static Object get() {
      return new Object();
    }
  }

  static class Holder {

    static Object field;
  }

  static class Sub extends Holder {}
}
