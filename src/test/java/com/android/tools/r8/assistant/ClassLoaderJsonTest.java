// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassLoaderJsonTest extends TestBase {

  private static final String MODEL_NAME =
      "com.android.tools.r8.assistant.ClassLoaderJsonTest$Model";

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
        .addProgramClasses(TestClass.class, Model.class)
        .setMinApi(parameters)
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "Not found");
    List<ReflectiveEvent> events =
        new ReflectiveOperationJsonParser(new DexItemFactory()).parse(path);
    assertEquals(2, events.size());
    String[] expectedClassNames = {MODEL_NAME, MODEL_NAME + "Missing"};
    for (int i = 0; i < events.size(); i++) {
      ReflectiveEvent event = events.get(i);
      assertEquals(i, event.getId());
      assertTrue(event.isClassLoaderLoadClass());
      assertEquals(
          expectedClassNames[i], event.asClassLoaderLoadClass().getClassName().getTypeName());
    }
    assertEquals(ImmutableList.of(MODEL_NAME), events.get(0).getResults());
    // The missing class has a request but no returned line.
    assertEquals(ImmutableList.of(), events.get(1).getResults());
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      ClassLoader classLoader = TestClass.class.getClassLoader();
      System.out.println(classLoader.loadClass(MODEL_NAME) == Model.class);
      try {
        classLoader.loadClass(MODEL_NAME + "Missing");
      } catch (ClassNotFoundException e) {
        System.out.println("Not found");
      }
    }
  }

  static class Model {}
}
