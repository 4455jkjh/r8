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

  private MergedClassesInformation() {}

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

  public static MergedClassesInformation build() {
    return new MergedClassesInformation();
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return object.toString();
  }

  public static void deserialize(MapVersion version, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      onMappingInfo.accept(new MergedClassesInformation());
    }
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }
}
