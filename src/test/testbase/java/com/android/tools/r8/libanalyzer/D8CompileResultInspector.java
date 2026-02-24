// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.libanalyzer.proto.D8CompileResult;

public class D8CompileResultInspector
    extends LibraryAnalyzerResultInspector<D8CompileResult, D8CompileResultInspector> {

  public D8CompileResultInspector(D8CompileResult d8CompileResult) {
    super(d8CompileResult);
  }

  @Override
  public D8CompileResultInspector self() {
    return this;
  }
}
