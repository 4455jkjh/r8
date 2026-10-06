// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Checks that the R8 Assistant command lines in the agent skill in src/assistant/skill parse.
 *
 * <p>The skill runs the jar only in fenced code blocks, as {@code java -jar "$R8_ASSISTANT_JAR"}
 * followed by the arguments, with a backslash at the end of each continued line, or in PowerShell
 * as {@code java -jar "$env:R8_ASSISTANT_JAR"} followed by the arguments on one line. The test
 * splits each command line into words as a shell does, without expansions, and parses the words
 * after the jar with {@link AssistantCommandLine#parse}.
 */
@RunWith(Parameterized.class)
public class SkillCommandLinesTest extends TestBase {

  private static final Path SKILL_DIR = Paths.get(ToolHelper.SOURCE_DIR, "assistant", "skill");

  private static final String CODE_FENCE = "```";
  private static final String JAVA_JAR = "java -jar";
  private static final List<String> JAR_INVOCATIONS =
      ImmutableList.of("java -jar \"$R8_ASSISTANT_JAR\" ", "java -jar \"$env:R8_ASSISTANT_JAR\" ");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testCommandLinesParse() throws IOException {
    int commandLineCount = 0;
    List<String> failures = new ArrayList<>();
    for (Path file : getMarkdownFiles()) {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      boolean inCodeBlock = false;
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (isCodeFence(line)) {
          inCodeBlock = !inCodeBlock;
          continue;
        }
        if (!line.contains(JAVA_JAR)) {
          continue;
        }
        String location = SKILL_DIR.relativize(file) + ":" + (i + 1) + ": ";
        if (!inCodeBlock || !isJarInvocation(line)) {
          failures.add(location + "Jar call that the test does not check: " + line.trim());
          continue;
        }
        // Join continued lines as the shell does, by removing each backslash and line break.
        StringBuilder commandLine = new StringBuilder(line);
        while (line.endsWith("\\") && i + 1 < lines.size() && !isCodeFence(lines.get(i + 1))) {
          commandLine.setLength(commandLine.length() - 1);
          line = lines.get(++i);
          commandLine.append(line);
        }
        commandLineCount++;
        try {
          List<String> words = splitShellWords(commandLine.toString());
          // Skip the words java, -jar and the jar.
          AssistantCommandLine.parse(words.subList(3, words.size()));
        } catch (AssistantUsageException e) {
          failures.add(
              location
                  + commandLine.toString().trim().replaceAll("\\s+", " ")
                  + ": "
                  + e.getMessage());
        }
      }
    }
    assertTrue(StringUtils.lines(failures), failures.isEmpty());
    assertNotEquals(0, commandLineCount);
  }

  private static List<Path> getMarkdownFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(SKILL_DIR)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".md"))
          .sorted()
          .collect(Collectors.toList());
    }
  }

  private static boolean isCodeFence(String line) {
    return line.trim().startsWith(CODE_FENCE);
  }

  private static boolean isJarInvocation(String line) {
    return JAR_INVOCATIONS.stream().anyMatch(line.trim()::startsWith);
  }

  /**
   * Splits a command line into words as a POSIX shell does, with quote removal but without
   * expansions. Rejects operators such as pipes and redirections.
   */
  private static List<String> splitShellWords(String commandLine) throws AssistantUsageException {
    List<String> words = new ArrayList<>();
    StringBuilder word = null;
    for (int i = 0; i < commandLine.length(); i++) {
      char c = commandLine.charAt(i);
      if (Character.isWhitespace(c)) {
        if (word != null) {
          words.add(word.toString());
          word = null;
        }
        continue;
      }
      if (word == null) {
        word = new StringBuilder();
      }
      if (c == '\'') {
        int end = commandLine.indexOf('\'', i + 1);
        if (end < 0) {
          throw new AssistantUsageException("Unterminated single quote");
        }
        word.append(commandLine, i + 1, end);
        i = end;
      } else if (c == '"') {
        for (i++; i < commandLine.length() && commandLine.charAt(i) != '"'; i++) {
          if (commandLine.charAt(i) == '\\'
              && i + 1 < commandLine.length()
              && "$`\"\\".indexOf(commandLine.charAt(i + 1)) >= 0) {
            i++;
          }
          word.append(commandLine.charAt(i));
        }
        if (i == commandLine.length()) {
          throw new AssistantUsageException("Unterminated double quote");
        }
      } else if (c == '\\') {
        if (++i == commandLine.length()) {
          throw new AssistantUsageException("Line continuation at the end of the code block");
        }
        word.append(commandLine.charAt(i));
      } else if ("|&;<>()`".indexOf(c) >= 0) {
        throw new AssistantUsageException("Unsupported shell syntax: " + c);
      } else {
        word.append(c);
      }
    }
    if (word != null) {
      words.add(word.toString());
    }
    return words;
  }
}
