// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.assistant.runtime.EmptyReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateAssistantRuntimeMethods extends MethodGenerationBase {
  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/assistant/AssistantRuntimeMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(
          EmptyReflectiveOperationReceiver.class,
          ReflectiveEventType.class,
          ReflectiveOperationJsonLogger.class,
          ReflectiveOperationReceiver.class,
          ReflectiveOperationReceiver.ClassFlag.class,
          ReflectiveOperationReceiver.NameLookupType.class,
          ReflectiveOracle.class,
          ReflectiveOracle.Stack.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateAssistantRuntimeMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  protected DexType getGeneratedType() {
    return GENERATED_TYPE;
  }

  @Override
  protected List<Class<?>> getMethodTemplateClasses() {
    return METHOD_TEMPLATE_CLASSES;
  }

  @Override
  protected List<Class<?>> getClassesToGenerate() {
    return METHOD_TEMPLATE_CLASSES;
  }

  @Override
  protected int getYear() {
    return 2026;
  }

  @Override
  protected boolean includeMethod(DexEncodedMethod method) {
    return true;
  }

  @Test
  public void testAssistantRuntimeMethodsGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        "Generated file is not up-to-date. Run GenerateAssistantRuntimeMethods to update.",
        generateMethods(),
        FileUtils.readTextFile(getGeneratedFile(), java.nio.charset.StandardCharsets.UTF_8));
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(
        TestDataSourceSet.TESTS_JAVA_8, TestDataSourceSet.TESTBASE_DATA_LOCATION);
    GenerateAssistantRuntimeMethods generator = new GenerateAssistantRuntimeMethods(null);
    System.out.println("Generating to: " + generator.getGeneratedFile().toAbsolutePath());
    generator.generateMethodsAndWriteThemToFile();
  }
}
