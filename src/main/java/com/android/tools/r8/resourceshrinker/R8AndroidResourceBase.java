// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.AndroidResourceOutput;
import com.android.tools.r8.ResourcePath;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;

abstract class R8AndroidResourceBase implements AndroidResourceOutput {

  protected final AndroidResourceInput androidResource;
  protected final Reporter reporter;

  public R8AndroidResourceBase(AndroidResourceInput androidResource, Reporter reporter) {
    this.androidResource = androidResource;
    this.reporter = reporter;
  }

  @Override
  public ResourcePath getPath() {
    return androidResource.getPath();
  }

  @Override
  public Origin getOrigin() {
    return androidResource.getOrigin();
  }
}
