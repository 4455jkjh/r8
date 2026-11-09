// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Models the {@code com.android.tools.r8.mergedClasses} mapping information tag.
 *
 * <p>Grammar notation: {@code '...'} denotes terminals, {@code [...]} denotes optional elements,
 * {@code (... | ...)} denotes alternatives, and {@code {...}} denotes zero or more repetitions.
 *
 * <pre>
 *   merged_class_entry         ::= base_class_entry { disambiguating_class_entry }
 *
 *   base_class_entry           ::= base_class_line merged_classes_line { member_line }
 *   base_class_line            ::= 'MERGED$$CLASS$$' mapping_source ' -> ' mapping_target ':'
 *   merged_classes_line        ::= '# { "id": "com.android.tools.r8.mergedClasses", "additionalMappings": ["' suffix '"' { ', "' suffix '"' } '] }'
 *
 *   disambiguating_class_entry ::= disambiguating_class_line merged_class_id_line { member_line }
 *   disambiguating_class_line  ::= mapping_source ' -> ' mapping_target suffix ':'
 *   suffix                     ::= string
 *   merged_class_id_line       ::= '# { "id": "com.android.tools.r8.mergedClassId", "value": {' field_value_entry { ', ' field_value_entry } '} }'
 *   field_value_entry          ::= '"' field_name ' ' field_type_descriptor '": ' integer
 * </pre>
 *
 * Example:
 *
 * <pre>
 *   MERGED$$CLASS$$Bar -> a:
 *    # { "id": "com.android.tools.r8.mergedClasses", "additionalMappings": ["$$0$$0", "$$1$$0", "$$1$$1"] }
 *    int fieldA -> a
 *   Bar -> a$$0$$0:
 *    # { "id": "com.android.tools.r8.mergedClassId", "value": { "id1 I": 0, "id2 I": 0 } }
 *    int fieldBar -> a
 *   com.a.b.Foo -> a$$1$$0:
 *    # { "id": "com.android.tools.r8.mergedClassId", "value": { "id1 I": 1, "id2 I": 0 } }
 *    int fieldFoo -> a
 *   com.a.c.Baz -> a$$1$$1:
 *    # { "id": "com.android.tools.r8.mergedClassId", "value": { "id1 I": 1, "id2 I": 1 } }
 *    int fieldBaz -> a
 * </pre>
 */
public class MergedClassesInformation extends ReferentialMappingInformation {

  public static final String ID = "com.android.tools.r8.mergedClasses";
  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_4;
  public static final String ADDITIONAL_MAPPINGS_KEY = "additionalMappings";
  public static final String MERGED_CLASS_PREFIX = "MERGED$$CLASS$$";
  public static final String DEFAULT_CLASS_ID_SEPARATOR = "$$";

  private final List<String> additionalMappings;

  private MergedClassesInformation(List<String> additionalMappings) {
    assert additionalMappings != null;
    this.additionalMappings = additionalMappings;
  }

  public List<String> getAdditionalMappings() {
    return additionalMappings;
  }

  public boolean hasAdditionalMappings() {
    return !additionalMappings.isEmpty();
  }

  public static String getPrefixedOriginalName(String originalName) {
    return MERGED_CLASS_PREFIX + originalName;
  }

  public static boolean isPrefixedOriginalName(String originalName) {
    return originalName.startsWith(MERGED_CLASS_PREFIX);
  }

  public static String stripPrefixedOriginalName(String originalName) {
    return isPrefixedOriginalName(originalName)
        ? originalName.substring(MERGED_CLASS_PREFIX.length())
        : originalName;
  }

  public static String formatSuffix(String separator, int... classIds) {
    StringBuilder builder = new StringBuilder();
    for (int id : classIds) {
      builder.append(separator).append(id);
    }
    return builder.toString();
  }

  public static String formatSuffix(String separator, List<Integer> classIds) {
    StringBuilder builder = new StringBuilder();
    for (int id : classIds) {
      builder.append(separator).append(id);
    }
    return builder.toString();
  }

  public static String getSuffixedObfuscatedName(String residualName, String suffix) {
    return residualName + suffix;
  }

  public static MergedClassesInformation create(List<String> additionalMappings) {
    return new MergedClassesInformation(additionalMappings);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Single-use builder; {@link #build()} may only be called once. */
  public static class Builder {

    private List<String> additionalMappings = new ArrayList<>();

    public Builder addAdditionalMapping(String mapping) {
      additionalMappings.add(mapping);
      return this;
    }

    public Builder addAdditionalMappings(Collection<String> mappings) {
      additionalMappings.addAll(mappings);
      return this;
    }

    public MergedClassesInformation build() {
      List<String> list = additionalMappings;
      additionalMappings = null;
      return create(Collections.unmodifiableList(list));
    }
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) {
    return existing;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isMergedClassesInformation();
  }

  @Override
  public boolean isMergedClassesInformation() {
    return true;
  }

  @Override
  public MergedClassesInformation asMergedClassesInformation() {
    return this;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonArray array = new JsonArray();
    for (String mapping : additionalMappings) {
      array.add(new JsonPrimitive(mapping));
    }
    object.add(ADDITIONAL_MAPPINGS_KEY, array);
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
        JsonElement mappingsElement =
            getJsonElementFromObject(
                object, diagnosticsHandler, lineNumber, ADDITIONAL_MAPPINGS_KEY, ID);
        if (mappingsElement != null) {
          Builder builder = builder();
          for (JsonElement element : mappingsElement.getAsJsonArray()) {
            try {
              builder.addAdditionalMapping(element.getAsString());
            } catch (UnsupportedOperationException | IllegalStateException ignored) {
              diagnosticsHandler.warning(
                  MappingInformationDiagnostics.invalidValueForObjectWithId(
                      lineNumber, ADDITIONAL_MAPPINGS_KEY, ID));
            }
          }
          onMappingInfo.accept(builder.build());
        }
      } catch (UnsupportedOperationException | IllegalStateException ignored) {
        diagnosticsHandler.warning(
            MappingInformationDiagnostics.invalidValueForObjectWithId(
                lineNumber, ADDITIONAL_MAPPINGS_KEY, ID));
      }
    }
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }
}
