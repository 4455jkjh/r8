// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.ScopedMappingInformation.ScopeReference;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.BiConsumer;

public class CompilerSynthesizedMappingInformation extends MappingInformation {

  public static final String ID = "com.android.tools.r8.synthesized";

  public static class Builder {

    public CompilerSynthesizedMappingInformation build() {
      return new CompilerSynthesizedMappingInformation();
    }
  }

  private CompilerSynthesizedMappingInformation() {}

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isCompilerSynthesizedMappingInformation() {
    return true;
  }

  @Override
  public CompilerSynthesizedMappingInformation asCompilerSynthesizedMappingInformation() {
    return this;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isCompilerSynthesizedMappingInformation();
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return object.toString();
  }

  public static void deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      ScopeReference implicitSingletonScope,
      BiConsumer<ScopeReference, MappingInformation> onMappingInfo) {
    if (version.isLessThan(MapVersion.MapVersionExperimental)) {
      return;
    }
    CompilerSynthesizedMappingInformation info = builder().build();
    for (ScopeReference reference :
        ScopedMappingInformation.deserializeScope(
            object, implicitSingletonScope, diagnosticsHandler, lineNumber, version)) {
      onMappingInfo.accept(reference, info);
    }
  }
}
