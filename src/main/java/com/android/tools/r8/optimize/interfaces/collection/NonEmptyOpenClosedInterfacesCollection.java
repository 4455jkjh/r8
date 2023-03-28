// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.collection;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;

public class NonEmptyOpenClosedInterfacesCollection extends OpenClosedInterfacesCollection {

  private final Set<DexType> openInterfaceTypes;

  public NonEmptyOpenClosedInterfacesCollection(Set<DexType> openInterfaceTypes) {
    this.openInterfaceTypes = openInterfaceTypes;
  }

  @Override
  public boolean isDefinitelyClosed(DexClass clazz) {
    assert clazz.isInterface();
    return !openInterfaceTypes.contains(clazz.getType());
  }

  @Override
  public OpenClosedInterfacesCollection rewrittenWithLens(GraphLens graphLens) {
    Set<DexType> rewrittenOpenInterfaceTypes =
        SetUtils.newIdentityHashSet(openInterfaceTypes.size());
    for (DexType openInterfaceType : openInterfaceTypes) {
      rewrittenOpenInterfaceTypes.add(graphLens.lookupType(openInterfaceType));
    }
    return new NonEmptyOpenClosedInterfacesCollection(rewrittenOpenInterfaceTypes);
  }

  @Override
  public OpenClosedInterfacesCollection withoutPrunedItems(PrunedItems prunedItems) {
    if (!prunedItems.hasRemovedClasses()) {
      return this;
    }
    Set<DexType> prunedOpenInterfaceTypes = SetUtils.newIdentityHashSet(openInterfaceTypes.size());
    for (DexType openInterfaceType : openInterfaceTypes) {
      if (!prunedItems.isRemoved(openInterfaceType)) {
        prunedOpenInterfaceTypes.add(openInterfaceType);
      }
    }
    return new NonEmptyOpenClosedInterfacesCollection(prunedOpenInterfaceTypes);
  }
}
