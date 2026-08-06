// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;

@KeepForApi
public class KotlinMetadataDiscardedDiagnostic implements Diagnostic {

  private final List<String> messages;

  KotlinMetadataDiscardedDiagnostic(List<String> messages) {
    this.messages = messages;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder("Kotlin metadata discarded checks failed.");
    for (String message : messages) {
      builder.append(System.lineSeparator());
      builder.append(message);
    }
    return builder.toString();
  }

  public static class Builder {

    private final ImmutableList.Builder<String> messagesBuilder = ImmutableList.builder();

    public Builder addFailedClasses(List<DexProgramClass> failed) {
      failed.sort(Comparator.comparing(DexClass::getType));
      for (DexProgramClass clazz : failed) {
        messagesBuilder.add("Kotlin metadata for " + clazz.getTypeName() + " was not discarded.");
      }
      return this;
    }

    public KotlinMetadataDiscardedDiagnostic build() {
      return new KotlinMetadataDiscardedDiagnostic(messagesBuilder.build());
    }
  }
}
