// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.assistant.cli.ToolRunner.ToolResult;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests running the instrument command with a fake runner for the build-tools. */
@RunWith(Parameterized.class)
public class InstrumentCommandRunTest extends TestBase {

  private static final String DEBUG_SIGNER = "C=US, O=Android, CN=Android Debug";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  private Path sdk;
  private Path keystore;
  private Path tempDir;
  private Path input;
  private FakeToolRunner toolRunner;

  @Before
  public void setUp() throws Exception {
    sdk = temp.newFolder("sdk").toPath();
    createBuildTools("35.0.0");
    keystore = Files.createFile(temp.getRoot().toPath().resolve("test.keystore"));
    tempDir = temp.getRoot().toPath().resolve("tmp");
    input = buildApk();
    toolRunner = new FakeToolRunner();
  }

  private void createBuildTools(String version) throws IOException {
    Path directory = Files.createDirectories(sdk.resolve("build-tools").resolve(version));
    for (String tool : AndroidBuildTools.REQUIRED_TOOLS) {
      Files.createFile(
          directory.resolve(
              AndroidBuildTools.getExecutableName(tool, AndroidBuildTools.isWindowsHost())));
    }
  }

  private String tool(String version, String tool) {
    return sdk.resolve("build-tools")
        .resolve(version)
        .resolve(AndroidBuildTools.getExecutableName(tool, AndroidBuildTools.isWindowsHost()))
        .toString();
  }

