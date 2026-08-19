// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SpillIntervalWithMultipleDifferentConstrainedUsesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8Debug() throws Exception {
    AssertUtils.assertFailsCompilation(
        () -> testForD8(parameters).addInnerClasses(getClass()).compile(),
        e -> assertTrue(e.getCause() instanceof IndexOutOfBoundsException));
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters).addInnerClasses(getClass()).release().compile();
  }

  static class Main {

    static File finalOutputVideo = new File(".");

    public static void encodeMain(
        Object a0,
        String a1,
        String a2,
        Q a3,
        String a4,
        String a5,
        int a6,
        double a7,
        double a8,
        double a9,
        boolean a10,
        int a11) {
      String local15 = a1;
      Dim local16 = new Dim();
      double local17 = (a3 == Q.SD) ? 1.5d : 2.5d;
      int local19 = (int) Math.ceil(local16.height * local17);
      int local20 = (int) Math.ceil(local16.width * local17);
      String local21 = local15 + local19;
      String local22 =
          buildPipeAndProcess(a0, local20, local19, a9, local21, a4, a5, a6, a7, a8, a10, a11);
      String local23 = local22 + String.valueOf(a3);
      boolean local24 = a6 > 0;
      int local25 = 0;
      String local26 = trimOrCopyOutput(a0, local15, local25, local24);
      String local27 = mergeOrCopyFinalOutput(a0, local23, local26, local24, local25);
      File local28 = new File(a1);
      String local29 = local28.getName();
      int local30 = local29.lastIndexOf(46);
      String local31 = (local30 > 0) ? local29.substring(0, local30) : local29;
      File local32 = new File(a2, local31 + ".avi");
      try {
        copyFile(finalOutputVideo, local32);
      } catch (IOException e) {
      }
    }

    static String buildPipeAndProcess(
        Object a0,
        int a1,
        int a2,
        double a3,
        String a4,
        String a5,
        String a6,
        int a7,
        double a8,
        double a9,
        boolean a10,
        int a11) {
      return a4;
    }

    static String trimOrCopyOutput(Object a0, String a1, int a2, boolean a3) {
      return a1;
    }

    static String mergeOrCopyFinalOutput(Object a0, String a1, String a2, boolean a3, int a4) {
      return a1;
    }

    static void copyFile(File a0, File a1) throws IOException {}

    enum Q {
      SD,
      HD
    }

    static class Dim {
      public int width = 640;
      public int height = 480;
    }
  }
}
