// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public interface ResolvedAndroidApiLevel {

  default boolean isInvalid() {
    return false;
  }

  default boolean isFuture() {
    return false;
  }

  default boolean isKnown() {
    return false;
  }

  default Known asKnown() {
    return null;
  }

  static Invalid invalid() {
    return Invalid.getInstance();
  }

  static Future future() {
    return Future.getInstance();
  }

  static Known known(AndroidApiLevel apiLevel) {
    return new Known(apiLevel);
  }

  class Invalid implements ResolvedAndroidApiLevel {
    private static final Invalid INSTANCE = new Invalid();

    private Invalid() {}

    public static Invalid getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isInvalid() {
      return true;
    }
  }

  class Future implements ResolvedAndroidApiLevel {
    private static final Future INSTANCE = new Future();

    private Future() {}

    public static Future getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isFuture() {
      return true;
    }
  }

  class Known implements ResolvedAndroidApiLevel {
    private final AndroidApiLevel apiLevel;

    public Known(AndroidApiLevel apiLevel) {
      assert apiLevel != null;
      this.apiLevel = apiLevel;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }

    @Override
    public boolean isKnown() {
      return true;
    }

    @Override
    public Known asKnown() {
      return this;
    }
  }
}
