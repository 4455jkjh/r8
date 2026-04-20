// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class D8AssistantInstrumentationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public D8AssistantInstrumentationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path jsonLog = temp.newFile().toPath();
    GlobalSyntheticsTestingConsumer globalsConsumer = new GlobalSyntheticsTestingConsumer();
    GlobalSyntheticsGeneratorCommand command =
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setGlobalSyntheticsConsumer(globalsConsumer)
            .build();
    runGlobalSyntheticsGenerator(
        command, options -> options.getAssistantOptions().enableAssistantInstrumentation = true);
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .apply(
            b ->
                b.getBuilder().addGlobalSyntheticsResourceProviders(globalsConsumer.getProviders()))
        .setMinApi(parameters)
        .addOptionsModification(
            o -> {
              o.getAssistantOptions().enableAssistantInstrumentation = true;
              o.testing.globalSyntheticCreatedCallback = null;
            })
        .compile()
        .inspect(this::inspect)
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + jsonLog.toString())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputThatMatches(containsString("Hello, world!"));

    String logContent = new String(Files.readAllBytes(jsonLog), StandardCharsets.UTF_8);
    assertThat(logContent, containsString("CLASS_FOR_NAME"));
    assertTrue(
        logContent.contains(
            "com.android.tools.r8.assistant.D8AssistantInstrumentationTest$TestClass"));
    assertTrue(logContent.contains("CLASS_GET_NAME"));
    assertTrue(logContent.contains("NAME"));
    assertTrue(logContent.contains("CLASS_GET_DECLARED_METHOD"));
    assertTrue(logContent.contains("CLASS_GET_FIELDS"));
    assertTrue(logContent.contains("CLASS_GET_CONSTRUCTORS"));
    assertTrue(logContent.contains("CLASS_GET_SUPERCLASS"));
    assertTrue(logContent.contains("CLASS_GET_PACKAGE"));
    assertTrue(logContent.contains("CLASS_FLAG"));
    assertTrue(logContent.contains("CLASS_GET_COMPONENT_TYPE"));
  }

  private void inspect(CodeInspector inspector) {
    // Check that assistant runtime classes are present.
    assertThat(
        inspector.clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle"), isPresent());
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack")
            .isPresent());
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack")
            .method("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack", "createStack")
            .isPresent());
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger")
            .isPresent());
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver$ClassFlag")
            .isPresent());
    assertTrue(
        inspector
            .clazz(
                "com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver$NameLookupType")
            .isPresent());
    // Check that TestClass is instrumented.
    // ReflectiveOracle.onClassForNameDefault(String) should be called.
    assertThat(
        inspector.clazz(TestClass.class).mainMethod(),
        CodeMatchers.invokesMethodWithName("onClassForNameDefault"));
  }

  static class TestClass {
    public static void main(String[] args) throws Exception {
      Class<?> clazz =
          Class.forName("com.android.tools.r8.assistant.D8AssistantInstrumentationTest$TestClass");
      System.out.println("Hello, world!");
      clazz.getName();
      clazz.getDeclaredMethod("main", String[].class);
      clazz.getFields();
      clazz.getConstructors();
      clazz.getSuperclass();
      clazz.getPackage();
      clazz.isInterface();
      clazz.getComponentType();
    }
  }
}
