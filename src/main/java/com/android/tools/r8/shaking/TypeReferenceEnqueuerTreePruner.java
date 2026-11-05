// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import java.util.Set;

public class TypeReferenceEnqueuerTreePruner extends TreePruner {

  private final Set<DexProgramClass> seenClasses;

  public TypeReferenceEnqueuerTreePruner(
      AppView<AppInfoWithLiveness> appView, Set<DexProgramClass> seenClasses) {
    super(appView, UnusedItemsPrinter.DONT_PRINT);
    this.seenClasses = seenClasses;
  }

  @Override
  public boolean isInstantiatedDirectly(DexProgramClass clazz) {
    assert isTypeLive(clazz.getType());
    return true;
  }

  @Override
  public boolean isLiveMethod(DexMethod method) {
    assert isTypeLive(method.getHolderType());
    return true;
  }

  @Override
  public boolean isLiveProgramClass(DexProgramClass clazz) {
    return seenClasses.contains(clazz);
  }

  @Override
  public boolean isReachableOrReferencedField(DexEncodedField field) {
    assert isTypeLive(field.getHolderType());
    return true;
  }

  @Override
  public boolean isTargetedMethod(DexMethod method) {
    assert isTypeLive(method.getHolderType());
    return true;
  }

  @Override
  public boolean isTypeLive(DexType type) {
    DexProgramClass clazz = appView.appInfo().definitionForProgramTypeWithoutExistenceAssert(type);
    return clazz != null ? isLiveProgramClass(clazz) : !appView.appInfo().wasPruned(type);
  }
}
