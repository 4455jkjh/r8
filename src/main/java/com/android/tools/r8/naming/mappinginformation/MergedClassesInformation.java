// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.Consumer;

// TODO(b/388931662): Fully deserialize and serialize.
public class MergedClassesInformation extends ReferentialMappingInformation {

  public static final String ID = "com.android.tools.r8.mergedClasses";
  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_3;

  private final JsonObject rawObject;

  private MergedClassesInformation(JsonObject rawObject) {
    this.rawObject = rawObject;
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

  public static MergedClassesInformation build(JsonObject rawObject) {
    return new MergedClassesInformation(rawObject);
  }

  public static MergedClassesInformation build() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return new MergedClassesInformation(object);
  }

  @Override
  public String serialize() {
    if (rawObject != null) {
      return rawObject.toString();
    }
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return object.toString();
  }

  public static void deserialize(
      MapVersion version, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      onMappingInfo.accept(new MergedClassesInformation(object));
    }
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }
}
