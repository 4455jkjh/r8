// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.apimodel.ApiJarInfo;
import com.android.tools.r8.apimodel.ApiJarReader;
import com.android.tools.r8.apimodel.FieldTypelessReference;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiDatabaseGeneratorTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiDatabaseGeneratorTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testGenerator() throws Exception {
    Path apiVersionsXml =
        writeApiXml(
            "api-versions.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"31\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder()
            .addInputPath(apiVersionsXml)
            .addInputPath(dummyJar)
            .setOutputPath(outputDb)
            .build();

    ApiDatabaseGenerator.run(command);

    assertTrue(Files.exists(outputDb));
    assertTrue(Files.size(outputDb) > 0);
  }

  @Test
  public void testGeneratorWithMergeAndErrors() throws Exception {
    Path apiVersionsXml1 =
        writeApiXml(
            "api-versions-1.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"31\"/>",
            "  </class>");

    Path apiVersionsXml2 =
        writeApiXml(
            "api-versions-2.xml",
            "  <class name=\"android/Foo\" since=\"32\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"30\"/>",
            "    <field name=\"baz\" since=\"33\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .addInputPath(dummyJar)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to duplicate entries");
    } catch (ApiDatabaseGeneratorException e) {
      // Expected.
    }

    diagnosticsHandler.assertErrorsMatch(
        diagnosticMessage(containsString("Duplicate class android.Foo")));
  }

  @Test
  public void testGeneratorWithThreeInputsAndErrors() throws Exception {
    Path apiVersionsXml1 =
        writeApiXml(
            "api-versions-1.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"31\"/>",
            "  </class>");

    Path apiVersionsXml2 =
        writeApiXml(
            "api-versions-2.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"baz()V\" since=\"32\"/>",
            "  </class>");

    Path apiVersionsXml3 =
        writeApiXml(
            "api-versions-3.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"33\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .addInputPath(apiVersionsXml3)
            .addInputPath(dummyJar)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to duplicate entries");
    } catch (ApiDatabaseGeneratorException e) {
      // Expected.
    }

    diagnosticsHandler.assertErrorsMatch(
        diagnosticMessage(equalTo("Duplicate class android.Foo found when merging .xml files.")),
        diagnosticMessage(equalTo("Duplicate class android.Foo found when merging .xml files.")));
  }

  @Test
  public void testGeneratorWithMergeAndSuppressedWarnings() throws Exception {
    Path apiVersionsXml1 =
        writeApiXml(
            "api-versions-1.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"31\"/>",
            "  </class>");

    Path apiVersionsXml2 =
        writeApiXml(
            "api-versions-2.xml",
            "  <class name=\"android/Foo\" since=\"32\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"30\"/>",
            "    <field name=\"baz\" since=\"33\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    String[] args = {
      "--output",
      outputDb.toString(),
      "--map-diagnostics:DuplicateApiDatabaseEntryDiagnostic",
      "error",
      "info",
      apiVersionsXml1.toString(),
      apiVersionsXml2.toString(),
      dummyJar.toString()
    };

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE, diagnosticsHandler)
            .build();

    ApiDatabaseGenerator.run(command);

    assertTrue(Files.exists(outputDb));
    assertTrue(Files.size(outputDb) > 0);

    // Errors and Warnings should be empty because they were mapped to info.
    diagnosticsHandler
        .assertNoErrors()
        .assertNoWarnings()
        // Instead, they should be in the info list.
        .assertInfosMatch(diagnosticMessage(containsString("Duplicate class android.Foo")));
  }

  @Test
  public void testGeneratorWithMergeAndNoneWarnings() throws Exception {
    Path apiVersionsXml1 =
        writeApiXml(
            "api-versions-1.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"31\"/>",
            "  </class>");

    Path apiVersionsXml2 =
        writeApiXml(
            "api-versions-2.xml",
            "  <class name=\"android/Foo\" since=\"32\">",
            "    <extends name=\"java/lang/Object\"/>",
            "    <method name=\"bar()V\" since=\"30\"/>",
            "    <field name=\"baz\" since=\"33\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    String[] args = {
      "--output",
      outputDb.toString(),
      "--map-diagnostics:DuplicateApiDatabaseEntryDiagnostic",
      "error",
      "none",
      apiVersionsXml1.toString(),
      apiVersionsXml2.toString(),
      dummyJar.toString()
    };

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE, diagnosticsHandler)
            .build();

    ApiDatabaseGenerator.run(command);

    assertTrue(Files.exists(outputDb));
    assertTrue(Files.size(outputDb) > 0);

    // Errors, Warnings, and Infos should all be empty because they were mapped to none.
    diagnosticsHandler.assertNoMessages();
  }

  @Test
  public void testGeneratorWithConflictingSupertypes() throws Exception {
    Path apiVersionsXml1 =
        writeApiXml(
            "api-versions-1.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"java/lang/Object\"/>",
            "  </class>");

    Path apiVersionsXml2 =
        writeApiXml(
            "api-versions-2.xml",
            "  <class name=\"android/Foo\" since=\"30\">",
            "    <extends name=\"android/Bar\"/>",
            "  </class>");
    Path dummyJar = temp.newFile("dummy.jar").toPath();

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .addInputPath(dummyJar)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to conflicting supertypes");
    } catch (ApiDatabaseGeneratorException e) {
      assertNotNull(e.getCause());
      assertTrue(
          e.getCause()
              .getMessage()
              .contains("has conflicting supertypes: java.lang.Object, android.Bar"));
    }
  }

  @Test
  public void testJarAndXmlInputs() throws Exception {
    Path xmlFile = temp.newFile("api-versions.xml").toPath();
    Path jarFile = temp.newFile("android.jar").toPath();

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder().addInputPath(xmlFile).addInputPath(jarFile).build();

    assertEquals(1, command.getXmlPaths().size());
    assertEquals(xmlFile, command.getXmlPaths().get(0));
    assertEquals(1, command.getJarPaths().size());
    assertEquals(jarFile, command.getJarPaths().get(0));
  }

  @Test
  public void testInvalidInputExtension() throws Exception {
    Path txtFile = temp.newFile("invalid.txt").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    try {
      ApiDatabaseGeneratorCommand.builder(diagnosticsHandler).addInputPath(txtFile).build();
      fail("Expected Command to fail building due to invalid input extension");
    } catch (ApiDatabaseGeneratorException e) {
      // Expected.
    }

    diagnosticsHandler.assertErrorsMatch(
        diagnosticMessage(containsString("Unsupported input file extension")),
        diagnosticMessage(containsString("At least one SDK JAR")),
        diagnosticMessage(containsString("At least one API XML")));
  }

  private Path writeApiXml(String filename, String... contentLines) throws Exception {
    Path file = temp.newFile(filename).toPath();
    String xml =
        StringUtils.lines(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<api version=\"3\">",
            StringUtils.joinLines(contentLines),
            "</api>");
    FileUtils.writeTextFile(file, xml);
    return file;
  }

  interface TestInterface {
    void instanceMethod();

    static void staticMethod() {}
  }

  static class TestClass {
    int field = 0;

    void instanceMethod() {}

    static void staticMethod() {}
  }

  @Test
  public void testExtractJarInfo() throws Exception {
    Path jar = temp.newFile("test.jar").toPath();
    testForR8(Backend.CF)
        .addProgramClasses(TestInterface.class, TestClass.class)
        .addKeepAllClassesRule()
        .compile()
        .writeToZip(jar);

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder()
            .addInputPath(jar)
            .addInputPath(writeApiXml("dummy.xml")) // Need at least one XML
            .build();

    ApiJarInfo jarInfo = ApiJarReader.extractJarInfo(command);

    ClassReference interfaceRef = Reference.classFromClass(TestInterface.class);
    assertTrue(jarInfo.isClassDefined(interfaceRef));
    assertTrue(jarInfo.isInterface(interfaceRef));

    ClassReference classRef = Reference.classFromClass(TestClass.class);
    assertTrue(jarInfo.isClassDefined(classRef));
    assertFalse(jarInfo.isInterface(classRef));

    TypeReference voidType = Reference.returnTypeFromDescriptor("V");
    List<TypeReference> noArgs = Collections.emptyList();

    MethodReference interfaceInstanceMethod =
        Reference.method(interfaceRef, "instanceMethod", noArgs, voidType);
    assertTrue(jarInfo.isMethodDefined(interfaceInstanceMethod));
    assertFalse(jarInfo.isStatic(interfaceInstanceMethod));

    MethodReference interfaceStaticMethod =
        Reference.method(interfaceRef, "staticMethod", noArgs, voidType);
    assertTrue(jarInfo.isMethodDefined(interfaceStaticMethod));
    assertTrue(jarInfo.isStatic(interfaceStaticMethod));

    MethodReference classInstanceMethod =
        Reference.method(classRef, "instanceMethod", noArgs, voidType);
    assertTrue(jarInfo.isMethodDefined(classInstanceMethod));
    assertFalse(jarInfo.isStatic(classInstanceMethod));

    MethodReference classStaticMethod =
        Reference.method(classRef, "staticMethod", noArgs, voidType);
    assertTrue(jarInfo.isMethodDefined(classStaticMethod));
    assertTrue(jarInfo.isStatic(classStaticMethod));

    FieldTypelessReference field = new FieldTypelessReference(classRef, "field");
    assertTrue(jarInfo.isFieldDefined(field));
  }
}
