// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiDatabaseGeneratorCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiDatabaseGeneratorCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: apidatabasegenerator [options]",
            "Combines Android API information (--xml) with Android SDK information (--jar)",
            "into an API database file required for compilation.",
            "Multiple inputs of both are supported and any entries not present in both JAR and XML",
            "form is trimmed away.",
            "The options are:",
            "  --help",
            "  -h                      # Print help.",
            "  --version               # Print version.",
            "  --jar <jar-file>        # Android SDK JAR file (e.g., android.jar).",
            "  --xml <xml-file>        # Android API XML file (e.g., api-versions.xml).",
            "  --output <database-file>",
            "                          # Output result in <database-file> (must be a file, not a"
                + " directory).",
            "                          # Defaults to 'api_database.ser'.",
            "  --dont-amend            # By default, the API database is amended with known missing"
                + " information.",
            "                          # This option disables that and processes inputs directly as"
                + " they are.",
            "  --map-diagnostics[:<type>] <from-level> <to-level>",
            "                          # Map diagnostics of <type> (default any) reported as"
                + " <from-level> to",
            "                          # <to-level> where <from-level> and <to-level> are one of"
                + " 'none', 'info',",
            "                          # 'warning', or 'error', and the optional <type> is either"
                + " the simple or",
            "                          # fully qualified Java type name of a diagnostic. If <type>"
                + " is unspecified,",
            "                          # all diagnostics at <from-level> will be mapped. Note that"
                + " fatal compiler",
            "                          # errors cannot be mapped."),
        ApiDatabaseGeneratorCommandParser.getUsageMessage());
  }

  @Test
  public void testParseJarAndXml() throws Exception {
    Path jarFile = temp.newFile("android.jar").toPath();
    Path xmlFile = temp.newFile("api-versions.xml").toPath();
    String[] args = {"--jar", jarFile.toString(), "--xml", xmlFile.toString()};
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    assertEquals(Collections.singletonList(jarFile), command.getJarPaths());
    assertEquals(Collections.singletonList(xmlFile), command.getXmlPaths());
  }

  @Test
  public void testParseMultipleJarsAndXmls() throws Exception {
    Path jar1 = temp.newFile("android1.jar").toPath();
    Path jar2 = temp.newFile("android2.jar").toPath();
    Path xml1 = temp.newFile("api1.xml").toPath();
    Path xml2 = temp.newFile("api2.xml").toPath();
    String[] args = {
      "--jar", jar1.toString(),
      "--jar", jar2.toString(),
      "--xml", xml1.toString(),
      "--xml", xml2.toString()
    };
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    assertEquals(Arrays.asList(jar1, jar2), command.getJarPaths());
    assertEquals(Arrays.asList(xml1, xml2), command.getXmlPaths());
  }
}
