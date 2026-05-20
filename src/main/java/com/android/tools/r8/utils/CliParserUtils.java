// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class CliParserUtils {

  public static void parsePositiveInt(
      String arg, IntConsumer handler, Consumer<String> errorConsumer) {
    try {
      int parsedArg = Integer.parseInt(arg);
      if (parsedArg < 1) {
        errorConsumer.accept(arg + " is not a positive integer");
      } else {
        handler.accept(parsedArg);
      }
    } catch (NumberFormatException e) {
      errorConsumer.accept(arg + " is not an integer");
    }
  }
}
