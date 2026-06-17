// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk11;
import static com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.L8_PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX;
import static com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryRemovedClassMapEntryTest extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        getJdk11(),
        ImmutableList.of(R8_L8SHRINK));
  }

  @Test
  public void test() throws Throwable {
    assumeTrue(parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .compile();
    assertThat(
        compileResult.getProguardMap(),
        allOf(
            containsString(PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX),
            not(containsString(L8_PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX))));
    assertThat(
        compileResult.getL8ProguardMap(),
        allOf(
            containsString(L8_PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX),
            not(containsString(PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX))));
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new LinkedHashSet<String>().spliterator();
      RemovedClass.removedMethod();
    }
  }

  static class RemovedClass {

    static void removedMethod() {
      System.out.println("The end");
    }
  }
}
