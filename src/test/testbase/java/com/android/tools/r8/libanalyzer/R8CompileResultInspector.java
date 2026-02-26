// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.libanalyzer.proto.R8CompileResult;

public class R8CompileResultInspector
    extends LibraryAnalyzerResultInspector<R8CompileResult, R8CompileResultInspector> {

  public R8CompileResultInspector(R8CompileResult r8CompileResult) {
    super(r8CompileResult);
  }

  @Override
  public R8CompileResultInspector self() {
    return this;
  }
}
