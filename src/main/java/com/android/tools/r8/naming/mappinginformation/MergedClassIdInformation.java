// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Models the {@code com.android.tools.r8.mergedClassId} mapping information tag.
 *
 * <p>See {@link MergedClassesInformation} for the format.
 */
public class MergedClassIdInformation extends ReferentialMappingInformation {

  public static final String ID = "com.android.tools.r8.mergedClassId";
  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_4;
  public static final String VALUE_KEY = "value";

  private final Object2IntMap<String> values;

  private MergedClassIdInformation(Object2IntMap<String> values) {
    assert values != null;
    assert values.keySet().stream().allMatch(MergedClassIdInformation::isValidFieldKey);
    this.values = values;
  }

  @Override
  public String getId() {
    return ID;
  }

  public Object2IntMap<String> getValues() {
    return values;
  }

  public Integer getValue(String fieldKey) {
    return values.containsKey(fieldKey) ? values.getInt(fieldKey) : null;
  }

  public static String getFieldKey(String fieldName, String fieldTypeDescriptor) {
    return fieldName + " " + fieldTypeDescriptor;
  }

  public static String getFieldKey(DexField field) {
    return getFieldKey(field.getName().toString(), field.getType().toDescriptorString());
  }

  private static boolean isValidFieldKey(String key) {
    int spaceIndex = key.indexOf(' ');
    return spaceIndex > 0 && spaceIndex < key.length() - 1 && key.lastIndexOf(' ') == spaceIndex;
  }

  public static String getFieldNameFromKey(String key) {
    return key.substring(0, key.indexOf(' '));
  }

  public static String getFieldTypeDescriptorFromKey(String key) {
    return key.substring(key.indexOf(' ') + 1);
  }

  public static MergedClassIdInformation create(Object2IntMap<String> values) {
    return new MergedClassIdInformation(values);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Single-use builder; {@link #build()} may only be called once. */
  public static class Builder {

    private Object2IntMap<String> values = new Object2IntLinkedOpenHashMap<>();

    public Builder addClassIdValue(String fieldKey, int value) {
      values.put(fieldKey, value);
      return this;
    }

    public Builder addClassIdValue(String fieldName, String fieldTypeDescriptor, int value) {
      return addClassIdValue(getFieldKey(fieldName, fieldTypeDescriptor), value);
    }

    public Builder addClassIdValue(DexField field, int value) {
      return addClassIdValue(getFieldKey(field), value);
    }

    public Builder addClassIdValues(Object2IntMap<String> values) {
      this.values.putAll(values);
      return this;
    }

    public boolean isEmpty() {
      return values.isEmpty();
    }

    public MergedClassIdInformation build() {
      Object2IntMap<String> map = values;
      values = null;
      return create(Object2IntMaps.unmodifiable(map));
    }
  }

  @Override
  public MappingInformation compose(MappingInformation existing) {
    return existing;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isMergedClassIdInformation();
  }

  @Override
  public boolean isMergedClassIdInformation() {
    return true;
  }

  @Override
  public MergedClassIdInformation asMergedClassIdInformation() {
    return this;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonObject valueObject = new JsonObject();
    values.forEach((fieldKey, value) -> valueObject.add(fieldKey, new JsonPrimitive(value)));
    object.add(VALUE_KEY, valueObject);
    return object.toString();
  }

  public static void deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      try {
        JsonElement valueElement =
            getJsonElementFromObject(object, diagnosticsHandler, lineNumber, VALUE_KEY, ID);
        if (valueElement != null) {
          Builder builder = builder();
          for (Map.Entry<String, JsonElement> entry : valueElement.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (!isValidFieldKey(key)) {
              diagnosticsHandler.warning(
                  MappingInformationDiagnostics.invalidValueForObjectWithId(
                      lineNumber, VALUE_KEY, ID));
              continue;
            }
            try {
              builder.addClassIdValue(key, entry.getValue().getAsInt());
            } catch (UnsupportedOperationException
                | IllegalStateException
                | NumberFormatException ignored) {
              diagnosticsHandler.warning(
                  MappingInformationDiagnostics.invalidValueForObjectWithId(
                      lineNumber, VALUE_KEY, ID));
            }
          }
          if (!builder.isEmpty()) {
            onMappingInfo.accept(builder.build());
          }
        }
      } catch (UnsupportedOperationException | IllegalStateException ignored) {
        diagnosticsHandler.warning(
            MappingInformationDiagnostics.invalidValueForObjectWithId(lineNumber, VALUE_KEY, ID));
      }
    }
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }
}
