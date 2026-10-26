// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

// Class.getGenericSuperclass() or Class.getGenericInterfaces().
public class ClassGetGenericSupertypes extends ReflectiveEvent {

  private final DexType type;

  protected ClassGetGenericSupertypes(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length == 1;
    type = toType(args[0], factory);
  }

  public DexType getType() {
    return type;
  }

  @Override
  public boolean isClassGetGenericSupertypes() {
    return true;
  }

  @Override
  public ClassGetGenericSupertypes asClassGetGenericSupertypes() {
    return this;
  }

  @Override
  public String getContentsString() {
    return type.toSourceString();
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    // TODO(b/428836085): The generic signature of the class also has to be kept.
    return keepInfoCollectionExported.getKeepClassInfo(type.asTypeReference()) != null;
  }
}
