/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.resourceshrinker.obfuscation;

import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import java.util.List;

/** Records obfuscation mappings from single file in proguard format. */
public final class ProguardMappingsRecorder {

  private final List<String> mappingLines;

  /**
   * @param mappingLines mapping file lines.
   */
  public ProguardMappingsRecorder(List<String> mappingLines) {
    this.mappingLines = mappingLines;
  }

  public void recordObfuscationMappings(ResourceShrinkerModel model) {
    model.setObfuscatedClasses(extractObfuscatedResourceClasses());
  }

  ObfuscatedClasses extractObfuscatedResourceClasses() {
    // Proguard obfuscation mappings file has the following structure:
    // # comment1
    // com.package.MainClass -> a.a:
    //     int field -> a
    //     boolean getFlag() -> b
    // com.package.R -> a.b:
    // com.package.R$style -> a.b.a:
    //     int Toolbar_android_gravity -> i1
    ObfuscatedClasses.Builder builder = new ObfuscatedClasses.Builder();
    for (String line : mappingLines) {
      if (isMethodMapping(line)) {
        int arrow = line.indexOf("->");
        String left = trimWhitespace(line.substring(0, arrow));
        int lastParen = left.lastIndexOf('(');
        String beforeArgs = lastParen == -1 ? left : left.substring(0, lastParen);
        int firstSpace = beforeArgs.indexOf(' ');
        String originalMethod =
            firstSpace == -1 ? beforeArgs : beforeArgs.substring(firstSpace + 1);
        String obfuscatedMethod = trimWhitespace(line.substring(arrow + 2));
        builder.addMethodMapping(originalMethod, obfuscatedMethod);
      } else if (isClassMapping(line)) {
        int arrow = line.indexOf("->");
        String originalClass = trimWhitespace(line.substring(0, arrow));
        String obfuscatedClass = trimClassChars(line.substring(arrow + 2));
        builder.addClassMapping(originalClass, obfuscatedClass);
      }
    }
    return builder.build();
  }

  private static boolean isClassMapping(String line) {
    return line.contains("->");
  }

  private static boolean isMethodMapping(String line) {
    return (line.startsWith(" ") || line.startsWith("\t")) && line.contains("->");
  }

  private static String trimWhitespace(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isWhitespace(value.charAt(start))) {
      start++;
    }
    while (end > start && isWhitespace(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static String trimClassChars(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isClassTrimChar(value.charAt(start))) {
      start++;
    }
    while (end > start && isClassTrimChar(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isClassTrimChar(char c) {
    return c == ' ' || c == '\t' || c == ':';
  }
}
