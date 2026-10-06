// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.assistant.cli.ToolRunner.ToolResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstrumentCommandTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static InstrumentCommand parse(String... args) throws AssistantUsageException {
    InstrumentCommand command = new InstrumentCommand();
    command.parseArguments(Arrays.asList(args));
    return command;
  }

  private static String parseError(String... args) {
    return assertThrows(AssistantUsageException.class, () -> parse(args)).getMessage();
  }

  @Test
  public void testLongOptions() throws Exception {
    InstrumentCommand command =
        parse(
            "--input",
            "in.apk",
            "--output",
            "out/out.apk",
            "--sdk",
            "sdk",
            "--build-tools",
            "35.0.0",
            "--ks",
            "release.keystore",
            "--ks-pass",
            "env:KS_PASS",
            "--ks-key-alias",
            "release",
            "--key-pass",
            "file:key-password.txt",
            "--runtime",
            "runtime.dex",
            "--temp-dir",
            "tmp",
            "--verbose");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out", "out.apk"), command.getOutput());
    assertEquals(Paths.get("sdk"), command.getSdk());
    assertEquals("35.0.0", command.getBuildToolsVersion());
    assertEquals(Paths.get("release.keystore"), command.getKeystore());
    assertEquals("env:KS_PASS", command.getKeystorePassword());
    assertEquals("release", command.getKeyAlias());
    assertEquals("file:key-password.txt", command.getKeyPassword());
    assertEquals(Paths.get("runtime.dex"), command.getRuntimeDex());
    assertEquals(Paths.get("tmp"), command.getTempDir());
    assertTrue(command.isVerbose());
  }

  @Test
  public void testShortOptions() throws Exception {
    InstrumentCommand command = parse("-i", "in.apk", "-o", "out.apk", "-r", "runtime.dex", "-v");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out.apk"), command.getOutput());
    assertEquals(Paths.get("runtime.dex"), command.getRuntimeDex());
    assertTrue(command.isVerbose());
  }

  @Test
  public void testOptionsWithEquals() throws Exception {
    InstrumentCommand command =
        parse("--input=in.apk", "--output=out.apk", "--ks-pass=pass:a=b", "--build-tools=36.0.0");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out.apk"), command.getOutput());
    assertEquals("pass:a=b", command.getKeystorePassword());
    assertEquals("36.0.0", command.getBuildToolsVersion());
  }

  @Test
  public void testPositionalArguments() throws Exception {
    InstrumentCommand command = parse("in.apk", "out.apk");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out.apk"), command.getOutput());

    command = parse("in.apk", "--output", "out.apk");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out.apk"), command.getOutput());

    command = parse("--input", "in.apk", "out.apk");
    assertEquals(Paths.get("in.apk"), command.getInput());
    assertEquals(Paths.get("out.apk"), command.getOutput());
  }

  @Test
  public void testDefaults() throws Exception {
    InstrumentCommand command = parse("in.apk", "out.apk");
    assertNull(command.getSdk());
    assertNull(command.getBuildToolsVersion());
    assertNull(command.getKeystore());
    assertEquals("pass:android", command.getKeystorePassword());
    assertEquals("androiddebugkey", command.getKeyAlias());
    assertEquals("pass:android", command.getKeyPassword());
    assertNull(command.getRuntimeDex());
    assertNull(command.getTempDir());
    assertFalse(command.isVerbose());
  }

  @Test
  public void testPasswordsArePassedThroughUnchanged() throws Exception {
    for (String password :
        ImmutableList.of("pass:", "pass:with spaces:and:colons", "env:KS_PASS", "file:/a/b.txt")) {
      InstrumentCommand command =
          parse("in.apk", "out.apk", "--ks-pass", password, "--key-pass", password);
      assertEquals(password, command.getKeystorePassword());
      assertEquals(password, command.getKeyPassword());
    }
  }

  @Test
  public void testUsageErrors() {
    assertEquals("Missing input APK: pass --input <apk>.", parseError());
    assertEquals("Missing output APK: pass --output <apk>.", parseError("in.apk"));
    assertEquals("Missing output APK: pass --output <apk>.", parseError("--input", "in.apk"));
    assertEquals("Unexpected argument: c.apk", parseError("a.apk", "b.apk", "c.apk"));
    assertEquals("Missing argument for --output.", parseError("in.apk", "--output"));
    assertEquals("Unknown option: --min-api", parseError("in.apk", "out.apk", "--min-api", "21"));
    assertEquals("Unknown option: -x", parseError("in.apk", "out.apk", "-x"));
    assertEquals(
        "Invalid value for --build-tools: 'sdk/build-tools/35.0.0'. Pass a version such as 36.0.0,"
            + " and the SDK directory with --sdk.",
        parseError("in.apk", "out.apk", "--build-tools", "sdk/build-tools/35.0.0"));
    assertEquals(
        "Invalid value for --ks-pass: use apksigner syntax, pass:<password>, env:<variable> or"
            + " file:<file>.",
        parseError("in.apk", "out.apk", "--ks-pass", "android"));
    // The rejected password is not echoed.
    String error = parseError("in.apk", "out.apk", "--key-pass", "s3cr3t");
    assertThat(error, containsString("Invalid value for --key-pass"));
    assertThat(error, not(containsString("s3cr3t")));
  }

  @Test
  public void testParsingHasNoSideEffects() throws Exception {
    Path root = temp.newFolder().toPath();
    Path tempDir = root.resolve("tmp");
    Path output = root.resolve("out").resolve("out.apk");
    parse("missing.apk", output.toString(), "--temp-dir", tempDir.toString());
    parseError("missing.apk", output.toString(), "--temp-dir", tempDir.toString(), "--bad");
    try (Stream<Path> files = Files.list(root)) {
      assertEquals(0, files.count());
    }
  }

  @Test
  public void testUsage() {
    String usage = new InstrumentCommand().getUsage();
    assertThat(
        usage,
        containsString(
            "Usage: java -jar r8-assistant.jar instrument --input <apk> --output <apk> [options]"));
    for (String option :
        ImmutableList.of(
            "--input <apk>",
            "-i <apk>",
            "--output <apk>",
            "-o <apk>",
            "--sdk <dir>",
            "--build-tools <version>",
            "--ks <file>",
            "--ks-pass <password>",
            "--ks-key-alias <alias>",
            "--key-pass <password>",
            "--runtime <file>",
            "-r <file>",
            "--temp-dir <dir>",
            "--verbose",
            "-v",
            "--help",
            "-h")) {
      assertThat(usage, containsString("  " + option));
    }
    assertThat(usage, containsString("~/.android/debug.keystore"));
    assertThat(usage, containsString("pass:android"));
    assertThat(usage, containsString("androiddebugkey"));
  }

  @Test
  public void testIsDebuggable() {
    assertTrue(
        InstrumentCommand.isDebuggable(
            StringUtils.lines(
                "package: name='com.example' versionCode='1'",
                "application-debuggable",
                "launchable-activity: name='com.example.Main'")));
    assertTrue(InstrumentCommand.isDebuggable("application-debuggable\r\n"));
    assertFalse(
        InstrumentCommand.isDebuggable(
            StringUtils.lines(
                "package: name='com.example.application-debuggable' versionCode='1'",
                "application-label:'application-debuggable'")));
    assertFalse(InstrumentCommand.isDebuggable(""));
  }

  @Test
  public void testGetSignerNames() {
    // The output of apksigner 36 and older.
    assertEquals(
        ImmutableList.of("C=US, O=Android, CN=Android Debug"),
        InstrumentCommand.getSignerNames(
            StringUtils.lines(
                "Signer #1 certificate DN: C=US, O=Android, CN=Android Debug",
                "Signer #1 certificate SHA-256 digest: 07f7",
                "Signer #1 certificate SHA-1 digest: 1a2b")));
    // The output of apksigner 37, with JVM warnings and a source stamp.
    assertEquals(
        ImmutableList.of("CN=Release, O=Example", "CN=Android Debug, O=Android, C=US"),
        InstrumentCommand.getSignerNames(
            StringUtils.lines(
                "WARNING: A restricted method in java.lang.System has been called",
                "V3.0 Signer: certificate DN: CN=Release, O=Example",
                "V3.0 Signer: certificate SHA-256 digest: 07f7",
                "Signer #2 certificate DN: CN=Android Debug, O=Android, C=US",
                "Source Stamp Signer certificate DN: CN=Stamp, O=Example",
                "  Source Stamp Signer certificate DN: CN=Stamp, O=Example")));
    assertEquals(ImmutableList.of(), InstrumentCommand.getSignerNames("DOES NOT VERIFY"));
  }

  @Test
  public void testIsAndroidDebugSigner() {
    assertTrue(InstrumentCommand.isAndroidDebugSigner("C=US, O=Android, CN=Android Debug"));
    assertTrue(InstrumentCommand.isAndroidDebugSigner("CN=Android Debug,O=Android,C=US"));
    assertFalse(InstrumentCommand.isAndroidDebugSigner("CN=Android Debugger, O=Android, C=US"));
    assertFalse(InstrumentCommand.isAndroidDebugSigner("CN=Release, OU=Android Debug"));
    assertFalse(InstrumentCommand.isAndroidDebugSigner("CN=Release, O=CN=Android Debug Inc"));
    assertFalse(InstrumentCommand.isAndroidDebugSigner(""));
  }

  @Test
  public void testDefaultKeystore() {
    assertEquals(
        Paths.get(System.getProperty("user.home"), ".android", "debug.keystore"),
        InstrumentCommand.getDefaultKeystore(ImmutableMap.<String, String>of()::get));
    assertEquals(
        Paths.get("android-user-home", "debug.keystore"),
        InstrumentCommand.getDefaultKeystore(
            ImmutableMap.of("ANDROID_USER_HOME", "android-user-home")::get));
    assertNull(
        InstrumentCommand.getDefaultKeystore(ImmutableMap.of("ANDROID_USER_HOME", "\0")::get));
  }

  @Test
  public void testIsDebuggableWithAapt2() throws Exception {
    // Only a Linux binary of aapt2 is checked in.
    assumeTrue(ToolHelper.isLinux());
    assertTrue(isDebuggable(buildApk("debuggable", "android:debuggable=\"true\"")));
    assertFalse(isDebuggable(buildApk("release", "android:debuggable=\"false\"")));
    assertFalse(isDebuggable(buildApk("default", "")));
  }

  private Path buildApk(String name, String applicationAttributes) throws Exception {
    Path directory = temp.newFolder(name).toPath();
    Path manifest = directory.resolve("AndroidManifest.xml");
    Files.write(
        manifest,
        StringUtils.lines(
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    package=\"com.example.app\">",
                "  <application " + applicationAttributes + "/>",
                "</manifest>")
            .getBytes(StandardCharsets.UTF_8));
    Path apk = directory.resolve(name + ".apk");
    ProcessResult result =
        ToolHelper.runAapt2(
            "link",
            "-I",
            ToolHelper.getAndroidJar(AndroidApiLevel.S).toString(),
            "--manifest",
            manifest.toString(),
            "-o",
            apk.toString());
    assertEquals(result.toString(), 0, result.exitCode);
    return apk;
  }

  private static boolean isDebuggable(Path apk) throws Exception {
    ToolResult result =
        ToolRunner.runProcess(
            ImmutableList.of(TestDeps.getAapt2().toString(), "dump", "badging", apk.toString()));
    assertEquals(result.getOutput(), 0, result.getExitCode());
    assertThat(result.getOutput(), containsString("package: name='com.example.app'"));
    return InstrumentCommand.isDebuggable(result.getOutput());
  }
}
