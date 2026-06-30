// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import static com.android.tools.r8.tools.apkanalyzer.ApkAnalyzer.getImprovementString;

import com.android.tools.r8.DexSegments;

class RebuildDexStats {

  final int size;
  final DebugInfoStats debugInfoStats;
  final DexSegments.Result dexSegments;
  final int jumboStrings;

  RebuildDexStats(
      int size, DebugInfoStats debugInfoStats, DexSegments.Result dexSegments, int jumboStrings) {
    this.size = size;
    this.debugInfoStats = debugInfoStats;
    this.dexSegments = dexSegments;
    this.jumboStrings = jumboStrings;
  }

  void printCsv(StringBuilder sb, ApkAnalyzerResult result) {
    sb.append(size).append(';');
    sb.append(getImprovementString(result.dexSize.total, size)).append(';');
    sb.append(dexSegments.getCode().getSegmentSize()).append(';');
    sb.append(jumboStrings).append(';');
    debugInfoStats.printCsv(sb, dexSegments);
  }

  static void printEmptyCsv(StringBuilder sb) {
    sb.append(";;;;");
    DebugInfoStats.printEmptyCsv(sb);
  }

  void printToStdout(String prefix, ApkAnalyzerResult result) {
    System.out.println(prefix + "_size=" + size);
    System.out.println(
        prefix + "_size_improvement=" + getImprovementString(result.dexSize.total, size));
    System.out.println(prefix + "_dex_code_size=" + dexSegments.getCode().getSegmentSize());
    System.out.println(prefix + "_jumbo_strings=" + jumboStrings);
    debugInfoStats.printToStdout(prefix, dexSegments);
  }
}
