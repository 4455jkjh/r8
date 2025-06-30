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
import com.android.tools.r8.utils.timing.Timing;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface for adding (synthetic) items to existing instances of {@link
 * com.android.tools.r8.profile.AbstractProfile}.
 *
 * <p>The interface will be implemented by {@link NopProfileCollectionAdditions} when the
 * compilation does not contain any ART profiles, for minimal performance overhead.
 *
 * <p>When one or more ART profiles are present, or a startup profile is, then this class is
 * implemented by {@link ConcreteProfileCollectionAdditions}.
 */
public abstract class ProfileCollectionAdditions {

  public static ProfileCollectionAdditions create(AppView<?> appView) {
    ArtProfileCollection artProfileCollection = appView.getArtProfileCollection();
    StartupProfile startupProfile = appView.getStartupProfile();
    if (artProfileCollection.isEmpty() && startupProfile.isEmpty()) {
      return nop();
    }
    return new ConcreteProfileCollectionAdditions(artProfileCollection, startupProfile);
  }

  public static NopProfileCollectionAdditions nop() {
    return NopProfileCollectionAdditions.getInstance();
  }

  public abstract void addMethodIfContextIsInProfile(ProgramMethod method, ProgramMethod context);

  public abstract void applyIfContextIsInProfile(
      DexMethod context, Consumer<ProfileAdditionsBuilder> builderConsumer);

  public abstract void commit(AppView<?> appView);

  public final void commit(AppView<?> appView, Timing timing) {
    timing.begin("Commit profile additions");
    commit(appView);
    timing.end();
  }

  public boolean isNop() {
    return false;
  }

  public ConcreteProfileCollectionAdditions asConcrete() {
    return null;
  }

  public abstract ProfileCollectionAdditions rewriteMethodReferences(
      Function<DexMethod, DexMethod> methodFn);

  public abstract ProfileCollectionAdditions setArtProfileCollection(
      ArtProfileCollection artProfileCollection);

  public abstract ProfileCollectionAdditions setStartupProfile(StartupProfile startupProfile);

  public abstract boolean verifyIsCommitted();
}
