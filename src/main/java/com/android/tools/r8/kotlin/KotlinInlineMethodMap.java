// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.collections.ImmutableDisjointIntRangeMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Supports querying {@code (file, lineStart, lineEnd)} pairs for which method that refers to in the
 * input. This is only collected for files which are pointed to by Kotlin inline annotations. It is
 * also only collected if a mapping file will be generated.
 *
 * <p>Context: When Kotlin compiles code that inlines functions, it adds a {@code
 * @kotlin.jvm.internal.SourceDebugExtension} (SMAP) attribute to the caller class mapping compiler
 * line numbers back to source lines in the inlinee source file. However, SMAP data only contains
 * the inlinee file path and line numbers, not the method signatures of the inlined functions.
 * During mapping file generation (e.g. in {@link
 * com.android.tools.r8.utils.positions.LineNumberOptimizer}), R8 reconstructs inlined frames by
 * looking up which source method corresponded to each inlined line.
 */
public class KotlinInlineMethodMap {

  private static final KotlinInlineMethodMap EMPTY =
      new KotlinInlineMethodMap(Collections.emptyMap());

  private final Map<DexType, ImmutableDisjointIntRangeMap<DexMethod>> inlineMethodMap;

  private KotlinInlineMethodMap(
      Map<DexType, ImmutableDisjointIntRangeMap<DexMethod>> inlineMethodMap) {
    this.inlineMethodMap = inlineMethodMap;
  }

  public static KotlinInlineMethodMap empty() {
    return EMPTY;
  }

  public DexMethod lookup(DexType type, int line) {
    ImmutableDisjointIntRangeMap<DexMethod> classLines = inlineMethodMap.get(type);
    return classLines != null ? classLines.get(line) : null;
  }

  public static KotlinInlineMethodMap create(
      Collection<DexProgramClass> classes,
      InternalOptions options,
      DexItemFactory factory,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    if (!options.shouldOutputMappingFile()) {
      // Null is used to differentiate non-initialized and empty.
      return null;
    }
    return timing.time(
        "Extract kotlin inline method map",
        () -> createInternal(classes, options, factory, executorService));
  }

  private static KotlinInlineMethodMap createInternal(
      Collection<DexProgramClass> classes,
      InternalOptions options,
      DexItemFactory factory,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexType> inlineeClasses = findInlineReferences(classes, options, factory, executorService);
    if (inlineeClasses.isEmpty()) {
      return empty();
    }
    var classLines = collectClassLines(classes, options, executorService, inlineeClasses);
    return new KotlinInlineMethodMap(classLines);
  }

  private static Map<DexType, ImmutableDisjointIntRangeMap<DexMethod>> collectClassLines(
      Collection<DexProgramClass> classes,
      InternalOptions options,
      ExecutorService executorService,
      Set<DexType> inlineeClasses)
      throws ExecutionException {
    Collection<Entry<DexType, ImmutableDisjointIntRangeMap<DexMethod>>> classLines =
        ThreadUtils.processItemsThatMatchesWithResultsThatMatches(
            classes,
            clazz -> inlineeClasses.contains(clazz.getType()),
            KotlinInlineMethodMap::computeClassLines,
            Objects::nonNull,
            options,
            executorService);
    return ImmutableMap.copyOf(classLines);
  }

  private static Set<DexType> findInlineReferences(
      Collection<DexProgramClass> classes,
      InternalOptions options,
      DexItemFactory factory,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexType> inlineeClasses = ConcurrentHashMap.newKeySet();
    ThreadUtils.processItemsWithFilterPreprocessing(
        classes,
        clazz -> {
          DexAnnotation annotation =
              clazz.annotations().getFirstMatching(factory.annotationSourceDebugExtension);
          if (annotation == null || annotation.annotation.elements.length == 0) {
            return null;
          }
          DexValue value = annotation.annotation.elements[0].value;
          if (!value.isDexValueString()) {
            return null;
          }
          return value.asDexValueString();
        },
        (clazz, value) -> {
          var smap = KotlinSourceDebugExtensionParser.parse(value);
          if (smap == null) {
            return;
          }
          smap.getInlineePositions()
              .forEach(
                  inlineePosition -> {
                    String internalName = inlineePosition.getSource().getPath();
                    String descriptor =
                        DescriptorUtils.getDescriptorFromClassInternalName(internalName);
                    DexType inlineeType = factory.createType(descriptor);
                    inlineeClasses.add(inlineeType);
                  });
        },
        options,
        executorService);
    return ImmutableSet.copyOf(inlineeClasses);
  }

  private static class MethodInterval {
    final int min;
    final int max;
    final DexMethod method;

    MethodInterval(int min, int max, DexMethod method) {
      this.min = min;
      this.max = max;
      this.method = method;
    }
  }

  private static Entry<DexType, ImmutableDisjointIntRangeMap<DexMethod>> computeClassLines(
      DexProgramClass clazz) {
    var builder = ImmutableDisjointIntRangeMap.<DexMethod>builder();
    for (DexEncodedMethod method : clazz.methods()) {
      MethodInterval interval = extractCfMethodInterval(method);
      if (interval != null) {
        try {
          builder.add(interval.min, interval.max, interval.method);
        } catch (IllegalArgumentException ignored) {
          // Kotlin inline annotations are supported on a best-effort basis, ignore this entry.
        }
      }
    }
    ImmutableDisjointIntRangeMap<DexMethod> rangeMap = builder.build();
    return rangeMap.isEmpty() ? null : Map.entry(clazz.getType(), rangeMap);
  }

  private static MethodInterval extractCfMethodInterval(DexEncodedMethod method) {
    if (!method.hasCode() || !method.getCode().isCfCode()) {
      return null;
    }
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (CfInstruction instruction : method.getCode().asCfCode().getInstructions()) {
      if (instruction.isPosition()) {
        int line = instruction.asPosition().getPosition().getLine();
        if (line < min) {
          min = line;
        }
        if (line > max) {
          max = line;
        }
      }
    }
    return min != Integer.MAX_VALUE ? new MethodInterval(min, max, method.getReference()) : null;
  }
}
