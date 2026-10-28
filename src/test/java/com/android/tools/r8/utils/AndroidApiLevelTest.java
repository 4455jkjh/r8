// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AndroidApiLevelTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiLevelTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() {
    assertSame(AndroidApiLevel.V, AndroidApiLevel.getAndroidApiLevel(35, 0));
    assertSame(AndroidApiLevel.BAKLAVA, AndroidApiLevel.getAndroidApiLevel(36, 0));
    assertSame(AndroidApiLevel.BAKLAVA_1, AndroidApiLevel.getAndroidApiLevel(36, 1));
    assertSame(AndroidApiLevel.CINNAMON_BUN, AndroidApiLevel.getAndroidApiLevel(37, 0));
    assertSame(AndroidApiLevel.CINNAMON_BUN_1, AndroidApiLevel.getAndroidApiLevel(37, 1));
    assertSame(AndroidApiLevel.CINNAMON_BUN_2, AndroidApiLevel.getAndroidApiLevel(37, 2));
    assertSame(AndroidApiLevel.MAIN, AndroidApiLevel.getAndroidApiLevel(37, 3));
    assertSame(AndroidApiLevel.MAIN, AndroidApiLevel.getAndroidApiLevel(38, 0));
    assertSame(AndroidApiLevel.MAIN, AndroidApiLevel.getAndroidApiLevel(38, 1));
    assertSame(AndroidApiLevel.MAIN, AndroidApiLevel.getAndroidApiLevel(10000, 0));
  }

  @Test
  public void testSerializationForAPIDatabase() {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      byte serialized = AndroidApiDataAccess.serializeApiLevel(apiLevel);
      assert serialized > 0;
      assert apiLevel == AndroidApiDataAccess.deserializeApiLevel(serialized);
      for (AndroidApiLevel apiLevel2 : AndroidApiLevel.getAndroidApiLevelsSorted()) {
        if (apiLevel != apiLevel2) {
          assert serialized != AndroidApiDataAccess.serializeApiLevel(apiLevel2);
        }
      }
    }
  }

  @Test
  public void testIllegal() {
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(0, 0));
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(35, -1));
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(35, 1));
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(36, 2));
  }

  @Test
  public void testResolveAndroidApiLevel() {
    UncheckedApiLevel v = new UncheckedApiLevel(35, 0);
    ResolvedAndroidApiLevel resolvedV = AndroidApiLevel.resolveAndroidApiLevel(v);
    assertTrue(resolvedV.isKnown());
    assertFalse(resolvedV.isFuture());
    assertFalse(resolvedV.isInvalid());
    assertSame(AndroidApiLevel.V, resolvedV.asKnown().getApiLevel());

    UncheckedApiLevel invalid = new UncheckedApiLevel(35, 1);
    ResolvedAndroidApiLevel resolvedInvalid = AndroidApiLevel.resolveAndroidApiLevel(invalid);
    assertFalse(resolvedInvalid.isKnown());
    assertFalse(resolvedInvalid.isFuture());
    assertTrue(resolvedInvalid.isInvalid());
    assertNull(resolvedInvalid.asKnown());

    UncheckedApiLevel future = new UncheckedApiLevel(38, 0);
    ResolvedAndroidApiLevel resolvedFuture = AndroidApiLevel.resolveAndroidApiLevel(future);
    assertFalse(resolvedFuture.isKnown());
    assertTrue(resolvedFuture.isFuture());
    assertFalse(resolvedFuture.isInvalid());
    assertNull(resolvedFuture.asKnown());
  }
}
