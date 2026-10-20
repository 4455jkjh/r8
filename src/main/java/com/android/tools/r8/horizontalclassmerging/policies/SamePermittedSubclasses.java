// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class SamePermittedSubclasses extends MultiClassSameReferencePolicy<DexType> {

  private final AppView<?> appView;

  public SamePermittedSubclasses(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public DexType getMergeKey(DexProgramClass clazz) {
    DexProgramClass superClazz = asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
    if (superClazz == null || !superClazz.hasPermittedSubclassAttributes()) {
      return appView.dexItemFactory().objectType;
    }
    for (PermittedSubclassAttribute permittedSubclassAttribute :
        superClazz.getPermittedSubclassAttributes()) {
      if (permittedSubclassAttribute.getPermittedSubclass().isIdenticalTo(clazz.getType())) {
        return clazz.getSuperType();
      }
    }
    return appView.dexItemFactory().objectType;
  }

  @Override
  public String getName() {
    return "SamePermittedSubclasses";
  }
}
