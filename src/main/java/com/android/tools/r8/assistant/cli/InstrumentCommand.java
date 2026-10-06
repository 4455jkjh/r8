// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static com.android.tools.r8.utils.internal.StringUtils.LINE_SEPARATOR;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8Assistant;
import com.android.tools.r8.assistant.cli.ToolRunner.ToolResult;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code instrument} command, which instruments a debug APK to log the reflective operations of
 * the app at runtime, and aligns and signs the result with the Android SDK build-tools.
 */
public class InstrumentCommand extends AssistantCommand {

  public static final String NAME = "instrument";

  public static final String DEFAULT_KEYSTORE_PASSWORD = "pass:android";
  public static final String DEFAULT_KEY_ALIAS = "androiddebugkey";
  public static final String DEFAULT_KEY_PASSWORD = "pass:android";

  private static final List<String> PASSWORD_PREFIXES = ImmutableList.of("pass:", "env:", "file:");
  private static final String CERTIFICATE_DN = "certificate DN:";
  private static final int HEADER_WIDTH = 85;

  private final Function<String, String> environment;
  private final ToolRunner toolRunner;

  private Path input;
  private Path output;
  private Path runtimeDex;
  private Path tempDir;
  private Path sdk;
  private String buildToolsVersion;
  private Path keystore;
  private String keystorePassword = DEFAULT_KEYSTORE_PASSWORD;
  private String keyAlias = DEFAULT_KEY_ALIAS;
  private String keyPassword = DEFAULT_KEY_PASSWORD;
  private boolean verbose = false;

  public InstrumentCommand() {
    this(System::getenv, ToolRunner::runProcess);
  }

  /** Creates the command with the given environment variables and runner for the build-tools. */
  public InstrumentCommand(Function<String, String> environment, ToolRunner toolRunner) {
    this.environment = environment;
    this.toolRunner = toolRunner;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Instrument a debug APK to log reflective operations at runtime.";
  }

  @Override
  public String getUsage() {
    return CliParserUtils.getUsageMessage(createParser(new ArrayList<>(), new ArrayList<>()));
  }

  private static CliParser<InstrumentCommand> createParser(
      List<String> errors, List<String> positionals) {
    List<String> headerLines = new ArrayList<>();
    headerLines.add(
        "Usage: "
            + AssistantCommandLine.INVOCATION
            + " "
            + NAME
            + " --input <apk> --output <apk> [options]");
    for (String line :
        StringUtils.wrapToWidth(
            "Instruments an APK so that the app logs the reflective operations that it performs at"
                + " runtime. The input APK must be debuggable or signed with the Android Debug"
                + " certificate. The output APK is aligned with zipalign and signed with apksigner"
                + " from the Android SDK build-tools, by default with the debug key, so that 'adb"
                + " install -r' can replace an installed debug build without losing its data.",
            HEADER_WIDTH)) {
      headerLines.add(" " + line);
    }
    headerLines.add(" Options:");
    String header = StringUtils.joinLines(headerLines);
    return new CliParser<InstrumentCommand>(header)
        .option1(
            "--input",
            "<apk>",
            "Input APK. Can also be passed as the first positional argument.",
            (command, value) -> command.input = toPath("--input", value, errors),
            "-i")
        .option1(
            "--output",
            "<apk>",
            "Output APK. Can also be passed as the second positional argument.",
            (command, value) -> command.output = toPath("--output", value, errors),
            "-o")
        .option1(
            "--sdk",
            "<dir>",
            "Android SDK directory (default: $ANDROID_HOME, $ANDROID_SDK_ROOT, or sdk.dir in"
                + " ./local.properties).",
            (command, value) -> command.sdk = toPath("--sdk", value, errors))
        .option1(
            "--build-tools",
            "<version>",
            "Build-tools version in the SDK, such as "
                + AndroidBuildTools.SUGGESTED_VERSION
                + " (default: the highest installed version with zipalign, apksigner and aapt2).",
            (command, value) -> command.buildToolsVersion = checkBuildToolsVersion(value, errors))
        .option1(
            "--ks",
            "<file>",
            "Keystore with the signing key (default: ~/.android/debug.keystore, or"
                + " $ANDROID_USER_HOME/debug.keystore if ANDROID_USER_HOME is set).",
            (command, value) -> command.keystore = toPath("--ks", value, errors))
        .option1(
            "--ks-pass",
            "<password>",
            "Keystore password in apksigner syntax: pass:<password>, env:<variable> or"
                + " file:<file> (default: "
                + DEFAULT_KEYSTORE_PASSWORD
                + ").",
            (command, value) ->
                command.keystorePassword = checkPassword("--ks-pass", value, errors))
        .option1(
            "--ks-key-alias",
            "<alias>",
            "Alias of the signing key in the keystore (default: " + DEFAULT_KEY_ALIAS + ").",
            (command, value) -> command.keyAlias = value)
        .option1(
            "--key-pass",
            "<password>",
            "Password of the signing key in apksigner syntax (default: "
                + DEFAULT_KEY_PASSWORD
                + ").",
            (command, value) -> command.keyPassword = checkPassword("--key-pass", value, errors))
        .option1(
            "--runtime",
            "<file>",
            "Assistant runtime DEX to inject (default: the runtime bundled with the assistant).",
            (command, value) -> command.runtimeDex = toPath("--runtime", value, errors),
            "-r")
        .option1(
            "--temp-dir",
            "<dir>",
            "Directory for temporary files (default: a new directory in the system temporary"
                + " directory).",
            (command, value) -> command.tempDir = toPath("--temp-dir", value, errors))
        .option0(
            "--verbose",
            "Print progress and the build-tools commands that are run.",
            command -> command.verbose = true,
            "-v")
        .apply(CliParserUtils.addHelpOption(command -> {}))
        .positional((command, value) -> positionals.add(value));
  }

