// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DevirtualizeLibraryInheritedMethodTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT =
      new String[] {
        "LibConcrete::foo",
        "LibConcrete::foo",
        "LibConcrete::foo",
        "LibConcrete::foo",
        "ProgramSub::foo",
        "NPE",
        "LibConcrete::foo",
        "LibConcrete::foo",
        "LibConcrete::abstractBaz",
        "LibOtherConcrete::foo",
        "LibOtherConcrete::abstractBaz",
        "LibInterface::defaultBar",
      };

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    // Compile against a stub where LibConcrete omits foo() (inherited from LibAbstractBase),
    // matching Metalava from-text stubs (e.g., ArrayList omitting AbstractList.iterator/add).
    byte[] stubLibConcrete =
        transformer(LibConcrete.class).removeMethodsWithName("foo").transform();
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibInterface.class, LibAbstractBase.class, LibOtherConcrete.class)
        .addLibraryClassFileData(stubLibConcrete)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class, ProgramSub.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .apply(setMockApiLevelForClass(LibInterface.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibAbstractBase.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibConcrete.class, AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibInterface.class.getDeclaredMethod("foo"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibAbstractBase.class.getDeclaredMethod("foo"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibInterface.class.getDeclaredMethod("abstractBaz"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibAbstractBase.class.getDeclaredMethod("abstractBaz"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibInterface.class.getDeclaredMethod("defaultBar"), AndroidApiLevel.B))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());

              MethodSubject invokeExact =
                  mainClass.uniqueMethodWithOriginalName("invokeExactInherited");
              assertThat(invokeExact, isPresent());
              assertThat(
                  invokeExact,
                  invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo"));
              assertThat(
                  invokeExact,
                  not(invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo")));

              MethodSubject invokeReuseCast =
                  mainClass.uniqueMethodWithOriginalName("invokeExactReuseCast");
              assertThat(invokeReuseCast, isPresent());
              assertThat(
                  invokeReuseCast,
                  invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo"));
              assertThat(
                  invokeReuseCast,
                  not(invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo")));
              assertEquals(
                  1,
                  invokeReuseCast
                      .streamInstructions()
                      .filter(ins -> ins.isCheckCast(LibConcrete.class.getTypeName()))
                      .count());

              MethodSubject invokeUpperBound =
                  mainClass.uniqueMethodWithOriginalName("invokeUpperBoundInherited");
              assertThat(invokeUpperBound, isPresent());
              assertThat(
                  invokeUpperBound,
                  invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo"));
              assertThat(
                  invokeUpperBound,
                  not(invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo")));

              MethodSubject invokeNullable =
                  mainClass.uniqueMethodWithOriginalName("invokeNullableConcrete");
              assertThat(invokeNullable, isPresent());
              assertThat(
                  invokeNullable,
                  invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo"));
              assertThat(
                  invokeNullable,
                  not(invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo")));

              MethodSubject invokeAbstractUpperBound =
                  mainClass.uniqueMethodWithOriginalName("invokeAbstractUpperBound");
              assertThat(invokeAbstractUpperBound, isPresent());
              assertThat(
                  invokeAbstractUpperBound,
                  invokesMethodWithHolderAndName(LibAbstractBase.class.getTypeName(), "foo"));
              assertThat(
                  invokeAbstractUpperBound,
                  invokesMethodWithHolderAndName(
                      LibAbstractBase.class.getTypeName(), "abstractBaz"));
              assertThat(
                  invokeAbstractUpperBound,
                  not(invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo")));
              assertThat(
                  invokeAbstractUpperBound,
                  not(
                      invokesMethodWithHolderAndName(
                          LibInterface.class.getTypeName(), "abstractBaz")));

              // Un-overridden default interface methods are left as invoke-interface (matching
              // Devirtualizer's existing resolvedHolder.isInterface() guard).
              MethodSubject invokeDefaultMethod =
                  mainClass.uniqueMethodWithOriginalName("invokeDefaultMethod");
              assertThat(invokeDefaultMethod, isPresent());
              assertThat(
                  invokeDefaultMethod,
                  invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "defaultBar"));
              assertThat(
                  invokeDefaultMethod,
                  not(
                      invokesMethodWithHolderAndName(
                          LibConcrete.class.getTypeName(), "defaultBar")));
            })
        .addRunClasspathClasses(
            LibInterface.class, LibAbstractBase.class, LibConcrete.class, LibOtherConcrete.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testApiLevelBailoutsOnDex() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(parameters.getApiLevel().isLessThan(AndroidApiLevel.Q));
    byte[] stubLibConcrete =
        transformer(LibConcrete.class).removeMethodsWithName("foo").transform();

    // 1. Unknown API level on LibConcrete must not devirtualize.
    runApiGuardCase(stubLibConcrete, null, null, false);

    // 2. LibConcrete introduced at API 29 (> minApi) must not devirtualize.
    runApiGuardCase(stubLibConcrete, AndroidApiLevel.Q, AndroidApiLevel.B, false);

    // 3. LibConcrete at API 1, but resolved LibAbstractBase.foo at API 29 (> LibInterface.foo).
    runApiGuardCase(stubLibConcrete, AndroidApiLevel.B, AndroidApiLevel.Q, false);

    // 4. Both LibConcrete and resolved LibAbstractBase.foo at API 1 (<= minApi) must devirtualize.
    runApiGuardCase(stubLibConcrete, AndroidApiLevel.B, AndroidApiLevel.B, true);
  }

  private void runApiGuardCase(
      byte[] stubLibConcrete,
      AndroidApiLevel concreteClassApiLevel,
      AndroidApiLevel resolvedMethodApiLevel,
      boolean expectDevirtualized)
      throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibInterface.class, LibAbstractBase.class)
        .addLibraryClassFileData(stubLibConcrete)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(ApiGuardMain.class)
        .setMinApi(parameters)
        .addKeepMainRule(ApiGuardMain.class)
        .enableInliningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .apply(setMockApiLevelForClass(LibInterface.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibAbstractBase.class, AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibInterface.class.getDeclaredMethod("foo"), AndroidApiLevel.B))
        .applyIf(
            concreteClassApiLevel != null,
            b -> b.apply(setMockApiLevelForClass(LibConcrete.class, concreteClassApiLevel)))
        .applyIf(
            resolvedMethodApiLevel != null,
            b ->
                b.apply(
                    setMockApiLevelForMethod(
                        LibAbstractBase.class.getDeclaredMethod("foo"), resolvedMethodApiLevel)))
        .compile()
        .inspect(
            inspector -> {
              MethodSubject invokeExact =
                  inspector
                      .clazz(ApiGuardMain.class)
                      .uniqueMethodWithOriginalName("invokeExactInherited");
              assertThat(invokeExact, isPresent());
              if (expectDevirtualized) {
                assertThat(
                    invokeExact,
                    invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo"));
              } else {
                assertThat(
                    invokeExact,
                    invokesMethodWithHolderAndName(LibInterface.class.getTypeName(), "foo"));
                assertThat(
                    invokeExact,
                    not(invokesMethodWithHolderAndName(LibConcrete.class.getTypeName(), "foo")));
              }
            });
  }

  public interface LibInterface {
    void foo();

    void abstractBaz();

    default void defaultBar() {
      System.out.println("LibInterface::defaultBar");
    }
  }

  public abstract static class LibAbstractBase implements LibInterface {
    @Override
    public void foo() {
      System.out.println("LibAbstractBase::foo");
    }

    @Override
    public abstract void abstractBaz();
  }

  public static class LibConcrete extends LibAbstractBase {
    // Stripped from the compile-time library stub above, present at runtime.
    @Override
    public void foo() {
      System.out.println("LibConcrete::foo");
    }

    @Override
    public void abstractBaz() {
      System.out.println("LibConcrete::abstractBaz");
    }
  }

  public static class LibOtherConcrete extends LibAbstractBase {
    @Override
    public void foo() {
      System.out.println("LibOtherConcrete::foo");
    }

    @Override
    public void abstractBaz() {
      System.out.println("LibOtherConcrete::abstractBaz");
    }
  }

  public static class ProgramSub extends LibConcrete {
    @Override
    public void foo() {
      System.out.println("ProgramSub::foo");
    }
  }

  public static class ApiGuardMain {

    @NeverInline
    @NoReturnTypeStrengthening
    static LibInterface getConcrete() {
      return new LibConcrete();
    }

    @NeverInline
    static void invokeExactInherited() {
      getConcrete().foo();
    }

    public static void main(String[] args) {
      invokeExactInherited();
    }
  }

  public static class Main {

    @NeverInline
    @NoReturnTypeStrengthening
    static LibInterface getConcrete() {
      return new LibConcrete();
    }

    @NeverInline
    @NoReturnTypeStrengthening
    static LibInterface getOtherConcrete() {
      return new LibOtherConcrete();
    }

    @NeverInline
    static void invokeExactInherited() {
      getConcrete().foo();
    }

    @NeverInline
    static void invokeExactReuseCast() {
      LibInterface x = getConcrete();
      x.foo();
      x.foo();
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void invokeUpperBoundInherited(LibInterface x) {
      if (x instanceof LibConcrete) {
        x.foo();
      }
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void invokeNullableConcrete(LibInterface x) {
      if (x == null || x instanceof LibConcrete) {
        try {
          x.foo();
        } catch (NullPointerException e) {
          System.out.println("NPE");
        }
      }
    }

    @NeverInline
    static void invokeAbstractUpperBound(boolean first) {
      LibInterface x = first ? getConcrete() : getOtherConcrete();
      x.foo();
      x.abstractBaz();
    }

    @NeverInline
    static void invokeDefaultMethod() {
      getConcrete().defaultBar();
    }

    public static void main(String[] args) {
      invokeExactInherited();
      invokeExactReuseCast();
      invokeUpperBoundInherited(getConcrete());
      invokeUpperBoundInherited(new ProgramSub());
      invokeNullableConcrete(null);
      invokeNullableConcrete(getConcrete());
      invokeAbstractUpperBound(System.currentTimeMillis() > 0);
      invokeAbstractUpperBound(System.currentTimeMillis() <= 0);
      invokeDefaultMethod();
    }
  }
}
