// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssistantTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSupertypesJsonTest extends TestBase {

  // The runtime class of ParameterizedType on ART.
  private static final String PARAMETERIZED_TYPE = "libcore.reflect.ParameterizedTypeImpl";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    List<ReflectiveEvent> events =
        runAssistant(
            testForAssistant()
                .addProgramClasses(TestClass.class, Box.class, Item.class, ItemBox.class),
            "true",
            "true",
            "2",
            "2",
            "0");
    // A ParameterizedType is summarized by its raw type, and arrays keep the declaration order.
    assertEquals(ImmutableList.of(Box.class.getName() + "<...>"), events.get(0).getResults());
    assertEquals(ImmutableList.of("java.lang.Object"), events.get(1).getResults());
    assertEquals(
        ImmutableList.of(ImmutableList.of("java.lang.Comparable<...>", "java.lang.Runnable")),
        events.get(2).getResults());
    assertEquals(
        ImmutableList.of(ImmutableList.of("java.lang.Runnable", "java.lang.Cloneable")),
        events.get(3).getResults());
    assertEquals(ImmutableList.of(ImmutableList.of()), events.get(4).getResults());
  }

  @Test
  public void testWithoutSignatures() throws Exception {
    // Without -keepattributes Signature R8 removes the signatures, so the generic supertypes are
    // plain classes.
    Path r8Output =
        testForR8(parameters)
            .addProgramClasses(TestClass.class, Box.class, Item.class, ItemBox.class)
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRules(Box.class, Item.class, ItemBox.class)
            .compile()
            .writeToZip();
    List<ReflectiveEvent> events =
        runAssistant(testForAssistant().addProgramFiles(r8Output), "false", "true", "2", "2", "0");
    assertEquals(ImmutableList.of(Box.class.getName()), events.get(0).getResults());
    assertEquals(
        ImmutableList.of(ImmutableList.of("java.lang.Comparable", "java.lang.Runnable")),
        events.get(2).getResults());
  }

  @Test
  public void testMissingRawType() throws Exception {
    // Resolving a missing raw type throws, so the ParameterizedType is summarized by its runtime
    // class.
    List<ReflectiveEvent> events =
        runAssistant(
            testForAssistant()
                .addProgramClasses(TestClass.class, Box.class, Item.class)
                .addProgramClassFileData(
                    transformer(ItemBox.class)
                        .setGenericSignature(signature -> signature.replace("$Box<", "$Missing<"))
                        .transform()),
            "true",
            "true",
            "2",
            "2",
            "0");
    assertEquals(ImmutableList.of(PARAMETERIZED_TYPE), events.get(0).getResults());
  }

  private List<ReflectiveEvent> runAssistant(AssistantTestBuilder builder, String... expectedOutput)
      throws Exception {
    Path path = temp.newFile().toPath();
    builder
        .setMinApi(parameters)
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(expectedOutput);
    List<ReflectiveEvent> events =
        new ReflectiveOperationJsonParser(new DexItemFactory()).parse(path);
    assertEquals(5, events.size());
    Class<?>[] expectedTypes = {ItemBox.class, Item.class, ItemBox.class, Item.class, Box.class};
    for (int i = 0; i < events.size(); i++) {
      ReflectiveEvent event = events.get(i);
      assertEquals(i, event.getId());
      assertEquals(
          i < 2
              ? ReflectiveEventType.CLASS_GET_GENERIC_SUPERCLASS
              : ReflectiveEventType.CLASS_GET_GENERIC_INTERFACES,
          event.getEventType());
      assertTrue(event.isClassGetGenericSupertypes());
      assertEquals(
          expectedTypes[i].getName(),
          event.asClassGetGenericSupertypes().getType().toSourceString());
    }
    return events;
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(ItemBox.class.getGenericSuperclass() instanceof ParameterizedType);
      System.out.println(Item.class.getGenericSuperclass() == Object.class);
      System.out.println(ItemBox.class.getGenericInterfaces().length);
      System.out.println(Item.class.getGenericInterfaces().length);
      System.out.println(Box.class.getGenericInterfaces().length);
    }
  }

  static class Box<T> {}

  static class Item implements Runnable, Cloneable {

    @Override
    public void run() {}
  }

  static class ItemBox extends Box<Item> implements Comparable<ItemBox>, Runnable {

    @Override
    public int compareTo(ItemBox other) {
      return 0;
    }

    @Override
    public void run() {}
  }
}
