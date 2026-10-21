// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.dontshrink;

public class R {

  public static class string {
    public static int used_in_code;
    public static int unused_string;
  }

  public static class drawable {
    public static int used_in_code;
    public static int drawable_from_code_xml;
    public static int drawable_from_kept_xml;
    public static int unused_drawable;
  }

  public static class xml {
    public static int xml_used_in_code;
    public static int xml_kept;
    public static int unused_xml;
  }
}
