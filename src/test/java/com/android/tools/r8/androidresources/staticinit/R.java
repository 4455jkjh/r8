// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.staticinit;

public class R {
  public static class string {
    public static int used_clinit;
    public static int unused_clinit;
    public static int used_0;
    public static int unused_0;
    public static int used_1;
    public static int unused_1;
  }

  public static class drawable {
    public static int used_drawable_clinit;
    public static int unused_drawable_clinit;
    public static int used_drawable_0;
    public static int unused_drawable_0;
    public static int used_drawable_1;
    public static int unused_drawable_1;
  }

  public static class styleable {
    public static int[] used_styleable_clinit;
    public static int[] unused_styleable_clinit;
    public static int[] used_styleable_0;
    public static int[] unused_styleable_0;
    public static int[] used_styleable_1;
    public static int[] unused_styleable_1;
  }
}
