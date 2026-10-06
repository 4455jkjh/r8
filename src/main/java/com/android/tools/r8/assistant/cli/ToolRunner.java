// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** Runs external tools, such as the build-tools zipalign, apksigner and aapt2. */
public interface ToolRunner {

  /** Runs the command and returns its exit code and its combined stdout and stderr. */
  ToolResult run(List<String> command) throws IOException, InterruptedException;

  /** Runs the command in a new process. */
  static ToolResult runProcess(List<String> command) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    // apksigner is a launcher script that runs java from the PATH (or from JAVA_HOME on Windows).
    // Let it fall back to the JVM that runs the assistant.
    String javaHome = System.getProperty("java.home");
    if (javaHome != null) {
      Map<String, String> environment = builder.environment();
      environment.putIfAbsent("JAVA_HOME", javaHome);
      String javaBin = Paths.get(javaHome, "bin").toString();
      String pathKey = "PATH";
      for (String key : environment.keySet()) {
        if (key.equalsIgnoreCase("PATH")) {
          pathKey = key;
          break;
        }
      }
      String path = environment.get(pathKey);
      environment.put(
          pathKey, path == null || path.isEmpty() ? javaBin : path + File.pathSeparator + javaBin);
    }
    Process process = builder.start();
    try {
      process.getOutputStream().close();
      byte[] output;
      try (InputStream stdout = process.getInputStream()) {
        output = ByteStreams.toByteArray(stdout);
      }
      return new ToolResult(process.waitFor(), new String(output, StandardCharsets.UTF_8));
    } finally {
      // Does nothing if the process has exited.
      process.destroy();
    }
  }

  /** The exit code and output of a tool. */
  final class ToolResult {

    private final int exitCode;
    private final String output;

    public ToolResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public int getExitCode() {
      return exitCode;
    }

    public String getOutput() {
      return output;
    }
  }
}
