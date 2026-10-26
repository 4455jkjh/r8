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
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.postprocessing.model.ResourceBundleGetBundle;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceBundleJsonTest extends TestBase {

  private static final String BUNDLE_NAME =
      "com.android.tools.r8.assistant.ResourceBundleJsonTest$Messages";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Before 7.0 the Control based getBundle does not fall back to the parent bundle once the
    // plain getBundle has cached that the bundle for the default locale is missing.
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withMaximumApiLevel()
        .build();
  }

  @Test
  public void test() throws Exception {
    Path path = temp.newFile().toPath();
    testForAssistant()
        .addProgramClasses(TestClass.class, Messages.class, Messages_de.class)
        .setMinApi(parameters)
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Hello", "Hello", "Hallo", "Hallo", "Hallo", "Hallo", "Missing");
    List<ReflectiveEvent> events =
        new ReflectiveOperationJsonParser(new DexItemFactory()).parse(path);
    assertEquals(7, events.size());
    String[] expectedLocales = {null, null, "de", "de", "de", "de", null};
    for (int i = 0; i < events.size(); i++) {
      ReflectiveEvent event = events.get(i);
      assertEquals(i, event.getId());
      assertTrue(event.isResourceBundleGetBundle());
      ResourceBundleGetBundle getBundle = event.asResourceBundleGetBundle();
      assertEquals(i < 6 ? BUNDLE_NAME : BUNDLE_NAME + "Missing", getBundle.getBaseName());
      assertEquals(expectedLocales[i], getBundle.getLocale());
    }
    // The result is the runtime class of the bundle, which depends on the locale.
    for (int i = 0; i < 2; i++) {
      assertEquals(ImmutableList.of(BUNDLE_NAME), events.get(i).getResults());
    }
    for (int i = 2; i < 6; i++) {
      assertEquals(ImmutableList.of(BUNDLE_NAME + "_de"), events.get(i).getResults());
    }
    // The missing bundle has a request but no returned line.
    assertEquals(ImmutableList.of(), events.get(6).getResults());
    for (String missingBaseName : new String[] {null, "", "null"}) {
      assertFalse(
          new ResourceBundleGetBundle(
                  ReflectiveEventType.RESOURCE_BUNDLE_GET_BUNDLE,
                  null,
                  new String[] {missingBaseName, null, null})
              .isKeptBy(null));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      ClassLoader classLoader = TestClass.class.getClassLoader();
      ResourceBundle.Control control =
          ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_CLASS);
      print(ResourceBundle.getBundle(BUNDLE_NAME));
      print(ResourceBundle.getBundle(BUNDLE_NAME, control));
      print(ResourceBundle.getBundle(BUNDLE_NAME, Locale.GERMAN));
      print(ResourceBundle.getBundle(BUNDLE_NAME, Locale.GERMAN, control));
      print(ResourceBundle.getBundle(BUNDLE_NAME, Locale.GERMAN, classLoader));
      print(ResourceBundle.getBundle(BUNDLE_NAME, Locale.GERMAN, classLoader, control));
      try {
        ResourceBundle.getBundle(BUNDLE_NAME + "Missing");
      } catch (MissingResourceException e) {
        System.out.println("Missing");
      }
    }

    static void print(ResourceBundle bundle) {
      System.out.println(bundle.getString("greeting"));
    }
  }

  public static class Messages extends ListResourceBundle {

    @Override
    protected Object[][] getContents() {
      return new Object[][] {{"greeting", "Hello"}};
    }
  }

  public static class Messages_de extends ListResourceBundle {

    @Override
    protected Object[][] getContents() {
      return new Object[][] {{"greeting", "Hallo"}};
    }
  }
}
