// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

class DesugaredLibraryInfo {

  final int index;
  final long size;

  DesugaredLibraryInfo(int index, long size) {
    this.index = index;
    this.size = size;
  }
}
