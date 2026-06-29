// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexSection;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkAnalyzer {

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("Usage: ApkAnalyzer <apk-path> [--csv]");
      System.exit(1);
    }

    String apkPathString = null;
    boolean csv = false;
    for (String arg : args) {
      if (arg.equals("--csv")) {
        csv = true;
      } else if (apkPathString == null) {
        apkPathString = arg;
      } else {
        System.err.println("Unexpected argument: " + arg);
        System.exit(1);
      }
    }

    if (apkPathString == null) {
      System.err.println("Usage: ApkAnalyzer <apk-path> [--csv]");
      System.exit(1);
    }

    Path apkPath = Paths.get(apkPathString);
    if (!Files.exists(apkPath)) {
      throw new NoSuchFileException(apkPathString);
    }

    ApkAnalyzerResult result = analyzeApk(apkPath);
    if (csv) {
      printCsv(result);
    } else {
      printResult(result);
    }
  }

  private static void printResult(ApkAnalyzerResult result) {
    System.out.println("dex_files=" + result.dexSize.count);
    System.out.println("dex_files_compressed=" + result.dexCompressedCount);
    System.out.println("dex_file_size_min=" + result.dexSize.min);
    System.out.println("dex_file_size_max=" + result.dexSize.max);
    System.out.println("dex_file_size_total=" + result.dexSize.total);
    System.out.println("dex_file_types_min=" + result.types.min);
    System.out.println("dex_file_types_max=" + result.types.max);
    System.out.println("dex_file_types_total=" + result.types.total);
    System.out.println("dex_file_fields_min=" + result.fields.min);
    System.out.println("dex_file_fields_max=" + result.fields.max);
    System.out.println("dex_file_fields_total=" + result.fields.total);
    System.out.println("dex_file_methods_min=" + result.methods.min);
    System.out.println("dex_file_methods_max=" + result.methods.max);
    System.out.println("dex_file_methods_total=" + result.methods.total);
    System.out.println("dex_debug_info_none=" + result.debugInfoNone);
    System.out.println("dex_debug_info_embedded_pc=" + result.debugInfoEmbeddedPc);
    System.out.println("dex_debug_info_event_based=" + result.debugInfoEventBased);
    for (int markerIndex = 0; markerIndex < result.markers.size(); markerIndex++) {
      System.out.println("marker_" + markerIndex + "=" + result.markers.get(markerIndex));
    }
    if (result.desugaredLibraryInfo != null) {
      System.out.println("dex_file_desugared_library_index=" + result.desugaredLibraryInfo.index);
      System.out.println("dex_file_desugared_library_size=" + result.desugaredLibraryInfo.size);
    }
    Long minApi = getMinApi(result.markers);
    if (minApi != null) {
      System.out.println("min_api=" + minApi);
    }
    if (result.mostOccurringSourceFile != null) {
      System.out.println("source_file=" + result.mostOccurringSourceFile);
      System.out.println("source_file_count=" + result.mostOccurringSourceFileCount);
    }
    System.out.println("annotation_invisible_count=" + result.runtimeInvisibleAnnotations);
    for (int d = 0; d < 10; d++) {
      System.out.println("class_depth_" + d + "_count=" + result.classDepthCounts[d]);
    }
    System.out.println("class_depth_10_or_higher_count=" + result.classDepthCounts[10]);
  }

  private static void printCsv(ApkAnalyzerResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append(result.dexSize.count).append(';');
    sb.append(result.dexCompressedCount).append(';');
    sb.append(result.dexSize.min).append(';');
    sb.append(result.dexSize.max).append(';');
    sb.append(result.dexSize.total).append(';');
    sb.append(result.types.min).append(';');
    sb.append(result.types.max).append(';');
    sb.append(result.types.total).append(';');
    sb.append(result.fields.min).append(';');
    sb.append(result.fields.max).append(';');
    sb.append(result.fields.total).append(';');
    sb.append(result.methods.min).append(';');
    sb.append(result.methods.max).append(';');
    sb.append(result.methods.total).append(';');
    sb.append(result.debugInfoNone).append(';');
    sb.append(result.debugInfoEmbeddedPc).append(';');
    sb.append(result.debugInfoEventBased).append(';');
    if (result.desugaredLibraryInfo != null) {
      sb.append(result.desugaredLibraryInfo.index).append(';');
      sb.append(result.desugaredLibraryInfo.size).append(';');
    } else {
      sb.append(';');
      sb.append(';');
    }
    Long minApi = getMinApi(result.markers);
    if (minApi != null) {
      sb.append(minApi).append(';');
    } else {
      sb.append(';');
    }
    if (result.mostOccurringSourceFile != null) {
      sb.append(result.mostOccurringSourceFile).append(';');
      sb.append(result.mostOccurringSourceFileCount).append(';');
    } else {
      sb.append(';');
      sb.append(';');
    }
    sb.append(result.runtimeInvisibleAnnotations).append(';');
    for (int d = 0; d < 10; d++) {
      sb.append(result.classDepthCounts[d]).append(';');
    }
    sb.append(result.classDepthCounts[10]);
    sb.append(';');
    for (Marker marker : result.markers) {
      sb.append(marker);
    }
    System.out.println(sb);
  }

  private static ApkAnalyzerResult analyzeApk(Path apkPath) throws IOException {
    DexApplication application = readApplication(apkPath);
    int dexCompressedCount = 0;
    MinMaxTotalStats dexSize = new MinMaxTotalStats();
    MinMaxTotalStats fields = new MinMaxTotalStats();
    MinMaxTotalStats methods = new MinMaxTotalStats();
    MinMaxTotalStats types = new MinMaxTotalStats();
    try (ZipFile zipFile = new ZipFile(apkPath.toFile())) {
      DesugaredLibraryInfo desugaredLibraryInfo = findDesugaredLibrary(application, zipFile);
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String entryName = entry.getName();
        if (!isStandardDexFile(entryName)) {
          continue;
        }
        if (desugaredLibraryInfo != null && getDexIndex(entryName) == desugaredLibraryInfo.index) {
          continue;
        }
        dexCompressedCount += BooleanUtils.intValue(entry.getMethod() != ZipEntry.STORED);
        long size = entry.getSize();
        if (size < 0) {
          throw new RuntimeException("Unknown size");
        }
        dexSize.add(size);
        try (InputStream is = zipFile.getInputStream(entry)) {
          parseDexStats(is, fields, methods, types);
        }
      }

      int debugInfoNone = 0;
      int debugInfoEmbeddedPc = 0;
      int debugInfoEventBased = 0;

      for (DexProgramClass clazz : application.classes()) {
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isAbstract() && method.hasCode() && method.getCode().isDexCode()) {
            DexCode code = method.getCode().asDexCode();
            DexDebugInfo debugInfo = code.getDebugInfo();
            if (debugInfo == null) {
              debugInfoNone++;
            } else if (debugInfo.isPcBasedInfo()) {
              debugInfoEmbeddedPc++;
            } else if (debugInfo.isEventBasedInfo()) {
              debugInfoEventBased++;
            }
          }
        }
      }

      String mostOccurringSourceFile = null;
      int mostOccurringSourceFileCount = 0;
      Map<String, Integer> sourceFileCounts = new HashMap<>();
      for (DexProgramClass clazz : application.classes()) {
        DexString sourceFile = clazz.getSourceFile();
        if (sourceFile != null) {
          String sourceFileStr = sourceFile.toString();
          sourceFileCounts.put(sourceFileStr, sourceFileCounts.getOrDefault(sourceFileStr, 0) + 1);
        }
      }
      for (Map.Entry<String, Integer> entry : sourceFileCounts.entrySet()) {
        if (entry.getValue() > mostOccurringSourceFileCount) {
          mostOccurringSourceFileCount = entry.getValue();
          mostOccurringSourceFile = entry.getKey();
        }
      }

      int runtimeInvisibleAnnotations = 0;
      for (DexProgramClass clazz : application.classes()) {
        runtimeInvisibleAnnotations += countRuntimeInvisibleAnnotations(clazz.annotations());
        for (DexEncodedField field : clazz.fields()) {
          runtimeInvisibleAnnotations += countRuntimeInvisibleAnnotations(field.annotations());
        }
        for (DexEncodedMethod method : clazz.methods()) {
          runtimeInvisibleAnnotations += countRuntimeInvisibleAnnotations(method.annotations());
          for (DexAnnotationSet annotationSet :
              method.getParameterAnnotations().getAnnotationSets()) {
            runtimeInvisibleAnnotations += countRuntimeInvisibleAnnotations(annotationSet);
          }
        }
      }

      int[] depthCounts = new int[11];
      for (DexProgramClass clazz : application.classes()) {
        String descriptor = clazz.type.toDescriptorString();
        int depth = getPackageDepth(descriptor);
        if (depth >= 10) {
          depthCounts[10]++;
        } else {
          depthCounts[depth]++;
        }
      }

      return new ApkAnalyzerResult(
          dexCompressedCount,
          dexSize.finish(),
          types.finish(),
          fields.finish(),
          methods.finish(),
          new ArrayList<>(application.dexItemFactory.extractMarkers()),
          desugaredLibraryInfo,
          debugInfoNone,
          debugInfoEmbeddedPc,
          debugInfoEventBased,
          mostOccurringSourceFile,
          mostOccurringSourceFileCount,
          runtimeInvisibleAnnotations,
          depthCounts);
    }
  }

  private static void parseDexStats(
      InputStream is, MinMaxTotalStats fields, MinMaxTotalStats methods, MinMaxTotalStats types)
      throws IOException {
    List<DexSection> sections = DexParser.parseMapFrom(is, Origin.unknown());
    for (DexSection section : sections) {
      if (section.type == Constants.TYPE_FIELD_ID_ITEM) {
        fields.add(section.length);
      } else if (section.type == Constants.TYPE_METHOD_ID_ITEM) {
        methods.add(section.length);
      } else if (section.type == Constants.TYPE_TYPE_ID_ITEM) {
        types.add(section.length);
      }
    }
  }

  private static DexApplication readApplication(Path apkPath) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    List<String> filters = Collections.singletonList("classes*.dex");
    FilteredClassPath filteredPath =
        new FilteredClassPath(apkPath, filters, new PathOrigin(apkPath), Position.UNKNOWN);
    builder.createAndAddProvider(filteredPath);
    InternalOptions options = new InternalOptions();
    options.skipReadingDexCode = false;
    options.setMinApiLevel(AndroidApiLevel.P);
    return new ApplicationReader(builder.build(), options, Timing.empty()).read();
  }

  private static DesugaredLibraryInfo findDesugaredLibrary(
      DexApplication application, ZipFile zipFile) {
    for (DexProgramClass clazz : application.classes()) {
      String descriptor = clazz.type.toDescriptorString();
      if (descriptor.startsWith("Lj$/")) {
        Origin origin = clazz.getOrigin();
        if (origin instanceof ArchiveEntryOrigin) {
          String entryName = ((ArchiveEntryOrigin) origin).getEntryName();
          int index = getDexIndex(entryName);
          if (index != -1) {
            ZipEntry entry = zipFile.getEntry(entryName);
            return new DesugaredLibraryInfo(index, entry.getSize());
          }
        }
      }
    }
    return null;
  }

  private static int getDexIndex(String entryName) {
    if (entryName.equals("classes.dex")) {
      return 0;
    }
    if (entryName.startsWith("classes") && entryName.endsWith(".dex")) {
      String middle = entryName.substring(7, entryName.length() - 4);
      try {
        return Integer.parseInt(middle) - 1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }
    return -1;
  }

  private static Long getMinApi(List<Marker> markers) {
    Long commonMinApi = null;
    for (Marker marker : markers) {
      if (!marker.hasMinApi()) {
        return null;
      }
      long markerMinApi = marker.getMinApi();
      if (commonMinApi == null) {
        commonMinApi = markerMinApi;
      } else if (commonMinApi != markerMinApi) {
        return null;
      }
    }
    return commonMinApi;
  }

  private static int countRuntimeInvisibleAnnotations(DexAnnotationSet set) {
    int result = 0;
    for (DexAnnotation annotation : set.annotations) {
      if (annotation.visibility == DexAnnotation.VISIBILITY_BUILD) {
        result++;
      }
    }
    return result;
  }

  private static int getPackageDepth(String descriptor) {
    int slashes = 0;
    for (int i = 1; i < descriptor.length() - 1; i++) {
      if (descriptor.charAt(i) == '/') {
        slashes++;
      }
    }
    return slashes;
  }

  private static boolean isStandardDexFile(String name) {
    if (!name.endsWith(".dex")) {
      return false;
    }
    if (name.equals("classes.dex")) {
      return true;
    }
    if (name.startsWith("classes") && name.length() > 11) {
      String middle = name.substring(7, name.length() - 4);
      try {
        Integer.parseInt(middle);
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }
}
