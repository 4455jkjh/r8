// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.NeverInline;

class StringConcatenationTestClass {
  @NeverInline
  public static void trivialSequence() {
    String x = "x";
    String y = "y";
    String z = "z";
    System.out.println(x + y + z);
  }

  @NeverInline
  public static void nonStringArgs() {
    StringBuilder builder = new StringBuilder();
    builder.append(4);
    builder.append(2);
    System.out.println(builder.toString());
  }

  @NeverInline
  public static void typeConversion() {
    StringBuilder builder = new StringBuilder();
    float f = 0.14f;
    builder.append(f);
    builder.append(' ');
    int i = (int) f;
    builder.append(i);
    builder.append(' ');

    boolean b = false;
    builder.append(b);
    builder.append(' ');
    Object n = null;
    builder.append(n);
    System.out.println(builder.toString());
  }

  @NeverInline
  public static void nestedBuilders_appendBuilderItself() {
    StringBuilder b1 = new StringBuilder();
    b1.append("Hello");
    b1.append(",");
    StringBuilder b2 = new StringBuilder();
    b2.append("R");
    // TODO(b/114002137): switch to use the integer.
    b2.append("8");
    b1.append(b2);
    System.out.println(b1.toString());
  }

  @NeverInline
  public static void nestedBuilders_appendBuilderResult() {
    StringBuilder b1 = new StringBuilder();
    b1.append("Hello");
    b1.append(",");
    StringBuilder b2 = new StringBuilder();
    b2.append("R");
    // TODO(b/114002137): switch to use the integer.
    b2.append("8");
    b1.append(b2.toString());
    System.out.println(b1.toString());
  }

  @NeverInline
  public static void simplePhi() {
    StringBuilder builder = new StringBuilder();
    builder.append("Hello");
    builder.append(",");
    System.out.println(builder.toString());

    if (System.currentTimeMillis() > 0) {
      builder.append("D");
    } else {
      builder.append("R");
    }
    // TODO(b/114002137): switch to use the integer.
    builder.append("8");
    System.out.println(builder.toString());
  }

  @NeverInline
  public static void phiAtInit() {
    // TODO(b/114002137): Use ASM to test two new-instance calls flow into the same <init>
    StringBuilder builder =
        System.currentTimeMillis() > 0 ? new StringBuilder("Hello") : new StringBuilder("Hi");
    builder.append(",R8");
    System.out.println(builder.toString());
  }

  @NeverInline
  public static void phiWithDifferentInits() {
    StringBuilder b1 = new StringBuilder("Hello");
    StringBuilder b2 = new StringBuilder("Hi");
    StringBuilder builder = System.currentTimeMillis() > 0 ? b1 : b2;
    builder.append(",R8");
    System.out.println(builder.toString());
  }

  @NeverInline
  public static void loop() {
    String r = "";
    for (int i = 0; i < 8; i++) {
      r = r + "na;";
    }
    System.out.println(r + "Batman!");
  }

  @NeverInline
  public static void loopWithBuilder() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      builder.append("na;");
    }
    builder.append("Batman!");
    System.out.println(builder.toString());
  }

  public static void main(String[] args) {
    trivialSequence();
    nonStringArgs();
    typeConversion();
    nestedBuilders_appendBuilderItself();
    nestedBuilders_appendBuilderResult();
    simplePhi();
    phiAtInit();
    phiWithDifferentInits();
    loop();
    loopWithBuilder();
  }
}
