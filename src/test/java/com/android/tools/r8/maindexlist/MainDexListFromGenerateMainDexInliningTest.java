// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexListFromGenerateMainDexInliningTest extends TestBase {

  private static List<ClassReference> mainDexList;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    mainDexList =
        testForMainDexListGenerator(getStaticTemp())
            .addInnerClasses(MainDexListFromGenerateMainDexInliningTest.class)
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " {",
                "  public static void main(java.lang.String[]);",
                "}")
            .run()
            .getMainDexList();
  }

  public MainDexListFromGenerateMainDexInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // The generated main dex list should contain Main (which is a root) and A (which is a direct
    // dependency of Main).
    assertEquals(2, mainDexList.size());
    assertEquals(A.class.getTypeName(), mainDexList.get(0).getTypeName());
    assertEquals(Main.class.getTypeName(), mainDexList.get(1).getTypeName());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addInliningAnnotations()
            .addKeepClassAndMembersRules(Main.class)
            .addMainDexListClassReferences(mainDexList)
            .collectMainDexClasses()
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters.getApiLevel())
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject fooMethodSubject = mainClassSubject.uniqueMethodWithName("foo");
    assertThat(fooMethodSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject barMethodSubject = aClassSubject.uniqueMethodWithName("bar");
    assertThat(barMethodSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    MethodSubject bazMethodSubject = bClassSubject.uniqueMethodWithName("baz");
    assertThat(bazMethodSubject, isPresent());

    assertThat(fooMethodSubject, invokesMethod(barMethodSubject));
    assertThat(barMethodSubject, invokesMethod(bazMethodSubject));

    // The main dex classes should be the same as the input main dex list.
    assertEquals(
        ImmutableSet.of(mainClassSubject.getFinalName(), aClassSubject.getFinalName()),
        compileResult.getMainDexClasses());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Main.main()");
    }

    static void foo() {
      // Should not allow inlining bar into foo(), since that adds B as a direct
      // dependence, and we don't include the direct dependencies of main dex list classes.
      A.bar();
    }
  }

  @NoHorizontalClassMerging
  static class A {

    static void bar() {
      B.baz();
    }
  }

  @NoHorizontalClassMerging
  static class B {

    @NeverInline
    static void baz() {
      System.out.println("B.baz");
    }
  }
}