  private Path buildApk() throws Exception {
    Path dex = testForD8(parameters).addProgramClasses(TestClass.class).compile().writeToZip();
    Path apk = temp.getRoot().toPath().resolve("input.apk");
    try (ZipOutputStream out =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(apk)))) {
      out.putNextEntry(new ZipEntry("assets/sample.txt"));
      out.write("Sample".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
      out.putNextEntry(new ZipEntry("classes.dex"));
      out.write(ZipUtils.readSingleEntry(dex, "classes.dex"));
      out.closeEntry();
    }
    return apk;
  }

  private ProcessResult runInstrument(Map<String, String> environment, String... args)
      throws IOException {
    List<String> arguments = new ArrayList<>();
    arguments.add(InstrumentCommand.NAME);
    arguments.add("--temp-dir");
    arguments.add(tempDir.toString());
    arguments.add("--runtime");
    arguments.add(ToolHelper.ASSISTANT_RUNTIME_DEX.toString());
    for (String arg : args) {
      arguments.add(arg);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    String encoding = StandardCharsets.UTF_8.name();
    int exitCode;
    try (PrintStream outStream = new PrintStream(out, true, encoding);
        PrintStream errStream = new PrintStream(err, true, encoding)) {
      exitCode =
          AssistantCommandLine.run(
              arguments.toArray(new String[0]),
              outStream,
              errStream,
              ImmutableList.of(new InstrumentCommand(environment::get, toolRunner)));
    }
    return new ProcessResult(
        exitCode, out.toString(encoding), err.toString(encoding), String.join(" ", arguments));
  }

  private ProcessResult runInstrument(String... args) throws IOException {
    return runInstrument(ImmutableMap.of(), args);
  }

  private List<String> withSigning(String... args) {
    List<String> arguments = new ArrayList<>();
    arguments.add("--sdk");
    arguments.add(sdk.toString());
    arguments.add("--ks");
    arguments.add(keystore.toString());
    for (String arg : args) {
      arguments.add(arg);
    }
    return arguments;
  }

  private ProcessResult runInstrumentWithSigning(String... args) throws IOException {
    return runInstrument(withSigning(args).toArray(new String[0]));
  }

  private void assertTempDirIsEmpty() throws IOException {
    assertTrue(Files.isDirectory(tempDir));
    try (Stream<Path> files = Files.list(tempDir)) {
      assertEquals(ImmutableList.of(), files.collect(Collectors.toList()));
    }
  }

  private static void assertInstrumentedApk(Path apk) throws IOException {
    assertTrue(Files.exists(apk));
    List<String> entries = new ArrayList<>();
    ZipUtils.iter(apk, entry -> entries.add(entry.getName()));
    assertTrue(entries.toString(), entries.contains("classes.dex"));
    assertTrue(entries.toString(), entries.contains("assets/sample.txt"));
    // The fake apksigner marks the APKs that it signed.
    assertTrue(entries.toString(), entries.contains(FakeToolRunner.SIGNED_MARKER));
  }

  @Test
  public void testDebuggableApk() throws Exception {
    Path output = temp.getRoot().toPath().resolve("out").resolve("instrumented.apk");
    ProcessResult result =
        runInstrumentWithSigning("-i", input.toString(), "-o", output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(
        StringUtils.lines("Wrote " + output + ", signed with " + keystore + "."), result.stdout);
    assertEquals("", result.stderr);
    assertInstrumentedApk(output);
    assertTempDirIsEmpty();

    List<List<String>> commands = toolRunner.commands;
    assertEquals(3, commands.size());
    assertEquals(
        ImmutableList.of(tool("35.0.0", "aapt2"), "dump", "badging", input.toString()),
        commands.get(0));
    List<String> zipalign = commands.get(1);
    assertEquals(
        ImmutableList.of(tool("35.0.0", "zipalign"), "-P", "16", "-f", "4"),
        zipalign.subList(0, 5));
    Path instrumented = Paths.get(zipalign.get(5));
    Path aligned = Paths.get(zipalign.get(6));
    assertEquals(tempDir, instrumented.getParent().getParent());
    List<String> sign = commands.get(2);
    assertEquals(
        ImmutableList.of(
            tool("35.0.0", "apksigner"),
            "sign",
            "--ks",
            keystore.toString(),
            "--ks-pass",
            "pass:android",
            "--ks-key-alias",
            "androiddebugkey",
            "--key-pass",
            "pass:android"),
        sign.subList(0, 10));
    assertEquals("--out", sign.get(10));
    assertEquals(aligned.toString(), sign.get(12));
  }

  @Test
  public void testOldBuildToolsAlignWithoutPageSize() throws Exception {
    sdk = temp.newFolder("old_sdk").toPath();
    createBuildTools("33.0.1");
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    List<String> zipalign = toolRunner.commands.get(1);
    assertEquals(
        ImmutableList.of(tool("33.0.1", "zipalign"), "-p", "-f", "4"), zipalign.subList(0, 4));
    assertInstrumentedApk(output);
  }

  @Test
  public void testExplicitBuildToolsVersion() throws Exception {
    createBuildTools("36.0.0");
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrumentWithSigning("--build-tools", "35.0.0", input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(tool("35.0.0", "aapt2"), toolRunner.commands.get(0).get(0));
  }

  @Test
  public void testApkSignedWithDebugCertificate() throws Exception {
    toolRunner.badging = "package: name='com.example.app'";
    toolRunner.certificates = "Signer #1 certificate DN: " + DEBUG_SIGNER;
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrumentWithSigning("--verbose", input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertThat(
        result.stdout,
        containsString(
            "Instrumenting " + input + ", which is signed with the Android Debug certificate."));
    assertEquals(
        ImmutableList.of(tool("35.0.0", "apksigner"), "verify", "--print-certs", input.toString()),
        toolRunner.commands.get(1));
    assertEquals(5, toolRunner.commands.size());
    assertEquals(
        ImmutableList.of("apksigner", "verify", "--print-certs"),
        ImmutableList.of(
            Paths.get(toolRunner.commands.get(4).get(0)).getFileName().toString(),
            toolRunner.commands.get(4).get(1),
            toolRunner.commands.get(4).get(2)));
    assertInstrumentedApk(output);
  }

  @Test
  public void testNonDebuggableApkSignedWithNonDebugKeyIsRefused() throws Exception {
    toolRunner.badging = "package: name='com.example.app'";
    toolRunner.certificates = "Signer #1 certificate DN: " + DEBUG_SIGNER;
    toolRunner.signedCertificates = "Signer #1 certificate DN: CN=Release";
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals(
        StringUtils.lines(
            "Error: Refusing to write "
                + output
                + ": "
                + input
                + " is not debuggable, and the keystore "
                + keystore
                + " did not sign with the Android Debug certificate (signer: CN=Release)."),
        result.stderr);
    assertFalse(Files.exists(output));
    assertTempDirIsEmpty();
    assertEquals(5, toolRunner.commands.size());
  }

  @Test
  public void testReleaseSignedApkIsRefused() throws Exception {
    toolRunner.badging = "package: name='com.example.app'";
    toolRunner.certificates =
        StringUtils.lines(
            "Signer #1 certificate DN: " + DEBUG_SIGNER, "Signer #2 certificate DN: CN=Release");
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals("", result.stdout);
    assertThat(
        result.stderr,
        containsString(
            "Error: Refusing to instrument "
                + input
                + ": it is not debuggable, and it is not signed with the Android Debug certificate"
                + " (signer: "
                + DEBUG_SIGNER
                + "; CN=Release)."));
    assertThat(result.stderr, containsString("./gradlew :app:assembleDebug"));
    assertThat(
        result.stderr,
        containsString(
            "./gradlew :app:assembleRelease -Pandroid.injected.signing.store.file=\""
                + keystore.toAbsolutePath()
                + "\" -Pandroid.injected.signing.store.password=android"
                + " -Pandroid.injected.signing.key.alias=androiddebugkey"
                + " -Pandroid.injected.signing.key.password=android"));
    assertFalse(Files.exists(output));
    assertFalse(Files.exists(tempDir));
    assertEquals(2, toolRunner.commands.size());
  }

  @Test
  public void testUnsignedApkIsRefused() throws Exception {
    toolRunner.badging = "package: name='com.example.app'";
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrumentWithSigning(
            "--ks-pass",
            "env:KS_PASS",
            "--key-pass",
            "pass:s3cret",
            input.toString(),
            output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertThat(
        result.stderr,
        containsString(
            "it is not signed with the Android Debug certificate (apksigner verify failed: DOES"
                + " NOT VERIFY ERROR: Missing META-INF/MANIFEST.MF)."));
    assertThat(
        result.stderr, containsString("-Pandroid.injected.signing.store.password=<password>"));
    assertThat(result.stderr, containsString("-Pandroid.injected.signing.key.password=<password>"));
    assertThat(result.stderr, not(containsString("s3cret")));
    assertFalse(Files.exists(output));
  }

  @Test
  public void testApkThatAapt2CannotReadIsRefused() throws Exception {
    toolRunner.badgingExitCode = 1;
    toolRunner.badging = input + ": error: failed opening zip: Invalid file.";
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertThat(
        result.stderr,
        containsString(
            "it is not debuggable (aapt2 failed: "
                + input
                + ": error: failed opening zip: Invalid file.)"));
  }

  @Test
  public void testApksignerExceptionIsSummarizedWithoutStackFrames() throws Exception {
    toolRunner.badging = "package: name='com.example.app'";
    toolRunner.verifyFailure =
        StringUtils.lines(
            "Exception in thread \"main\" com.android.apksig.apk.ApkFormatException: Missing"
                + " AndroidManifest.xml",
            "\tat com.android.apksig.ApkSigner.getAndroidManifestFromApk(ApkSigner.java:982)",
            "\tat com.android.apksig.ApkVerifier.verify(ApkVerifier.java:194)");
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertThat(
        result.stderr,
        containsString(
            "(apksigner verify failed: Exception in thread \"main\""
                + " com.android.apksig.apk.ApkFormatException: Missing AndroidManifest.xml)."));
  }

  @Test
  public void testCustomSigningOptionsArePassedToApksigner() throws Exception {
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrumentWithSigning(
            "--ks-pass",
            "pass:store secret",
            "--ks-key-alias",
            "release",
            "--key-pass",
            "file:key.txt",
            "-v",
            input.toString(),
            output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    List<String> sign = toolRunner.commands.get(2);
    assertEquals(
        ImmutableList.of(
            "--ks-pass",
            "pass:store secret",
            "--ks-key-alias",
            "release",
            "--key-pass",
            "file:key.txt"),
        sign.subList(4, 10));
    // Passwords are not printed in verbose mode.
    assertThat(result.stdout, containsString("--ks-pass pass:****** --ks-key-alias release"));
    assertThat(result.stdout, containsString("--key-pass file:key.txt"));
    assertThat(result.stdout, not(containsString("store secret")));
  }

  @Test
  public void testSdkAndKeystoreFromEnvironment() throws Exception {
    Path androidUserHome = temp.newFolder("android_user_home").toPath();
    Path debugKeystore = Files.createFile(androidUserHome.resolve("debug.keystore"));
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrument(
            ImmutableMap.of(
                "ANDROID_HOME", sdk.toString(), "ANDROID_USER_HOME", androidUserHome.toString()),
            input.toString(),
            output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(
        StringUtils.lines("Wrote " + output + ", signed with " + debugKeystore + "."),
        result.stdout);
    assertEquals(debugKeystore.toString(), toolRunner.commands.get(2).get(3));
  }

  @Test
  public void testMissingDebugKeystore() throws Exception {
    Path androidUserHome = temp.newFolder("android_user_home").toPath();
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result =
        runInstrument(
            ImmutableMap.of("ANDROID_USER_HOME", androidUserHome.toString()),
            "--sdk",
            sdk.toString(),
            input.toString(),
            output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(
        StringUtils.lines(
            "Error: The debug keystore "
                + androidUserHome.resolve("debug.keystore")
                + " does not exist. Build a debug variant of any app once to create it (for example"
                + " with ./gradlew :app:assembleDebug), or pass --ks <keystore>."),
        result.stderr);
    assertEquals(ImmutableList.of(), toolRunner.commands);
  }

  @Test
  public void testMissingKeystore() throws Exception {
    Path missing = temp.getRoot().toPath().resolve("missing.keystore");
    ProcessResult result =
        runInstrument(
            "--sdk",
            sdk.toString(),
            "--ks",
            missing.toString(),
            input.toString(),
            temp.getRoot().toPath().resolve("instrumented.apk").toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(
        StringUtils.lines("Error: The keystore passed with --ks does not exist: " + missing),
        result.stderr);
  }

  @Test
  public void testMissingSdk() throws Exception {
    ProcessResult result =
        runInstrument(
            "--sdk",
            temp.getRoot().toPath().resolve("missing").toString(),
            input.toString(),
            temp.getRoot().toPath().resolve("instrumented.apk").toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertThat(
        result.stderr,
        containsString("Error: The Android SDK directory passed with --sdk does not exist: "));
  }

  @Test
  public void testZipalignFailure() throws Exception {
    toolRunner.zipalignExitCode = 1;
    Path output = temp.getRoot().toPath().resolve("instrumented.apk");
    ProcessResult result = runInstrumentWithSigning(input.toString(), output.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(
        StringUtils.lines("Error: zipalign failed with exit code 1: Unable to align."),
        result.stderr);
    assertFalse(Files.exists(output));
    assertTempDirIsEmpty();
  }

  @Test
  public void testInPlace() throws Exception {
    ProcessResult result = runInstrumentWithSigning(input.toString(), input.toString());
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertInstrumentedApk(input);
    assertTempDirIsEmpty();
  }

  /**
   * Simulates the build-tools: aapt2 and apksigner verify print the configured output, zipalign
   * copies the APK, and apksigner sign copies the APK and adds an entry to mark it as signed.
   */
  private static class FakeToolRunner implements ToolRunner {

    static final String SIGNED_MARKER = "META-INF/FAKE-SIGNATURE";

    final List<List<String>> commands = new ArrayList<>();
    String badging = FakeAndroidSdk.DEBUGGABLE_BADGING;
    int badgingExitCode = 0;
    // The output of apksigner verify, which fails with verifyFailure if not set.
    String certificates = null;
    String signedCertificates = null;
    String verifyFailure =
        StringUtils.lines("DOES NOT VERIFY", "ERROR: Missing META-INF/MANIFEST.MF");
    int zipalignExitCode = 0;

    @Override
    public ToolResult run(List<String> command) throws IOException {
      commands.add(command);
      String fileName = Paths.get(command.get(0)).getFileName().toString();
      String tool =
          fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
      switch (tool) {
        case "aapt2":
          return new ToolResult(badgingExitCode, badging);
        case "zipalign":
          if (zipalignExitCode != 0) {
            return new ToolResult(zipalignExitCode, "WARNING: Ignored\nUnable to align.\n");
          }
          Files.copy(
              Paths.get(command.get(command.size() - 2)),
              Paths.get(command.get(command.size() - 1)));
          return new ToolResult(0, "");
        case "apksigner":
          if (command.get(1).equals("verify")) {
            String output =
                signedCertificates != null && command.get(command.size() - 1).endsWith("signed.apk")
                    ? signedCertificates
                    : certificates;
            return output != null ? new ToolResult(0, output) : new ToolResult(1, verifyFailure);
          }
          sign(
              Paths.get(command.get(command.size() - 1)),
              Paths.get(command.get(command.indexOf("--out") + 1)));
          return new ToolResult(0, "");
        default:
          throw new AssertionError("Unexpected tool: " + command);
      }
    }

    private static void sign(Path input, Path output) throws IOException {
      try (ZipOutputStream out =
          new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
        ZipUtils.iter(
            input,
            (entry, stream) -> {
              out.putNextEntry(new ZipEntry(entry.getName()));
              stream.transferTo(out);
              out.closeEntry();
            });
        out.putNextEntry(new ZipEntry(SIGNED_MARKER));
        out.closeEntry();
      }
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello");
    }
  }
}
