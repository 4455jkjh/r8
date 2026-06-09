// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class Jdk27DeserializeLambdaMethodsTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().withNoneRuntime().build();
  }

  private static Path TEST_CLASS_FILE;

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    // Build test constants.
    Path output = getStaticTemp().newFolder("output").toPath();
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
            "  public static void main() throws Exception {",
            "    runTest(0, create0());",
            "    runTest(1, create1());",
            "    System.err.println(\"OK\");",
            "  }",
            "}");
    javac(TestRuntime.getCheckedInJdk27(), getStaticTemp())
        .addSourceFiles(testJavaSource)
        .setOutputPath(output)
        .compile();
    TEST_CLASS_FILE = output.resolve("Test.class");
  }

  @Test
  public void testJdk27SerializeLambdaCodeGeneration() throws Exception {
    parameters.assumeNoneRuntime();
    CodeInspector inspector = new CodeInspector(TEST_CLASS_FILE);
    ClassSubject clazz = inspector.clazz("Test");
    assertTrue(clazz.isPresent());
    assertEquals(
        3,
        clazz.allMethods().stream()
            .filter(method -> method.getOriginalMethodName().startsWith("$deserializeLambda$"))
            .count());
    assertEquals(
        2,
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
        2,
        clazz.allMethods().stream()
            .flatMap(MethodSubject::streamInstructions)
            .filter(InstructionSubject::isInvokeVirtual)
            .map(instruction -> instruction.getMethod().getName())
            .filter(name -> name.isEqualTo("getInstantiatedMethodType"))
            .count());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(TEST_CLASS_FILE)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("Test");
              assertTrue(clazz.isPresent());
              // TODO(b/521062024): Should be 0.
              assertEquals(
                  2,
                  clazz.allMethods().stream()
                      .filter(
                          method ->
                              method.getOriginalMethodName().startsWith("$deserializeLambda$"))
                      .count());
              // TODO(b/521062024): Should be true.
              assertFalse(
                  clazz.allMethods().stream()
                      .flatMap(MethodSubject::streamInstructions)
                      .filter(InstructionSubject::isInvokeVirtual)
                      .map(instruction -> instruction.getMethod().getName())
                      .noneMatch(name -> name.isEqualTo("getInstantiatedMethodType")));
            });
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters)
        .addProgramFiles(TEST_CLASS_FILE)
        .setMinApi(parameters)
        .addKeepMainRule("Test")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("Test");
              assertTrue(clazz.isPresent());
              assertEquals(
                  0,
                  clazz.allMethods().stream()
                      .filter(
                          method ->
                              method.getOriginalMethodName().startsWith("$deserializeLambda$"))
                      .count());
              assertTrue(
                  clazz.allMethods().stream()
                      .flatMap(MethodSubject::streamInstructions)
                      .filter(InstructionSubject::isInvokeVirtual)
                      .map(instruction -> instruction.getMethod().getName())
                      .noneMatch(name -> name.isEqualTo("getInstantiatedMethodType")));
            });
  }
}
