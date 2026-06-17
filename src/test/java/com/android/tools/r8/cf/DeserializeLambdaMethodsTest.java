// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DeserializeLambdaMethodsTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CfVm cfVm;

  @Parameterized.Parameters(name = "{0}, jdk = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            // Test is using java.util.function.Supplier.
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
            .withNoneRuntime()
            .withIncludeAllPartialCompilation()
            .build(),
        cfVmsToTest);
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

  private static Path testJavaSource;
  private static final Map<CfVm, Path> TEST_CLASS_FILES = new HashMap<>();
  public static Path TEST_CLASS_FILE_JDK_27_RELEASE_25;

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    // Build test source code.
    testJavaSource =
        FileUtils.writeTextFile(
            getStaticTemp().newFolder("src").toPath().resolve("Test.java"),
            "import java.io.*;",
            "import java.util.function.Supplier;",
            "",
            "class Test {",
            "  private static Supplier<Integer> create0() {",
            "    return (Supplier<Integer> & Serializable) () -> 0;",
            "  }",
            "  private static Supplier<Integer> create1() {",
            "    return (Supplier<Integer> & Serializable) () -> 1;",
            "  }",
            "  private static void runTest(int expectedResult, Supplier<Integer> instance) throws"
                + " Exception {",
            "    ByteArrayOutputStream baos = new ByteArrayOutputStream();",
            "    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {",
            "      oos.writeObject(instance);",
            "      oos.close();",
            "    }",
            "    try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());",
            "        ObjectInputStream ois = new ObjectInputStream(in)) {",
            "      int actual = ((Supplier<Integer>) ois.readObject()).get();",
            "      if (expectedResult != actual) {",
            "        throw new AssertionError(\"Expected: \" + expectedResult + \", actual: \" +"
                + " actual);",
            "      }",
            "    }",
            "  }",
            "  public static void main(String[] args) throws Exception {",
            "    // Don't actually run the lambda deserialization code, as it is not supported",
            "    // on Android",
            "    if (args.length > 0) {",
            "      runTest(0, create0());",
            "      runTest(1, create1());",
            "    }",
            "    System.out.println(\"OK\");",
            "  }",
            "}");
    Path output = getStaticTemp().newFolder("output").toPath();
    for (CfVm jdk : cfVmsToTest) {
      Path classes = output.resolve(jdk.toString());
      Files.createDirectory(classes);
      javac(TestRuntime.getCheckedInJdk(jdk), getStaticTemp())
          .addSourceFiles(testJavaSource)
          .setOutputPath(classes)
          .compile();
      TEST_CLASS_FILES.put(jdk, classes.resolve("Test.class"));
    }
    Path classes = output.resolve("classes");
    Files.createDirectory(classes);
    javac(TestRuntime.getCheckedInJdk(CfVm.JDK27), getStaticTemp())
        .addSourceFiles(testJavaSource)
        .setOutputPath(classes)
        .setRelease("25")
        .compile();
    TEST_CLASS_FILE_JDK_27_RELEASE_25 = classes.resolve("Test.class");
  }

  private void checkJdkSerializeLambdaCodeGeneration(Path classFile) throws Exception {
    parameters.assumeNoneRuntime();
    CodeInspector inspector = new CodeInspector(classFile);
    ClassSubject clazz = inspector.clazz("Test");
    assertTrue(clazz.isPresent());
    assertEquals(
        cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 3 : 1,
        clazz.allMethods().stream()
            .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
            .count());
    assertEquals(
        cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 2 : 0,
        clazz
            .uniqueMethodWithOriginalName("$deserializeLambda$")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .filter(
                instruction -> instruction.getMethod().getHolderType().getTypeName().equals("Test"))
            .map(instruction -> instruction.getMethod().getName())
            .filter(name -> name.startsWith("$deserializeLambda$$"))
            .count());
    assertEquals(
        cfVm.isGreaterThanOrEqualTo(CfVm.JDK27) ? 2 : 0,
        clazz.allMethods().stream()
            .flatMap(MethodSubject::streamInstructions)
            .filter(InstructionSubject::isInvokeVirtual)
            .map(instruction -> instruction.getMethod().getName())
            .filter(name -> name.isEqualTo("getInstantiatedMethodType"))
            .count());
  }

  @Test
  public void testJdkSerializeLambdaCodeGeneration() throws Exception {
    parameters.assumeNoneRuntime();
    checkJdkSerializeLambdaCodeGeneration(TEST_CLASS_FILES.get(cfVm));
  }

  @Test
  public void testJdkSerializeLambdaCodeGenerationJdk27Release25() throws Exception {
    parameters.assumeNoneRuntime();
    parameters.assumeNoPartialCompilation();
    assumeTrue(cfVm.isEqualTo(CfVm.JDK27));
    checkJdkSerializeLambdaCodeGeneration(TEST_CLASS_FILE_JDK_27_RELEASE_25);
  }

  private static void expectedCodeAfterLambdaDeserializationMethodsRemoval(
      CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Test");
    assertTrue(clazz.isPresent());
    assertEquals(
        0,
        clazz.allMethods().stream()
            .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
            .count());
    assertTrue(
        clazz.allMethods().stream()
            .flatMap(MethodSubject::streamInstructions)
            .filter(InstructionSubject::isInvokeVirtual)
            .map(instructionSubject -> instructionSubject.getMethod().getName())
            .noneMatch(name -> name.isEqualTo("getInstantiatedMethodType")));
  }

  private static void unexpectedCodeAfterLambdaDeserializationMethodsRemoval(
      CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Test");
    assertTrue(clazz.isPresent());
    assertEquals(
        2,
        clazz.allMethods().stream()
            .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
            .count());
  }

  @Test
  public void testDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    testForDesugaring(parameters)
        .addProgramFiles(TEST_CLASS_FILES.get(cfVm))
        .run(parameters.getRuntime(), "Test")
        .inspect(DeserializeLambdaMethodsTest::expectedCodeAfterLambdaDeserializationMethodsRemoval)
        .assertSuccessWithOutputLines("OK");
  }

  @Test
  public void testDesugaringJdk27Release25() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(cfVm.isEqualTo(CfVm.JDK27));
    testForDesugaring(parameters)
        .addProgramFiles(TEST_CLASS_FILE_JDK_27_RELEASE_25)
        .run(parameters.getRuntime(), "Test")
        .inspect(
            DeserializeLambdaMethodsTest::unexpectedCodeAfterLambdaDeserializationMethodsRemoval)
        .assertSuccessWithOutputLines("OK");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    parameters.assumeNoPartialCompilation();
    testForD8()
        .addProgramFiles(TEST_CLASS_FILES.get(cfVm))
        .setMinApi(parameters)
        .compile()
        .inspect(
            DeserializeLambdaMethodsTest::expectedCodeAfterLambdaDeserializationMethodsRemoval);
  }

  @Test
  public void testD8Jdk27Release25() throws Exception {
    parameters.assumeDexRuntime();
    parameters.assumeNoPartialCompilation();
    assumeTrue(cfVm.isEqualTo(CfVm.JDK27));
    testForD8()
        .addProgramFiles(TEST_CLASS_FILE_JDK_27_RELEASE_25)
        .setMinApi(parameters)
        .compile()
        .inspect(
            DeserializeLambdaMethodsTest::unexpectedCodeAfterLambdaDeserializationMethodsRemoval);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters)
        .addProgramFiles(TEST_CLASS_FILES.get(cfVm))
        .setMinApi(parameters)
        .addKeepMainRule("Test")
        .compile()
        .inspect(
            DeserializeLambdaMethodsTest::expectedCodeAfterLambdaDeserializationMethodsRemoval);
  }

  @Test
  public void testR8Jdk27Release25() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(cfVm.isEqualTo(CfVm.JDK27));
    testForR8(parameters)
        .addProgramFiles(TEST_CLASS_FILE_JDK_27_RELEASE_25)
        .setMinApi(parameters)
        .addKeepMainRule("Test")
        .compile()
        .inspect(
            DeserializeLambdaMethodsTest::expectedCodeAfterLambdaDeserializationMethodsRemoval);
  }
}
