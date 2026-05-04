// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.utils.internal.FileUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class ByteDataUtils {

  public static void writeToFile(Path output, OutputStream defValue, ByteDataView contents)
      throws IOException {
    FileUtils.writeToFile(
        output, defValue, contents.getBuffer(), contents.getOffset(), contents.getLength());
  }
}
