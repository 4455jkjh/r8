// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepDeserializeLambdaMethodTestRunner extends TestBase {

  private static final Class<?> TEST_CLASS_CF =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestCf.class;
  private static final Class<?> TEST_CLASS_DEX =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestDex.class;

  private static final String EXPECTED =
      StringUtils.lines("base lambda", KeepDeserializeLambdaMethodTest.LAMBDA_MESSAGE);

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CfVm cfVm;

  @Parameterized.Parameters(name = "{0}, javac = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().withNoneRuntime().build(), cfVmsToTest);
  }

  private static final List<CfVm> cfVmsToTest = new ArrayList<>();

  static {
    // JDK 8 is not supported for testing on Windows.
    if (!ToolHelper.isWindows()) {
      cfVmsToTest.add(CfVm.JDK8);
    }
    cfVmsToTest.addAll(
        ImmutableList.of(CfVm.JDK11, CfVm.JDK17, CfVm.JDK21, CfVm.JDK25, CfVm.JDK27));
  }

  private static final Map<CfVm, Path> TEST_CLASS_FILES_SHARED = new HashMap<>();
  private static final Map<CfVm, Path> TEST_CLASS_FILES_DEX = new HashMap<>();
  private static final Map<CfVm, Path> TEST_CLASS_FILES_CF = new HashMap<>();

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    Path output = getStaticTemp().newFolder("output").toPath();
    for (CfVm jdk : cfVmsToTest) {
      Path classes = output.resolve(jdk.toString());
      Files.createDirectory(classes);
      javac(TestRuntime.getCheckedInJdk(jdk), getStaticTemp())
          .addSourceFiles(
              ToolHelper.getSourceFileForTestClassFromResources(
                  KeepDeserializeLambdaMethodTest.class))
          .setOutputPath(classes)
          .compile();
      TEST_CLASS_FILES_SHARED.put(
          jdk, classes.resolve("com/android/tools/r8/cf/KeepDeserializeLambdaMethodTest.class"));
      TEST_CLASS_FILES_DEX.put(
          jdk, classes.resolve("com/android/tools/r8/cf/KeepDeserializeLambdaMethodTestDex.class"));
      TEST_CLASS_FILES_CF.put(
          jdk, classes.resolve("com/android/tools/r8/cf/KeepDeserializeLambdaMethodTestCf.class"));
    }
  }

  private Class<?> getMainClass() {
    return parameters.isCfRuntime() ? TEST_CLASS_CF : TEST_CLASS_DEX;
  }

  private Path getMainClassFile() {
    return parameters.isCfRuntime()
        ? TEST_CLASS_FILES_CF.get(cfVm)
        : TEST_CLASS_FILES_DEX.get(cfVm);
  }

  private List<Path> getClassFiles() {
    return ImmutableList.of(TEST_CLASS_FILES_SHARED.get(cfVm), getMainClassFile());
  }

  @Test
  public void testJavacSerializeLambdaCodeGeneration() throws Exception {
    parameters.assumeNoneRuntime();
    for (ClassSubject clazz :
        ImmutableList.of(
            new CodeInspector(TEST_CLASS_FILES_SHARED.get(cfVm))
                .clazz(KeepDeserializeLambdaMethodTest.class),
            new CodeInspector(TEST_CLASS_FILES_CF.get(cfVm))
                .clazz(KeepDeserializeLambdaMethodTestCf.class),
            new CodeInspector(TEST_CLASS_FILES_DEX.get(cfVm))
                .clazz(KeepDeserializeLambdaMethodTestDex.class))) {
      assertTrue(clazz.isPresent());
      assertEquals(
          cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 2 : 1,
          clazz.allMethods().stream()
              .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
              .count());
      assertEquals(
          clazz.toString(),
          cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 1 : 0,
          clazz
              .uniqueMethodWithOriginalName("$deserializeLambda$")
              .streamInstructions()
              .filter(InstructionSubject::isInvokeStatic)
              .filter(
                  instruction ->
                      instruction
                          .getMethod()
                          .getHolderType()
                          .getTypeName()
                          .equals(clazz.getOriginalTypeName()))
              .map(instruction -> instruction.getMethod().getName())
              .filter(name -> name.startsWith("$deserializeLambda$$"))
              .count());
      assertEquals(
          cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 1 : 0,
          clazz.allMethods().stream()
              .flatMap(MethodSubject::streamInstructions)
              .filter(InstructionSubject::isInvokeVirtual)
              .map(instruction -> instruction.getMethod().getName())
              .filter(name -> name.isEqualTo("getInstantiatedMethodType"))
              .count());
    }
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime() && parameters.getCfRuntime().isNewerThanOrEqual(cfVm)));
    testForRuntime(parameters)
        .addProgramFiles(getClassFiles())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkPresenceOfDeserializedLambdas);
  }

  @Test
  public void testDontKeepDeserializeLambdaR8() throws Exception {
    assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime() && parameters.getCfRuntime().isNewerThanOrEqual(cfVm)));
    test(false);
  }

  @Test
  public void testKeepDeserializedLambdaR8() throws Exception {
    assumeTrue(
        parameters.isDexRuntime()
            || (parameters.isCfRuntime() && parameters.getCfRuntime().isNewerThanOrEqual(cfVm)));
    test(true);
  }

  private void test(boolean addKeepDeserializedLambdaRule)
      throws IOException, CompilationFailedException, ExecutionException {
    R8CompatTestBuilder builder =
        testForR8Compat(parameters.getBackend())
            .addProgramFiles(getClassFiles())
            .setMinApi(parameters)
            .addKeepMainRule(getMainClass())
            .applyIf(
                cfVm.isLessThanOrEqualTo(CfVm.JDK11),
                b ->
                    b.addOptionsModification(
                        options ->
                            options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces()));
    if (addKeepDeserializedLambdaRule) {
      // For DEX the lambda deserialization methods are always removed during desugaring,
      // so the rule below not match anything.
      builder.allowUnusedProguardConfigurationRules(parameters.isDexRuntime());
      builder.addKeepRules(
          "-keepclassmembers class * {",
          "private static synthetic java.lang.Object "
              + "$deserializeLambda$(java.lang.invoke.SerializedLambda);",
          "}",
          // TODO(b/148836254): Support deserialized lambdas without the need of additional rules.
          "-keep class * { private static synthetic void lambda$*(); }");
    } else {
      builder.addDontObfuscate().addDontShrink();
    }
    builder
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkPresenceOfDeserializedLambdas);
  }

  private void checkPresenceOfDeserializedLambdas(CodeInspector inspector) {
    for (Class<?> clazz : ImmutableList.of(KeepDeserializeLambdaMethodTest.class, getMainClass())) {
      assertEquals(
          // With R8 the outlined$deserializeLambda$$... methods are inlined into
          // $deserializeLambda$.
          parameters.isCfRuntime() ? 1 : 0,
          inspector.clazz(clazz).allMethods().stream()
              .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
              .count());
    }
  }
}
