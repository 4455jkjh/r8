// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.instantiatesClass;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelAndroidxApiImplMinorTest extends TestBase {

  private static final ClassReference API_36_CLASS =
      Reference.classFromDescriptor("Landroidx/TestApi36Impl;");
  private static final ClassReference API_37_CLASS =
      Reference.classFromDescriptor("Landroidx/TestApi37Impl;");

  private final TestParameters parameters;
  private final AndroidApiLevel minApi;

  @Parameters(name = "{0}, minApi: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        ImmutableList.of(
            AndroidApiLevel.B,
            AndroidApiLevel.L,
            AndroidApiLevel.BAKLAVA,
            AndroidApiLevel.BAKLAVA_1));
  }

  public ApiModelAndroidxApiImplMinorTest(TestParameters parameters, AndroidApiLevel minApi) {
    parameters.assertNoneRuntime();
    this.parameters = parameters;
    this.minApi = minApi;
  }

  private void configureLibraryAndApiLevels(TestCompilerBuilder<?, ?, ?, ?, ?> builder)
      throws Exception {
    builder
        .addLibraryClasses(LibraryClass36.class, LibraryClass36_1.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(minApi)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(setMockApiLevelForClass(LibraryClass36.class, AndroidApiLevel.BAKLAVA))
        .apply(setMockApiLevelForClass(LibraryClass36_1.class, AndroidApiLevel.BAKLAVA_1))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass36.class.getDeclaredMethod("foo"), AndroidApiLevel.BAKLAVA))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass36.class.getDeclaredConstructor(), AndroidApiLevel.BAKLAVA))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass36_1.class.getDeclaredMethod("bar"), AndroidApiLevel.BAKLAVA_1))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass36_1.class.getDeclaredConstructor(), AndroidApiLevel.BAKLAVA_1));
  }

  private void configureBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    builder
        .addProgramClassFileData(
            transformer(TestApiImpl.class)
                .setClassDescriptor(API_36_CLASS.getDescriptor())
                .transform(),
            transformer(TestApiImpl.class)
                .setClassDescriptor(API_37_CLASS.getDescriptor())
                .transform())
        .apply(this::configureLibraryAndApiLevels);
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(Backend.DEX, parameters)
        .setMode(CompilationMode.DEBUG)
        .apply(this::configureBuilder)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(Backend.DEX, parameters)
        .setMode(CompilationMode.RELEASE)
        .apply(this::configureBuilder)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX, parameters)
        .apply(this::configureBuilder)
        .addKeepRules(
            "-keep class " + API_36_CLASS.getTypeName() + " { *; }",
            "-keep class " + API_37_CLASS.getTypeName() + " { *; }")
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::inspect);
  }

  @Test
  public void testR8Inlining() throws Exception {
    testForR8(Backend.DEX, parameters)
        .addProgramClassFileData(
            transformer(TestApiImpl.class)
                .setClassDescriptor(API_36_CLASS.getDescriptor())
                .transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(TestApiImpl.class), API_36_CLASS.getDescriptor())
                .transform())
        .apply(this::configureLibraryAndApiLevels)
        .addKeepMainRule(Main.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::inspectR8Inlining);
  }

  private void inspect(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    // TestApi36Impl outlines API 36.0: LibraryClass36 calls stay direct, while LibraryClass36_1
    // (API 36.1) method calls are outlined whenever minApi < 36.1, and constructor calls are
    // outlined when L <= minApi < 36.1.
    FoundClassSubject api36Subject = getPresentClass(inspector, API_36_CLASS);
    assertDirectMethodCall(
        inspector, syntheticItems, api36Subject, "has36Method", LibraryClass36.class, "foo");
    assertDirectCtorCall(
        inspector, syntheticItems, api36Subject, "has36Ctor", LibraryClass36.class);
    if (minApi.isLessThan(AndroidApiLevel.BAKLAVA_1)) {
      assertOutlinedMethodCall(
          inspector, syntheticItems, api36Subject, "has36_1Method", LibraryClass36_1.class, "bar");
    } else {
      assertDirectMethodCall(
          inspector, syntheticItems, api36Subject, "has36_1Method", LibraryClass36_1.class, "bar");
    }
    if (minApi.isGreaterThanOrEqualTo(AndroidApiLevel.L)
        && minApi.isLessThan(AndroidApiLevel.BAKLAVA_1)) {
      assertOutlinedCtorCall(
          inspector, syntheticItems, api36Subject, "has36_1Ctor", LibraryClass36_1.class);
    } else {
      assertDirectCtorCall(
          inspector, syntheticItems, api36Subject, "has36_1Ctor", LibraryClass36_1.class);
    }

    // TestApi37Impl outlines API 37.0: both 36.0 and 36.1 calls stay direct.
    FoundClassSubject api37Subject = getPresentClass(inspector, API_37_CLASS);
    assertDirectMethodCall(
        inspector, syntheticItems, api37Subject, "has36Method", LibraryClass36.class, "foo");
    assertDirectCtorCall(
        inspector, syntheticItems, api37Subject, "has36Ctor", LibraryClass36.class);
    assertDirectMethodCall(
        inspector, syntheticItems, api37Subject, "has36_1Method", LibraryClass36_1.class, "bar");
    assertDirectCtorCall(
        inspector, syntheticItems, api37Subject, "has36_1Ctor", LibraryClass36_1.class);
  }

  private void inspectR8Inlining(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    // At minApi < 36.0 (BAKLAVA), outlining drops has36_1Method (at B and L) and has36_1Ctor
    // (at L) to minApi so they inline into Main, leaving only has36Method, has36Ctor, and (at B)
    // has36_1Ctor on TestApi36Impl. Once minApi >= 36.0, TestApi36Impl inlines completely into
    // Main, while at minApi == 36.0 the 36.1 method and constructor outlines stay out-of-line.
    boolean inlined36 = minApi.isGreaterThanOrEqualTo(AndroidApiLevel.BAKLAVA);
    ClassSubject api36Subject = inspector.clazz(API_36_CLASS);
    assertThat(api36Subject, notIf(isPresent(), inlined36));
    if (!inlined36) {
      ImmutableList.Builder<String> expectedRemainingMethods =
          ImmutableList.<String>builder().add("has36Method", "has36Ctor");
      if (minApi.isLessThan(AndroidApiLevel.L)) {
        expectedRemainingMethods.add("has36_1Ctor");
      }
      assertThat(
          api36Subject.asFoundClassSubject().allMethods().stream()
              .map(MethodSubject::getOriginalMethodName)
              .collect(Collectors.toList()),
          UnorderedCollectionMatcher.matchesItemsOneToOne(expectedRemainingMethods.build()));
    }

    ClassSubject mainSubject = inspector.clazz(Main.class);
    assertThat(mainSubject, isPresent());
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresent());

    assertThat(
        mainMethod, notIf(invokesMethodWithHolderAndName(LibraryClass36.class, "foo"), !inlined36));
    assertThat(mainMethod, notIf(instantiatesClass(LibraryClass36.class), !inlined36));
    assertThat(
        mainMethod,
        notIf(invokesMethodWithHolderAndName(LibraryClass36.class, "<init>"), !inlined36));

    boolean direct36_1 = minApi.isGreaterThanOrEqualTo(AndroidApiLevel.BAKLAVA_1);
    assertThat(
        mainMethod,
        notIf(invokesMethodWithHolderAndName(LibraryClass36_1.class, "bar"), !direct36_1));
    assertThat(mainMethod, notIf(instantiatesClass(LibraryClass36_1.class), !direct36_1));
    assertThat(
        mainMethod,
        notIf(invokesMethodWithHolderAndName(LibraryClass36_1.class, "<init>"), !direct36_1));

    ImmutableList.Builder<Matcher<MethodSubject>> expectedOutlines = ImmutableList.builder();
    if (!direct36_1) {
      expectedOutlines.add(invokesMethodWithHolderAndName(LibraryClass36_1.class, "bar"));
      if (minApi.isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
        expectedOutlines.add(
            allOf(
                instantiatesClass(LibraryClass36_1.class),
                invokesMethodWithHolderAndName(LibraryClass36_1.class, "<init>")));
      }
    }
    assertThat(
        getInvokedOutlineMethods(inspector, syntheticItems, mainMethod),
        UnorderedCollectionMatcher.matchesOneToOne(expectedOutlines.build()));
  }

  private static FoundClassSubject getPresentClass(
      CodeInspector inspector, ClassReference classReference) {
    ClassSubject subject = inspector.clazz(classReference);
    assertThat(subject, isPresent());
    return subject.asFoundClassSubject();
  }

  private static List<FoundMethodSubject> getInvokedOutlineMethods(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems, MethodSubject caller) {
    return caller
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .map(InstructionSubject::getMethod)
        .flatMap(
            invoked ->
                inspector.allClasses().stream()
                    .filter(
                        clazz ->
                            clazz
                                    .getFinalReference()
                                    .equals(invoked.getHolderType().asClassReference())
                                && syntheticItems.isExternalApiOutlineClass(
                                    clazz.getOriginalReference()))
                    .flatMap(clazz -> clazz.allMethods().stream())
                    .filter(m -> m.getFinalReference().equals(invoked.asMethodReference())))
        .collect(Collectors.toList());
  }

  private static void assertDirectMethodCall(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      FoundClassSubject clazz,
      String callerMethodName,
      Class<?> targetClass,
      String targetMethodName) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(callerMethodName);
    assertThat(method, isPresent());
    assertThat(method, invokesMethodWithHolderAndName(targetClass, targetMethodName));
    assertThat(
        getInvokedOutlineMethods(inspector, syntheticItems, method),
        UnorderedCollectionMatcher.matchesOneToOne(ImmutableList.of()));
  }

  private static void assertOutlinedMethodCall(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      FoundClassSubject clazz,
      String callerMethodName,
      Class<?> targetClass,
      String targetMethodName) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(callerMethodName);
    assertThat(method, isPresent());
    assertThat(method, not(invokesMethodWithHolderAndName(targetClass, targetMethodName)));
    List<FoundMethodSubject> outlineMethods =
        getInvokedOutlineMethods(inspector, syntheticItems, method);
    assertThat(
        outlineMethods,
        UnorderedCollectionMatcher.matchesOneToOne(
            ImmutableList.of(invokesMethodWithHolderAndName(targetClass, targetMethodName))));
  }

  private static void assertDirectCtorCall(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      FoundClassSubject clazz,
      String callerMethodName,
      Class<?> targetClass) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(callerMethodName);
    assertThat(method, isPresent());
    assertThat(method, instantiatesClass(targetClass));
    assertThat(method, invokesMethodWithHolderAndName(targetClass, "<init>"));
    assertThat(
        getInvokedOutlineMethods(inspector, syntheticItems, method),
        UnorderedCollectionMatcher.matchesOneToOne(ImmutableList.of()));
  }

  private static void assertOutlinedCtorCall(
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems,
      FoundClassSubject clazz,
      String callerMethodName,
      Class<?> targetClass) {
    MethodSubject method = clazz.uniqueMethodWithOriginalName(callerMethodName);
    assertThat(method, isPresent());
    assertThat(method, not(instantiatesClass(targetClass)));
    assertThat(method, not(invokesMethodWithHolderAndName(targetClass, "<init>")));
    List<FoundMethodSubject> outlineMethods =
        getInvokedOutlineMethods(inspector, syntheticItems, method);
    assertThat(
        outlineMethods,
        UnorderedCollectionMatcher.matchesOneToOne(
            ImmutableList.of(
                allOf(
                    instantiatesClass(targetClass),
                    invokesMethodWithHolderAndName(targetClass, "<init>")))));
  }

  public static class LibraryClass36 {
    public LibraryClass36() {}

    public static void foo() {}
  }

  public static class LibraryClass36_1 {
    public LibraryClass36_1() {}

    public static void bar() {}
  }

  public static class /* androidx. */ TestApiImpl {
    public static void has36Method() {
      LibraryClass36.foo();
    }

    public static Object has36Ctor() {
      return new LibraryClass36();
    }

    public static void has36_1Method() {
      LibraryClass36_1.bar();
    }

    public static Object has36_1Ctor() {
      return new LibraryClass36_1();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      TestApiImpl.has36Method();
      System.out.println(TestApiImpl.has36Ctor());
      TestApiImpl.has36_1Method();
      System.out.println(TestApiImpl.has36_1Ctor());
    }
  }
}
