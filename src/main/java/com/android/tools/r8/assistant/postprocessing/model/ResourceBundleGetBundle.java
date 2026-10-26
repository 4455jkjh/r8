// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ResourceBundleGetBundle extends ReflectiveEvent {

  private final String baseName;
  // The locale and class loader are null if the called overload does not take them.
  private final String locale;
  private final ClassReference classLoader;

  public ResourceBundleGetBundle(ReflectiveEventType eventType, String[] stack, String[] args) {
    super(eventType, stack);
    assert args.length == 3;
    baseName = args[0];
    locale = "null".equals(args[1]) ? null : args[1];
    classLoader = toClassOrNull(args[2]);
  }

  public String getBaseName() {
    return baseName;
  }

  public String getLocale() {
    return locale;
  }

  @Override
  public String getContentsString() {
    return baseName
        + ", locale: "
        + locale
        + ", classloader: "
        + (classLoader == null ? "null" : classLoader.getTypeName());
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    // TODO(b/428836085): The bundle classes for the locales, which are named after the base name,
    //  also have to be kept.
    ClassReference bundleClass = toClassOrNull(baseName);
    return bundleClass != null && keepInfoCollectionExported.getKeepClassInfo(bundleClass) != null;
  }

  @Override
  public boolean isResourceBundleGetBundle() {
    return true;
  }

  @Override
  public ResourceBundleGetBundle asResourceBundleGetBundle() {
    return this;
  }
}
