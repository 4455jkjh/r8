// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.apimodel.jar.ApiJarReader;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiJarReaderTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiJarReaderTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testApiJarReader() throws Exception {
    AndroidApiLevel apiLevel = AndroidApiLevel.API_DATABASE_LEVEL;
    ApiJarInfo jarInfo = ApiJarReader.read(ImmutableList.of(ToolHelper.getAndroidJar(apiLevel)));
    assertNotNull(jarInfo);
    assertEquals(6_440, jarInfo.size());
    assertTrue(jarInfo.hasClass("java/lang/Object"));
  }
}
