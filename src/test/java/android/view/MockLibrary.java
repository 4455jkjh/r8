// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package android.view;

public class MockLibrary {
  public static void doReflection() throws Exception {
    Class.forName("com.android.tools.r8.assistant.D8AssistantInstrumentationTest$TestClass")
        .getName();
  }
}
