// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.info.field.EmptyInstanceFieldInitializationInfoCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonTrivialInstanceInitializerInfoBuilderTest extends TestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static NonTrivialInstanceInitializerInfo.Builder builder() {
    return NonTrivialInstanceInitializerInfo.builder(
        EmptyInstanceFieldInitializationInfoCollection.getInstance());
  }

  @Test
  public void testMayHaveOtherSideEffectsThanInstanceFieldAssignments() {
    assertFalse(builder().mayHaveOtherSideEffectsThanInstanceFieldAssignments());
    assertTrue(
        builder()
            .setMayHaveOtherSideEffectsThanInstanceFieldAssignments()
            .mayHaveOtherSideEffectsThanInstanceFieldAssignments());
    // Clearing the other bits does not imply that there are other side effects.
    assertFalse(
        builder()
            .setInstanceFieldInitializationMayDependOnEnvironment()
            .setReceiverMayEscapeOutsideConstructorChain()
            .mayHaveOtherSideEffectsThanInstanceFieldAssignments());
  }
}
