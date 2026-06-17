// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.google.common.io.ByteStreams;
import java.io.IOException;

class R8PassThroughAndroidResource extends R8AndroidResourceBase {

  public R8PassThroughAndroidResource(AndroidResourceInput androidResource, Reporter reporter) {
    super(androidResource, reporter);
  }

  @Override
  public ByteDataView getByteDataView() {
    try {
      return ByteDataView.of(ByteStreams.toByteArray(androidResource.getByteStream()));
    } catch (IOException | ResourceException e) {
      reporter.error(new ExceptionDiagnostic(e, androidResource.getOrigin()));
    }
    return null;
  }
}
