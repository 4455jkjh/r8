// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import com.android.tools.r8.DexSegments;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;

public class DebugInfoStats {

  int debugInfoNone = 0;
  int debugInfoEmbeddedPc = 0;
  int debugInfoEventBased = 0;

  public static DebugInfoStats create(DexApplication application) {
    DebugInfoStats stats = new DebugInfoStats();
    for (DexProgramClass clazz : application.classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (!method.isAbstract() && method.hasCode() && method.getCode().isDexCode()) {
          DexCode code = method.getCode().asDexCode();
          DexDebugInfo debugInfo = code.getDebugInfo();
          if (debugInfo == null) {
            stats.debugInfoNone++;
          } else if (debugInfo.isPcBasedInfo()) {
            stats.debugInfoEmbeddedPc++;
          } else if (debugInfo.isEventBasedInfo()) {
            stats.debugInfoEventBased++;
          }
        }
      }
    }
    return stats;
  }

  void printCsv(StringBuilder sb, DexSegments.Result dexSegments) {
    sb.append(debugInfoNone).append(';');
    sb.append(debugInfoEmbeddedPc).append(';');
    sb.append(debugInfoEventBased).append(';');
    sb.append(dexSegments.getDebugInfo().getSegmentSize()).append(';');
  }

  static void printEmptyCsv(StringBuilder sb) {
    sb.append(";;;;");
  }

  void printToStdout(String prefix, DexSegments.Result dexSegments) {
    System.out.println(prefix + "_dex_debug_info_none=" + debugInfoNone);
    System.out.println(prefix + "_dex_debug_info_embedded_pc=" + debugInfoEmbeddedPc);
    System.out.println(prefix + "_dex_debug_info_event_based=" + debugInfoEventBased);
    System.out.println(
        prefix + "_dex_debug_info_size=" + dexSegments.getDebugInfo().getSegmentSize());
  }
}
