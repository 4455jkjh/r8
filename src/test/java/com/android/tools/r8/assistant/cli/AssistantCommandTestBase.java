// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs assistant commands in-process and writes reflection logs for them. */
public abstract class AssistantCommandTestBase extends TestBase {

  static final String LOADER = "dalvik.system.PathClassLoader";

  Path writeFile(String name, String... lines) throws IOException {
    return FileUtils.writeTextFile(temp.getRoot().toPath().resolve(name), lines);
  }

  static String readFile(Path file) throws IOException {
    return normalize(FileUtils.readTextFile(file, StandardCharsets.UTF_8));
  }

  /**
   * Parses the arguments, which are converted with toString(), and runs the command. Line
   * separators in the output are normalized to {@code \n}.
   */
  static ProcessResult run(AssistantCommand command, Object... args) throws Exception {
    List<String> arguments = new ArrayList<>();
    for (Object arg : args) {
      arguments.add(arg.toString());
    }
    command.parseArguments(arguments);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    String encoding = StandardCharsets.UTF_8.name();
    int exitCode;
    try (PrintStream outStream = new PrintStream(out, true, encoding);
        PrintStream errStream = new PrintStream(err, true, encoding)) {
      exitCode = command.run(outStream, errStream);
    }
    return new ProcessResult(
        exitCode,
        normalize(out.toString(encoding)),
        normalize(err.toString(encoding)),
        command.getName() + " " + String.join(" ", arguments));
  }

  static String normalize(String text) {
    return text.replace("\r\n", "\n");
  }

  /**
   * A request line of a reflection log, as written by {@code ReflectiveOperationJsonLogger}, with
   * the caller as the only stack frame. Null arguments are written as JSON null.
   */
  static String request(int id, String event, String caller, String... args) {
    JsonObject object = new JsonObject();
    object.addProperty("event", event);
    if (id >= 0) {
      object.addProperty("id", id);
    }
    JsonArray stack = new JsonArray();
    stack.add(caller);
    object.add("stack", stack);
    JsonArray array = new JsonArray();
    for (String arg : args) {
      array.add(arg == null ? JsonNull.INSTANCE : new JsonPrimitive(arg));
    }
    object.add("args", array);
    return object.toString();
  }

  /** An after-call line for a request that returned a string, such as a class name. */
  static String returned(int id, String result) {
    return returned(id, result == null ? JsonNull.INSTANCE : new JsonPrimitive(result));
  }

  /** An after-call line for a request that returned a boolean. */
  static String returned(int id, boolean result) {
    return returned(id, new JsonPrimitive(result));
  }

  /** An after-call line for a bulk lookup that returned the given members. */
  static String returnedMembers(int id, String... members) {
    JsonArray array = new JsonArray();
    for (String member : members) {
      array.add(member);
    }
    return returned(id, array);
  }

  /**
   * An after-call line for an event that returned a list of types, such as getGenericInterfaces().
   */
  static String returnedTypes(int id, String... types) {
    return returnedMembers(id, types);
  }

  private static String returned(int id, JsonElement result) {
    JsonObject object = new JsonObject();
    object.addProperty("returned", id);
    object.add("result", result);
    return object.toString();
  }
}
