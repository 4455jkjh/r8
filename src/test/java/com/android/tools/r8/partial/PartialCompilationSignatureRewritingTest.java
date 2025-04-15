// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationSignatureRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    parameters.assumeNoPartialCompilation();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), ExcludedClass.class)
        .assertSuccessWithOutputLines("class " + IncludedClass.class.getTypeName());
  }

  @Test
  public void testR8() throws Exception {
    testForR8Partial(parameters)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClass.class)
        .compile()
        .apply(
            compileResult -> {
              // TODO(b/410726575): IncludedClass should be present.
              ClassSubject includedClassSubject =
                  compileResult.inspector().clazz(IncludedClass.class);
              assertThat(includedClassSubject, isAbsent());
              compileResult
                  .run(parameters.getRuntime(), ExcludedClass.class)
                  .assertFailureWithErrorThatThrows(ClassNotFoundException.class);
            });
  }

  static class ExcludedClass<T extends IncludedClass> {

    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
      for (TypeVariable<Class<ExcludedClass>> typeParameter :
          ExcludedClass.class.getTypeParameters()) {
        for (Type bound : typeParameter.getBounds()) {
          System.out.println(bound);
        }
      }
    }
  }

  static class IncludedClass {}
}
