// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The zipalign, apksigner and aapt2 tools of an installed Android SDK build-tools version. */
public class AndroidBuildTools {

  /** The tools that a build-tools version must contain to be used. */
  public static final List<String> REQUIRED_TOOLS =
      ImmutableList.of("zipalign", "apksigner", "aapt2");

  /** The build-tools version suggested for installation when no usable version is found. */
  public static final String SUGGESTED_VERSION = "36.0.0";

  // zipalign can align uncompressed native libraries to 16 KB pages with -P 16 since 35.0.0.
  private static final BuildToolsVersion FIRST_VERSION_WITH_ZIPALIGN_PAGE_SIZE =
      BuildToolsVersion.parse("35.0.0");

  private final Path directory;
  private final String version;
  private final boolean windows;

  AndroidBuildTools(Path directory, String version, boolean windows) {
    this.directory = directory;
    this.version = version;
    this.windows = windows;
  }

  public Path getDirectory() {
    return directory;
  }

  public String getVersion() {
    return version;
  }

  public Path getZipalign() {
    return getTool("zipalign");
  }

  public Path getApksigner() {
    return getTool("apksigner");
  }

  public Path getAapt2() {
    return getTool("aapt2");
  }

  private Path getTool(String tool) {
    return directory.resolve(getExecutableName(tool, windows));
  }

  public boolean supportsZipalignPageSize() {
    return BuildToolsVersion.parse(version).compareTo(FIRST_VERSION_WITH_ZIPALIGN_PAGE_SIZE) >= 0;
  }

  public List<String> getZipalignCommand(Path input, Path output) {
    ImmutableList.Builder<String> command = ImmutableList.builder();
    command.add(getZipalign().toString());
    if (supportsZipalignPageSize()) {
      command.add("-P", "16");
    } else {
      command.add("-p");
    }
    return command.add("-f", "4", input.toString(), output.toString()).build();
  }

  public List<String> getApksignerSignCommand(
      Path keystore,
      String keystorePassword,
      String keyAlias,
      String keyPassword,
      Path input,
      Path output) {
    return ImmutableList.of(
        getApksigner().toString(),
        "sign",
        "--ks",
        keystore.toString(),
        "--ks-pass",
        keystorePassword,
        "--ks-key-alias",
        keyAlias,
        "--key-pass",
        keyPassword,
        "--out",
        output.toString(),
        input.toString());
  }

  public List<String> getApksignerVerifyCommand(Path apk) {
    return ImmutableList.of(getApksigner().toString(), "verify", "--print-certs", apk.toString());
  }

  public List<String> getAapt2BadgingCommand(Path apk) {
    return ImmutableList.of(getAapt2().toString(), "dump", "badging", apk.toString());
  }

  public static String getExecutableName(String tool, boolean windows) {
    if (!windows) {
      return tool;
    }
    return tool.equals("apksigner") ? "apksigner.bat" : tool + ".exe";
  }

  public static boolean isWindowsHost() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  /**
   * Finds the build-tools to use.
   *
   * <p>The SDK is the directory passed with {@code --sdk}, or else the first existing directory of
   * {@code $ANDROID_HOME}, {@code $ANDROID_SDK_ROOT} and {@code sdk.dir} in {@code
   * local.properties} in the working directory. The build-tools are the version passed with {@code
   * --build-tools}, or else the highest installed version that contains all {@link
   * #REQUIRED_TOOLS}.
   */
  public static AndroidBuildTools find(
      Path sdkOption,
      String versionOption,
      Function<String, String> environment,
      Path workingDirectory,
      boolean windows)
      throws NotFoundException {
    Path sdk = findSdk(sdkOption, environment, workingDirectory);
    return versionOption != null
        ? findVersion(sdk, versionOption, windows)
        : findHighestVersion(sdk, windows);
  }

