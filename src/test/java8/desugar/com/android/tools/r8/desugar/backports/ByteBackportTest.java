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
public final class ByteBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ByteBackportTest(TestParameters parameters) {
    super(parameters, Byte.class, Main.class);
    registerTarget(AndroidApiLevel.K, 7);
  }

  @Override
  public void testD8() throws Exception {
    registerTarget(AndroidApiLevel.O, 17);
    registerTarget(AndroidApiLevel.N, 9);
    super.testD8();
  }

  @Override
  public void testD8Cf() throws Exception {
    registerTarget(AndroidApiLevel.O, 17);
    registerTarget(AndroidApiLevel.N, 9);
    super.testD8Cf();
  }

  @Override
  public void testR8() throws Exception {
    registerTarget(AndroidApiLevel.O, 16);
    registerTarget(AndroidApiLevel.N, 8);
    super.testR8();
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isR8TestBuilder()) {
      R8TestBuilder<?, ?, ?> r8Builder = builder.asR8TestBuilder();
      r8Builder.addKeepRules("-keepclassmembers class * { byte disguise(byte); }");
    }
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testToUnsignedInt();
      testToUnsignedLong();
    }

    private static byte disguise(byte b) {
      return b;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void testHashCode() {
      for (int i = Byte.MIN_VALUE; i < Byte.MAX_VALUE; i++) {
        assertEquals(i, Byte.hashCode((byte) i));
      }
      // Test unused invoke.
      Byte.hashCode(disguise((byte) 1));
    }

    private static void testCompare() {
      assertTrue(Byte.compare(disguise((byte) 1), disguise((byte) 0)) > 0);
      assertTrue(Byte.compare(disguise((byte) 0), disguise((byte) 0)) == 0);
      assertTrue(Byte.compare(disguise((byte) 0), disguise((byte) 1)) < 0);
      assertTrue(Byte.compare(disguise(Byte.MIN_VALUE), disguise(Byte.MAX_VALUE)) < 0);
      assertTrue(Byte.compare(disguise(Byte.MAX_VALUE), disguise(Byte.MIN_VALUE)) > 0);
      assertTrue(Byte.compare(disguise(Byte.MIN_VALUE), disguise(Byte.MIN_VALUE)) == 0);
      assertTrue(Byte.compare(disguise(Byte.MAX_VALUE), disguise(Byte.MAX_VALUE)) == 0);
    }

    private static void testToUnsignedInt() {
      assertEquals(0, Byte.toUnsignedInt(disguise((byte) 0)));
      assertEquals(127, Byte.toUnsignedInt(disguise(Byte.MAX_VALUE)));
      assertEquals(128, Byte.toUnsignedInt(disguise(Byte.MIN_VALUE)));
      assertEquals(255, Byte.toUnsignedInt(disguise((byte) -1)));
    }

    private static void testToUnsignedLong() {
      assertEquals(0L, Byte.toUnsignedLong(disguise((byte) 0)));
      assertEquals(127L, Byte.toUnsignedLong(disguise(Byte.MAX_VALUE)));
      assertEquals(128L, Byte.toUnsignedLong(disguise(Byte.MIN_VALUE)));
      assertEquals(255L, Byte.toUnsignedLong(disguise((byte) -1)));
    }
  }
}
