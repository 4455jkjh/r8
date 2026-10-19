// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceInitializerDescriptionEqualsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() {
    DexItemFactory factory = new DexItemFactory();
    InstanceInitializerDescription intDescription = createDescription(factory, factory.intType);
    assertEquals(intDescription, createDescription(factory, factory.intType));
    assertNotEquals(intDescription, createDescription(factory, factory.longType));
    // Reference parameter types are relaxed to java.lang.Object.
    assertEquals(
        createDescription(factory, factory.stringType),
        createDescription(factory, factory.objectType));
  }

  // Creates the description of an instance initializer `<init>(parameterType)` that only calls
  // Object.<init>().
  private static InstanceInitializerDescription createDescription(
      DexItemFactory factory, DexType parameterType) {
    DexMethod instanceInitializer =
        factory.createInstanceInitializer(factory.createType("LA;"), parameterType);
    InstanceInitializerDescription.Builder builder =
        new InstanceInitializerDescription.Builder(factory, instanceInitializer);
    builder.addInvokeConstructor(factory.objectMembers.constructor, Collections.emptyList());
    return builder.build();
  }
}
