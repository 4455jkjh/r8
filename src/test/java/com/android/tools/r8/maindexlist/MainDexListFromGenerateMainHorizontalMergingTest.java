// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
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
public class MainDexListFromGenerateMainHorizontalMergingTest extends TestBase {

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
            .addInnerClasses(MainDexListFromGenerateMainHorizontalMergingTest.class)
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " { public static void foo(); }")
            .run()
            .getMainDexList();
  }

  public MainDexListFromGenerateMainHorizontalMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  @Test
  public void testMainDexList() throws Exception {
    assertEquals(3, mainDexList.size());
    Set<String> mainDexReferences =
        mainDexList.stream().map(TypeReference::getTypeName).collect(Collectors.toSet());
    assertTrue(mainDexReferences.contains(A.class.getTypeName()));
    assertTrue(mainDexReferences.contains(B.class.getTypeName()));
    assertTrue(mainDexReferences.contains(Main.class.getTypeName()));
    runTest(
        builder ->
            builder.addMainDexListClassReferences(mainDexList).allowDiagnosticWarningMessages());
  }

  @Test
  public void testMainDexTracing() throws Exception {
    runTest(
        builder ->
            builder.addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " { public static void foo(); }"));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> testBuilder) throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addInliningAnnotations()
        .addKeepClassAndMembersRules(Main.class, Outside.class)
        .collectMainDexClasses()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspector(
            horizontallyMergedClassesInspector -> {
              horizontallyMergedClassesInspector.assertClassesNotMerged(B.class, A.class);
            })
        .apply(testBuilder)
        .compile()
        .apply(this::inspectCompileResult)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputThatMatches(containsString(Outside.class.getTypeName()));
  }

  private void inspectCompileResult(R8TestCompileResult compileResult) throws Exception {
    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject fooMethodSubject = mainClassSubject.uniqueMethodWithName("foo");
    assertThat(fooMethodSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    MethodSubject fooASubject = aClassSubject.uniqueMethodWithName("foo");
    assertThat(fooASubject, isPresent());

    assertThat(fooMethodSubject, invokesMethod(fooASubject));

    compileResult.inspectMainDexClasses(
        mainDexClasses -> {
          assertEquals(
              ImmutableSet.of(
                  mainClassSubject.getFinalName(),
                  aClassSubject.getFinalName(),
                  bClassSubject.getFinalName()),
              mainDexClasses);
        });
  }

  static class Main {

    public static void main(String[] args) {
      B.print();
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
  public static class B {

    @NeverInline
    public static void print() {
      System.out.println(Outside.class);
    }
  }
}
