// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.determinism;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import java.nio.file.Path;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TiviDeterminismTest extends DumpDeterminismTestBase {

  public TiviDeterminismTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  Path getDumpFile() {
    return TestDeps.getTiviDumpAppZip();
  }
}
