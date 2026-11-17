// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Set;

public class IndexedItemTransaction implements IndexedItemCollection {

  private final AppView<?> appView;
  private final VirtualFile.VirtualFileIndexedItemCollection base;
  private final LensCodeRewriterUtils rewriter;

  private final Set<DexProgramClass> classes = Sets.newIdentityHashSet();
  private final Set<DexCallSite> callSites = Sets.newIdentityHashSet();
  private final Set<DexField> fields = Sets.newIdentityHashSet();
  private final Set<DexMethod> methods = Sets.newIdentityHashSet();
  private final Set<DexMethodHandle> methodHandles = Sets.newIdentityHashSet();
  private final Set<DexProto> protos = Sets.newIdentityHashSet();
  private final Set<DexString> strings = Sets.newIdentityHashSet();
  private final Set<DexType> types = Sets.newIdentityHashSet();

  public IndexedItemTransaction(
      VirtualFile.VirtualFileIndexedItemCollection base, AppView<?> appView) {
    this.appView = appView;
    this.base = base;
    this.rewriter = new LensCodeRewriterUtils(appView, true);
  }

  public static IndexedItemTransaction create(
      VirtualFile.VirtualFileIndexedItemCollection base, AppView<?> appView) {
    return new IndexedItemTransaction(base, appView);
  }

  public LensCodeRewriterUtils getRewriter() {
    return rewriter;
  }

  private <T extends DexItem> boolean addItem(
      T item, Reference2IntMap<T> baseSet, Set<T> transactionSet) {
    assert currentClass != null;
    if (baseSet.containsKey(item)) {
      return false;
    }
    return transactionSet.add(item);
  }

  public void addClassAndDependencies(DexProgramClass clazz, PrecollectedClassItems precollected) {
    if (precollected != null) {
      precollected.replayInto(appView, this, clazz);
    } else {
      clazz.collectIndexedItems(appView, this, rewriter);
    }
    currentClass = null;
  }

  DexProgramClass currentClass = null;

  @Override
  public boolean addClass(DexProgramClass clazz) {
    assert currentClass == null;
    if (!base.classes.contains(clazz) && classes.add(clazz)) {
      currentClass = clazz;
      return true;
    }
    return false;
  }

  @Override
  public boolean addField(DexField field) {
    return addItem(field, base.fields, fields);
  }

  @Override
  public boolean addMethod(DexMethod method) {
    return addItem(method, base.methods, methods);
  }

  @Override
  public boolean addString(DexString string) {
    return addItem(string, base.strings, strings);
  }

  public void addChecksumString(DexString string) {
    addChecksumOrMarkerString(string);
  }

  public void addMarkerString(DexString string) {
    addChecksumOrMarkerString(string);
  }

  private void addChecksumOrMarkerString(DexString string) {
    // Note that strictly speaking, at this point the transaction has not been committed, yet we
    // still update the virtual file directly. This is OK only because we always commit all
    // checksums and markers (i.e., this transaction is guaranteed to be committed in the future).
    assert isEmpty();
    base.strings.put(string, base.strings.getInt(string) + 1);
  }

  @Override
  public boolean addType(DexType type) {
    assert SyntheticNaming.verifyNotInternalSynthetic(type);
    return addItem(type, base.types, types);
  }

  @Override
  public boolean addProto(DexProto proto) {
    if (addItem(proto, base.protos, protos)) {
      DexString shorty =
          base.shortyCache.computeIfAbsent(
              proto.createShortyString(), appView.dexItemFactory()::createString);
      addString(shorty);
      return true;
    }
    return false;
  }

  @Override
  public boolean addCallSite(DexCallSite callSite) {
    return addItem(callSite, base.callSites, callSites);
  }

  @Override
  public boolean addMethodHandle(DexMethodHandle methodHandle) {
    return addItem(methodHandle, base.methodHandles, methodHandles);
  }

  public int getNumberOfMethods() {
    return methods.size() + base.getNumberOfMethods();
  }

  public int getNumberOfClasses() {
    return classes.size() + base.classes.size();
  }

  public int getNumberOfTypes() {
    return types.size() + base.types.size();
  }

  public int getNumberOfFields() {
    return fields.size() + base.getNumberOfFields();
  }

  private <T extends DexItem> void commitItemsTo(Set<T> items, Reference2IntMap<T> committedItems) {
    for (T item : items) {
      assert !committedItems.containsKey(item);
      committedItems.put(item, 0);
    }
    items.clear();
  }

  public void commit() {
    base.classes.addAll(classes);
    classes.clear();
    commitItemsTo(callSites, base.callSites);
    commitItemsTo(fields, base.fields);
    commitItemsTo(methods, base.methods);
    commitItemsTo(methodHandles, base.methodHandles);
    commitItemsTo(strings, base.strings);
    commitItemsTo(protos, base.protos);
    commitItemsTo(types, base.types);
  }

  public void abort() {
    classes.clear();
    fields.clear();
    methods.clear();
    protos.clear();
    types.clear();
    strings.clear();
    callSites.clear();
    methodHandles.clear();
  }

  public boolean isEmpty() {
    return classes.isEmpty()
        && fields.isEmpty()
        && methods.isEmpty()
        && protos.isEmpty()
        && types.isEmpty()
        && strings.isEmpty()
        && callSites.isEmpty()
        && methodHandles.isEmpty();
  }
}
