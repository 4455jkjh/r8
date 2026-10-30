// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

// An operation on a class, such as Class.getSuperclass() or Class.isInstance(Object), with an
// optional class argument: the argument of isAssignableFrom and asSubclass, or the class of the
// object passed to isInstance and cast.
public class ClassOperation extends ReflectiveEvent {

  private final DexType type;
  private final DexType argumentType;

  protected ClassOperation(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    type = toType(args[0], factory);
    argumentType =
        args.length > 1 && args[1] != null && !args[1].equals("null")
            ? toType(args[1], factory)
            : null;
  }

  public DexType getType() {
    return type;
  }

  // The class argument, or null if the operation has none or it is null.
  public DexType getArgumentType() {
    return argumentType;
  }

  @Override
  public boolean isClassOperation() {
    return true;
  }

  @Override
  public ClassOperation asClassOperation() {
    return this;
  }

  @Override
  public String getContentsString() {
    return type.toSourceString()
        + (argumentType != null ? ", " + argumentType.toSourceString() : "");
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    if (type == null || !type.isClassType()) {
      return false;
    }
    return keepInfoCollectionExported.getKeepClassInfo(type.asTypeReference()) != null;
  }
}