  @Override
  public void parseArguments(List<String> args) throws AssistantUsageException {
    List<String> errors = new ArrayList<>();
    List<String> positionals = new ArrayList<>();
    createParser(errors, positionals).parse(args.toArray(new String[0]), this, errors::add);
    for (String positional : positionals) {
      if (input == null) {
        input = toPath("the input APK", positional, errors);
      } else if (output == null) {
        output = toPath("the output APK", positional, errors);
      } else {
        errors.add("Unexpected argument: " + positional);
      }
    }
    if (errors.isEmpty()) {
      if (input == null) {
        errors.add("Missing input APK: pass --input <apk>.");
      }
      if (output == null) {
        errors.add("Missing output APK: pass --output <apk>.");
      }
    }
    if (!errors.isEmpty()) {
      throw new AssistantUsageException(errors.get(0));
    }
  }

  private static Path toPath(String option, String value, List<String> errors) {
    if (value.isEmpty()) {
      errors.add("Invalid path for " + option + ": path is empty");
      return null;
    }
    try {
      return Paths.get(value);
    } catch (InvalidPathException e) {
      errors.add("Invalid path for " + option + ": " + value);
      return null;
    }
  }

  private static String checkBuildToolsVersion(String value, List<String> errors) {
    if (value.isEmpty() || value.contains("/") || value.contains("\\")) {
      errors.add(
          "Invalid value for --build-tools: '"
              + value
              + "'. Pass a version such as "
              + AndroidBuildTools.SUGGESTED_VERSION
              + ", and the SDK directory with --sdk.");
    }
    return value;
  }

  private static String checkPassword(String option, String value, List<String> errors) {
    if (PASSWORD_PREFIXES.stream().noneMatch(value::startsWith)) {
      errors.add(
          "Invalid value for "
              + option
              + ": use apksigner syntax, pass:<password>, env:<variable> or file:<file>.");
    }
    return value;
  }

  public Path getInput() {
    return input;
  }

  public Path getOutput() {
    return output;
  }

