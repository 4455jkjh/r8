// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import com.android.tools.r8.dex.Marker;
import java.util.List;

class ApkAnalyzerResult {

  // Number of compressed dex files.
  final int dexCompressedCount;

  // Dex size stats.
  final MinMaxTotalStats dexSize;

  // Constant pool stats.
  final MinMaxTotalStats fields;
  final MinMaxTotalStats methods;
  final MinMaxTotalStats types;

  // Debug info stats.
  final int debugInfoNone;
  final int debugInfoEmbeddedPc;
  final int debugInfoEventBased;

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
  final Integer rebuildSize;
  final Integer rebuildDexOptSize;
  final Integer rebuildNoRefinementSize;
  final Integer rebuildContainerSize;
  final Integer rebuildContainerDexOptSize;

  ApkAnalyzerResult(
      int dexCountCompressed,
      MinMaxTotalStats dexSize,
      MinMaxTotalStats types,
      MinMaxTotalStats fields,
      MinMaxTotalStats methods,
      List<Marker> dexMarkers,
      DesugaredLibraryInfo desugaredLibInfo,
      int debugInfoNone,
      int debugInfoEmbeddedPc,
      int debugInfoEventBased,
      String mostOccurringSourceFile,
      int mostOccurringSourceFileCount,
      int runtimeInvisibleAnnotations,
      int[] classDepthCounts,
      Integer rebuildSize,
      Integer rebuildDexOptSize,
      Integer rebuildNoRefinementSize,
      Integer rebuildContainerSize,
      Integer rebuildContainerDexOptSize) {
    this.dexCompressedCount = dexCountCompressed;
    this.dexSize = dexSize;
    this.types = types;
    this.fields = fields;
    this.methods = methods;
    this.dexMarkers = dexMarkers;
    this.desugaredLibraryInfo = desugaredLibInfo;
    this.debugInfoNone = debugInfoNone;
    this.debugInfoEmbeddedPc = debugInfoEmbeddedPc;
    this.debugInfoEventBased = debugInfoEventBased;
    this.mostOccurringSourceFile = mostOccurringSourceFile;
    this.mostOccurringSourceFileCount = mostOccurringSourceFileCount;
    this.runtimeInvisibleAnnotations = runtimeInvisibleAnnotations;
    this.classDepthCounts = classDepthCounts;
    this.rebuildSize = rebuildSize;
    this.rebuildDexOptSize = rebuildDexOptSize;
    this.rebuildNoRefinementSize = rebuildNoRefinementSize;
    this.rebuildContainerSize = rebuildContainerSize;
    this.rebuildContainerDexOptSize = rebuildContainerDexOptSize;
  }
}
