// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

public abstract class MappingInformation {

  public static final String MAPPING_ID_KEY = "id";

  public abstract String getId();

  public abstract String serialize();

  public boolean isMetaInfMappingInformation() {
    return false;
  }

  public MetaInfMappingInformation asMetaInfMappingInformation() {
    return null;
  }

  public boolean isFileNameInformation() {
    return false;
  }

  public FileNameInformation asFileNameInformation() {
    return null;
  }

  public boolean isCompilerSynthesizedMappingInformation() {
    return false;
  }

  public CompilerSynthesizedMappingInformation asCompilerSynthesizedMappingInformation() {
    return null;
  }

  public abstract boolean allowOther(MappingInformation information);

  public static void fromJsonObject(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    if (object == null) {
      diagnosticsHandler.info(MappingInformationDiagnostics.notValidJson(lineNumber));
      return;
    }
    JsonElement id = object.get(MAPPING_ID_KEY);
    if (id == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.noKeyInJson(lineNumber, MAPPING_ID_KEY));
      return;
    }
    String idString = id.getAsString();
    if (idString == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.notValidString(lineNumber, MAPPING_ID_KEY));
      return;
    }
    deserialize(
        idString,
        version,
        object,
        diagnosticsHandler,
        lineNumber,
        onMappingInfo);
  }

  private static void deserialize(
      String id,
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    switch (id) {
      case MetaInfMappingInformation.ID:
        MetaInfMappingInformation.deserialize(
            version, object, diagnosticsHandler, lineNumber, onMappingInfo);
        return;
      case FileNameInformation.ID:
        FileNameInformation.deserialize(
            version, object, diagnosticsHandler, lineNumber, onMappingInfo);
        return;
      case CompilerSynthesizedMappingInformation.ID:
        CompilerSynthesizedMappingInformation.deserialize(
            version, object, diagnosticsHandler, lineNumber, onMappingInfo);
        return;
      default:
        diagnosticsHandler.info(MappingInformationDiagnostics.noHandlerFor(lineNumber, id));
    }
  }

  static JsonElement getJsonElementFromObject(
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      String key,
      String id) {
    JsonElement element = object.get(key);
    if (element == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.noKeyForObjectWithId(lineNumber, key, MAPPING_ID_KEY, id));
    }
    return element;
  }
}
