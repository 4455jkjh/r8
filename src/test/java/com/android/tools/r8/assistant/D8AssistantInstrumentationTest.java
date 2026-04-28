// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import android.view.MockLibrary;
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
        .addProgramClasses(TestClass.class, MockLibrary.class)
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
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + jsonLog)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputThatMatches(containsString("Hello, world!"));

    String logContent = new String(Files.readAllBytes(jsonLog), StandardCharsets.UTF_8);
    assertThat(logContent, containsString("CLASS_FOR_NAME"));
    assertThat(
        logContent,
        containsString("com.android.tools.r8.assistant.D8AssistantInstrumentationTest$TestClass"));
    assertThat(logContent, containsString("CLASS_GET_NAME"));
    assertThat(logContent, containsString("NAME"));
    assertThat(logContent, containsString("CLASS_GET_DECLARED_METHOD"));
    assertThat(logContent, containsString("CLASS_GET_FIELDS"));
    assertThat(logContent, containsString("CLASS_GET_CONSTRUCTORS"));
    assertThat(logContent, containsString("CLASS_GET_SUPERCLASS"));
    assertThat(logContent, containsString("CLASS_GET_PACKAGE"));
    assertThat(logContent, containsString("CLASS_FLAG"));
    assertThat(logContent, containsString("CLASS_GET_COMPONENT_TYPE"));

    // Verify reflection ON android.* class is IGNORED.
    assertThat(logContent, not(containsString("android.view.MockLibrary")));

    // Verify reflection FROM android.* class is IGNORED.
    assertThat(logContent, not(containsString("android.view.MockLibrary.doReflection")));

    // Verify java.* target is IGNORED.
    assertThat(logContent, not(containsString("java.lang.Object")));
  }

  private void inspect(CodeInspector inspector) {
    // Check that assistant runtime classes are present.
    assertThat(
        inspector.clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle"), isPresent());
    assertThat(
        inspector.clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack"),
        isPresent());
    assertThat(
        inspector
            .clazz("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack")
            .method("com.android.tools.r8.assistant.runtime.ReflectiveOracle$Stack", "createStack"),
        isPresent());
    assertThat(
        inspector.clazz("com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger"),
        isPresent());
    assertThat(
        inspector.clazz(
            "com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver$ClassFlag"),
        isPresent());
    assertThat(
        inspector.clazz(
            "com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver$NameLookupType"),
        isPresent());
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

      // Reflection ON android.* class from app code.
      // MockLibrary is in android.test package.
      Class.forName("android.view.MockLibrary").getName();

      // Reflection FROM android.* class.
      MockLibrary.doReflection();

      // Reflection ON java.* class.
      Object.class.getName();
    }
  }
}
