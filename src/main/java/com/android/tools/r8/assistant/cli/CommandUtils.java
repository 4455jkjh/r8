// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.utils.internal.FileUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the assistant commands. */
final class CommandUtils {

  private CommandUtils() {}

  static Path toPath(String option, String value, List<String> errors) {
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

  /** Throws the first of the parse errors, if any. */
  static void checkNoErrors(List<String> errors) throws AssistantUsageException {
    if (!errors.isEmpty()) {
      throw new AssistantUsageException(errors.get(0));
    }
  }

  /** Prints an error and returns false if one of the files does not exist. */
  static boolean checkFilesExist(String option, List<Path> paths, PrintStream err) {
    for (Path path : paths) {
      if (!Files.isRegularFile(path)) {
        err.println("Error: the file given to " + option + " does not exist: " + path);
        return false;
      }
    }
    return true;
  }

  static String describePaths(List<Path> paths) {
    List<String> names = new ArrayList<>();
    for (Path path : paths) {
      names.add(path.toString());
    }
    return String.join(", ", names);
  }

  static void writeFile(Path path, String text) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    FileUtils.writeTextFile(path, text);
  }

  static void printWarnings(List<String> warnings, PrintStream err) {
    for (String warning : warnings) {
      err.println("Warning: " + warning);
    }
  }

  static String plural(int count, String singular, String plural) {
    return count + " " + (count == 1 ? singular : plural);
  }
}
