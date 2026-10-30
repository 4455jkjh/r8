// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.horizontalclassmerging.policies.ArtInstanceFieldLayout.OBJECT_HEADER_SIZE;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtInstanceFieldLayoutTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final DexItemFactory factory = new DexItemFactory();

  @Test
  public void testNoFields() {
    assertLayout(OBJECT_HEADER_SIZE, 8, 8);
  }

  @Test
  public void testRoundUpToObjectAlignment() {
    assertLayout(OBJECT_HEADER_SIZE, 9, 16, factory.byteType);
    assertLayout(OBJECT_HEADER_SIZE, 12, 16, factory.objectType);
    assertLayout(OBJECT_HEADER_SIZE, 16, 16, factory.intType, factory.floatType);
    assertLayout(OBJECT_HEADER_SIZE, 20, 24, factory.intType, factory.longType);
  }

  @Test
  public void testAlignmentGapIsFilledWithSmallerFields() {
    // Without an int to fill the gap, the long is aligned to offset 16.
    assertLayout(OBJECT_HEADER_SIZE, 24, 24, factory.objectType, factory.longType);
    // The int is placed in the gap at offset 12, before the long.
    assertLayout(OBJECT_HEADER_SIZE, 24, 24, factory.objectType, factory.longType, factory.intType);
  }

  @Test
  public void testUnalignedSuperclassFields() {
    // The superclass fields end at offset 9, e.g., the superclass has a single byte field.
    assertLayout(9, 16, 16, factory.objectType);
    // The byte and the short are placed at offset 9 and 10, before the reference at offset 12.
    assertLayout(9, 16, 16, factory.objectType, factory.shortType, factory.byteType);
  }

  private void assertLayout(
      int startOffset, int expectedEndOffset, int expectedInstanceSize, DexType... fieldTypes) {
    int endOffset =
        ArtInstanceFieldLayout.getInstanceFieldsEndOffset(
            startOffset, ImmutableList.copyOf(fieldTypes));
    assertEquals(expectedEndOffset, endOffset);
    assertEquals(expectedInstanceSize, ArtInstanceFieldLayout.getInstanceSize(endOffset));
  }
}
