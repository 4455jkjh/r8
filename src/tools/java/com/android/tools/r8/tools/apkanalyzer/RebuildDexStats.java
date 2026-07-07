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
  final StringStats stringStats;

  RebuildDexStats(
      int size,
      DebugInfoStats debugInfoStats,
      DexSegments.Result dexSegments,
      StringStats stringStats) {
    this.size = size;
    this.debugInfoStats = debugInfoStats;
    this.dexSegments = dexSegments;
    this.stringStats = stringStats;
  }

  void printCsv(StringBuilder sb, ApkAnalyzerResult result) {
    sb.append(size).append(';');
    sb.append(getImprovementString(result.dexSize.total, size)).append(';');
    sb.append(dexSegments.getCode().getSegmentSize()).append(';');
    sb.append(dexSegments.getStrings().getItemCount()).append(';');
    sb.append(dexSegments.getStrings().getSegmentSize()).append(';');
    sb.append(dexSegments.getStringData().getSegmentSize()).append(';');
    sb.append(stringStats.jumboStrings).append(';');
    sb.append(stringStats.uniqueConstStrings).append(';');
    sb.append(stringStats.singleRefIntegers).append(';');
    debugInfoStats.printCsv(sb, dexSegments);
  }

  static void printEmptyCsv(StringBuilder sb) {
    sb.append(";;;;;;;;");
    DebugInfoStats.printEmptyCsv(sb);
  }

  void printToStdout(String prefix, ApkAnalyzerResult result) {
    System.out.println(prefix + "_size=" + size);
    System.out.println(
        prefix + "_size_improvement=" + getImprovementString(result.dexSize.total, size));
    System.out.println(prefix + "_dex_code_size=" + dexSegments.getCode().getSegmentSize());
    System.out.println(prefix + "_dex_string_ids_len=" + dexSegments.getStrings().getItemCount());
    System.out.println(
        prefix + "_dex_string_ids_size=" + dexSegments.getStrings().getSegmentSize());
    System.out.println(
        prefix + "_dex_string_data_size=" + dexSegments.getStringData().getSegmentSize());
    System.out.println(prefix + "_dex_string_jumbo_count=" + stringStats.jumboStrings);
    System.out.println(prefix + "_dex_string_unique_count=" + stringStats.uniqueConstStrings);
    System.out.println(prefix + "_dex_string_single_ref_ints=" + stringStats.singleRefIntegers);
    debugInfoStats.printToStdout(prefix, dexSegments);
  }
}
