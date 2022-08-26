// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.TextInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UTF8TextInputStream implements TextInputStream {

  private final InputStream inputStream;

  public UTF8TextInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }
}
