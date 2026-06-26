// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiDatabaseGeneratorTest extends TestBase {

  @Rule public TemporaryFolder temp = new TemporaryFolder();

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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder()
            .addInputPath(apiVersionsXml)
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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to duplicate entries");
    } catch (ApiDatabaseGeneratorException e) {
      // Expected.
    }

    List<Diagnostic> errors = diagnosticsHandler.getErrors();
    assertEquals(1, errors.size());
    Diagnostic error = errors.get(0);
    String msg = error.getDiagnosticMessage();
    assertTrue(msg.contains("Duplicate class android.Foo"));
    assertTrue(msg.contains("api-versions-1.xml"));
    assertTrue(msg.contains("api-versions-2.xml"));
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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .addInputPath(apiVersionsXml3)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to duplicate entries");
    } catch (ApiDatabaseGeneratorException e) {
      // Expected.
    }

    List<Diagnostic> errors = diagnosticsHandler.getErrors();
    assertEquals(2, errors.size());

    // Verify class errors show the clean chain.
    assertTrue(
        hasError(
            errors,
            "Duplicate class android.Foo found in",
            "api-versions-1.xml",
            "api-versions-2.xml"));
    assertTrue(
        hasError(
            errors,
            "Duplicate class android.Foo found in",
            "api-versions-2.xml",
            "api-versions-3.xml"));
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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    String[] args = {
      "--output",
      outputDb.toString(),
      "--map-diagnostics:DuplicateApiDatabaseEntryDiagnostic",
      "error",
      "info",
      apiVersionsXml1.toString(),
      apiVersionsXml2.toString()
    };

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE, diagnosticsHandler)
            .build();

    ApiDatabaseGenerator.run(command);

    assertTrue(Files.exists(outputDb));
    assertTrue(Files.size(outputDb) > 0);

    // Errors and Warnings should be empty because they were mapped to info.
    assertTrue(
        "Expected 0 errors, got: " + diagnosticsHandler.getErrors().size(),
        diagnosticsHandler.getErrors().isEmpty());
    assertTrue(
        "Expected 0 warnings, got: " + diagnosticsHandler.getWarnings().size(),
        diagnosticsHandler.getWarnings().isEmpty());

    // Instead, they should be in the info list.
    List<Diagnostic> infos = diagnosticsHandler.getInfos();
    assertEquals(1, infos.size());
    Diagnostic info = infos.get(0);
    String msg = info.getDiagnosticMessage();
    assertTrue(msg.contains("Duplicate class android.Foo"));
    assertTrue(msg.contains("api-versions-1.xml"));
    assertTrue(msg.contains("api-versions-2.xml"));
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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    String[] args = {
      "--output",
      outputDb.toString(),
      "--map-diagnostics:DuplicateApiDatabaseEntryDiagnostic",
      "error",
      "none",
      apiVersionsXml1.toString(),
      apiVersionsXml2.toString()
    };

    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE, diagnosticsHandler)
            .build();

    ApiDatabaseGenerator.run(command);

    assertTrue(Files.exists(outputDb));
    assertTrue(Files.size(outputDb) > 0);

    // Errors, Warnings, and Infos should all be empty because they were mapped to none.
    assertTrue(
        "Expected 0 errors, got: " + diagnosticsHandler.getErrors().size(),
        diagnosticsHandler.getErrors().isEmpty());
    assertTrue(
        "Expected 0 warnings, got: " + diagnosticsHandler.getWarnings().size(),
        diagnosticsHandler.getWarnings().isEmpty());
    assertTrue(
        "Expected 0 infos, got: " + diagnosticsHandler.getInfos().size(),
        diagnosticsHandler.getInfos().isEmpty());
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

    Path outputDb = temp.newFile("api_database.ser").toPath();

    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder(diagnosticsHandler)
            .addInputPath(apiVersionsXml1)
            .addInputPath(apiVersionsXml2)
            .setOutputPath(outputDb)
            .build();

    try {
      ApiDatabaseGenerator.run(command);
      fail("Expected API database generation to fail due to conflicting supertypes");
    } catch (ApiDatabaseGeneratorException e) {
      assertTrue(e.getCause() != null);
      assertTrue(
          e.getCause()
              .getMessage()
              .contains("has conflicting supertypes: java.lang.Object, android.Bar"));
    }
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

  private boolean hasError(List<Diagnostic> errors, String prefix, String... sources) {
    for (Diagnostic error : errors) {
      String msg = error.getDiagnosticMessage();
      if (msg.startsWith(prefix)) {
        boolean allMatch = true;
        for (String source : sources) {
          if (!msg.contains(source)) {
            allMatch = false;
            break;
          }
        }
        if (allMatch) {
          return true;
        }
      }
    }
    return false;
  }
}
