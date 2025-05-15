// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.assistant.ReflectiveCallExtractor.extractReflectiveCalls;
import static com.android.tools.r8.assistant.ReflectiveCallExtractor.printMethods;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonReflectiveCallExtractorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testGson() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Map<DexType, Collection<DexMethod>> reflectiveMethods =
        extractReflectiveCalls(ToolHelper.GSON, factory);
    Set<DexMethod> instrumentedMethodsForTesting =
        new InstrumentedReflectiveMethodList(factory).getInstrumentedMethodsForTesting();
    int supported = 0;
    int unsupported = 0;
    for (DexType dexType : reflectiveMethods.keySet()) {
      Collection<DexMethod> methods = reflectiveMethods.get(dexType);
      ArrayList<DexMethod> toRemove = new ArrayList<>();
      for (DexMethod dexMethod : methods) {
        if (instrumentedMethodsForTesting.contains(dexMethod)) {
          toRemove.add(dexMethod);
          supported++;
        } else {
          unsupported++;
        }
      }
      methods.removeAll(toRemove);
    }
    Assert.assertEquals(6, supported);
    Assert.assertEquals("Missing :\n" + printMethods(reflectiveMethods), 69, unsupported);
  }
}
