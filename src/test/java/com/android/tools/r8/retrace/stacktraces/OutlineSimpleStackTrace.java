// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class OutlineSimpleStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList("java.io.IOException: INVALID_SENDER", "\tat a.a(:1)", "\tat b.s(:27)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# { id: 'com.android.tools.r8.mapping', version: 'experimental' }",
        "outline.Class -> a:",
        "  1:2:int outline() -> a",
        "# { 'id':'com.android.tools.r8.outline' }",
        "some.Class -> b:",
        "  1:1:void foo.bar.Baz.qux():42:42 -> s",
        "  4:4:int outlineCaller(int):98:98 -> s",
        "  27:27:int outlineCaller(int):0:0 -> s",
        "# { 'id':'com.android.tools.r8.outlineCallsite', 'positions': { '1': 4, '2': 5 } }");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER", "\tat some.Class.outlineCaller(Class.java:98)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER",
        "\tat some.Class.int outlineCaller(int)(Class.java:98)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
