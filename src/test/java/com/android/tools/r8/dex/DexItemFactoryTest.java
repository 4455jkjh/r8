// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexItemFactoryTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexItemFactoryTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void commonItems() {
    DexItemFactory factory = new DexItemFactory();

    Object[] data =
        new Object[] {
          "B", factory.byteDescriptor, factory.byteType,
          "C", factory.charDescriptor, factory.charType,
          "D", factory.doubleDescriptor, factory.doubleType,
          "F", factory.floatDescriptor, factory.floatType,
          "I", factory.intDescriptor, factory.intType,
          "J", factory.longDescriptor, factory.longType,
          "S", factory.shortDescriptor, factory.shortType,
          "V", factory.voidDescriptor, factory.voidType,
          "Z", factory.booleanDescriptor, factory.booleanType,
          "Ljava/lang/String;", factory.stringDescriptor, factory.stringType,
          "Ljava/lang/Object;", factory.objectDescriptor, factory.objectType,
        };

    for (int i = 0; i < data.length; i += 3) {
      DexString string1 = factory.createString((String) data[i]);
      DexString string2 = factory.createString((String) data[i]);
      DexItem type1 = factory.createType(string1);
      DexItem type2 = factory.createType(string2);
      DexItem expectedDexString = (DexString) data[i + 1];
      DexItem expectedDexType = (DexType) data[i + 2];

      assertSame(expectedDexString, string1);
      assertSame(expectedDexString, string2);
      assertSame(expectedDexType, type1);
      assertSame(expectedDexType, type2);
    }
  }

  @Test
  public void getPrimitiveTypeName() {
    DexItemFactory factory = new DexItemFactory();
    assertEquals("boolean", factory.booleanType.getName());
    assertEquals("byte", factory.byteType.getName());
    assertEquals("short", factory.shortType.getName());
    assertEquals("char", factory.charType.getName());
    assertEquals("int", factory.intType.getName());
    assertEquals("float", factory.floatType.getName());
    assertEquals("long", factory.longType.getName());
    assertEquals("double", factory.doubleType.getName());
  }

  @Test
  public void stringAndTypeIdentityAndCanonicalization() {
    DexItemFactory factory1 = new DexItemFactory();
    DexItemFactory factory2 = new DexItemFactory();

    DexString s1 = factory1.createString("Lfoo/Bar;");
    DexString s2 = factory2.createString("Lfoo/Bar;");
    assertNotEquals(s1, s2);
    assertEquals(System.identityHashCode(s1), s1.hashCode());
    assertSame(s1, factory1.createString("Lfoo/Bar;"));
    assertSame(s1, factory1.lookupString("Lfoo/Bar;"));
    assertTrue(s1.isEqualToAscii("Lfoo/Bar;"));
    assertFalse(s1.isEqualToAscii("Lfoo/Baz;"));

    DexType t1 = factory1.createType(s1);
    DexType t2 = factory2.createType(s2);
    assertNotEquals(t1, t2);
    assertEquals(System.identityHashCode(t1), t1.hashCode());
    assertSame(t1, factory1.createType("Lfoo/Bar;"));
    assertSame(t1, factory1.lookupType(s1));

    assertEquals(-1, Arrays.hashCode(DexString.encodeToMutf8("SfaNaFg")));
    assertEquals(DexString.NON_ASCII_MUTF8_HASH, DexString.tryComputeAsciiMutf8Hash("SfaNaFg"));

    String[] samples =
        new String[] {"", "\u0000", "a\u0000b", "\u00e6\u00f8\u00e5", "\ud800\udc00", "SfaNaFg"};
    List<DexString> expectedPending = new ArrayList<>();
    for (String sample : samples) {
      DexString fromString = factory1.createString(sample);
      DexString fromBytes = factory1.createString(sample.length(), DexString.encodeToMutf8(sample));
      assertSame(fromString, fromBytes);
      assertSame(fromString, factory1.lookupString(sample));
      expectedPending.add(fromString);
    }

    DexString malformedEmpty = factory1.createString(0, new byte[0]);
    assertNotEquals(factory1.emptyString, malformedEmpty);
    assertSame(malformedEmpty, factory1.createString(0, new byte[0]));
    assertTrue(factory1.emptyString.isEqualToAscii(""));
    assertFalse(malformedEmpty.isEqualToAscii(""));
    assertNull(factory1.lookupString("not_present_pending"));

    factory1.commitPendingItems();

    for (int i = 0; i < samples.length; i++) {
      String sample = samples[i];
      DexString expected = expectedPending.get(i);
      assertSame(expected, factory1.createString(sample));
      assertSame(expected, factory1.createString(sample.length(), DexString.encodeToMutf8(sample)));
      assertSame(expected, factory1.lookupString(sample));
    }
    assertSame(malformedEmpty, factory1.createString(0, new byte[0]));
    assertSame(t1, factory1.lookupType(s1));
    assertSame(t1, factory1.createType(s1));
    assertNull(factory1.lookupString("not_present_committed"));

    List<DexString> bulkAscii = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      bulkAscii.add(factory1.createString("Lcom/example/Generated" + i + ";"));
    }
    factory1.commitPendingItems();
    for (int i = 0; i < 250; i++) {
      String value = "Lcom/example/Generated" + i + ";";
      assertSame(bulkAscii.get(i), factory1.createString(value));
      assertSame(bulkAscii.get(i), factory1.lookupString(value));
    }
    assertSame(s1, factory1.createString("Lfoo/Bar;"));
    assertSame(t1, factory1.lookupType(s1));
  }
}
