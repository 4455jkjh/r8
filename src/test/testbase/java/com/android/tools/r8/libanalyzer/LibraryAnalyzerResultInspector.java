// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static org.junit.Assert.assertNotNull;

public abstract class LibraryAnalyzerResultInspector<
    T, I extends LibraryAnalyzerResultInspector<T, I>> {

  protected final T result;

  LibraryAnalyzerResultInspector(T result) {
    this.result = result;
  }

  public I assertPresent() {
    assertNotNull(result);
    return self();
  }

  public T getResult() {
    return result;
  }

  public abstract I self();
}