  public Path getRuntimeDex() {
    return runtimeDex;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public Path getSdk() {
    return sdk;
  }

  public String getBuildToolsVersion() {
    return buildToolsVersion;
  }

  /** Returns the keystore passed with {@code --ks}, or null to use the debug keystore. */
  public Path getKeystore() {
    return keystore;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getKeyAlias() {
    return keyAlias;
  }

  public String getKeyPassword() {
    return keyPassword;
  }

  public boolean isVerbose() {
    return verbose;
  }

  @Override
  public int run(PrintStream out, PrintStream err) throws Exception {
    validatePaths();
    AndroidBuildTools buildTools =
        AndroidBuildTools.find(
            sdk, buildToolsVersion, environment, Paths.get(""), AndroidBuildTools.isWindowsHost());
    if (verbose) {
      out.println(
          "Using build-tools " + buildTools.getVersion() + " in " + buildTools.getDirectory());
    }
    Path signingKeystore = getSigningKeystore();
    boolean debuggable = checkIsDebugBuild(buildTools, signingKeystore, out);
    if (verbose) {
      out.println(
          "Instrumenting "
              + input
              + ", which is "
              + (debuggable ? "debuggable" : "signed with the Android Debug certificate")
              + ".");
    }
    Path workDirectory = createWorkDirectory();
    try {
      instrumentAlignAndSign(buildTools, signingKeystore, debuggable, workDirectory, out);
    } finally {
      try {
        deleteRecursively(workDirectory);
      } catch (IOException e) {
        err.println("Warning: Could not delete " + workDirectory + ": " + e.getMessage());
      }
    }
    out.println("Wrote " + output + ", signed with " + signingKeystore + ".");
    return AssistantCommandLine.EXIT_SUCCESS;
  }

  private void validatePaths() throws InstrumentException {
    if (!Files.isRegularFile(input)) {
      throw new InstrumentException("Input APK does not exist or is not a file: " + input);
    }
    if (runtimeDex != null && !Files.isRegularFile(runtimeDex)) {
      throw new InstrumentException("Runtime DEX does not exist or is not a file: " + runtimeDex);
    }
    if (Files.isDirectory(output)) {
      throw new InstrumentException("Output APK is a directory: " + output);
    }
    if (tempDir != null && Files.exists(tempDir) && !Files.isDirectory(tempDir)) {
      throw new InstrumentException("Temp directory is not a directory: " + tempDir);
    }
  }

  private void instrumentAlignAndSign(
      AndroidBuildTools buildTools,
      Path signingKeystore,
      boolean debuggable,
      Path workDirectory,
      PrintStream out)
      throws Exception {
    Path instrumented = workDirectory.resolve("instrumented.apk");
    R8Assistant.Options options =
        new R8Assistant.Options()
            .setInputApk(input)
            .setOutputApk(instrumented)
            .setRuntimeDex(runtimeDex)
            .setTempDir(workDirectory);
    try {
      R8Assistant.runOnApk(options);
    } catch (CompilationFailedException | IllegalArgumentException e) {
      throw new InstrumentException("Failed to instrument " + input + ": " + e.getMessage(), e);
    }
    Path aligned = workDirectory.resolve("aligned.apk");
    runToolOrFail("zipalign", buildTools.getZipalignCommand(instrumented, aligned), out);
    Path signed = workDirectory.resolve("signed.apk");
    runToolOrFail(
        "apksigner",
        buildTools.getApksignerSignCommand(
            signingKeystore, keystorePassword, keyAlias, keyPassword, aligned, signed),
        out);
    if (!debuggable) {
      checkSignedWithAndroidDebug(buildTools, signed, signingKeystore, out);
    }
    try {
      Path outputDirectory = output.toAbsolutePath().getParent();
      if (outputDirectory != null) {
        Files.createDirectories(outputDirectory);
      }
      Files.move(signed, output, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new InstrumentException(
          "Failed to write output APK " + output + ": " + e.getMessage(), e);
    }
  }

  private Path getSigningKeystore() throws InstrumentException {
    if (keystore != null) {
      if (!Files.isRegularFile(keystore)) {
        throw new InstrumentException("The keystore passed with --ks does not exist: " + keystore);
      }
      return keystore;
    }
    Path debugKeystore = getDefaultKeystore(environment);
    if (debugKeystore == null || !Files.isRegularFile(debugKeystore)) {
      throw new InstrumentException(
          "The debug keystore "
              + (debugKeystore != null ? debugKeystore : "~/.android/debug.keystore")
              + " does not exist. Build a debug variant of any app once to create it (for"
              + " example with ./gradlew :app:assembleDebug), or pass --ks <keystore>.");
    }
    return debugKeystore;
  }

  /**
   * Returns the debug keystore that the Android Gradle plugin creates and signs debug builds with.
   */
  public static Path getDefaultKeystore(Function<String, String> environment) {
    try {
      String androidUserHome = environment.apply("ANDROID_USER_HOME");
      if (androidUserHome != null && !androidUserHome.isEmpty()) {
        return Paths.get(androidUserHome, "debug.keystore");
      }
      String userHome = System.getProperty("user.home");
      if (userHome == null || userHome.isEmpty()) {
        return null;
      }
      return Paths.get(userHome, ".android", "debug.keystore");
    } catch (InvalidPathException e) {
      return null;
    }
  }

  /**
   * Checks that the input is a debug build: debuggable, or signed with the Android Debug
   * certificate like a release build signed for verification. Returns true if it is debuggable.
   */
  private boolean checkIsDebugBuild(AndroidBuildTools buildTools, Path keystore, PrintStream out)
      throws InstrumentException, InterruptedException {
    ToolResult badging = runTool(buildTools.getAapt2BadgingCommand(input), out);
    if (badging.getExitCode() == 0 && isDebuggable(badging.getOutput())) {
      return true;
    }
    ToolResult certificates = runTool(buildTools.getApksignerVerifyCommand(input), out);
    List<String> signers = getVerifiedSigners(certificates);
    if (isSignedWithAndroidDebug(signers)) {
      return false;
    }
    StringBuilder message = new StringBuilder("Refusing to instrument ").append(input);
    message.append(": it is not debuggable");
    if (badging.getExitCode() != 0) {
      message.append(" (aapt2 failed: ").append(summarize(badging.getOutput(), 3)).append(")");
    }
    message.append(", and it is not signed with the Android Debug certificate");
    appendSignerFailureDetails(message, certificates, signers);
    message
        .append(".")
        .append(LINE_SEPARATOR)
        .append("Only debug builds can be instrumented, so that instrumented apps never reach")
        .append(" users. Build a debug variant, for example with ./gradlew :app:assembleDebug, or")
        .append(" a release variant signed with the debug key:")
        .append(LINE_SEPARATOR)
        .append("  ./gradlew :app:assembleRelease")
        .append(" -Pandroid.injected.signing.store.file=\"")
        .append(keystore.toAbsolutePath())
        .append("\"")
        .append(" -Pandroid.injected.signing.store.password=")
        .append(getPasswordForGradle(keystorePassword))
        .append(" -Pandroid.injected.signing.key.alias=")
        .append(keyAlias)
        .append(" -Pandroid.injected.signing.key.password=")
        .append(getPasswordForGradle(keyPassword));
    throw new InstrumentException(message.toString());
  }

  private void checkSignedWithAndroidDebug(
      AndroidBuildTools buildTools, Path signedApk, Path signingKeystore, PrintStream out)
      throws InstrumentException, InterruptedException {
    ToolResult certificates = runTool(buildTools.getApksignerVerifyCommand(signedApk), out);
    List<String> signers = getVerifiedSigners(certificates);
    if (isSignedWithAndroidDebug(signers)) {
      return;
    }
    StringBuilder message =
        new StringBuilder("Refusing to write ")
            .append(output)
            .append(": ")
            .append(input)
            .append(" is not debuggable, and the keystore ")
            .append(signingKeystore)
            .append(" did not sign with the Android Debug certificate");
    appendSignerFailureDetails(message, certificates, signers);
    message.append(".");
    throw new InstrumentException(message.toString());
  }

  private static List<String> getVerifiedSigners(ToolResult certificates) {
    return certificates.getExitCode() == 0
        ? getSignerNames(certificates.getOutput())
        : Collections.emptyList();
  }

  private static boolean isSignedWithAndroidDebug(List<String> signers) {
    return !signers.isEmpty() && signers.stream().allMatch(InstrumentCommand::isAndroidDebugSigner);
  }

  private static void appendSignerFailureDetails(
      StringBuilder message, ToolResult certificates, List<String> signers) {
    if (certificates.getExitCode() != 0) {
      message
          .append(" (apksigner verify failed: ")
          .append(summarize(certificates.getOutput(), 3))
          .append(")");
    } else if (!signers.isEmpty()) {
      message.append(" (signer: ").append(String.join("; ", signers)).append(")");
    }
  }

  // Only the well-known password of the debug keystore is shown, so that errors do not leak
  // passwords.
  private static String getPasswordForGradle(String password) {
    return password.equals(DEFAULT_KEYSTORE_PASSWORD) || password.equals(DEFAULT_KEY_PASSWORD)
        ? password.substring("pass:".length())
        : "<password>";
  }

  /** Returns true if the output of {@code aapt2 dump badging} says the app is debuggable. */
  public static boolean isDebuggable(String badgingOutput) {
    return StringUtils.splitLines(badgingOutput).stream()
        .anyMatch(line -> line.trim().equals("application-debuggable"));
  }

  /**
   * Returns the distinguished names of the signer certificates listed by {@code apksigner verify
   * --print-certs}, such as {@code Signer #1 certificate DN: C=US, O=Android, CN=Android Debug}
   * (build-tools 36 and older) or {@code V3.0 Signer: certificate DN: C=US, O=Android, CN=Android
   * Debug} (build-tools 37). Source stamps are not signers of the app.
   */
  public static List<String> getSignerNames(String printCertsOutput) {
    List<String> names = new ArrayList<>();
    for (String line : StringUtils.splitLines(printCertsOutput)) {
      int index = line.indexOf(CERTIFICATE_DN);
      if (index >= 0 && !line.trim().startsWith("Source Stamp")) {
        names.add(line.substring(index + CERTIFICATE_DN.length()).trim());
      }
    }
    return names;
  }

  /** Returns true if the distinguished name has the common name CN=Android Debug. */
  public static boolean isAndroidDebugSigner(String distinguishedName) {
    return StringUtils.split(distinguishedName, ',').stream()
        .anyMatch(attribute -> attribute.trim().equals("CN=Android Debug"));
  }

  private ToolResult runTool(List<String> command, PrintStream out)
      throws InstrumentException, InterruptedException {
    if (verbose) {
      out.println("$ " + toDisplayString(command));
    }
    try {
      return toolRunner.run(command);
    } catch (IOException e) {
      throw new InstrumentException("Could not run " + command.get(0) + ": " + e.getMessage(), e);
    }
  }

  private void runToolOrFail(String name, List<String> command, PrintStream out)
      throws InstrumentException, InterruptedException {
    ToolResult result = runTool(command, out);
    if (result.getExitCode() != 0) {
      throw new InstrumentException(
          name
              + " failed with exit code "
              + result.getExitCode()
              + ": "
              + summarize(result.getOutput(), Integer.MAX_VALUE));
    }
  }

  // Passwords passed as pass:<password> are hidden when printing commands.
  private static String toDisplayString(List<String> command) {
    return command.stream()
        .map(argument -> argument.startsWith("pass:") ? "pass:******" : argument)
        .collect(Collectors.joining(" "));
  }

  // Returns the first lines of the output of a tool, without the warnings of the JVM or the tool
  // and without stack frames.
  private static String summarize(String toolOutput, int maxLines) {
    List<String> lines =
        StringUtils.splitLines(toolOutput).stream()
            .map(String::trim)
            .filter(
                line -> !line.isEmpty() && !line.startsWith("WARNING") && !line.startsWith("at "))
            .limit(maxLines)
            .collect(Collectors.toList());
    return lines.isEmpty() ? "no output" : String.join(" ", lines);
  }

  private Path createWorkDirectory() throws InstrumentException {
    try {
      if (tempDir == null) {
        return Files.createTempDirectory("r8-assistant-");
      }
      Files.createDirectories(tempDir);
      return Files.createTempDirectory(tempDir, "r8-assistant-");
    } catch (IOException e) {
      throw new InstrumentException(
          "Failed to create temporary directory"
              + (tempDir != null ? " in " + tempDir : "")
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    List<Path> paths;
    try (Stream<Path> stream = Files.walk(directory)) {
      paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }
    for (Path path : paths) {
      Files.deleteIfExists(path);
    }
  }

  /** A failure of the command. The message is shown to the user. */
  private static class InstrumentException extends Exception {

    InstrumentException(String message) {
      super(message);
    }

    InstrumentException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
