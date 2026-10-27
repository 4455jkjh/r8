// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static com.android.tools.r8.jdk9.privateitf.DexVersion35UpToApi30Test.assertDexVersion;
import static com.android.tools.r8.jdk9.privateitf.DexVersion35UpToApi30Test.getDefaultDexVersion;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrivateInterfaceMethodStaticizerTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("1", "2", "3", "4", "5", "6", "7", "8");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void testD8Debug() throws Exception {
    testD8(CompilationMode.DEBUG);
  }

  @Test
  public void testD8Release() throws Exception {
    testD8(CompilationMode.RELEASE);
  }

  private void testD8(CompilationMode mode) throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .setMode(mode)
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    compileResult
        .run(parameters.getRuntime(), EnclosingMethodTestClass.class)
        .assertSuccessWithOutputLines(getExpectedEnclosingMethodName());
  }

  @Test
  public void testD8DexInput() throws Exception {
    TestCompileResult<?, ?> dexInputCompileResult =
        testForD8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = false)
            .compile();
    DexVersion expectedInputDexVersion = getDefaultDexVersion(parameters);
    assertTrue(expectedInputDexVersion.isGreaterThanOrEqualTo(DexVersion.V37));
    assertDexVersion(expectedInputDexVersion, dexInputCompileResult);
    Path dexInput = dexInputCompileResult.writeToZip();
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addProgramFiles(dexInput)
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    compileResult
        .run(parameters.getRuntime(), EnclosingMethodTestClass.class)
        .assertSuccessWithOutputLines(getExpectedEnclosingMethodName());
  }

  @Test
  public void testD8ClasspathNestMember() throws Exception {
    List<Class<?>> otherNestClasses =
        Arrays.stream(getClass().getNestMembers())
            .filter(c -> c != I.class && c != getClass())
            .collect(Collectors.toList());
    TestCompileResult<?, ?> ifaceCompileResult =
        testForD8(parameters)
            .addProgramClasses(I.class)
            .addClasspathClasses(getClass())
            .addClasspathClasses(otherNestClasses)
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .compile();
    assertExpectedDexVersion(ifaceCompileResult);
    testForD8(parameters)
        .addStrippedOuter(getClass())
        .addProgramClasses(otherNestClasses)
        .addClasspathClasses(I.class)
        .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
        .compile()
        .addRunClasspathFiles(ifaceCompileResult.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Debug() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRules(TestClass.class, EnclosingMethodTestClass.class)
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .debug()
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    compileResult
        .run(parameters.getRuntime(), EnclosingMethodTestClass.class)
        .assertSuccessWithOutputLines(getExpectedEnclosingMethodName());
  }

  @Test
  public void testR8WithoutOptimization() throws Exception {
    // Without optimization, the private interface methods are not inlined or removed.
    TestCompileResult<?, ?> compileResult =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRules(TestClass.class, EnclosingMethodTestClass.class)
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addDontOptimize()
            .addDontObfuscate()
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .inspect(
            inspector -> {
              ClassSubject iface = inspector.clazz(I.class);
              assertThat(iface, isPresent());
              assertEquals(
                  parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R),
                  iface.allMethods(FoundMethodSubject::isPrivate).stream()
                      .allMatch(FoundMethodSubject::isStatic));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    compileResult
        .run(parameters.getRuntime(), EnclosingMethodTestClass.class)
        .assertSuccessWithOutputLines(getExpectedEnclosingMethodName());
  }

  @Test
  public void testR8WithKeptPrivateInterfaceMethod() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-keep interface " + I.class.getTypeName() + " {",
                "  private int compute(int);",
                "}")
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .inspect(
            inspector -> {
              MethodSubject compute =
                  inspector.clazz(I.class).uniqueMethodWithOriginalName("compute");
              if (parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)) {
                assertThat(compute, isPresentAndRenamed());
                assertEquals("compute$staticized", compute.getFinalName());
                assertTrue(compute.isStatic());
              } else {
                assertThat(compute, isPresentAndNotRenamed());
                assertFalse(compute.isStatic());
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8PartialWithInterfaceInD8() throws Exception {
    testR8Partial(false);
  }

  @Test
  public void testR8PartialWithInterfaceInR8() throws Exception {
    testR8Partial(true);
  }

  private void testR8Partial(boolean isInterfaceInR8) throws Exception {
    // Split the nest of the test class such that the interface I, its super interface and its nest
    // mates that access its private methods are in one part, and the remaining classes are in the
    // other part.
    List<Class<?>> interfaceClasses = new ArrayList<>();
    List<Class<?>> otherClasses = new ArrayList<>();
    for (Class<?> clazz : getClass().getNestMembers()) {
      if (clazz == getClass()) {
        continue;
      }
      if (clazz == SuperI.class || clazz.getName().startsWith(I.class.getName())) {
        interfaceClasses.add(clazz);
      } else {
        otherClasses.add(clazz);
      }
    }
    R8PartialTestBuilder builder = testForR8Partial(parameters).addStrippedOuter(getClass());
    if (isInterfaceInR8) {
      builder
          .addR8IncludedClasses(false, getClass())
          .addR8IncludedClasses(interfaceClasses.toArray(new Class<?>[0]))
          .addR8ExcludedClasses(otherClasses.toArray(new Class<?>[0]));
    } else {
      builder
          .addR8ExcludedClasses(false, getClass())
          .addR8ExcludedClasses(interfaceClasses.toArray(new Class<?>[0]))
          .addR8IncludedClasses(otherClasses.toArray(new Class<?>[0]));
    }
    TestCompileResult<?, ?> compileResult =
        builder
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addR8PartialOptionsModification(
                options -> options.testing.enableDexVersion35UpToApi30 = true)
            .applyIf(
                isInterfaceInR8,
                b -> b.enableInliningAnnotations().enableNoVerticalClassMergingAnnotations(),
                b -> b.addKeepMainRules(TestClass.class, EnclosingMethodTestClass.class))
            .compile();
    assertExpectedDexVersion(compileResult);
    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    // R8 only keeps the EnclosingMethod attribute of kept classes.
    if (!isInterfaceInR8) {
      compileResult
          .run(parameters.getRuntime(), EnclosingMethodTestClass.class)
          .assertSuccessWithOutputLines(getExpectedEnclosingMethodName());
    }
  }

  private String getExpectedEnclosingMethodName() {
    return parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)
        ? "describe$staticized"
        : "describe";
  }

  private void assertExpectedDexVersion(TestCompileResult<?, ?> compileResult) throws Exception {
    assertDexVersion(
        parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)
            ? DexVersion.V35
            : getDefaultDexVersion(parameters),
        compileResult);
  }

  @NoVerticalClassMerging
  interface SuperI {

    @NeverInline
    default int superValue$staticized(I i) {
      return 8;
    }
  }

  @NoVerticalClassMerging
  interface I extends SuperI {

    default void run() {
      System.out.println(compute(1));
      IntSupplier lambda = () -> compute(2);
      System.out.println(lambda.getAsInt());
      IntUnaryOperator methodReference = this::compute;
      System.out.println(methodReference.applyAsInt(3));
      System.out.println(new NestMember().call(this));
      // The staticized value() conflicts with the static value$staticized(I).
      System.out.println(value());
      System.out.println(value$staticized(this));
      // The staticized superValue() must not match superValue$staticized(I) in SuperI, otherwise
      // the invoke-interface in TestClass would resolve to the private static method.
      System.out.println(superValue());
    }

    default Object getDescription() {
      return describe();
    }

    @NeverInline
    private int compute(int i) {
      return helper(i);
    }

    @NeverInline
    private int helper(int i) {
      return i;
    }

    @NeverInline
    private int value() {
      return 5;
    }

    @NeverInline
    static int value$staticized(I i) {
      return 6;
    }

    @NeverInline
    private int superValue() {
      return 7;
    }

    @NeverInline
    private Object describe() {
      return new Object() {};
    }

    class NestMember {

      @NeverInline
      int call(I i) {
        return i.compute(4);
      }
    }
  }

  static class A implements I {}

  static class TestClass {

    public static void main(String[] args) {
      I a = new A();
      a.run();
      System.out.println(a.superValue$staticized(a));
    }
  }

  static class EnclosingMethodTestClass {

    public static void main(String[] args) {
      System.out.println(new A().getDescription().getClass().getEnclosingMethod().getName());
    }
  }
}
