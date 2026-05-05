// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.desugaredlibrary.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.CharBuffer;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CharSequenceIsEmptyTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "side-effect",
          "true",
          "side-effect",
          "true",
          "true",
          "true",
          "false",
          "false",
          "false",
          "false",
          "true",
          "true",
          "true",
          "true");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.O)
            .build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public CharSequenceIsEmptyTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Main.class)
        .noMinification()
        // Need CharSequence#isEmpty in library.
        .overrideLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class Main {

    public static class MyCharSeq implements CharSequence {

      @Override
      public int length() {
        return 0;
      }

      @Override
      public char charAt(int index) {
        return 0;
      }

      @Override
      public CharSequence subSequence(int start, int end) {
        return null;
      }

      @Override
      public String toString() {
        return "M";
      }
    }

    public static class MyCharSeqOverride implements CharSequence {

      @Override
      public boolean isEmpty() {
        System.out.println("side-effect");
        return CharSequence.super.isEmpty();
      }

      @Override
      public int length() {
        return 0;
      }

      @Override
      public char charAt(int index) {
        return 0;
      }

      @Override
      public CharSequence subSequence(int start, int end) {
        return null;
      }

      @Override
      public String toString() {
        return "M";
      }
    }

    public static void main(String[] args) {
      MyCharSeqOverride seqOver = new MyCharSeqOverride();
      System.out.println(seqOver.isEmpty());
      System.out.println(isEmpty(seqOver));
      MyCharSeq seq = new MyCharSeq();
      System.out.println(seq.isEmpty());
      System.out.println(isEmpty(seq));
      CharBuffer buffer = CharBuffer.wrap("buffer");
      System.out.println(buffer.isEmpty());
      System.out.println(isEmpty(buffer));
      String string = "string";
      System.out.println(string.isEmpty());
      System.out.println(isEmpty(string));
      StringBuilder sb = new StringBuilder();
      System.out.println(sb.isEmpty());
      System.out.println(isEmpty(sb));
      System.out.println(sb.toString().isEmpty());
      System.out.println(isEmpty(sb.toString()));
    }

    public static boolean isEmpty(CharSequence seq) {
      return seq.isEmpty();
    }
  }
}
