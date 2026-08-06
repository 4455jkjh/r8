// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ShortBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ShortBackportTest(TestParameters parameters) {
    super(parameters, Short.class, Main.class);
    registerTarget(AndroidApiLevel.O, 16);
    registerTarget(AndroidApiLevel.N, 8);
    registerTarget(AndroidApiLevel.K, 7);
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isR8TestBuilder()) {
      R8TestBuilder<?, ?, ?> r8Builder = builder.asR8TestBuilder();
      r8Builder.addKeepRules("-keepclassmembers class * { short disguise(short); }");
    }
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testToUnsignedInt();
      testToUnsignedLong();
    }

    private static short disguise(short d) {
      return d;
    }

    private static void testHashCode() {
      for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
        assertEquals(i, Short.hashCode((short) i));
      }
    }

    private static void testCompare() {
      assertTrue(Short.compare(disguise((short) 1), disguise((short) 0)) > 0);
      assertTrue(Short.compare(disguise((short) 0), disguise((short) 0)) == 0);
      assertTrue(Short.compare(disguise((short) 0), disguise((short) 1)) < 0);
      assertTrue(Short.compare(disguise(Short.MIN_VALUE), disguise(Short.MAX_VALUE)) < 0);
      assertTrue(Short.compare(disguise(Short.MAX_VALUE), disguise(Short.MIN_VALUE)) > 0);
      assertTrue(Short.compare(disguise(Short.MIN_VALUE), disguise(Short.MIN_VALUE)) == 0);
      assertTrue(Short.compare(disguise(Short.MAX_VALUE), disguise(Short.MAX_VALUE)) == 0);
    }

    private static void testToUnsignedInt() {
      assertEquals(0, Short.toUnsignedInt(disguise((short) 0)));
      assertEquals(32767, Short.toUnsignedInt(disguise(Short.MAX_VALUE)));
      assertEquals(32768, Short.toUnsignedInt(disguise(Short.MIN_VALUE)));
      assertEquals(65535, Short.toUnsignedInt(disguise((short) -1)));
    }

    private static void testToUnsignedLong() {
      assertEquals(0L, Short.toUnsignedLong(disguise((short) 0)));
      assertEquals(32767L, Short.toUnsignedLong(disguise(Short.MAX_VALUE)));
      assertEquals(32768L, Short.toUnsignedLong(disguise(Short.MIN_VALUE)));
      assertEquals(65535L, Short.toUnsignedLong(disguise((short) -1)));
    }
  }
}
