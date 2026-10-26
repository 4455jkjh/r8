// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class InstanceInitializerInfoContext {

  public boolean isAlwaysTrue() {
    return false;
  }

  public abstract boolean isSatisfiedBy(InvokeMethod invoke);

  /** Returns the rewritten context, or null if the context can no longer be satisfied. */
  public InstanceInitializerInfoContext fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }
}
