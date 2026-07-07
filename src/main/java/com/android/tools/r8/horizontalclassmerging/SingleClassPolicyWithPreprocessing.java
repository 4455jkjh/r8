// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging;

import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class SingleClassPolicyWithPreprocessing extends SingleClassPolicy {

  public abstract void preprocess(
      LinkedList<HorizontalMergeGroup> groups, ExecutorService executorService)
      throws ExecutionException;

  @Override
  public boolean isSingleClassPolicyWithPreprocessing() {
    return true;
  }

  @Override
  public SingleClassPolicyWithPreprocessing asSingleClassPolicyWithPreprocessing() {
    return this;
  }
}
