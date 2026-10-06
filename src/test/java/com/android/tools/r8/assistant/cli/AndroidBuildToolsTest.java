// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.cli.AndroidBuildTools.BuildToolsVersion;
import com.android.tools.r8.assistant.cli.AndroidBuildTools.NotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidBuildToolsTest extends TestBase {

  private static final String INSTALL_HINT =
      "Install them with: android sdk install \"build-tools;36.0.0\""
          + " (or: sdkmanager \"build-tools;36.0.0\").";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static Function<String, String> environment(String... keysAndValues) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      builder.put(keysAndValues[i], keysAndValues[i + 1]);
    }
    Map<String, String> map = builder.build();
    return map::get;
  }

  private static Path createBuildTools(Path sdk, String version, boolean windows, String... tools)
      throws IOException {
    Path directory = Files.createDirectories(sdk.resolve("build-tools").resolve(version));
    for (String tool : tools) {
      Files.createFile(directory.resolve(AndroidBuildTools.getExecutableName(tool, windows)));
    }
    return directory;
  }

  private static Path createBuildTools(Path sdk, String version) throws IOException {
    return createBuildTools(sdk, version, false, "zipalign", "apksigner", "aapt2");
  }

  private Path newWorkingDirectory() throws IOException {
    return temp.newFolder().toPath();
  }

  private AndroidBuildTools find(Path sdk, String version) throws Exception {
    return AndroidBuildTools.find(sdk, version, environment(), newWorkingDirectory(), false);
  }

  private String findError(Path sdk, String version, boolean windows) throws IOException {
    Path workingDirectory = newWorkingDirectory();
    return assertThrows(
            NotFoundException.class,
            () -> AndroidBuildTools.find(sdk, version, environment(), workingDirectory, windows))
        .getMessage();
  }

  @Test
  public void testSdkOptionHasPrecedence() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    Path androidHome = temp.newFolder("android_home").toPath();
    assertEquals(
        sdk,
        AndroidBuildTools.findSdk(
            sdk, environment("ANDROID_HOME", androidHome.toString()), newWorkingDirectory()));
  }

  @Test
  public void testAndroidHomeHasPrecedenceOverAndroidSdkRoot() throws Exception {
    Path androidHome = temp.newFolder("android_home").toPath();
    Path androidSdkRoot = temp.newFolder("android_sdk_root").toPath();
    assertEquals(
        androidHome,
        AndroidBuildTools.findSdk(
            null,
            environment(
                "ANDROID_HOME",
                androidHome.toString(),
                "ANDROID_SDK_ROOT",
                androidSdkRoot.toString()),
            newWorkingDirectory()));
    assertEquals(
        androidSdkRoot,
        AndroidBuildTools.findSdk(
            null,
            environment("ANDROID_SDK_ROOT", androidSdkRoot.toString()),
            newWorkingDirectory()));
  }

  @Test
  public void testEnvironmentHasPrecedenceOverLocalProperties() throws Exception {
    Path androidHome = temp.newFolder("android_home").toPath();
    Path sdkDir = temp.newFolder("sdk_dir").toPath();
    Path workingDirectory = newWorkingDirectory();
    writeLocalProperties(workingDirectory, sdkDirProperty(sdkDir));
    assertEquals(
        androidHome,
        AndroidBuildTools.findSdk(
            null, environment("ANDROID_HOME", androidHome.toString()), workingDirectory));
    assertEquals(sdkDir, AndroidBuildTools.findSdk(null, environment(), workingDirectory));
  }

  @Test
  public void testLocalPropertiesWithRelativeSdkDir() throws Exception {
    Path workingDirectory = newWorkingDirectory();
    Path sdk = Files.createDirectories(workingDirectory.resolve("relative").resolve("sdk"));
    writeLocalProperties(workingDirectory, "# Comment", "sdk.dir=relative/sdk");
    Path found = AndroidBuildTools.findSdk(null, environment(), workingDirectory);
    assertEquals(sdk.toRealPath(), found.toRealPath());
  }

  @Test
  public void testInvalidLocationsAreSkipped() throws Exception {
    Path sdkDir = temp.newFolder("sdk_dir").toPath();
    Path workingDirectory = newWorkingDirectory();
    writeLocalProperties(workingDirectory, sdkDirProperty(sdkDir));
    Path missing = temp.getRoot().toPath().resolve("missing");
    assertEquals(
        sdkDir,
        AndroidBuildTools.findSdk(
            null,
            environment("ANDROID_HOME", missing.toString(), "ANDROID_SDK_ROOT", ""),
            workingDirectory));
  }

  @Test
  public void testMissingSdkOption() throws Exception {
    Path missing = temp.getRoot().toPath().resolve("missing");
    Path androidHome = temp.newFolder("android_home").toPath();
    Path workingDirectory = newWorkingDirectory();
    NotFoundException e =
        assertThrows(
            NotFoundException.class,
            () ->
                AndroidBuildTools.findSdk(
                    missing,
                    environment("ANDROID_HOME", androidHome.toString()),
                    workingDirectory));
    assertEquals(
        "The Android SDK directory passed with --sdk does not exist: " + missing, e.getMessage());
  }

  @Test
  public void testNoSdk() throws Exception {
    Path missing = temp.getRoot().toPath().resolve("missing");
    Path workingDirectory = newWorkingDirectory();
    writeLocalProperties(workingDirectory, sdkDirProperty(missing));
    NotFoundException e =
        assertThrows(
            NotFoundException.class,
            () ->
                AndroidBuildTools.findSdk(
                    null, environment("ANDROID_SDK_ROOT", missing.toString()), workingDirectory));
    assertThat(
        e.getMessage(),
        containsString(
            "Could not find the Android SDK. Pass --sdk <dir>, set ANDROID_HOME, or run from a"
                + " project directory with a local.properties file that sets sdk.dir."));
    assertThat(e.getMessage(), containsString("$ANDROID_SDK_ROOT is not a directory: " + missing));
    assertThat(
        e.getMessage(),
        containsString(
            "sdk.dir in "
                + workingDirectory.resolve("local.properties")
                + " is not a directory: "
                + missing));
    Path emptyWorkingDirectory = newWorkingDirectory();
    assertEquals(
        "Could not find the Android SDK. Pass --sdk <dir>, set ANDROID_HOME, or run from a project"
            + " directory with a local.properties file that sets sdk.dir.",
        assertThrows(
                NotFoundException.class,
                () -> AndroidBuildTools.findSdk(null, environment(), emptyWorkingDirectory))
            .getMessage());
  }

  @Test
  public void testHighestCompleteVersionIsSelected() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    createBuildTools(sdk, "9.0.0");
    createBuildTools(sdk, "30.0.3");
    Path expected = createBuildTools(sdk, "35.0.1");
    createBuildTools(sdk, "35.0.0");
    createBuildTools(sdk, "35.0.1-rc1");
    createBuildTools(sdk, "36.0.0", false, "zipalign", "aapt2");
    createBuildTools(sdk, "android-4.4W");
    AndroidBuildTools buildTools = find(sdk, null);
    assertEquals(expected, buildTools.getDirectory());
    assertEquals("35.0.1", buildTools.getVersion());
    assertEquals(expected.resolve("zipalign"), buildTools.getZipalign());
    assertEquals(expected.resolve("apksigner"), buildTools.getApksigner());
    assertEquals(expected.resolve("aapt2"), buildTools.getAapt2());
  }

  @Test
  public void testReleaseCandidateIsSelectedWithoutRelease() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    createBuildTools(sdk, "34.0.0");
    createBuildTools(sdk, "35.0.0-rc1");
    createBuildTools(sdk, "35.0.0-rc2");
    assertEquals("35.0.0-rc2", find(sdk, null).getVersion());
  }

  @Test
  public void testExplicitVersion() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    Path expected = createBuildTools(sdk, "33.0.1");
    createBuildTools(sdk, "35.0.0");
    AndroidBuildTools buildTools = find(sdk, "33.0.1");
    assertEquals(expected, buildTools.getDirectory());
    assertEquals("33.0.1", buildTools.getVersion());
  }

  @Test
  public void testNoBuildTools() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    assertEquals(
        "No build-tools are installed in " + sdk + ". " + INSTALL_HINT,
        findError(sdk, null, false));
    Files.createDirectories(sdk.resolve("build-tools"));
    assertEquals(
        "No build-tools are installed in " + sdk + ". " + INSTALL_HINT,
        findError(sdk, null, false));
  }

  @Test
  public void testIncompleteBuildTools() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    createBuildTools(sdk, "33.0.1", false, "zipalign");
    createBuildTools(sdk, "36.0.0", false, "apksigner", "aapt2");
    assertEquals(
        "No build-tools version in "
            + sdk.resolve("build-tools")
            + " contains zipalign, apksigner, aapt2: 36.0.0 (missing zipalign), 33.0.1 (missing"
            + " apksigner, aapt2). "
            + INSTALL_HINT,
        findError(sdk, null, false));
  }

  @Test
  public void testExplicitVersionNotInstalled() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    createBuildTools(sdk, "35.0.0");
    assertEquals(
        "Build-tools 34.0.0 is not installed in "
            + sdk
            + ". Install them with: android sdk install \"build-tools;34.0.0\" (or: sdkmanager"
            + " \"build-tools;34.0.0\").",
        findError(sdk, "34.0.0", false));
  }

  @Test
  public void testExplicitVersionIncomplete() throws Exception {
    Path sdk = temp.newFolder("sdk").toPath();
    Path directory = createBuildTools(sdk, "34.0.0", false, "zipalign", "aapt2");
    assertEquals(
        "Build-tools 34.0.0 in "
            + directory
            + " does not contain apksigner. Install them with: android sdk install"
            + " \"build-tools;34.0.0\" (or: sdkmanager \"build-tools;34.0.0\").",
        findError(sdk, "34.0.0", false));
  }

  @Test
  public void testWindowsExecutableNames() throws Exception {
    assertEquals("zipalign.exe", AndroidBuildTools.getExecutableName("zipalign", true));
    assertEquals("apksigner.bat", AndroidBuildTools.getExecutableName("apksigner", true));
    assertEquals("aapt2.exe", AndroidBuildTools.getExecutableName("aapt2", true));
    assertEquals("apksigner", AndroidBuildTools.getExecutableName("apksigner", false));

    Path sdk = temp.newFolder("sdk").toPath();
    Path windowsTools = createBuildTools(sdk, "35.0.0", true, "zipalign", "apksigner", "aapt2");
    Path linuxTools = createBuildTools(sdk, "36.0.0");
    Path workingDirectory = newWorkingDirectory();
    AndroidBuildTools buildTools =
        AndroidBuildTools.find(sdk, null, environment(), workingDirectory, true);
    assertEquals(windowsTools, buildTools.getDirectory());
    assertEquals(windowsTools.resolve("zipalign.exe"), buildTools.getZipalign());
    assertEquals(windowsTools.resolve("apksigner.bat"), buildTools.getApksigner());
    assertEquals(windowsTools.resolve("aapt2.exe"), buildTools.getAapt2());
    assertEquals(
        linuxTools,
        AndroidBuildTools.find(sdk, null, environment(), workingDirectory, false).getDirectory());
    assertThat(
        findError(sdk, "36.0.0", true),
        containsString("does not contain zipalign.exe, apksigner.bat, aapt2.exe."));
  }

  @Test
  public void testVersionOrdering() {
    assertOrdered(
        "android-4.4W",
        "debian",
        "9.0.0",
        "10.0.0",
        "34.0.0-rc1",
        "34.0.0-rc2",
        "34.0.0-rc10",
        "34.0.0",
        "34.0.1",
        "35.0.0",
        "100.0.0");
    assertEquals(0, BuildToolsVersion.parse("36").compareTo(BuildToolsVersion.parse("36.0.0")));
    assertEquals(
        0, BuildToolsVersion.parse("036.0.0").compareTo(BuildToolsVersion.parse("36.0.0")));
  }

  private static void assertOrdered(String... versions) {
    for (int i = 0; i < versions.length; i++) {
      for (int j = 0; j < versions.length; j++) {
        int expected = Integer.compare(i, j);
        int actual =
            Integer.signum(
                BuildToolsVersion.parse(versions[i])
                    .compareTo(BuildToolsVersion.parse(versions[j])));
        assertEquals(versions[i] + " vs " + versions[j], expected, actual);
      }
    }
  }

  @Test
  public void testZipalignPageSizeSupport() {
    assertFalse(new AndroidBuildTools(Paths.get("bt"), "34.0.0", false).supportsZipalignPageSize());
    assertFalse(
        new AndroidBuildTools(Paths.get("bt"), "35.0.0-rc4", false).supportsZipalignPageSize());
    assertTrue(new AndroidBuildTools(Paths.get("bt"), "35.0.0", false).supportsZipalignPageSize());
    assertTrue(new AndroidBuildTools(Paths.get("bt"), "37.0.0", false).supportsZipalignPageSize());
  }

  @Test
  public void testCommandLines() {
    Path directory = Paths.get("sdk", "build-tools", "35.0.0");
    AndroidBuildTools buildTools = new AndroidBuildTools(directory, "35.0.0", false);
    Path in = Paths.get("in.apk");
    Path out = Paths.get("out.apk");
    String zipalign = directory.resolve("zipalign").toString();
    String apksigner = directory.resolve("apksigner").toString();
    assertEquals(
        ImmutableList.of(zipalign, "-P", "16", "-f", "4", "in.apk", "out.apk"),
        buildTools.getZipalignCommand(in, out));
    assertEquals(
        ImmutableList.of(zipalign, "-p", "-f", "4", "in.apk", "out.apk"),
        new AndroidBuildTools(directory, "34.0.0", false).getZipalignCommand(in, out));
    assertEquals(
        ImmutableList.of(
            apksigner,
            "sign",
            "--ks",
            "debug.keystore",
            "--ks-pass",
            "env:KS_PASS",
            "--ks-key-alias",
            "key",
            "--key-pass",
            "file:key.txt",
            "--out",
            "out.apk",
            "in.apk"),
        buildTools.getApksignerSignCommand(
            Paths.get("debug.keystore"), "env:KS_PASS", "key", "file:key.txt", in, out));
    assertEquals(
        ImmutableList.of(apksigner, "verify", "--print-certs", "in.apk"),
        buildTools.getApksignerVerifyCommand(in));
    assertEquals(
        ImmutableList.of(directory.resolve("aapt2").toString(), "dump", "badging", "in.apk"),
        buildTools.getAapt2BadgingCommand(in));
  }

  private static void writeLocalProperties(Path directory, String... lines) throws IOException {
    Files.write(
        directory.resolve("local.properties"), ImmutableList.copyOf(lines), StandardCharsets.UTF_8);
  }

  // Escapes the directory like Android Studio does, for example sdk.dir=C\:\\Android\\Sdk.
  private static String sdkDirProperty(Path sdkDir) {
    return "sdk.dir=" + sdkDir.toString().replace("\\", "\\\\").replace(":", "\\:");
  }
}
