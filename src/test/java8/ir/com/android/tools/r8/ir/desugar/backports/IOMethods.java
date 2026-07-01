// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.IOStub;
import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class IOMethods {
  public static void printlnObject(Object obj) {
    System.out.println(obj);
  }

  public static void println() {
    System.out.println();
  }

  public static void print(Object obj) {
    System.out.print(obj);
    System.out.flush();
  }

  public static String readln() {
    try {
      String enc = System.getProperty("stdin.encoding", null);
      Charset cs =
          (enc != null && Charset.isSupported(enc)) ? Charset.forName(enc) : StandardCharsets.UTF_8;
      return new BufferedReader(new InputStreamReader(System.in, cs)).readLine();
    } catch (IOException ioe) {
      throw new IOError(ioe);
    }
  }

  public static String readlnString(String prompt) {
    IOStub.print(prompt);
    return IOStub.readln();
  }
}
