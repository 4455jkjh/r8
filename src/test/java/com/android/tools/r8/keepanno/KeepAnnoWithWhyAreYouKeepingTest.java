// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepAnnoWithWhyAreYouKeepingTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello anno");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  @Test
  public void test() throws Exception {
    KeepAnnoTestBuilder keepAnnoTestBuilder =
        testForKeepAnno(parameters)
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .setExcludedOuterClass(getClass())
            .applyIfShrinker(
                b -> {
                  b.addKeepRules("-whyareyoukeeping class **B {}");
                  b.allowStdoutMessages(); //
                });

    if (parameters.isNativeR8()) {
      try {
        keepAnnoTestBuilder.run(TestClass.class);
      } catch (CompilationFailedException e) {
        // TODO(b/381217105): Should not throw
      }
    } else {
      keepAnnoTestBuilder
          .run(TestClass.class)
          .assertSuccessWithOutput(EXPECTED)
          .inspect(
              codeInspector -> {
                assertTrue(codeInspector.clazz(B.class).init().isPresent());
              });
    }
  }

  static class TestClass {
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }

  static class A {
    @UsesReflection(@KeepTarget(classConstant = B.class, kind = KeepItemKind.CLASS_AND_MEMBERS))
    public void foo() throws Exception {
      System.out.println("Hello anno");
    }
  }

  static class B {}
}