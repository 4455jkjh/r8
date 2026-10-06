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
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Reflection from library code in the app, such as androidx or kotlinx code, is only ignored if it
// targets library classes.
@RunWith(Parameterized.class)
public class LibraryCallerJsonTest extends TestBase {

  private static final String LIBRARY_CALLER_NAME = "androidx.fragment.app.LibraryCaller";
  private static final String MODEL_NAME =
      "com.android.tools.r8.assistant.LibraryCallerJsonTest$Model";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    String libraryCallerDescriptor = "Landroidx/fragment/app/LibraryCaller;";
    Path path = temp.newFile().toPath();
    testForAssistant()
        .addProgramClasses(Model.class)
        .addProgramClassFileData(
            transformer(TestClass.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(LibraryCaller.class), libraryCallerDescriptor)
                .transform(),
            transformer(LibraryCaller.class)
                .setClassDescriptor(libraryCallerDescriptor)
                .removeInnerClasses()
                .transform())
        .setMinApi(parameters)
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "1");
    List<ReflectiveEvent> events =
        new ReflectiveOperationJsonParser(new DexItemFactory()).parse(path);
    assertEquals(3, events.size());

    // The library caller reflects on an app class.
    assertTrue(events.get(0).isClassForName());
    assertEquals(MODEL_NAME, events.get(0).asClassForName().getClassName().getTypeName());
    assertTrue(events.get(0).getStack()[0].startsWith(LIBRARY_CALLER_NAME + ".instantiate("));
    assertEquals(ImmutableList.of(MODEL_NAME), events.get(0).getResults());
    assertEquals(ReflectiveEventType.CLASS_GET_CONSTRUCTOR, events.get(1).getEventType());
    assertEquals(
        MODEL_NAME, events.get(1).asClassGetMember().getMember().getHolderType().toSourceString());
    assertTrue(events.get(1).getStack()[0].startsWith(LIBRARY_CALLER_NAME + ".instantiate("));
    assertEquals(ImmutableList.of("<init>()"), events.get(1).getResults());

    // The library caller also reflects on itself, which is not logged, but app code reflecting on
    // the library class is.
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHODS, events.get(2).getEventType());
    assertEquals(
        LIBRARY_CALLER_NAME, events.get(2).asClassGetMembers().getHolder().toSourceString());
    assertTrue(events.get(2).getStack()[0].startsWith(TestClass.class.getName() + ".main("));
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      ClassLoader classLoader = TestClass.class.getClassLoader();
      System.out.println(LibraryCaller.instantiate(classLoader, MODEL_NAME) instanceof Model);
      System.out.println(LibraryCaller.class.getDeclaredMethods().length);
    }
  }

  // Renamed to androidx.fragment.app.LibraryCaller. Like FragmentFactory, this instantiates an app
  // class by name.
  public static class LibraryCaller {

    public static Object instantiate(ClassLoader classLoader, String className) throws Exception {
      Class<?> clazz = Class.forName(className, false, classLoader);
      Class.forName("[Ljava.lang.String;", false, classLoader);
      Class.forName("[L" + LibraryCaller.class.getName() + ";", false, classLoader);
      LibraryCaller.class.getDeclaredConstructors();
      return clazz.getConstructor().newInstance();
    }
  }

  public static class Model {}
}
