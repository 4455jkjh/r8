// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.movetohost;

import com.android.tools.r8.NeverPropagateValue;

public class HostConflictField {

  static CandidateConflictField INSTANCE = new CandidateConflictField();

  @NeverPropagateValue
  public String field;
}