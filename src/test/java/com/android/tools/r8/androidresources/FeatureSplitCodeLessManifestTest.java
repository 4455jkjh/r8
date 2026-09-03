// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FeatureSplitCodeLessManifestTest extends TestBase {

  private static final String MANIFEST_WITH_HAS_CODE_TRUE =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "          package=\"com.android.tools.r8\">\n"
          + "    <application android:hasCode=\"true\" android:label=\"@string/app_name\">\n"
          + "    </application>\n"
          + "</manifest>\n";

  private static final String MANIFEST_WITH_HAS_CODE_FALSE =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "          package=\"com.android.tools.r8\">\n"
          + "    <application android:hasCode=\"false\" android:label=\"@string/app_name\">\n"
          + "    </application>\n"
          + "</manifest>\n";

  // Resource ID for android.R.attr.hasCode (package 0x01, type 0x01 (attr), entry 0x000c).
  private static final int ANDROID_HAS_CODE_RESOURCE_ID = 0x0101000c;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public static AndroidTestResource getBaseResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(BaseR.string.class)
        .build(temp);
  }

  public static AndroidTestResource getFeatureWithCodeResources(TemporaryFolder temp)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x80)
        .addRClassInitializeWithDefaultValues(FeatureWithCodeR.string.class)
        .build(temp);
  }

  public static AndroidTestResource getFeatureWithoutCodeResources(TemporaryFolder temp)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x81)
        .addRClassInitializeWithDefaultValues(FeatureWithoutCodeR.string.class)
        .build(temp);
  }

  public static AndroidTestResource getFeatureWithExplicitHasCodeTrueResources(TemporaryFolder temp)
      throws Exception {
    return new AndroidTestResourceBuilder()
        .withManifest(MANIFEST_WITH_HAS_CODE_TRUE)
        .addStringValue("app_name", "App")
        .setPackageId(0x82)
        .addRClassInitializeWithDefaultValues(FeatureExplicitHasCodeTrueR.string.class)
        .build(temp);
  }

  public static AndroidTestResource getFeatureWithExplicitHasCodeFalseResources(
      TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withManifest(MANIFEST_WITH_HAS_CODE_FALSE)
        .addStringValue("app_name", "App")
        .setPackageId(0x83)
        .addRClassInitializeWithDefaultValues(FeatureExplicitHasCodeFalseR.string.class)
        .build(temp);
  }

  @Test
  public void testFeatureCodeStrippedSetsHasCodeFalse() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureWithCodeTemp = ToolHelper.getTemporaryFolderForTest();
    featureWithCodeTemp.create();
    TemporaryFolder featureWithoutCodeTemp = ToolHelper.getTemporaryFolderForTest();
    featureWithoutCodeTemp.create();

    String featureWithCodeName = "feature_with_code";
    String featureWithoutCodeName = "feature_without_code";

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(BaseMain.class)
            .addAndroidResources(getBaseResources(temp))
            .addFeatureSplitAndroidResources(
                getFeatureWithCodeResources(featureWithCodeTemp),
                featureWithCodeName,
                FeatureWithCodeClass.class)
            .addFeatureSplitAndroidResources(
                getFeatureWithoutCodeResources(featureWithoutCodeTemp),
                featureWithoutCodeName,
                FeatureWithoutCodeClass.class)
            .enableOptimizedShrinking()
            .addKeepMainRule(BaseMain.class)
            .addKeepMainRule(FeatureWithCodeClass.class)
            // Note: FeatureWithoutCodeClass is NOT kept, so tree shaking eliminates all its code.
            .compile();

    // Check base manifest
    XmlNode baseManifest = getManifestXmlNode(compileResult.getResourceShrinkerOutput());
    assertTrue(getEffectiveHasCode(baseManifest));
    validateWithBundletoolIfAvailable(baseManifest, true);

    // Check feature with kept code: hasCode is not set to false
    XmlNode featureWithCodeManifest =
        getManifestXmlNode(compileResult.getResourceShrinkerOutputForFeature(featureWithCodeName));
    assertTrue(getEffectiveHasCode(featureWithCodeManifest));
    validateWithBundletoolIfAvailable(featureWithCodeManifest, true);

    // Check feature without kept code: hasCode is automatically rewritten to false
    XmlNode featureWithoutCodeManifest =
        getManifestXmlNode(
            compileResult.getResourceShrinkerOutputForFeature(featureWithoutCodeName));
    assertFalse(getEffectiveHasCode(featureWithoutCodeManifest));
    assertHasCodeFalseAttribute(featureWithoutCodeManifest);
    validateWithBundletoolIfAvailable(featureWithoutCodeManifest, false);

    // Also verify that the unused resource in feature_without_code was shrunk
    compileResult.inspectShrunkenResourcesForFeature(
        resourceTableInspector ->
            resourceTableInspector.assertDoesNotContainResourceWithName(
                "string", "feature_without_code_unused"),
        featureWithoutCodeName);
  }

  @Test
  public void testExplicitHasCodeTrueRewrittenToFalse() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureTemp = ToolHelper.getTemporaryFolderForTest();
    featureTemp.create();

    String featureName = "feature_explicit_has_code";

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(BaseMain.class)
            .addAndroidResources(getBaseResources(temp))
            .addFeatureSplitAndroidResources(
                getFeatureWithExplicitHasCodeTrueResources(featureTemp),
                featureName,
                FeatureWithoutCodeClass.class)
            .enableOptimizedShrinking()
            .addKeepMainRule(BaseMain.class)
            // FeatureWithoutCodeClass not kept
            .compile();

    XmlNode featureManifest =
        getManifestXmlNode(compileResult.getResourceShrinkerOutputForFeature(featureName));
    assertFalse(getEffectiveHasCode(featureManifest));
    assertHasCodeFalseAttribute(featureManifest);
    validateWithBundletoolIfAvailable(featureManifest, false);
  }

  @Test
  public void testCodeLessFeatureFromBeginning() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureTemp = ToolHelper.getTemporaryFolderForTest();
    featureTemp.create();

    String featureName = "feature_codeless";

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(BaseMain.class)
            .addAndroidResources(getBaseResources(temp))
            // No classes passed at all
            .addFeatureSplitAndroidResources(
                getFeatureWithoutCodeResources(featureTemp), featureName)
            .enableOptimizedShrinking()
            .addKeepMainRule(BaseMain.class)
            .compile();

    XmlNode featureManifest =
        getManifestXmlNode(compileResult.getResourceShrinkerOutputForFeature(featureName));
    assertFalse(getEffectiveHasCode(featureManifest));
    assertHasCodeFalseAttribute(featureManifest);
    validateWithBundletoolIfAvailable(featureManifest, false);
  }

  @Test
  public void testExplicitHasCodeFalseRetained() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    TemporaryFolder featureTemp = ToolHelper.getTemporaryFolderForTest();
    featureTemp.create();

    String featureName = "feature_explicit_has_code_false";

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(BaseMain.class)
            .addAndroidResources(getBaseResources(temp))
            .addFeatureSplitAndroidResources(
                getFeatureWithExplicitHasCodeFalseResources(featureTemp),
                featureName,
                FeatureWithoutCodeClass.class)
            .enableOptimizedShrinking()
            .addKeepMainRule(BaseMain.class)
            .compile();

    XmlNode featureManifest =
        getManifestXmlNode(compileResult.getResourceShrinkerOutputForFeature(featureName));
    assertFalse(getEffectiveHasCode(featureManifest));
    assertHasCodeFalseAttribute(featureManifest);
    validateWithBundletoolIfAvailable(featureManifest, false);
  }

  private static XmlNode getManifestXmlNode(Path resourceZip) throws IOException {
    assertNotNull(resourceZip);
    byte[] manifestBytes = ZipUtils.readSingleEntry(resourceZip, "AndroidManifest.xml");
    return XmlNode.parseFrom(manifestBytes);
  }

  private static boolean getEffectiveHasCode(XmlNode xmlNode) {
    XmlElement appElement = getApplicationElement(xmlNode);
    if (appElement == null) {
      return true;
    }
    for (XmlAttribute attr : appElement.getAttributeList()) {
      if (attr.getName().equals("hasCode")
          && (attr.getNamespaceUri().equals("http://schemas.android.com/apk/res/android")
              || attr.getResourceId() == ANDROID_HAS_CODE_RESOURCE_ID)) {
        if (attr.hasCompiledItem() && attr.getCompiledItem().hasPrim()) {
          return attr.getCompiledItem().getPrim().getBooleanValue();
        }
        return Boolean.parseBoolean(attr.getValue());
      }
    }
    return true;
  }

  private static void assertHasCodeFalseAttribute(XmlNode xmlNode) {
    XmlElement appElement = getApplicationElement(xmlNode);
    assertNotNull(appElement);
    boolean found = false;
    for (XmlAttribute attr : appElement.getAttributeList()) {
      if (attr.getName().equals("hasCode")
          && (attr.getNamespaceUri().equals("http://schemas.android.com/apk/res/android")
              || attr.getResourceId() == ANDROID_HAS_CODE_RESOURCE_ID)) {
        assertEquals("http://schemas.android.com/apk/res/android", attr.getNamespaceUri());
        assertEquals(ANDROID_HAS_CODE_RESOURCE_ID, attr.getResourceId());
        assertEquals("false", attr.getValue());
        assertTrue(attr.hasCompiledItem());
        assertTrue(attr.getCompiledItem().hasPrim());
        assertFalse(attr.getCompiledItem().getPrim().getBooleanValue());
        found = true;
        break;
      }
    }
    assertTrue("hasCode attribute was not found in <application>", found);
  }

  private static XmlElement getApplicationElement(XmlNode xmlNode) {
    if (!xmlNode.hasElement()) {
      return null;
    }
    for (XmlNode child : xmlNode.getElement().getChildList()) {
      if (child.hasElement() && child.getElement().getName().equals("application")) {
        return child.getElement();
      }
    }
    return null;
  }

  private static void validateWithBundletoolIfAvailable(XmlNode xmlNode, boolean expectedHasCode)
      throws Exception {
    Path bundletoolJar =
        Paths.get(
            ToolHelper.THIRD_PARTY_DIR,
            "bundletool",
            "bundletool-1.11.0",
            "bundletool-all-1.11.0.jar");
    if (!Files.exists(bundletoolJar)) {
      return;
    }
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {bundletoolJar.toUri().toURL()},
            FeatureSplitCodeLessManifestTest.class.getClassLoader())) {
      Class<?> manifestClass =
          loader.loadClass("com.android.tools.build.bundletool.model.AndroidManifest");
      Method createMethod = manifestClass.getMethod("create", XmlNode.class);
      Object manifestObj = createMethod.invoke(null, xmlNode);
      Method getEffectiveHasCodeMethod = manifestClass.getMethod("getEffectiveHasCode");
      boolean effectiveHasCode = (boolean) getEffectiveHasCodeMethod.invoke(manifestObj);
      assertEquals(expectedHasCode, effectiveHasCode);
    }
  }

  public static class BaseMain {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(BaseR.string.base_used);
      }
    }
  }

  public static class BaseR {
    public static class string {
      public static int base_used;
    }
  }

  public static class FeatureWithCodeClass {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(FeatureWithCodeR.string.feature_with_code_used);
      }
    }
  }

  public static class FeatureWithCodeR {
    public static class string {
      public static int feature_with_code_used;
    }
  }

  public static class FeatureWithoutCodeClass {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(FeatureWithoutCodeR.string.feature_without_code_unused);
      }
    }
  }

  public static class FeatureWithoutCodeR {
    public static class string {
      public static int feature_without_code_unused;
    }
  }

  public static class FeatureExplicitHasCodeTrueR {
    public static class string {
      public static int feature_explicit_has_code_unused;
    }
  }

  public static class FeatureExplicitHasCodeFalseR {
    public static class string {
      public static int feature_explicit_has_code_false_unused;
    }
  }
}