  public static Path findSdk(
      Path sdkOption, Function<String, String> environment, Path workingDirectory)
      throws NotFoundException {
    if (sdkOption != null) {
      if (!Files.isDirectory(sdkOption)) {
        throw new NotFoundException(
            "The Android SDK directory passed with --sdk does not exist: " + sdkOption);
      }
      return sdkOption;
    }
    List<String> rejected = new ArrayList<>();
    for (String variable : ImmutableList.of("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
      String value = environment.apply(variable);
      if (value != null && !value.isEmpty()) {
        Path sdk = toDirectory(value, null);
        if (sdk != null) {
          return sdk;
        }
        rejected.add("$" + variable + " is not a directory: " + value);
      }
    }
    Path localProperties = workingDirectory.resolve("local.properties");
    if (Files.isRegularFile(localProperties)) {
      String sdkDir = readSdkDir(localProperties);
      if (sdkDir != null && !sdkDir.isEmpty()) {
        Path sdk = toDirectory(sdkDir, localProperties.toAbsolutePath().getParent());
        if (sdk != null) {
          return sdk;
        }
        rejected.add("sdk.dir in " + localProperties + " is not a directory: " + sdkDir);
      }
    }
    StringBuilder message =
        new StringBuilder(
            "Could not find the Android SDK. Pass --sdk <dir>, set ANDROID_HOME, or run from a"
                + " project directory with a local.properties file that sets sdk.dir.");
    for (String reason : rejected) {
      message.append(" ").append(reason).append(".");
    }
    throw new NotFoundException(message.toString());
  }

  private static Path toDirectory(String path, Path base) {
    try {
      Path directory = base != null ? base.resolve(path) : Paths.get(path);
      return Files.isDirectory(directory) ? directory : null;
    } catch (InvalidPathException e) {
      return null;
    }
  }

  private static String readSdkDir(Path localProperties) throws NotFoundException {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(localProperties)) {
      properties.load(input);
    } catch (IOException | IllegalArgumentException e) {
      throw new NotFoundException("Could not read " + localProperties + ": " + e.getMessage(), e);
    }
    return properties.getProperty("sdk.dir");
  }

  private static AndroidBuildTools findVersion(Path sdk, String version, boolean windows)
      throws NotFoundException {
    Path directory = sdk.resolve("build-tools").resolve(version);
    if (!Files.isDirectory(directory)) {
      throw new NotFoundException(
          "Build-tools "
              + version
              + " is not installed in "
              + sdk
              + ". "
              + getInstallHint(version));
    }
    List<String> missing = getMissingTools(directory, windows);
    if (!missing.isEmpty()) {
      throw new NotFoundException(
          "Build-tools "
              + version
              + " in "
              + directory
              + " does not contain "
              + String.join(", ", missing)
              + ". "
              + getInstallHint(version));
    }
    return new AndroidBuildTools(directory, version, windows);
  }

  private static AndroidBuildTools findHighestVersion(Path sdk, boolean windows)
      throws NotFoundException {
    Path buildToolsDirectory = sdk.resolve("build-tools");
    List<Path> directories = getVersionDirectoriesHighestFirst(buildToolsDirectory);
    List<String> incomplete = new ArrayList<>();
    for (Path directory : directories) {
      List<String> missing = getMissingTools(directory, windows);
      if (missing.isEmpty()) {
        return new AndroidBuildTools(directory, directory.getFileName().toString(), windows);
      }
      incomplete.add(directory.getFileName() + " (missing " + String.join(", ", missing) + ")");
    }
    String problem =
        directories.isEmpty()
            ? "No build-tools are installed in " + sdk + "."
            : "No build-tools version in "
                + buildToolsDirectory
                + " contains "
                + String.join(", ", REQUIRED_TOOLS)
                + ": "
                + String.join(", ", incomplete)
                + ".";
    throw new NotFoundException(problem + " " + getInstallHint(SUGGESTED_VERSION));
  }

