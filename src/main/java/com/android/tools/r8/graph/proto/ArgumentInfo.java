// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class ArgumentInfo {

  static final ArgumentInfo NO_INFO =
      new ArgumentInfo() {

        @Override
        public ArgumentInfo combine(ArgumentInfo info) {
          assert false : "ArgumentInfo NO_INFO should not be combined";
          return info;
        }

        @Override
        public boolean isNone() {
          return true;
        }

        @Override
        public ArgumentInfo rewrittenWithLens(
            AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
          return this;
        }

        @Override
        public boolean equals(Object obj) {
          return obj == this;
        }

        @Override
        public int hashCode() {
          return System.identityHashCode(this);
        }
      };

  @SuppressWarnings("ConstantConditions")
  public static ArgumentInfo combine(ArgumentInfo arg1, ArgumentInfo arg2) {
    if (arg1 == null) {
      assert arg2 != null;
      return arg2;
    }
    if (arg2 == null) {
      assert arg1 != null;
      return arg1;
    }
    return arg1.combine(arg2);
  }

  public boolean isNone() {
    return false;
  }

  public boolean isRemovedArgumentInfo() {
    return false;
  }

  public RemovedArgumentInfo asRemovedArgumentInfo() {
    return null;
  }

  public boolean isRewrittenTypeInfo() {
    return false;
  }

  public RewrittenTypeInfo asRewrittenTypeInfo() {
    return null;
  }

  // ArgumentInfo are combined with `this` first, and the `info` argument second.
  public abstract ArgumentInfo combine(ArgumentInfo info);

  public abstract ArgumentInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens);

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
