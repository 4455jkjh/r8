// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetMember;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReflectiveOperationReturnedJsonTest extends TestBase {

  private static final String MODEL_NAME =
      "com.android.tools.r8.assistant.ReflectiveOperationReturnedJsonTest$Model";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    Path path = temp.newFile().toPath();
    testForAssistant()
        .addProgramClasses(TestClass.class, Model.class, Model.Listener.class)
        .setMinApi(parameters)
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Not found",
            "5",
            "update",
            "2",
            "No constructor",
            "false",
            "Listener is an interface",
            "name",
            "No field",
            "No method");
    List<ReflectiveEvent> events =
        new ReflectiveOperationJsonParser(new DexItemFactory()).parse(path);
    assertEquals(12, events.size());
    for (int i = 0; i < events.size(); i++) {
      assertEquals(i, events.get(i).getId());
    }

    // The swallowed Class.forName failure has a request but no returned line.
    assertTrue(events.get(0).isClassForName());
    assertEquals(ImmutableList.of(), events.get(0).getResults());
    // The successful lookups return the class, also when the result is unused.
    assertTrue(events.get(1).isClassForName());
    assertEquals(ImmutableList.of(MODEL_NAME), events.get(1).getResults());
    assertTrue(events.get(2).isClassForName());
    assertEquals(ImmutableList.of(MODEL_NAME), events.get(2).getResults());

    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELDS, events.get(3).getEventType());
    assertEquals(
        ImmutableList.of(
            ImmutableList.of(
                "int id",
                "int[][] matrix",
                "java.lang.String name",
                "java.lang.String[] tags",
                "long count")),
        events.get(3).getResults());
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHOD, events.get(4).getEventType());
    assertEquals(
        Reference.methodFromMethod(
            Model.class.getDeclaredMethod("update", int.class, String[].class)),
        events.get(4).asClassGetMember().getMember().asDexMethod().asMethodReference());
    assertEquals(
        ImmutableList.of("void update(int,java.lang.String[])"), events.get(4).getResults());
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR, events.get(5).getEventType());
    assertEquals(ImmutableList.of("<init>(int,java.lang.String)"), events.get(5).getResults());
    // The swallowed constructor lookup failure has a request but no returned line.
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR, events.get(6).getEventType());
    assertEquals(ImmutableList.of(), events.get(6).getResults());

    assertTrue(events.get(7).isClassFlagEvent());
    assertEquals(ImmutableList.of(false), events.get(7).getResults());
    assertTrue(events.get(8).isClassFlagEvent());
    assertEquals(ImmutableList.of(true), events.get(8).getResults());

    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELD, events.get(9).getEventType());
    assertEquals(ImmutableList.of("java.lang.String name"), events.get(9).getResults());
    // The swallowed member lookup failures have a request with an unknown member type (***) but no
    // returned line.
    ClassGetMember missingField = events.get(10).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELD, missingField.getEventType());
    assertEquals("missing", missingField.getMember().getName().toString());
    assertEquals("***", missingField.getMember().asDexField().getType().toDescriptorString());
    assertFalse(missingField.hasReturned());
    assertFalse(missingField.hasMemberType());
    assertEquals(MODEL_NAME + ".missing", missingField.getContentsString());
    ClassGetMember missingMethod = events.get(11).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHOD, missingMethod.getEventType());
    assertEquals("missing", missingMethod.getMember().getName().toString());
    assertEquals(
        "***", missingMethod.getMember().asDexMethod().getReturnType().toDescriptorString());
    assertFalse(missingMethod.hasReturned());
    assertFalse(missingMethod.hasMemberType());
    assertEquals(MODEL_NAME + ".missing(int)", missingMethod.getContentsString());
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      try {
        Class.forName("com.android.tools.r8.assistant.ReflectiveOperationReturnedJsonTest$Missing");
      } catch (ClassNotFoundException e) {
        System.out.println("Not found");
      }
      Class.forName(MODEL_NAME);
      Class<?> model = lookup(MODEL_NAME);
      System.out.println(model.getDeclaredFields().length);
      System.out.println(model.getDeclaredMethod("update", int.class, String[].class).getName());
      System.out.println(
          model.getDeclaredConstructor(int.class, String.class).getParameterTypes().length);
      try {
        model.getDeclaredConstructor(String.class);
      } catch (NoSuchMethodException e) {
        System.out.println("No constructor");
      }
      System.out.println(isInterface(model));
      if (Model.Listener.class.isInterface()) {
        System.out.println("Listener is an interface");
      }
      System.out.println(model.getDeclaredField("name").getName());
      try {
        model.getDeclaredField("missing");
      } catch (NoSuchFieldException e) {
        System.out.println("No field");
      }
      try {
        model.getDeclaredMethod("missing", int.class);
      } catch (NoSuchMethodException e) {
        System.out.println("No method");
      }
    }

    static Class<?> lookup(String name) {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException e) {
        return null;
      }
    }

    static boolean isInterface(Class<?> clazz) {
      return clazz.isInterface();
    }
  }

  static class Model {

    static long count;

    int id;
    int[][] matrix;
    String name;
    String[] tags;

    Model(int id, String name) {}

    void update(int id, String[] tags) {}

    interface Listener {}
  }
}
