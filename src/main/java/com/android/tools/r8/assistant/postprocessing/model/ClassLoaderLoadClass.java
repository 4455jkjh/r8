// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ClassLoaderLoadClass extends ReflectiveEvent {

  private final ClassReference className;
  private final ClassReference classLoader;

  public ClassLoaderLoadClass(ReflectiveEventType eventType, String[] stack, String[] args) {
    super(eventType, stack);
    assert args.length == 2;
    className = toClassOrNull(args[0]);
    classLoader = toClassOrNull(args[1]);
  }

  @Override
  public String getContentsString() {
    return (className == null ? "null" : className.getTypeName())
        + ", classloader: "
        + (classLoader == null ? "null" : classLoader.getTypeName());
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    return className != null && keepInfoCollectionExported.getKeepClassInfo(className) != null;
  }

  @Override
  public boolean isClassLoaderLoadClass() {
    return true;
  }

  @Override
  public ClassLoaderLoadClass asClassLoaderLoadClass() {
    return this;
  }

  public ClassReference getClassName() {
    return className;
  }
}