  private static List<Path> getVersionDirectoriesHighestFirst(Path buildToolsDirectory)
      throws NotFoundException {
    List<Path> directories = new ArrayList<>();
    if (!Files.isDirectory(buildToolsDirectory)) {
      return directories;
    }
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(buildToolsDirectory)) {
      for (Path entry : entries) {
        if (Files.isDirectory(entry)) {
          directories.add(entry);
        }
      }
    } catch (IOException e) {
      throw new NotFoundException(
          "Could not list " + buildToolsDirectory + ": " + e.getMessage(), e);
    }
    directories.sort(
        Comparator.comparing(
                (Path directory) -> BuildToolsVersion.parse(directory.getFileName().toString()))
            .reversed());
    return directories;
  }

  private static List<String> getMissingTools(Path directory, boolean windows) {
    List<String> missing = new ArrayList<>();
    for (String tool : REQUIRED_TOOLS) {
      if (!Files.isRegularFile(directory.resolve(getExecutableName(tool, windows)))) {
        missing.add(getExecutableName(tool, windows));
      }
    }
    return missing;
  }

  private static String getInstallHint(String version) {
    String buildToolsPackage = "\"build-tools;" + version + "\"";
    return "Install them with: android sdk install "
        + buildToolsPackage
        + " (or: sdkmanager "
        + buildToolsPackage
        + ").";
  }

  /** Thrown when the SDK or the build-tools cannot be found. The message is shown to the user. */
  public static class NotFoundException extends Exception {

    public NotFoundException(String message) {
      super(message);
    }

    public NotFoundException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * A build-tools version, such as 35.0.0 or 34.0.0-rc3. Release candidates order before the
   * release, and directory names that do not start with a number order before all versions.
   */
  public static final class BuildToolsVersion implements Comparable<BuildToolsVersion> {

    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)(.*)");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(.*?)(\\d+)");

    private final List<String> numbers;
    private final String qualifier;

    private BuildToolsVersion(List<String> numbers, String qualifier) {
      this.numbers = numbers;
      this.qualifier = qualifier;
    }

    public static BuildToolsVersion parse(String name) {
      Matcher matcher = VERSION.matcher(name);
      if (!matcher.matches()) {
        return new BuildToolsVersion(ImmutableList.of(), name);
      }
      return new BuildToolsVersion(
          ImmutableList.copyOf(matcher.group(1).split("\\.", -1)), matcher.group(2));
    }

    @Override
    public int compareTo(BuildToolsVersion other) {
      if (numbers.isEmpty() || other.numbers.isEmpty()) {
        if (numbers.isEmpty() != other.numbers.isEmpty()) {
          return numbers.isEmpty() ? -1 : 1;
        }
        return qualifier.compareTo(other.qualifier);
      }
      for (int i = 0; i < Math.max(numbers.size(), other.numbers.size()); i++) {
        int result =
            compareNumbers(
                i < numbers.size() ? numbers.get(i) : "0",
                i < other.numbers.size() ? other.numbers.get(i) : "0");
        if (result != 0) {
          return result;
        }
      }
      if (qualifier.isEmpty() || other.qualifier.isEmpty()) {
        // A release orders after its release candidates.
        return Boolean.compare(qualifier.isEmpty(), other.qualifier.isEmpty());
      }
      Matcher matcher = TRAILING_NUMBER.matcher(qualifier);
      Matcher otherMatcher = TRAILING_NUMBER.matcher(other.qualifier);
      if (matcher.matches()
          && otherMatcher.matches()
          && matcher.group(1).equals(otherMatcher.group(1))) {
        return compareNumbers(matcher.group(2), otherMatcher.group(2));
      }
      return qualifier.compareTo(other.qualifier);
    }

    private static int compareNumbers(String number, String other) {
      String digits = stripLeadingZeros(number);
      String otherDigits = stripLeadingZeros(other);
      if (digits.length() != otherDigits.length()) {
        return Integer.compare(digits.length(), otherDigits.length());
      }
      return digits.compareTo(otherDigits);
    }

    private static String stripLeadingZeros(String number) {
      int start = 0;
      while (start < number.length() - 1 && number.charAt(start) == '0') {
        start++;
      }
      return number.substring(start);
    }
  }
}
