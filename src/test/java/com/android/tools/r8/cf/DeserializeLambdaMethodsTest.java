// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final List<CfVm> cfVmsToTest =
      ImmutableList.of(CfVm.JDK8, CfVm.JDK11, CfVm.JDK17, CfVm.JDK21, CfVm.JDK25, CfVm.JDK27);
  private static final Map<CfVm, Path> TEST_CLASS_FILES = new HashMap<>();

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    // Build test source code.
    Path testJavaSource =
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
  }

  @Test
  public void testJdkSerializeLambdaCodeGeneration() throws Exception {
    parameters.assumeNoneRuntime();
    CodeInspector inspector = new CodeInspector(TEST_CLASS_FILES.get(cfVm));
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
}
