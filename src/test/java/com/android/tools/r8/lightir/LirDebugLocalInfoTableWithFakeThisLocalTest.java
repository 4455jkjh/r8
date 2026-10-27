// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LirDebugLocalInfoTableWithFakeThisLocalTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public LirDebugLocalInfoTableWithFakeThisLocalTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testWithoutEnds() {
    InternalOptions options = new InternalOptions();
    DexItemFactory factory = options.dexItemFactory();
    DexType fooType = factory.createType("LFoo;");
    DexMethod method = factory.createMethod(fooType, factory.createProto(factory.voidType), "bar");
    LirCode<Integer> code =
        LirCode.builder(
                method, false, LirStrategy.getDefaultStrategy().getEncodingStrategy(), options)
            .addArgument(0, false)
            .setDebugValue(new DebugLocalInfo(factory.thisName, fooType, null), 0)
            .addReturnVoid()
            .build();
    assertEquals("this", code.getDebugLocalInfo(0).name.toString());
    assertNull(code.getDebugLocalEnds(0));
    LirCode<Integer> newCode = code.newCodeWithFakeThisParameter(factory);
    assertEquals("_this", newCode.getDebugLocalInfo(0).name.toString());
    assertNull(newCode.getDebugLocalEnds(0));
  }
}
