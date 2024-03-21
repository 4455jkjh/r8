// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.optimize.compose.UpdateChangedFlagsAbstractFunction;

public interface InFlow {

  default boolean isAbstractFunction() {
    return false;
  }

  default AbstractFunction asAbstractFunction() {
    return null;
  }

  default boolean isBaseInFlow() {
    return false;
  }

  default BaseInFlow asBaseInFlow() {
    return null;
  }

  default boolean isFieldValue() {
    return false;
  }

  default FieldValue asFieldValue() {
    return null;
  }

  default boolean isInstanceFieldReadAbstractFunction() {
    return false;
  }

  default InstanceFieldReadAbstractFunction asInstanceFieldReadAbstractFunction() {
    return null;
  }

  default boolean isMethodParameter() {
    return false;
  }

  default MethodParameter asMethodParameter() {
    return null;
  }

  default boolean isUnknownAbstractFunction() {
    return false;
  }

  default boolean isUpdateChangedFlagsAbstractFunction() {
    return false;
  }

  default UpdateChangedFlagsAbstractFunction asUpdateChangedFlagsAbstractFunction() {
    return null;
  }
}
