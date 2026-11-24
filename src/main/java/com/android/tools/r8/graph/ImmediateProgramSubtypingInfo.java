// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public class ImmediateProgramSubtypingInfo
    extends ImmediateSubtypingInfo<DexProgramClass, DexProgramClass> {

  private ImmediateProgramSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexProgramClass, Object> immediateSubtypes) {
    super(appView, immediateSubtypes);
  }

  public static ImmediateProgramSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return create(appView, appView.appInfo().classes());
  }

  public static ImmediateProgramSubtypingInfo createWithDeterministicOrder(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return create(appView, appView.appInfo().classesWithDeterministicOrder());
  }

  private static ImmediateProgramSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView, Collection<DexProgramClass> classes) {
    Map<DexProgramClass, Object> immediateSubtypes = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      DexType superType = clazz.getSuperType();
      if (superType != null) {
        addImmediateSuperclass(
            immediateSubtypes, appView.definitionForProgramType(superType), clazz);
      }
      for (DexType iface : clazz.getInterfaces().getBacking()) {
        addImmediateSuperclass(immediateSubtypes, appView.definitionForProgramType(iface), clazz);
      }
    }
    return new ImmediateProgramSubtypingInfo(appView, immediateSubtypes);
  }

  @Override
  DexProgramClass toS(DexClass clazz) {
    return DexProgramClass.asProgramClassOrNull(clazz);
  }
}
