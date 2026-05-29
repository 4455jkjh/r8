// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexContainerFormatRobustnessTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexContainerFormatRobustnessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMalformedFileSizeZero() throws Exception {
    // Create a V41 header with file_size = 0.
    byte[] dexBytes = new byte[Constants.TYPE_HEADER_ITEM_SIZE_V41];
    ByteBuffer buffer = ByteBuffer.wrap(dexBytes);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    // Magic.
    buffer.put(Constants.DEX_FILE_MAGIC_PREFIX);
    buffer.put(new byte[] {'0', '4', '1'});
    buffer.put(Constants.DEX_FILE_MAGIC_SUFFIX);

    // Endian tag at 40.
    buffer.putInt(Constants.ENDIAN_TAG_OFFSET, Constants.ENDIAN_CONSTANT);

    // Header size at 36.
    buffer.putInt(Constants.HEADER_SIZE_OFFSET, Constants.TYPE_HEADER_ITEM_SIZE_V41);

    // Container size at 112.
    buffer.putInt(Constants.CONTAINER_SIZE_OFFSET, Constants.TYPE_HEADER_ITEM_SIZE_V41);

    // Container off at 116.
    buffer.putInt(Constants.CONTAINER_OFF_OFFSET, 0);

    // Data size at 104 and Data off at 108 are already 0.

    // File size at 32 is also 0.

    Path evilDex = temp.newFile("evil.dex").toPath();
    Files.write(evilDex, dexBytes);

    try {
      testForD8(Backend.DEX)
          .addProgramFiles(evilDex)
          .setMinApi(AndroidApiLevel.BAKLAVA) // Will use default or none, but we need to set it.
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                // Once fixed, we expect a compilation error.
                diagnostics.assertErrorsCount(1);
                diagnostics.assertErrorsMatch(
                    diagnosticMessage(
                        org.hamcrest.CoreMatchers.containsString(
                            "Malformed V41 DEX container. Invalid DEX segment file_size 0 at offset"
                                + " 0.")));
              });
    } catch (CompilationFailedException e) {
      // Expected failure, diagnostics are verified by compileWithExpectedDiagnostics.
      return;
    }
    fail("Expected compilation to fail");
  }
}
