// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.ExtractR8Rules;
import com.android.tools.r8.ExtractR8RulesCommand;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.LibraryProvidedProguardRulesTestUtils;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryProvidedProguardRulesR8SpecificTest
    extends LibraryProvidedProguardRulesTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryType libraryType;

  @Parameter(2)
  public ProviderType providerType;

  @Parameters(name = "{0}, libraryType: {1}, providerType: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        ImmutableList.of(LibraryType.JAR_WITH_RULES),
        ProviderType.values());
  }

  private static final String EXPECTED_A = StringUtils.lines("-keep class A1", "-keep class A2");

  private static final String EXPECTED_B = StringUtils.lines("-keep class B1", "-keep class B2");

  private static final String EXPECTED_C = StringUtils.lines("-keep class C1", "-keep class C2");

  private static final String EXPECTED_D = StringUtils.lines("-keep class D1", "-keep class D2");

  private static final String EXPECTED_E = StringUtils.lines("-keep class E1", "-keep class E2");

  private static final String EXPECTED_X = StringUtils.lines("-keep class X1", "-keep class X2");

  private Path buildLibrary() throws Exception {
    ZipBuilder jarBuilder =
        ZipBuilder.builder(temp.newFile(libraryType.isAar() ? "classes.jar" : "test.jar").toPath());
    if (libraryType.hasRulesInJar()) {
      jarBuilder.addText("META-INF/com.android.tools/r8/test1.pro", "-keep class A1");
      jarBuilder.addText("META-INF/com.android.tools/r8/test2.pro", "-keep class A2");
      jarBuilder.addText("META-INF/com.android.tools/r8-from-4.0.0/test1.pro", "-keep class B1");
      jarBuilder.addText("META-INF/com.android.tools/r8-from-4.0.0/test2.pro", "-keep class B2");
      jarBuilder.addText("META-INF/com.android.tools/r8-upto-8.1.0/test1.pro", "-keep class C1");
      jarBuilder.addText("META-INF/com.android.tools/r8-upto-8.1.0/test2.pro", "-keep class C2");
      jarBuilder.addText(
          "META-INF/com.android.tools/r8-from-5.0.0-upto-8.0.0/test1.pro", "-keep class D1");
      jarBuilder.addText(
          "META-INF/com.android.tools/r8-from-5.0.0-upto-8.0.0/test2.pro", "-keep class D2");
      jarBuilder.addText("META-INF/com.android.tools/r8-from-10.5.0/test1.pro", "-keep class E1");
      jarBuilder.addText("META-INF/com.android.tools/r8-from-10.5.0/test2.pro", "-keep class E2");
      jarBuilder.addText("META-INF/proguard/test1.pro", "-keep class X1");
      jarBuilder.addText("META-INF/proguard/test2.pro", "-keep class X2");
    }
    if (libraryType.isAar()) {
      // TODO(b/228319861): Also test AARs.
      fail("Not tested");
      return null;
    } else {
      return jarBuilder.build();
    }
  }

  private Path buildLibraryProguardOnlyRules(String directory) throws Exception {
    ZipBuilder jarBuilder =
        ZipBuilder.builder(
            temp.newFolder().toPath().resolve(libraryType.isAar() ? "classes.jar" : "test.jar"));
    if (libraryType.hasRulesInJar()) {
      jarBuilder.addText("META-INF/" + directory + "/test1.pro", "-keep class X1");
      jarBuilder.addText("META-INF/" + directory + "/test2.pro", "-keep class X2");
    }
    if (libraryType.isAar()) {
      // TODO(b/228319861): Also test AARs.
      fail("Not tested");
      return null;
    } else {
      return jarBuilder.build();
    }
  }

  private Path buildLibraryR8VersionAgnosticOnlyRules() throws Exception {
    ZipBuilder jarBuilder =
        ZipBuilder.builder(
            temp.newFolder().toPath().resolve(libraryType.isAar() ? "classes.jar" : "test.jar"));
    if (libraryType.hasRulesInJar()) {
      jarBuilder.addText("META-INF/com.android.tools/r8/test1.pro", "-keep class A1");
      jarBuilder.addText("META-INF/com.android.tools/r8/test2.pro", "-keep class A2");
    }
    if (libraryType.isAar()) {
      // TODO(b/228319861): Also test AARs.
      fail("Not tested");
      return null;
    } else {
      return jarBuilder.build();
    }
  }

  private Path buildLibraryProguardOnlyRules() throws Exception {
    return buildLibraryProguardOnlyRules("proguard");
  }

  private void runTest(SemanticVersion compilerVersion, String expected) throws Exception {
    Path library = buildLibrary();
    testForR8(Backend.DEX)
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(AndroidApiLevel.B)
        .setFakeCompilerVersion(compilerVersion)
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspectProguardConfiguration(
            configuration ->
                assertEquals(
                    expected,
                    stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));

    // Test the standalone rules extractor (test independent of providerType).
    if (providerType == ProviderType.API) {
      StringBuilder resultBuilder = new StringBuilder();
      ExtractR8RulesCommand.Builder builder = ExtractR8RulesCommand.builder();
      ExtractR8RulesCommand command =
          builder
              .addProgramFiles(library)
              .setRulesConsumer((s, h) -> resultBuilder.append(s))
              .setCompilerVersion(compilerVersion)
              .build();
      ExtractR8Rules.run(command);
      assertEquals(expected, resultBuilder.toString());
    }
  }

  private static String stripCommentsAndInJars(String configuration, boolean expectOneInJar) {
    int expectedInJars = expectOneInJar ? 1 : 0;
    int foundInJars = 0;
    List<String> lines = StringUtils.splitLines(configuration);
    List<String> filtered = new ArrayList<>(lines.size());
    for (String line : lines) {
      if (line.trim().startsWith("-injars ")) {
        if (++foundInJars > expectedInJars) {
          fail("Unexpected: " + line);
        }
      } else if (!line.trim().startsWith("#")) {
        filtered.add(line);
      }
    }
    assertEquals(expectedInJars, foundInJars);
    return StringUtils.lines(filtered);
  }

  @Test
  public void runTestVersion3() throws Exception {
    runTest(
        SemanticVersion.create(3, 0, 0), StringUtils.lines(EXPECTED_A.trim(), EXPECTED_C.trim()));
  }

  @Test
  public void runTestVersion4() throws Exception {
    runTest(
        SemanticVersion.create(4, 0, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim()));
  }

  @Test
  public void runTestVersion5() throws Exception {
    runTest(
        SemanticVersion.create(5, 0, 0),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim(), EXPECTED_D.trim()));
  }

  @Test
  public void runTestVersion7_99_99() throws Exception {
    runTest(
        SemanticVersion.create(7, 99, 99),
        StringUtils.lines(
            EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim(), EXPECTED_D.trim()));
  }

  @Test
  public void runTestVersion8() throws Exception {
    runTest(
        SemanticVersion.create(8, 0, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim()));
  }

  @Test
  public void runTestVersion8_0_99() throws Exception {
    runTest(
        SemanticVersion.create(8, 0, 99),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_C.trim()));
  }

  @Test
  public void runTestVersion8_1() throws Exception {
    runTest(
        SemanticVersion.create(8, 1, 0), StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim()));
  }

  @Test
  public void runTestVersion8_2() throws Exception {
    runTest(
        SemanticVersion.create(8, 2, 0), StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim()));
  }

  @Test
  public void runTestVersion10() throws Exception {
    runTest(
        SemanticVersion.create(10, 0, 0), StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim()));
  }

  @Test
  public void runTestVersion10_5() throws Exception {
    runTest(
        SemanticVersion.create(10, 5, 0),
        StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_E.trim()));
  }

  @Test
  public void runTestVersionMainR8VersionSpecificRules() throws Exception {
    if (!Version.isMainVersion()) {
      return;
    }
    Path library = buildLibrary();
    testForR8(Backend.DEX)
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(AndroidApiLevel.B)
        .allowUnusedProguardConfigurationRules()
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                assertEquals(
                    1,
                    diagnostics.getWarnings().stream()
                        .filter(
                            LibraryProvidedProguardRulesTestUtils.getDiagnosticMatcher()::matches)
                        .count()))
        .inspectProguardConfiguration(
            configuration ->
                assertEquals(
                    StringUtils.lines(EXPECTED_A.trim(), EXPECTED_B.trim(), EXPECTED_E.trim()),
                    stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
  }

  @Test
  public void runTestVersionMainR8VersionAgnosticOnlyRules() throws Exception {
    if (!Version.isMainVersion()) {
      return;
    }
    Path library = buildLibraryR8VersionAgnosticOnlyRules();
    testForR8(Backend.DEX)
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(AndroidApiLevel.B)
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspectProguardConfiguration(
            configuration ->
                assertEquals(
                    EXPECTED_A,
                    stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
  }

  @Test
  public void testProguardOnlyRules() throws Exception {
    Path library = buildLibraryProguardOnlyRules();
    testForR8(Backend.DEX)
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(AndroidApiLevel.B)
        .setFakeCompilerVersion(SemanticVersion.create(1, 2, 3))
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspectProguardConfiguration(
            configuration ->
                assertEquals(
                    EXPECTED_X,
                    stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
  }

  @Test
  public void testProguardOnlyRulesVersionMain() throws Exception {
    Path library = buildLibraryProguardOnlyRules();
    testForR8(Backend.DEX)
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(AndroidApiLevel.B)
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspectProguardConfiguration(
            configuration ->
                assertEquals(
                    EXPECTED_X,
                    stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
  }

  @Test
  public void testUnusedProguardOnlyRules() throws Exception {
    for (String directory :
        ImmutableList.of(
            "proguard-from-6.1.0",
            "proguard-upto-7.0.0",
            "proguard-from-6.1.0-upto-7.0.0",
            "proguard610",
            "com.android.tools/proguard",
            "com.android.tools/proguard-from-6.1.0",
            "com.android.tools/proguard-upto-7.0.0",
            "com.android.tools/proguard-from-6.1.0-upto-7.0.0",
            "com.android.tools/proguard610")) {
      Path library = buildLibraryProguardOnlyRules(directory);
      testForR8(Backend.DEX)
          .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
          .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
          .setMinApi(AndroidApiLevel.B)
          .setFakeCompilerVersion(SemanticVersion.create(1, 2, 3))
          .compile()
          .inspectProguardConfiguration(
              configuration ->
                  assertEquals(
                      "",
                      stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
    }
  }

  @Test
  public void testUnusedProguardOnlyRulesVersionMain() throws Exception {
    for (String directory :
        ImmutableList.of(
            "proguard-from-6.1.0",
            "proguard-upto-7.0.0",
            "proguard-from-6.1.0-upto-7.0.0",
            "proguard610",
            "com.android.tools/proguard",
            "com.android.tools/proguard-from-6.1.0",
            "com.android.tools/proguard-upto-7.0.0",
            "com.android.tools/proguard-from-6.1.0-upto-7.0.0",
            "com.android.tools/proguard610")) {
      Path library = buildLibraryProguardOnlyRules(directory);
      testForR8(Backend.DEX)
          .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
          .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
          .setMinApi(AndroidApiLevel.B)
          .compile()
          .inspectProguardConfiguration(
              configuration ->
                  assertEquals(
                      "",
                      stripCommentsAndInJars(configuration, providerType == ProviderType.INJARS)));
    }
  }
}
