// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fully expanded, per-kind deduplicated indexed items referenced by a {@link DexProgramClass}, used
 * by {@link DexDistributionRefinement} for cache-friendly reference counting and target-file cost
 * evaluation.
 */
final class ExpandedClassItems {

  final DexProgramClass owner;
  final DexField[] fields;
  final DexMethod[] methods;
  final DexType[] types;
  final DexProto[] protos;
  final DexString[] strings;
  final DexCallSite[] callSites;
  final DexMethodHandle[] methodHandles;

  private ExpandedClassItems(
      DexProgramClass owner,
      DexField[] fields,
      DexMethod[] methods,
      DexType[] types,
      DexProto[] protos,
      DexString[] strings,
      DexCallSite[] callSites,
      DexMethodHandle[] methodHandles) {
    this.owner = owner;
    this.fields = fields;
    this.methods = methods;
    this.types = types;
    this.protos = protos;
    this.strings = strings;
    this.callSites = callSites;
    this.methodHandles = methodHandles;
  }

  static final class ExpandedItemCollector implements IndexedItemCollection {

    private static final int INITIAL_CAPACITY = 16;
    // ReferenceOpenHashSet#clear zeros the entire backing array, so replace sets that grew past
    // this threshold for an outlier class rather than paying to clear and retain the oversized
    // table across the bucket.
    private static final int MAX_RETAINED_SET_SIZE = 4096;

    private final AppView<?> appView;
    private final DexItemFactory dexItemFactory;
    private final Map<String, DexString> shortyCache = new HashMap<>();

    private Set<DexField> fields = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexMethod> methods = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexType> types = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexProto> protos = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexString> strings = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexCallSite> callSites = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private Set<DexMethodHandle> methodHandles = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);

    ExpandedItemCollector(AppView<?> appView) {
      this.appView = appView;
      this.dexItemFactory = appView.dexItemFactory();
    }

    private static <T> T[] drain(Set<T> set, T[] empty) {
      if (set.isEmpty()) {
        return empty;
      }
      T[] result = set.toArray(empty);
      if (result.length <= MAX_RETAINED_SET_SIZE) {
        set.clear();
      }
      return result;
    }

    private static <T> Set<T> shrinkIfNeeded(Set<T> set, T[] drained) {
      return drained.length > MAX_RETAINED_SET_SIZE
          ? new ReferenceOpenHashSet<>(INITIAL_CAPACITY)
          : set;
    }

    ExpandedClassItems expand(DexProgramClass clazz, LensCodeRewriterUtils rewriter) {
      clazz.collectIndexedItems(appView, this, rewriter);
      DexField[] drainedFields = drain(fields, DexField.EMPTY_ARRAY);
      DexMethod[] drainedMethods = drain(methods, DexMethod.EMPTY_ARRAY);
      DexType[] drainedTypes = drain(types, DexType.EMPTY_ARRAY);
      DexProto[] drainedProtos = drain(protos, DexProto.EMPTY_ARRAY);
      DexString[] drainedStrings = drain(strings, DexString.EMPTY_ARRAY);
      DexCallSite[] drainedCallSites = drain(callSites, DexCallSite.EMPTY_ARRAY);
      DexMethodHandle[] drainedMethodHandles = drain(methodHandles, DexMethodHandle.EMPTY_ARRAY);
      fields = shrinkIfNeeded(fields, drainedFields);
      methods = shrinkIfNeeded(methods, drainedMethods);
      types = shrinkIfNeeded(types, drainedTypes);
      protos = shrinkIfNeeded(protos, drainedProtos);
      strings = shrinkIfNeeded(strings, drainedStrings);
      callSites = shrinkIfNeeded(callSites, drainedCallSites);
      methodHandles = shrinkIfNeeded(methodHandles, drainedMethodHandles);
      return new ExpandedClassItems(
          clazz,
          drainedFields,
          drainedMethods,
          drainedTypes,
          drainedProtos,
          drainedStrings,
          drainedCallSites,
          drainedMethodHandles);
    }

    @Override
    public boolean addClass(DexProgramClass clazz) {
      return true;
    }

    @Override
    public boolean addField(DexField field) {
      return fields.add(field);
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return methods.add(method);
    }

    @Override
    public boolean addString(DexString string) {
      return strings.add(string);
    }

    @Override
    public boolean addProto(DexProto proto) {
      if (protos.add(proto)) {
        // Match IndexedItemTransaction#addProto by recording each proto's shorty descriptor string.
        strings.add(
            shortyCache.computeIfAbsent(proto.createShortyString(), dexItemFactory::createString));
        return true;
      }
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      return types.add(type);
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return callSites.add(callSite);
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return methodHandles.add(methodHandle);
    }
  }
}
