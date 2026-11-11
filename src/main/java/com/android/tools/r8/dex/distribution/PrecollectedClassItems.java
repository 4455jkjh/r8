// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;

/**
 * Compact per-class snapshot of deduplicated root indexed items referenced by a {@link
 * DexProgramClass}.
 *
 * <p>Collected in parallel across worker threads before serial {@link
 * com.android.tools.r8.dex.VirtualFile} transaction population. Composite items ({@link DexMethod},
 * {@link DexField}, {@link DexType}, {@link DexProto}, {@link DexCallSite}, {@link
 * DexMethodHandle}) are stored as root references without expanding their child items, so replaying
 * onto {@link IndexedItemTransaction} still short-circuits in one lookup whenever a method, field,
 * or type is already present in the target {@link com.android.tools.r8.dex.VirtualFile}.
 */
public final class PrecollectedClassItems {

  private final DexMethod[] methods;
  private final DexField[] fields;
  private final DexType[] directTypes;
  private final DexString[] directStrings;
  private final DexProto[] directProtos;
  private final DexCallSite[] callSites;
  private final DexMethodHandle[] methodHandles;

  private PrecollectedClassItems(
      DexMethod[] methods,
      DexField[] fields,
      DexType[] directTypes,
      DexString[] directStrings,
      DexProto[] directProtos,
      DexCallSite[] callSites,
      DexMethodHandle[] methodHandles) {
    this.methods = methods;
    this.fields = fields;
    this.directTypes = directTypes;
    this.directStrings = directStrings;
    this.directProtos = directProtos;
    this.callSites = callSites;
    this.methodHandles = methodHandles;
  }

  public static boolean canPrecollect(AppView<?> appView) {
    return appView.testing().collectIndexedItemsCallback == null;
  }

  void replayInto(
      AppView<?> appView, IndexedItemTransaction transaction, DexProgramClass programClass) {
    if (!transaction.addClass(programClass)) {
      return;
    }
    for (DexType type : directTypes) {
      type.collectIndexedItems(appView, transaction);
    }
    for (DexString string : directStrings) {
      transaction.addString(string);
    }
    for (DexField field : fields) {
      field.collectIndexedItems(appView, transaction);
    }
    for (DexMethod method : methods) {
      method.collectIndexedItems(appView, transaction);
    }
    for (DexProto proto : directProtos) {
      proto.collectIndexedItems(appView, transaction);
    }
    for (DexCallSite callSite : callSites) {
      callSite.collectIndexedItems(appView, transaction);
    }
    for (DexMethodHandle methodHandle : methodHandles) {
      methodHandle.collectIndexedItems(appView, transaction);
    }
  }

  static final class WorkerCollector implements IndexedItemCollection {

    private static final int INITIAL_CAPACITY = 16;

    private final AppView<?> appView;
    private final LensCodeRewriterUtils rewriter;

    private final Set<DexMethod> methods = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexField> fields = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexType> types = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexString> strings = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexProto> protos = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexCallSite> callSites = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);
    private final Set<DexMethodHandle> methodHandles = new ReferenceOpenHashSet<>(INITIAL_CAPACITY);

    WorkerCollector(AppView<?> appView, LensCodeRewriterUtils rewriter) {
      this.appView = appView;
      this.rewriter = rewriter;
    }

    private static <T> T[] drain(Set<T> set, T[] empty) {
      if (set.isEmpty()) {
        return empty;
      }
      T[] result = set.toArray(empty);
      set.clear();
      return result;
    }

    PrecollectedClassItems collect(DexProgramClass clazz) {
      clazz.collectIndexedItems(appView, this, rewriter);
      return new PrecollectedClassItems(
          drain(methods, DexMethod.EMPTY_ARRAY),
          drain(fields, DexField.EMPTY_ARRAY),
          drain(types, DexType.EMPTY_ARRAY),
          drain(strings, DexString.EMPTY_ARRAY),
          drain(protos, DexProto.EMPTY_ARRAY),
          drain(callSites, DexCallSite.EMPTY_ARRAY),
          drain(methodHandles, DexMethodHandle.EMPTY_ARRAY));
    }

    @Override
    public boolean addClass(DexProgramClass dexProgramClass) {
      return true;
    }

    // Composite items intentionally return false so child items are expanded only in replayInto().
    @Override
    public boolean addField(DexField field) {
      fields.add(field);
      return false;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      methods.add(method);
      return false;
    }

    @Override
    public boolean addString(DexString string) {
      return strings.add(string);
    }

    @Override
    public boolean addProto(DexProto proto) {
      protos.add(proto);
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      types.add(type);
      return false;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      callSites.add(callSite);
      return false;
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      methodHandles.add(methodHandle);
      return false;
    }
  }
}
