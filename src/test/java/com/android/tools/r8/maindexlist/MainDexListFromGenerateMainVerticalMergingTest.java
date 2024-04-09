// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexListFromGenerateMainVerticalMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexListFromGenerateMainVerticalMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMainTracing() throws Exception {
    runTest(
        builder ->
            builder.addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " { public static void foo(); }"));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> testBuilder) throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addInliningAnnotations()
            .addKeepClassAndMembersRules(Main.class, Outside.class)
            .collectMainDexClasses()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .apply(testBuilder)
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject fooMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethodSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject fooAMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooAMethodSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    assertThat(fooMethodSubject, invokesMethod(fooAMethodSubject));

    compileResult.inspectMainDexClasses(
        mainDexClasses -> {
          assertEquals(
              ImmutableSet.of(
                  mainClassSubject.getFinalName(),
                  aClassSubject.getFinalName(),
                  bClassSubject.getFinalName()),
              mainDexClasses);
        });

    compileResult.run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines("B::print");
  }

  static class Main {

    public static void main(String[] args) {
      new B().print();
    }

    public static void foo() {
      A.foo();
    }
  }

  public static class Outside {}

  public static class A {

    @NeverInline
    public static void foo() {
      System.out.println("A::foo");
    }
  }

  @NeverClassInline
  public static class B extends A {

    public static Outside outsideField;

    {
      outsideField = System.currentTimeMillis() > 0 ? null : new Outside();
    }

    @NeverInline
    public void print() {
      if (outsideField == null) {
        System.out.println("B::print");
      }
    }
  }
}
