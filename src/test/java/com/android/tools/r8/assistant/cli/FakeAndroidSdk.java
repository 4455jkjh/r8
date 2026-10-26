// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A fake Android SDK for tests on hosts without an SDK. Its build-tools are POSIX shell scripts:
 * aapt2 prints badging for a debuggable app, apksigner verify reports an unsigned APK, and zipalign
 * and apksigner sign copy their input to their output.
 */
public class FakeAndroidSdk {

  private static final String BUILD_TOOLS_VERSION = "36.0.0";

  public static final String DEBUGGABLE_BADGING =
      StringUtils.lines(
          "package: name='com.example.app' versionCode='1' versionName='1.0'",
          "application-debuggable");

  private final Path directory;
  private final Path buildTools;
  private final Path keystore;

  private FakeAndroidSdk(Path directory, Path buildTools, Path keystore) {
    this.directory = directory;
    this.buildTools = buildTools;
    this.keystore = keystore;
  }

  /** Creates the SDK in the directory. */
  public static FakeAndroidSdk create(Path directory) throws IOException {
    Path buildTools = directory.resolve("build-tools").resolve(BUILD_TOOLS_VERSION);
    Files.createDirectories(buildTools);
    writeScript(buildTools.resolve("aapt2"), "cat \"$dir/badging.txt\"");
    writeScript(
        buildTools.resolve("apksigner"),
        "if [ \"$1\" = verify ]; then",
        "  echo 'DOES NOT VERIFY'",
        "  exit 1",
        "fi",
        "out=",
        "while [ $# -gt 1 ]; do",
        "  if [ \"$1\" = --out ]; then out=$2; fi",
        "  shift",
        "done",
        "cp \"$1\" \"$out\"");
    writeScript(
        buildTools.resolve("zipalign"), "while [ $# -gt 2 ]; do shift; done", "cp \"$1\" \"$2\"");
    // The fake apksigner does not read the keystore.
    Path keystore = Files.createFile(directory.resolve("debug.keystore"));
    return new FakeAndroidSdk(directory, buildTools, keystore).setBadging(DEBUGGABLE_BADGING);
  }

  private static void writeScript(Path script, String... lines) throws IOException {
    String content =
        StringUtils.lines("#!/bin/sh", "dir=$(dirname \"$0\")") + StringUtils.lines(lines);
    Files.write(script, content.getBytes(StandardCharsets.UTF_8));
    assertTrue(script.toFile().setExecutable(true));
  }

  public Path getDirectory() {
    return directory;
  }

  /** An empty file to pass as the keystore. */
  public Path getKeystore() {
    return keystore;
  }

  /** Sets the output of {@code aapt2 dump badging}. */
  private FakeAndroidSdk setBadging(String output) throws IOException {
    Files.write(buildTools.resolve("badging.txt"), output.getBytes(StandardCharsets.UTF_8));
    return this;
  }
}
