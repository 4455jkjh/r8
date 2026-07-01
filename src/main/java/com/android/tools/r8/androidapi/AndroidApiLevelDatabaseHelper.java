// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import java.util.HashSet;
import java.util.Set;

public class AndroidApiLevelDatabaseHelper {

  public static Set<String> notModeledTypes() {
    // The types below are known not to be modeled by any api-versions.
    Set<String> notModeledTypes = new HashSet<>();
    notModeledTypes.add("androidx.annotation.RecentlyNullable");
    notModeledTypes.add("androidx.annotation.RecentlyNonNull");
    notModeledTypes.add("android.annotation.Nullable");
    notModeledTypes.add("android.annotation.NonNull");
    notModeledTypes.add("android.annotation.FlaggedApi");
    notModeledTypes.add(
        "android.adservices.ondevicepersonalization.FederatedComputeScheduleRequest");
    notModeledTypes.add(
        "android.adservices.ondevicepersonalization.FederatedComputeScheduleResponse");
    return notModeledTypes;
  }
}
