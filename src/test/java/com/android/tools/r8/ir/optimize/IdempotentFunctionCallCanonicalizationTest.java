// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class IdempotentFunctionMain {
  public static void main(String[] args) {
    // Primitive will be boxed automatically.
    {
      Map<String, Boolean> map = new HashMap<>();
      // After canonicalization, only one 'true' and one 'false' conversions remain.
      map.put("A", true);
      map.put("B", true);
      map.put("C", false);
      map.put("D", true);
      map.put("E", false);
      map.put("F", true);
      System.out.println(map.get("B"));
      System.out.println(map.get("E"));
    }

    {
      Map<String, Integer> map = new HashMap<>();
      // After canonicalization, only one 8 and one 35 conversions remain.
      map.put("A", 35);
      map.put("B", 8);
      map.put("C", 8);
      map.put("D", 8);
      map.put("E", 35);
      System.out.println(map.get("B"));
      System.out.println(map.get("E"));
    }

    {
      List<Long> ll = new ArrayList<>();
      // After canonicalization, 1, 2, 3, 4, 5, 6, and 9 conversions remain.
      // 3.141592654
      ll.add(3L);
      ll.add(1L);
      ll.add(4L);
      ll.add(1L);
      ll.add(5L);
      ll.add(9L);
      ll.add(2L);
      ll.add(6L);
      ll.add(5L);
      ll.add(4L);
      for (Long l : ll) {
        System.out.print(l);
      }
      System.out.println();
    }
  }
}

@RunWith(Parameterized.class)
public class IdempotentFunctionCallCanonicalizationTest extends TestBase {
  private static final Class<?> MAIN = IdempotentFunctionMain.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "true",
      "false",
      "8",
      "35",
      "3141592654"
  );
  private static final String BOOLEAN_DESCRIPTOR = "Ljava/lang/Boolean;";
  private static final String INTEGER_DESCRIPTOR = "Ljava/lang/Integer;";
  private static final String LONG_DESCRIPTOR = "Ljava/lang/Long;";
  private static final int EXPECTED_BOOLEAN_VALUE_OF = 2;
  private static final int EXPECTED_INTEGER_VALUE_OF = 2;
  private static final int EXPECTED_LONG_VALUE_OF = 7;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public IdempotentFunctionCallCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference once (for CF backend)",
        parameters.getBackend() == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isValueOf(DexMethod method, String descriptor) {
    return method.holder.toDescriptorString().equals(descriptor)
        && method.getArity() == 1
        && method.proto.returnType.toDescriptorString().equals(descriptor)
        && method.name.toString().equals("valueOf");
  }

  private long countValueOf(MethodSubject method, String descriptor) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isValueOf(instructionSubject.getMethod(), descriptor);
      }
      return false;
    })).count();
  }

  private void test(
      TestRunResult result,
      int expectedBooleanValueOfCount,
      int expectedIntValueOfCount,
      int expectedLongValueOfCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedBooleanValueOfCount, countValueOf(mainMethod, BOOLEAN_DESCRIPTOR));
    assertEquals(expectedIntValueOfCount, countValueOf(mainMethod, INTEGER_DESCRIPTOR));
    assertEquals(expectedLongValueOfCount, countValueOf(mainMethod, LONG_DESCRIPTOR));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.getBackend() == Backend.DEX);

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, EXPECTED_BOOLEAN_VALUE_OF, EXPECTED_INTEGER_VALUE_OF, EXPECTED_LONG_VALUE_OF);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, EXPECTED_BOOLEAN_VALUE_OF, EXPECTED_INTEGER_VALUE_OF, EXPECTED_LONG_VALUE_OF);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    int expectedBooleanValueOfCount =
        parameters.getBackend() == Backend.CF ? 6 : EXPECTED_BOOLEAN_VALUE_OF;
    int expectedIntValueOfCount =
        parameters.getBackend() == Backend.CF ? 5 : EXPECTED_INTEGER_VALUE_OF;
    int expectedLongValueOfCount =
        parameters.getBackend() == Backend.CF ? 10 : EXPECTED_LONG_VALUE_OF;
    test(result, expectedBooleanValueOfCount, expectedIntValueOfCount, expectedLongValueOfCount);
  }
}
