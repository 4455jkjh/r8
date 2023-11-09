// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MappingSupplierInternal;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface MappingSupplier<T extends MappingSupplier<T>>
    extends MappingSupplierBase<T>, MappingSupplierInternal {

  Retracer createRetracer(DiagnosticsHandler diagnosticsHandler);
}
