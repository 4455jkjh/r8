// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ClassNameMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GMSCoreV10ProguardMapReaderTest extends GMSCoreCompilationTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GMSCoreV10ProguardMapReaderTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void roundTripTestGmsCoreV10() throws IOException {
    Path map = TestDeps.getGmscoreV10Dir().resolve(PG_MAP);
    ClassNameMapper firstMapper = ClassNameMapper.mapperFromFile(map).sorted();
    ClassNameMapper secondMapper =
        ClassNameMapper.mapperFromString(firstMapper.toString()).sorted();
    Assert.assertEquals(firstMapper, secondMapper);
  }
}
