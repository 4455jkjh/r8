// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.utils.structural.Ordered;

public enum MapVersion implements Ordered<MapVersion> {
  MapVersionNone("none"),
  MapVersionExperimental("experimental");

  public static final MapVersion STABLE = MapVersionNone;

  private final String name;

  MapVersion(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static MapVersion fromName(String name) {
    for (MapVersion version : MapVersion.values()) {
      if (version.getName().equals(name)) {
        return version;
      }
    }
    return null;
  }
}
