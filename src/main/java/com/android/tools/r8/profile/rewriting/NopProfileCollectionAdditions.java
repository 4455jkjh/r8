// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.ArtProfileCollection;
import com.android.tools.r8.profile.rewriting.ProfileAdditions.ProfileAdditionsBuilder;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import java.util.function.Consumer;
import java.util.function.Function;

public class NopProfileCollectionAdditions extends ProfileCollectionAdditions {

  private static final NopProfileCollectionAdditions INSTANCE = new NopProfileCollectionAdditions();

  private NopProfileCollectionAdditions() {}

  public static NopProfileCollectionAdditions getInstance() {
    return INSTANCE;
  }

  @Override
  public void addMethodIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void addMethodAndHolderIfContextIsInProfile(ProgramMethod method, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void applyIfContextIsInProfile(
      DexMethod context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    // Intentionally empty.
  }

  @Override
  public void commit(AppView<?> appView) {
    // Intentionally empty.
  }

  @Override
  public boolean isNop() {
    return true;
  }

  @Override
  public NopProfileCollectionAdditions rewriteMethodReferences(
      Function<DexMethod, DexMethod> methodFn) {
    // Intentionally empty.
    return this;
  }

  @Override
  public NopProfileCollectionAdditions setArtProfileCollection(
      ArtProfileCollection artProfileCollection) {
    // Intentionally empty.
    return this;
  }

  @Override
  public NopProfileCollectionAdditions setStartupProfile(StartupProfile startupProfile) {
    // Intentionally empty.
    return this;
  }

  @Override
  public boolean verifyIsCommitted() {
    // Nothing to commit.
    return true;
  }
}
