// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringStats {

  int jumboStrings;
  int singleRefIntegers;
  int uniqueConstStrings;

  public static StringStats create(DexApplication application) {
    StringStats stringStats = new StringStats();
    Map<DexString, Integer> integerStringReferenceCounts = new HashMap<>();
    Set<DexString> seenConstStrings = new HashSet<>();
    for (DexProgramClass clazz : application.classes()) {
      for (DexEncodedMethod method : clazz.methods(DexEncodedMethod::hasCode)) {
        if (method.getCode().isDexCode()) {
          DexCode code = method.getCode().asDexCode();
          for (DexInstruction instruction : code.getInstructions()) {
            DexString string;
            if (instruction.isConstString()) {
              string = instruction.asConstString().getString();
            } else if (instruction.isConstString16()) {
              string = instruction.asConstString16().getString();
            } else if (instruction.isConstStringJumbo()) {
              string = instruction.asConstStringJumbo().getString();
              stringStats.jumboStrings++;
            } else {
              continue;
            }
            if (isDigitsOnly(string.toString())) {
              integerStringReferenceCounts.put(
                  string, integerStringReferenceCounts.getOrDefault(string, 0) + 1);
            }
            seenConstStrings.add(string);
          }
        }
      }
    }
    stringStats.singleRefIntegers =
        (int) integerStringReferenceCounts.values().stream().filter(count -> count == 1).count();
    stringStats.uniqueConstStrings = seenConstStrings.size();
    return stringStats;
  }

  public static boolean isDigitsOnly(String str) {
    return str != null && !str.isEmpty() && str.chars().allMatch(Character::isDigit);
  }
}
