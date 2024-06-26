// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypeDesc;
import com.google.protobuf.MessageOrBuilder;
import java.util.function.Consumer;

public final class KeepSpecUtils {

  private KeepSpecUtils() {}

  public static <T extends MessageOrBuilder> void doBuild(
      T builder, Consumer<T> buildMethod, Consumer<T> setter) {
    buildMethod.accept(builder);
    setter.accept(builder);
  }

  public static <T extends MessageOrBuilder> String toString(T builder, Consumer<T> buildMethod) {
    buildMethod.accept(builder);
    return builder.toString();
  }

  public static TypeDesc desc(String descriptor) {
    return TypeDesc.newBuilder().setDesc(descriptor).build();
  }
}
