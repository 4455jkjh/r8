// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.utils.Reporter;

class R8AndroidResourceWithData extends R8AndroidResourceBase {

  private final byte[] data;

  public R8AndroidResourceWithData(
      AndroidResourceInput androidResource, Reporter reporter, byte[] data) {
    super(androidResource, reporter);
    this.data = data;
  }

  @Override
  public ByteDataView getByteDataView() {
    return ByteDataView.of(data);
  }
}
