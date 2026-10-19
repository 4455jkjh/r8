// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.retrace.stacktraces;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.utils.internal.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RetraceInternalStackTraceForTest implements StackTraceForTest {

  private final String obfuscatedFile;
  private final String deobfuscatedFile;

  public RetraceInternalStackTraceForTest(String obfuscatedFile, String deobfuscatedFile) {
    this.obfuscatedFile = obfuscatedFile;
    this.deobfuscatedFile = deobfuscatedFile;
  }

  @Override
  public List<String> obfuscatedStackTrace() {
    Path obfuscatedPath = TestDeps.getRetraceInternalDir().resolve(obfuscatedFile);
    try {
      return FileUtils.readAllLines(obfuscatedPath);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read file " + obfuscatedPath.toString());
    }
  }

  @Override
  public String mapping() {
    Path mappingPath = TestDeps.getRetraceInternalDir().resolve("mapping.txt");
    try {
      return new String(Files.readAllBytes(mappingPath));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read mapping file " + mappingPath.toString());
    }
  }

  @Override
  public List<String> retracedStackTrace() {
    Path deobfuscatedPath = TestDeps.getRetraceInternalDir().resolve(deobfuscatedFile);
    try {
      return FileUtils.readAllLines(deobfuscatedPath);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read file " + deobfuscatedPath.toString());
    }
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    throw new RuntimeException("Do not test");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
