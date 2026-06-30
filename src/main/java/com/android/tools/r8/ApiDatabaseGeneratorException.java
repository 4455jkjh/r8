// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Exception thrown when API database generation failed to complete. */
@KeepForApi
public class ApiDatabaseGeneratorException extends Exception {

  public ApiDatabaseGeneratorException() {
    super("API database generation failed to complete");
  }

  public ApiDatabaseGeneratorException(String message) {
    super(message);
  }

  public ApiDatabaseGeneratorException(String message, Throwable cause) {
    super(message, cause);
  }
}
