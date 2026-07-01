// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import com.android.tools.r8.DexSegments;
import com.android.tools.r8.dex.Marker;
import java.util.List;

class ApkAnalyzerResult {

  // Number of compressed dex files.
  final int dexCompressedCount;

  // Dex size stats.
  final MinMaxTotalStats dexSize;
  final int jumboStrings;

  // Resource size stats.
  final MinMaxTotalStats resSize;
  final Long resTableCompressedSize;
  final Long resTableUncompressedSize;

  // Constant pool stats.
  final MinMaxTotalStats fields;
  final MinMaxTotalStats methods;
  final MinMaxTotalStats types;

  // Debug info stats.
  final DebugInfoStats debugInfoStats;
  final DexSegments.Result dexSegments;

  // Desugared library info.
  final DesugaredLibraryInfo desugaredLibraryInfo;

  // Marker stats.
  final List<Marker> dexMarkers;

  // Source file stats.
  final String mostOccurringSourceFile;
  final int mostOccurringSourceFileCount;

  // Annotation stats.
  final int runtimeInvisibleAnnotations;

  // Class depth stats.
  final int[] classDepthCounts;

  // Rebuilt size using D8.
  final RebuildDexStats rebuildResult;
  final RebuildDexStats rebuildDexOptResult;
  final RebuildDexStats rebuildMapOutResult;
  final RebuildDexStats rebuildReuseDistResult;
  final RebuildDexStats rebuildNoRefinementResult;
  final RebuildDexStats rebuildContainerResult;
  final RebuildDexStats rebuildContainerDexOptResult;
  final RebuildDexStats rebuildContainerDexOptMapOutSize;

  ApkAnalyzerResult(
      int dexCountCompressed,
      MinMaxTotalStats dexSize,
      MinMaxTotalStats resSize,
      Long resTableCompressedSize,
      Long resTableUncompressedSize,
      MinMaxTotalStats types,
      MinMaxTotalStats fields,
      MinMaxTotalStats methods,
      List<Marker> dexMarkers,
      DesugaredLibraryInfo desugaredLibInfo,
      DebugInfoStats debugInfoStats,
      DexSegments.Result dexSegments,
      int jumboStrings,
      String mostOccurringSourceFile,
      int mostOccurringSourceFileCount,
      int runtimeInvisibleAnnotations,
      int[] classDepthCounts,
      RebuildDexStats rebuildResult,
      RebuildDexStats rebuildDexOptResult,
      RebuildDexStats rebuildMapOutResult,
      RebuildDexStats rebuildReuseDistResult,
      RebuildDexStats rebuildNoRefinementResult,
      RebuildDexStats rebuildContainerResult,
      RebuildDexStats rebuildContainerDexOptResult,
      RebuildDexStats rebuildContainerDexOptMapOutSize) {
    this.dexCompressedCount = dexCountCompressed;
    this.dexSize = dexSize;
    this.resSize = resSize;
    this.resTableCompressedSize = resTableCompressedSize;
    this.resTableUncompressedSize = resTableUncompressedSize;
    this.types = types;
    this.fields = fields;
    this.methods = methods;
    this.dexMarkers = dexMarkers;
    this.desugaredLibraryInfo = desugaredLibInfo;
    this.debugInfoStats = debugInfoStats;
    this.dexSegments = dexSegments;
    this.jumboStrings = jumboStrings;
    this.mostOccurringSourceFile = mostOccurringSourceFile;
    this.mostOccurringSourceFileCount = mostOccurringSourceFileCount;
    this.runtimeInvisibleAnnotations = runtimeInvisibleAnnotations;
    this.classDepthCounts = classDepthCounts;
    this.rebuildResult = rebuildResult;
    this.rebuildDexOptResult = rebuildDexOptResult;
    this.rebuildMapOutResult = rebuildMapOutResult;
    this.rebuildReuseDistResult = rebuildReuseDistResult;
    this.rebuildNoRefinementResult = rebuildNoRefinementResult;
    this.rebuildContainerResult = rebuildContainerResult;
    this.rebuildContainerDexOptResult = rebuildContainerDexOptResult;
    this.rebuildContainerDexOptMapOutSize = rebuildContainerDexOptMapOutSize;
  }
}
