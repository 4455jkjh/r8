// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ResolvedAndroidApiLevel;
import com.android.tools.r8.utils.UncheckedApiLevel;
import com.android.tools.r8.utils.internal.ThrowingConsumer;

public class ApiLevelParser {

  public static AndroidApiLevel parse(String versionString) throws ApiDatabaseGeneratorException {
    return parse(
        versionString,
        message -> {
          throw new ApiDatabaseGeneratorException(message);
        });
  }

  public static <E extends Throwable> AndroidApiLevel parse(
      String versionString, ThrowingConsumer<String, E> onError) throws E {
    UncheckedApiLevel unchecked = UncheckedApiLevel.parse(versionString, onError);
    if (unchecked == null) {
      return null;
    }
    return getAndroidApiLevel(unchecked, onError);
  }

  public static <E extends Throwable> AndroidApiLevel getAndroidApiLevel(
      UncheckedApiLevel unchecked, ThrowingConsumer<String, E> onError) throws E {
    ResolvedAndroidApiLevel resolved = AndroidApiLevel.resolveAndroidApiLevel(unchecked);
    if (resolved.isKnown()) {
      return resolved.asKnown().getApiLevel();
    }
    if (resolved.isFuture()) {
      onError.accept(
          "Unsupported Android API level '"
              + unchecked
              + "': versions above "
              + AndroidApiLevel.LATEST.getNumericString()
              + " are not supported");
      return null;
    }
    assert resolved.isInvalid();
    onError.accept("Invalid Android API level: " + unchecked);
    return null;
  }
}
