// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.Generator;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class KeepItemGeneratedFilesTest {

  @Test
  public void checkUpToDate() throws IOException {
    Generator.run(
        (file, content) -> {
          try {
            String expectedContent = FileUtils.readTextFile(file, StandardCharsets.UTF_8);
            assertEquals(expectedContent, content);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
