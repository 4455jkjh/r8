// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.DXTestRunResult;
import com.android.tools.r8.ProguardTestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidTypesTest extends JasminTestBase {

  private enum Compiler {
    DX,
    D8,
    JAVAC,
    PROGUARD,
    R8
  }

  private enum Mode {
    NO_INVOKE {

      @Override
      public String getExpectedOutput(
          Compiler compiler, TestParameters parameters, boolean useInterface) {
        return StringUtils.joinLines("Hello!", "Goodbye!", "");
      }

      @Override
      public String instruction() {
        return "";
      }
    },
    INVOKE_UNVERIFIABLE_METHOD {

      @Override
      public String getExpectedOutput(
          Compiler compiler, TestParameters parameters, boolean useInterface) {
        if (!useInterface) {
          return StringUtils.joinLines("Hello!", "");
        }

        switch (compiler) {
          case JAVAC:
            return StringUtils.joinLines("Hello!", "Goodbye!", "");

          case DX:
          case D8:
            switch (parameters.getDexRuntimeVersion()) {
              case V4_0_4:
              case V4_4_4:
              case V10_0_0:
              case V12_0_0:
                return StringUtils.joinLines("Hello!", "Goodbye!", "");

              case V5_1_1:
              case V6_0_1:
              case V8_1_0:
              case V9_0_0:
              case DEFAULT:
                return StringUtils.joinLines(
                    "Hello!", "Unexpected outcome of checkcast", "Goodbye!", "");

              case V7_0_0:
              case V13_0_0:
                return StringUtils.joinLines(
                    "Hello!",
                    "Unexpected outcome of checkcast",
                    "Unexpected outcome of instanceof",
                    "Goodbye!",
                    "");

              default:
                throw new Unreachable();
            }

          case R8:
            return StringUtils.joinLines(
                "Hello!",
                "Unexpected outcome of getstatic",
                "Unexpected outcome of checkcast",
                "Goodbye!",
                "");

          case PROGUARD:
            return StringUtils.joinLines(
                "Hello!", "Unexpected outcome of checkcast", "Goodbye!", "");

          default:
            throw new Unreachable();
        }
      }

      @Override
      public String instruction() {
          return "invokestatic UnverifiableClass/unverifiableMethod()V";
      }
    },
    INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS {

      @Override
      public String getExpectedOutput(
          Compiler compiler, TestParameters parameters, boolean useInterface) {
        switch (compiler) {
          case R8:
          case PROGUARD:
            // The unverifiable method has been removed as a result of tree shaking, so the code
            // does not fail with a verification error when trying to load class UnverifiableClass.
            return StringUtils.joinLines("Hello!", "In verifiable method!", "Goodbye!", "");

          default:
            if (useInterface) {
              return StringUtils.joinLines("Hello!", "In verifiable method!", "Goodbye!", "");
            }
            // The code fails with a verification error because the verifiableMethod() is being
            // called on UnverifiableClass, which does not verify due to unverifiableMethod().
            return StringUtils.joinLines("Hello!", "");
        }
      }

      @Override
      public String instruction() {
        return "invokestatic UnverifiableClass/verifiableMethod()V";
      }
    };

    public abstract String getExpectedOutput(
        Compiler compiler, TestParameters parameters, boolean useInterface);

    public abstract String instruction();
  }

  private final TestParameters parameters;
  private final Mode mode;
  private final boolean useInterface;

  public InvalidTypesTest(TestParameters parameters, Mode mode, boolean useInterface) {
    this.parameters = parameters;
    this.mode = mode;
    this.useInterface = useInterface;
  }

  @Parameters(name = "{0}, mode: {1}, use interface: {2}")
  public static Collection<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        Mode.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    if (useInterface) {
      jasminBuilder.addInterface("A");
    } else {
      jasminBuilder.addClass("A").addDefaultConstructor();
    }
    jasminBuilder.addClass("B").addDefaultConstructor();
    jasminBuilder.addInterface("I");

    ClassBuilder mainClass = jasminBuilder.addClass("TestClass");
    mainClass.addStaticField("f", "LA;");
    mainClass.addStaticField("g", "LI;");
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        // Print "Hello!".
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Hello!\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        // Invoke method on UnverifiableClass, depending on the mode.
        mode.instruction(),
        // Print "Goodbye!".
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Goodbye!\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    mainClass
        .staticMethodBuilder("m", ImmutableList.of(), "V")
        .setCode(
            // Print "Unexpected outcome of getstatic" if reading TestClass.f yields `null`.
            "getstatic TestClass/f LA;",
            "ifnonnull Label0",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"Unexpected outcome of getstatic\"",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            // Print "Unexpected outcome of checkcast" if TestClass.f can be casted to A.
            "Label0:",
            "getstatic TestClass/f LA;",
            "checkcast A", // (should throw)
            "pop",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"Unexpected outcome of checkcast\"",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            "goto Label1",
            "Catch:",
            "pop",
            // Print "Unexpected outcome of instanceof" if TestClass.f is an instance of A.
            "Label1:",
            "getstatic TestClass/f LA;",
            "instanceof A", // (should return false)
            "ifeq Return",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"Unexpected outcome of instanceof\"",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            // Return.
            "Return:",
            "return",
            ".catch java/lang/Throwable from Label0 to Catch using Catch")
        .build();

    ClassBuilder UnverifiableClass = jasminBuilder.addClass("UnverifiableClass");
    UnverifiableClass.staticMethodBuilder("<clinit>", ImmutableList.of(), "V")
        .setCode("new B", "dup", "invokespecial B/<init>()V", "putstatic TestClass/g LI;", "return")
        .build();
    UnverifiableClass.staticMethodBuilder("unverifiableMethod", ImmutableList.of(), "V")
        .setCode(
            "new B",
            "dup",
            "invokespecial B/<init>()V",
            "putstatic TestClass/f LA;",
            "invokestatic TestClass/m()V",
            "return")
        .build();
    UnverifiableClass.staticMethodBuilder("verifiableMethod", ImmutableList.of(), "V")
        .setCode(
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"In verifiable method!\"",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            "return")
        .build();

    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar);

    if (parameters.isCfRuntime()) {
      TestRunResult<?> jvmResult =
          testForJvm().addClasspath(inputJar).run(parameters.getRuntime(), mainClass.name);
      checkTestRunResult(jvmResult, Compiler.JAVAC);

      ProguardTestRunResult proguardResult =
          testForProguard()
              .addProgramFiles(inputJar)
              .addKeepMainRule(mainClass.name)
              .addKeepRules("-keep class TestClass { public static I g; }")
              .run(mainClass.name);
      checkTestRunResult(proguardResult, Compiler.PROGUARD);
    } else {
      assert parameters.isDexRuntime();

      DXTestRunResult dxResult =
          testForDX()
              .addProgramFiles(inputJar)
              .setMinApi(parameters)
              .run(parameters.getRuntime(), mainClass.name);
      checkTestRunResult(dxResult, Compiler.DX);

      D8TestRunResult d8Result =
          testForD8()
              .addProgramFiles(inputJar)
              .setMinApi(parameters)
              .run(parameters.getRuntime(), mainClass.name);
      checkTestRunResult(d8Result, Compiler.D8);
    }

    boolean allowDiagnosticWarningMessages =
        mode == Mode.INVOKE_UNVERIFIABLE_METHOD && !useInterface;
    R8TestRunResult r8Result =
        testForR8(parameters.getBackend())
            .addProgramFiles(inputJar)
            .addKeepMainRule(mainClass.name)
            .addKeepRules(
                "-keep class TestClass { public static I g; }",
                "-neverinline class TestClass { public static void m(); }")
            .enableProguardTestOptions()
            .addOptionsModification(
                options -> {
                  if (mode == Mode.INVOKE_UNVERIFIABLE_METHOD) {
                    options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
                    options.testing.allowTypeErrors = true;
                  } else if (mode == Mode.INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS) {
                    options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
                  }
                })
            .allowDiagnosticWarningMessages(allowDiagnosticWarningMessages)
            .setMinApi(parameters)
            .compileWithExpectedDiagnostics(
                diagnostics -> {
                  if (allowDiagnosticWarningMessages) {
                    diagnostics.assertWarningsMatch(
                        allOf(
                            diagnosticType(UnverifiableCfCodeDiagnostic.class),
                            diagnosticMessage(
                                containsString(
                                    "Unverifiable code in `void"
                                        + " UnverifiableClass.unverifiableMethod()`"))),
                        diagnosticMessage(
                            equalTo(
                                "The method `void UnverifiableClass.unverifiableMethod()` does not"
                                    + " type check and will be assumed to be unreachable.")));
                  }
                })
            .run(parameters.getRuntime(), mainClass.name);
    checkTestRunResult(r8Result, Compiler.R8);
  }

  private void checkTestRunResult(TestRunResult<?> result, Compiler compiler) {
    switch (mode) {
      case NO_INVOKE:
        result.assertSuccessWithOutput(getExpectedOutput(compiler));
        break;

      case INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS:
        if (useInterface || compiler == Compiler.R8 || compiler == Compiler.PROGUARD) {
          result.assertSuccessWithOutput(getExpectedOutput(compiler));
        } else {
          result
              .assertFailureWithOutput(getExpectedOutput(compiler))
              .assertFailureWithErrorThatMatches(getMatcherForExpectedError(compiler));
        }
        break;

      case INVOKE_UNVERIFIABLE_METHOD:
        if (useInterface) {
          result.assertSuccessWithOutput(getExpectedOutput(compiler));
        } else {
          result
              .assertFailureWithOutput(getExpectedOutput(compiler))
              .assertFailureWithErrorThatMatches(getMatcherForExpectedError(compiler));
        }
        break;

      default:
        throw new Unreachable();
    }
  }

  private String getExpectedOutput(Compiler compiler) {
    return mode.getExpectedOutput(compiler, parameters, useInterface);
  }

  private Matcher<String> getMatcherForExpectedError(Compiler compiler) {
    if (compiler == Compiler.R8 && mode == Mode.INVOKE_UNVERIFIABLE_METHOD) {
      return containsString("java.lang.NullPointerException");
    }
    if (parameters.isCfRuntime()) {
      return allOf(
          containsString("java.lang.VerifyError"),
          containsString("Bad type in putfield/putstatic"));
    }

    assert parameters.isDexRuntime();
    return allOf(
        containsString("java.lang.VerifyError"),
        anyOf(
            containsString("register v0 has type Precise Reference: B but expected Reference: A"),
            containsString("VFY: storing type 'LB;' into field type 'LA;'")));
  }
}
