// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DateInstantConversionTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DateInstantConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testConversion() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addInnerClasses(DateInstantConversionTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(getExpectedResult());
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  private String getExpectedResult() {
    return StringUtils.lines("true", "false", "true");
  }

  static class Main {

    public static void main(String[] args) {
      // Case 1: No nanos
      Instant nowNoNanos = Instant.ofEpochMilli(1234567890L);
      Date dateNoNanos = Date.from(nowNoNanos);
      System.out.println(dateNoNanos.toInstant().equals(nowNoNanos));

      // Case 2: With nanos
      Instant nowWithNanos = nowNoNanos.plusNanos(123456);
      Date dateWithNanos = Date.from(nowWithNanos);
      boolean result = dateWithNanos.toInstant().equals(nowWithNanos);
      System.out.println(result);
      if (!result) {
        // Last character is Z.
        String shortVersion = allButLast(dateWithNanos.toInstant().toString());
        String longVersion = allButLast(nowWithNanos.toString());
        System.out.println(longVersion.startsWith(shortVersion));
      }
    }

    public static String allButLast(String s) {
      return s.substring(0, s.length() - 1);
    }
  }
}
