// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.internal.MappingSupplierInternal;

@Keep
public abstract class MappingSupplier<T extends MappingSupplier<T>>
    extends MappingSupplierInternal {

  /***
   * Register an allowed mapping lookup
   *
   * @param classReference The minified class reference allowed to be lookup up.
   */
  public abstract T registerUse(ClassReference classReference);

  /***
   * Allow looking up all class references. This can cause a slow down because it may fetch
   * information multiple times or read the entire mapping.
   */
  public abstract T allowLookupAllClasses();

  public abstract void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler);
}
