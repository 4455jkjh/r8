// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.ZipUtils;
import java.util.Arrays;

public abstract class PrivateInterfaceMethodStaticizerTestBase extends TestBase {

  static boolean isStaticized(TestParameters parameters) {
    return parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R);
  }

  static DexVersion getDefaultDexVersion(TestParameters parameters) {
    DexVersion dexVersion = DexVersion.getDexVersion(parameters.getApiLevel());
    return dexVersion.isGreaterThan(DexVersion.V39) ? DexVersion.V39 : dexVersion;
  }

  static void assertDexVersion(DexVersion expected, TestCompileResult<?, ?> compileResult)
      throws Exception {
    byte[] magic = new byte[8];
    assertEquals(8, ZipUtils.readSingleEntry(compileResult.writeToZip(), "classes.dex", magic));
    assertArrayEquals(expected.getBytes(), Arrays.copyOfRange(magic, 4, 7));
  }

  static void assertExpectedDexVersion(
      TestParameters parameters, TestCompileResult<?, ?> compileResult) throws Exception {
    assertDexVersion(
        isStaticized(parameters) ? DexVersion.V35 : getDefaultDexVersion(parameters),
        compileResult);
  }
}
