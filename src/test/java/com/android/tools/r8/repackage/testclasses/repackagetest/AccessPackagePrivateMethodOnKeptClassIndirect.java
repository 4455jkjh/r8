// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagetest;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;

@NeverMerge
public class AccessPackagePrivateMethodOnKeptClassIndirect {

  @NeverInline
  static void test() {
    AccessPackagePrivateMethodOnKeptClassDirect.test();
  }
}
