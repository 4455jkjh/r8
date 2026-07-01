// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk25.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class IOBackportJava25Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK25)
        .withDexRuntimes()
        // StandardCharSet is not present below 19.
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .enableApiLevelsForCf()
        .build();
  }

  public IOBackportJava25Test(TestParameters parameters) {
    super(parameters, IO.class, IOBackportJava25Main.class);
    registerTarget(AndroidApiLevel.MAIN, 5);
  }

  public static class IOBackportJava25Main {
    public static void main(String[] args) {
      InputStream originalIn = System.in;
      PrintStream originalOut = System.out;
      ByteArrayOutputStream baos = testPrint();
      testReadLn();
      testReadLnString(baos);
      if (originalIn != null) {
        System.setIn(originalIn);
      }
      System.setOut(originalOut);
    }

    private static ByteArrayOutputStream testPrint() {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      System.setOut(new PrintStream(baos));
      IO.print("second");
      IO.println();
      IO.println("first");
      assertEquals(baos.toString(), "second\nfirst\n");
      return baos;
    }

    private static void testReadLn() {
      // The JVM can only set once the input, leading to the read of this two.
      ByteArrayInputStream bais1 = new ByteArrayInputStream("one\ntwo\n".getBytes());
      System.setIn(bais1);
      String readln0 = IO.readln();
      assertEquals(readln0, "one");
    }

    private static void testReadLnString(ByteArrayOutputStream baos) {
      // Art requires to set the input each time, reading this two.
      ByteArrayInputStream bais2 = new ByteArrayInputStream("two\n".getBytes());
      System.setIn(bais2);
      String readln1 = IO.readln("last");
      assertEquals(baos.toString(), "second\nfirst\nlast");
      assertEquals(readln1, "two");
    }

    static void assertEquals(String x, String y) {
      if (!y.equals(x)) {
        throw new RuntimeException("Not equals " + x + " and " + y);
      }
    }
  }
}
