// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.Set;

/** Data provided in the primary callback of {@link DexFilePerClassFileConsumer}. */
@Keep
public interface DexFilePerClassFileConsumerData {

  /** Class descriptor of the class from the input class-file. */
  String getPrimaryClassDescriptor();

  /** DEX encoded data in a ByteDataView wrapper. */
  ByteDataView getByteDataView();

  /** Copy of the bytes for the DEX encoded data. */
  byte[] getByteDataCopy();

  /** Class descriptors for all classes defined in the DEX data. */
  Set<String> getClassDescriptors();

  /** Diagnostics handler for reporting. */
  DiagnosticsHandler getDiagnosticsHandler();
}
