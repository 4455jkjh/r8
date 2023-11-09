// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.Collection;

/** Android resource provider. */
@KeepForApi
public interface AndroidResourceProvider {

  // Provide all android resources
  Collection<AndroidResourceInput> getAndroidResources() throws ResourceException;

  default void finished(DiagnosticsHandler handler) {}
}
