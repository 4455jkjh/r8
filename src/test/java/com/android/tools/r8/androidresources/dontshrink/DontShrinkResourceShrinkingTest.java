// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.dontshrink;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontShrinkResourceShrinkingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationMode mode;

  @Parameter(2)
  public boolean optimized;

  @Parameter(3)
  public boolean nonFinalStaticValues;

  @Parameters(name = "{0}, mode: {1}, optimized: {2}, nonFinalStaticValues: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        CompilationMode.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private static final String UNUSED_XML =
      "<dream xmlns:android=\"http://schemas.android.com/apk/res/android\"/>\n";

  private static final Function<Boolean, AndroidTestResource> testResourcesCache =
      memoizeFunction(DontShrinkResourceShrinkingTest::buildTestResources);

  private static String xmlReferencingDrawable(String drawableName) {
    return "<dream xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        + "       android:previewImage=\"@drawable/"
        + drawableName
        + "\"/>\n";
  }

  private static AndroidTestResource buildTestResources(boolean nonFinalStaticValues)
      throws Exception {
    TemporaryFolder resourceTemp = new TemporaryFolder(getStaticTemp().newFolder());
    resourceTemp.create();
    AndroidTestResourceBuilder builder =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class, R.xml.class)
            .addKeepXmlFor("@xml/xml_kept")
            .addXml("xml_used_in_code.xml", xmlReferencingDrawable("drawable_from_code_xml"))
            .addXml("xml_kept.xml", xmlReferencingDrawable("drawable_from_kept_xml"))
            .addXml("unused_xml.xml", UNUSED_XML);
    if (nonFinalStaticValues) {
      builder.useStaticValueForNonFinalFields();
    }
    return builder.build(resourceTemp);
  }

  @Test
  public void testDontShrink() throws Exception {
    runTest(false, false);
  }

  @Test
  public void testDontShrinkAndDontObfuscate() throws Exception {
    assumeTrue(mode.isRelease());
    runTest(true, false);
  }

  @Test
  public void testDontShrinkAndDontObfuscateWithApplyMapping() throws Exception {
    runTest(true, true);
  }

  private void runTest(boolean addDontObfuscate, boolean addApplyMapping) throws Exception {
    // R8Partial does not support legacy resource shrinking.
    assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResourcesCache.apply(nonFinalStaticValues))
        .addKeepMainRule(TestClass.class)
        .setMode(mode)
        .addDontShrink()
        .applyIf(addDontObfuscate, TestShrinkerBuilder::addDontObfuscate)
        .applyIf(addApplyMapping, b -> b.addApplyMapping(""))
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .addResourceShrinkerLogCapture()
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "app_name");
              resourceTableInspector.assertContainsResourceWithName("string", "used_in_code");
              resourceTableInspector.assertContainsResourceWithName("drawable", "used_in_code");
              resourceTableInspector.assertContainsResourceWithName("xml", "xml_used_in_code");
              resourceTableInspector.assertContainsResourceWithName(
                  "drawable", "drawable_from_code_xml");
              resourceTableInspector.assertContainsResourceWithName("xml", "xml_kept");
              resourceTableInspector.assertContainsResourceWithName(
                  "drawable", "drawable_from_kept_xml");
              if (!parameters.isRandomPartialCompilation()) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "drawable", "unused_drawable");
                resourceTableInspector.assertDoesNotContainResourceWithName("xml", "unused_xml");
              }
            })
        .inspectResourceShrinkerLog(
            inspector -> {
              assertTrue(inspector.getFinished());
              if (optimized) {
                inspector.ensureReachableOptimized("string", "app_name");
                inspector.ensureReachableOptimized("string", "used_in_code");
                inspector.ensureReachableOptimized("drawable", "used_in_code");
                inspector.ensureReachableOptimized("xml", "xml_used_in_code");
                inspector.ensureReachableOptimized("drawable", "drawable_from_code_xml");
                inspector.ensureReachableOptimized("xml", "xml_kept");
                inspector.ensureReachableOptimized("drawable", "drawable_from_kept_xml");
                if (!parameters.isRandomPartialCompilation()) {
                  inspector.ensureUnreachableOptimized("string", "unused_string");
                  inspector.ensureUnreachableOptimized("drawable", "unused_drawable");
                  inspector.ensureUnreachableOptimized("xml", "unused_xml");
                }
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  public static class TestClass {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.used_in_code);
        System.out.println(R.drawable.used_in_code);
        System.out.println(R.xml.xml_used_in_code);
      }
    }
  }
}
