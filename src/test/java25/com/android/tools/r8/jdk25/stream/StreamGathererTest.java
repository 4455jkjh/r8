// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk25.stream;

import static com.android.tools.r8.ToolHelper.DexVm.Version.V16_0_0;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StreamGathererTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("[1, 2][2, 3][3, 4][4, 5]");
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public StreamGathererTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testReference() throws Exception {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK11 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isNewerThan(V16_0_0),
            b -> b.assertSuccessWithOutput(EXPECTED_RESULT),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testDesugaredLib() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      String windows2 =
          Stream.of(1, 2, 3, 4, 5)
              .gather(Gatherers.windowSliding(2))
              .gather(Gatherers.fold(() -> "", (string, l) -> string + l))
              .findAny()
              .get();
      System.out.println(windows2);
    }
  }
}
