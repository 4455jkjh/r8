// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.androidapi.ComputedApiLevel.KnownApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;

public abstract class AndroidApiLevelCompute {

  private final KnownApiLevel[] knownApiLevelCache;

  public AndroidApiLevelCompute() {
    knownApiLevelCache = new KnownApiLevel[AndroidApiLevel.LATEST.getLevel() + 1];
    for (AndroidApiLevel value : AndroidApiLevel.values()) {
      if (value != AndroidApiLevel.ANDROID_PLATFORM) {
        knownApiLevelCache[value.getLevel()] = new KnownApiLevel(value);
      }
    }
  }

  public KnownApiLevel of(AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.ANDROID_PLATFORM) {
      return ComputedApiLevel.platform();
    }
    return knownApiLevelCache[apiLevel.getLevel()];
  }

  public abstract ComputedApiLevel computeApiLevelForLibraryReference(
      DexReference reference, ComputedApiLevel unknownValue);

  public abstract ComputedApiLevel computeApiLevelForDefinition(
      Iterable<DexType> types, ComputedApiLevel unknownValue);

  public ComputedApiLevel computeApiLevelForDefinition(
      DexMember<?, ?> reference, DexItemFactory factory, ComputedApiLevel unknownValue) {
    return computeApiLevelForDefinition(reference.getReferencedBaseTypes(factory), unknownValue);
  }

  public static AndroidApiLevelCompute create(AppView<?> appView) {
    return appView.options().apiModelingOptions().enableApiCallerIdentification
        ? new DefaultAndroidApiLevelCompute(appView)
        : new NoAndroidApiLevelCompute();
  }

  public static ComputedApiLevel computeInitialMinApiLevel(InternalOptions options) {
    if (options.apiModelingOptions().enableApiCallerIdentification) {
      return options.getMinApiLevel() == AndroidApiLevel.ANDROID_PLATFORM
          ? ComputedApiLevel.platform()
          : new KnownApiLevel(options.getMinApiLevel());
    } else {
      return ComputedApiLevel.unknown();
    }
  }

  public ComputedApiLevel getPlatformApiLevelOrUnknown(AppView<?> appView) {
    if (appView.options().getMinApiLevel() == AndroidApiLevel.ANDROID_PLATFORM) {
      return ComputedApiLevel.platform();
    }
    return ComputedApiLevel.unknown();
  }

  public static class NoAndroidApiLevelCompute extends AndroidApiLevelCompute {

    @Override
    public ComputedApiLevel computeApiLevelForDefinition(
        Iterable<DexType> types, ComputedApiLevel unknownValue) {
      return unknownValue;
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReference(
        DexReference reference, ComputedApiLevel unknownValue) {
      return unknownValue;
    }
  }

  public static class DefaultAndroidApiLevelCompute extends AndroidApiLevelCompute {

    private final AndroidApiReferenceLevelCache cache;
    private final ComputedApiLevel minApiLevel;

    public DefaultAndroidApiLevelCompute(AppView<?> appView) {
      this.cache = AndroidApiReferenceLevelCache.create(appView, this);
      this.minApiLevel = of(appView.options().getMinApiLevel());
    }

    @Override
    public ComputedApiLevel computeApiLevelForDefinition(
        Iterable<DexType> types, ComputedApiLevel unknownValue) {
      ComputedApiLevel computedLevel = minApiLevel;
      for (DexType type : types) {
        computedLevel = cache.lookupMax(type, computedLevel, unknownValue);
      }
      return computedLevel;
    }

    @Override
    public ComputedApiLevel computeApiLevelForLibraryReference(
        DexReference reference, ComputedApiLevel unknownValue) {
      return cache.lookup(reference, unknownValue);
    }
  }
}
