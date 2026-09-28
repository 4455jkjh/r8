// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.IntBox;
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
import java.util.function.Function;
import java.util.function.Predicate;

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

  public static KotlinInlineMethodMap createForR8(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (!options.shouldOutputMappingFile()) {
      return null;
    }
    return timing.time(
        "Extract kotlin inline method map",
        () -> {
          AppInfoWithLiveness appInfo = appView.appInfo();
          Function<DexType, DexProgramClass> classProvider =
              type -> {
                // Code might validly be inlined from non-present sources.
                DexProgramClass clazz =
                    appInfo.definitionForProgramTypeWithoutExistenceAssert(type);
                if (clazz != null) {
                  return clazz;
                }
                if (options.getR8PartialR8SubCompilationOptions() != null) {
                  return options.getR8PartialR8SubCompilationOptions().getDexingOutputClass(type);
                }
                return null;
              };
          return createInternal(
              appInfo.classes(),
              appInfo::isLiveProgramClass,
              classProvider,
              options,
              appView.dexItemFactory(),
              executorService);
        });
  }

  public static KotlinInlineMethodMap createForD8(
      AppView<AppInfo> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (!options.shouldOutputMappingFile()) {
      return null;
    }
    return timing.time(
        "Extract kotlin inline method map",
        () ->
            createInternal(
                appView.appInfo().classes(),
                clazz -> true,
                appView::definitionForProgramType,
                options,
                appView.dexItemFactory(),
                executorService));
  }

  private static KotlinInlineMethodMap createInternal(
      Collection<DexProgramClass> classes,
      Predicate<DexProgramClass> isLive,
      Function<DexType, DexProgramClass> classProvider,
      InternalOptions options,
      DexItemFactory factory,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexType> inlineeClasses =
        findInlineReferences(classes, isLive, options, factory, executorService);
    if (inlineeClasses.isEmpty()) {
      return empty();
    }
    var classLines = collectClassLines(inlineeClasses, classProvider, options, executorService);
    return new KotlinInlineMethodMap(classLines);
  }

  private static Map<DexType, ImmutableDisjointIntRangeMap<DexMethod>> collectClassLines(
      Set<DexType> inlineeClasses,
      Function<DexType, DexProgramClass> classProvider,
      InternalOptions options,
      ExecutorService executorService)
      throws ExecutionException {
    Collection<Entry<DexType, ImmutableDisjointIntRangeMap<DexMethod>>> classLines =
        ThreadUtils.processItemsWithResultsThatMatches(
            inlineeClasses,
            type -> {
              DexProgramClass clazz = classProvider.apply(type);
              return clazz != null ? computeClassLines(clazz) : null;
            },
            Objects::nonNull,
            options.getThreadingModule(),
            executorService);
    return ImmutableMap.copyOf(classLines);
  }

  private static Set<DexType> findInlineReferences(
      Collection<DexProgramClass> classes,
      Predicate<DexProgramClass> isLive,
      InternalOptions options,
      DexItemFactory factory,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexType> inlineeClasses = ConcurrentHashMap.newKeySet();
    ThreadUtils.processItemsWithFilterPreprocessing(
        classes,
        clazz -> {
          if (!isLive.test(clazz)) {
            return null;
          }
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
      MethodInterval interval = extractMethodInterval(method);
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

  private static MethodInterval extractMethodInterval(DexEncodedMethod method) {
    if (!method.hasCode()) {
      return null;
    }
    Code code = method.getCode();
    if (code.isLazyCfCode()) {
      // Unwrap LazyCfCode since it does not override forEachPosition.
      code = code.asCfCode();
    }
    IntBox min = new IntBox(Integer.MAX_VALUE);
    IntBox max = new IntBox(Integer.MIN_VALUE);
    code.forEachPosition(
        method.getReference(),
        method.isD8R8Synthesized(),
        position -> {
          int line = position.getLine();
          min.setMin(line);
          max.setMax(line);
        });
    return min.get() != Integer.MAX_VALUE
        ? new MethodInterval(min.get(), max.get(), method.getReference())
        : null;
  }
}
