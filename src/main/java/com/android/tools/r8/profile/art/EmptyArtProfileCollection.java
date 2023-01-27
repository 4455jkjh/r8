// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;

public class EmptyArtProfileCollection extends ArtProfileCollection {

  private static final EmptyArtProfileCollection INSTANCE = new EmptyArtProfileCollection();

  private EmptyArtProfileCollection() {}

  static EmptyArtProfileCollection getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isNonEmpty() {
    return false;
  }

  @Override
  public NonEmptyArtProfileCollection asNonEmpty() {
    return null;
  }

  @Override
  public ArtProfileCollection rewrittenWithLens(GraphLens lens) {
    return this;
  }

  @Override
  public ArtProfileCollection rewrittenWithLens(NamingLens lens, DexItemFactory dexItemFactory) {
    return this;
  }

  @Override
  public void supplyConsumers(AppView<?> appView) {
    assert appView.options().getArtProfileOptions().getArtProfilesForRewriting().isEmpty();
  }

  @Override
  public ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems) {
    return this;
  }
}
