// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.IntBox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidApiVersionsXmlParserTest extends TestBase {

  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.API_DATABASE_LEVEL;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiVersionsXmlParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testParsedApiVersionsXmlSize() throws Exception {
    // This tests makes a rudimentary check on the number of classes, fields and methods in
    // api-versions.xml to ensure that the runtime tests do not vacuously succeed.
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParserChecked.parse(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL),
            ToolHelper.getAndroidJar(API_LEVEL),
            API_LEVEL,
            false);
    IntBox numberOfFields = new IntBox(0);
    IntBox numberOfMethods = new IntBox(0);
    parsedApiClasses.forEach(
        apiClass -> {
          numberOfFields.increment(apiClass.fieldCount());
          numberOfMethods.increment(apiClass.methodCount());
        });
    // These numbers will change when updating api-versions.xml.
    assertEquals(6_498, parsedApiClasses.size());
    assertEquals(32_818, numberOfFields.get());
    assertEquals(49_867, numberOfMethods.get());
  }

  private static final String sampleVersion4ApiVersionsXml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<api version=\"4\">\n"
          + "        <sdk id=\"36\" shortname=\"B-ext\" name=\"Baklava Extensions\"\n"
          + "             reference=\"android/os/Build$VERSION_CODES$BAKLAVA\"/>\n"
          + "\n"
          + "        <!-- This class was introduced in Android R -->\n"
          + "        <class name=\"android/os/ext/SdkExtensions\" since=\"30.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <!-- This method was introduced in Android S. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 2. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <method name=\"getAllExtensionVersions()Ljava/util/Map;\""
          + " since=\"31.0\"\n"
          + "                        sdks=\"30:2,31:2,33:4,34:7,35:12,36:16,0:31.0\"/>\n"
          + "                <method name=\"getExtensionVersion(I)I\"/>\n"
          + "                <!-- This field was introduced in Android U. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 4. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <field name=\"AD_SERVICES\" since=\"34.0\""
          + " sdks=\"30:4,31:4,33:4,34:7,35:12,36:16,0:34.0\"/>\n"
          + "        </class>\n"
          + "\n"
          + "        <!-- This class was introduced in Baklava. It does not exist in any SDK"
          + " extension. -->\n"
          + "        <class name=\"android/os/FromBaklava\" since=\"36.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "        <class name=\"android/os/AlsoFromBaklava\" since=\"36\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "        <class name=\"android/os/FromBaklava1\" since=\"36.1\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "</api>\n";

  static class SdkExtensionsStub {
    @SuppressWarnings("unused")
    int AD_SERVICES;
  }

  static class TemplateClass {}

  private static void mockAndroidJarForSampleVersion4ApiVersionsXml(Path outputPath)
      throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputPath))) {
      ZipUtils.writeToZipStream(
          out,
          "android/os/ext/SdkExtensions.class",
          transformer(SdkExtensionsStub.class)
              .setClassDescriptor("Landroid/os/ext/SdkExtensions;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/FromBaklava.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/FromBaklava;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/AlsoFromBaklava.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/AlsoFromBaklava;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/FromBaklava1.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/FromBaklava1;")
              .transform(),
          ZipEntry.STORED);
    }
  }

  @Test
  public void testApiVersionsXmlVersion4() throws Exception {
    Path apiVersionsXml = temp.newFile("api-versions.xml").toPath();
    FileUtils.writeTextFile(apiVersionsXml, sampleVersion4ApiVersionsXml);
    Path apiLibrary = temp.newFile("android.jar").toPath();
    mockAndroidJarForSampleVersion4ApiVersionsXml(apiLibrary);
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParserChecked.parse(apiVersionsXml, apiLibrary, API_LEVEL, true);
    assertEquals(4, parsedApiClasses.size());
    ParsedApiClass sdkExtension = parsedApiClasses.get(0);
    assertEquals(
        sdkExtension.getClassReference(),
        Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"));
    assertEquals(AndroidApiLevel.R, sdkExtension.getRange().intro);
    sdkExtension.forEachMethod(
        (method, apiRange) -> {
          if (apiRange.intro.equals(AndroidApiLevel.R)) {
            assertEquals(
                method,
                Reference.methodFromDescriptor(
                    "Landroid/os/ext/SdkExtensions;", "getExtensionVersion", "(I)I"));
          } else if (apiRange.intro.equals(AndroidApiLevel.S)) {
            assertEquals(
                method,
                Reference.methodFromDescriptor(
                    "Landroid/os/ext/SdkExtensions;",
                    "getAllExtensionVersions",
                    "()Ljava/util/Map;"));
          } else {
            fail();
          }
        });
    sdkExtension.forEachField(
        (field, apiRange) -> {
          if (apiRange.intro.equals(AndroidApiLevel.U)) {
            assertEquals(
                field,
                new FieldTypelessReference(
                    Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"),
                    "AD_SERVICES"));
          } else {
            fail();
          }
        });
    checkMockClass(parsedApiClasses.get(1), "Landroid/os/FromBaklava;", AndroidApiLevel.BAKLAVA);
    checkMockClass(
        parsedApiClasses.get(2), "Landroid/os/AlsoFromBaklava;", AndroidApiLevel.BAKLAVA);
    checkMockClass(parsedApiClasses.get(3), "Landroid/os/FromBaklava1;", AndroidApiLevel.BAKLAVA_1);
  }

  private static void checkMockClass(
      ParsedApiClass apiClass, String descriptor, AndroidApiLevel apiLevel) {
    assertEquals(apiClass.getClassReference(), Reference.classFromDescriptor(descriptor));
    assertEquals(apiLevel, apiClass.getRange().intro);
    apiClass.forEachMethod(
        (method, apiRange) -> {
          if (apiRange.intro.equals(apiLevel)) {
            assertEquals(method, Reference.methodFromDescriptor(descriptor, "foo", "(I)V"));
          } else {
            fail();
          }
        });
    assertEquals(1, apiClass.fieldCount() + apiClass.methodCount());
  }
}
