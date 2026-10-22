// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInstructionFactory;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for the InstructionFactory. */
@RunWith(Parameterized.class)
public class InstructionFactoryTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InstructionFactoryTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void emptyBuffer() {
    ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
    DexInstructionFactory factory = new DexInstructionFactory();
    DexInstruction[] instructions =
        factory.readSequenceFrom(emptyBuffer.asShortBuffer(), 0, 0, new OffsetToObjectMapping());
    assertTrue(instructions.length == 0);
  }
}
