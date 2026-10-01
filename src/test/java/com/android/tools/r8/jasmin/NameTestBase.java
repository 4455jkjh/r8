// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

class NameTestBase extends JasminTestBase {

  protected final TestParameters parameters;

  NameTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  protected DexVm getVm() {
    return parameters.getDexVm();
  }

  @Override
  protected ProcessResult runOnJavaRaw(JasminBuilder builder, String main) throws Exception {
    Path out = temp.newFolder().toPath();
    builder.writeClassFiles(out);
    return ToolHelper.runJava(parameters.asCfRuntime(), ImmutableList.of(out), main);
  }

  // TestString is a String with modified toString() which prints \\uXXXX for
  // characters outside 0x20..0x7e.
  static class TestString {
    private final String value;

    TestString(String value) {
      this.value = value;
    }

    String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return StringUtils.toASCIIString(value);
    }
  }

  // The return value is a collection of rows with the following fields:
  // - TestParameters.
  // - Name (String) to test (can be class name, field name, method name).
  // - boolean, whether it runs on the JVM.
  // - boolean, whether it runs on the ART.
  static Collection<Object[]> getCommonNameTestData(TestParameters parameters) {
    boolean supportSpaces =
        parameters.isDexRuntime()
            && parameters.asDexRuntime().getMinApiLevel().getMajor()
                >= AndroidApiLevel.R.getMajor();
    return Arrays.asList(
        new Object[][] {
          {parameters, new TestString("azAZ09$_"), true, true},
          {parameters, new TestString("_"), false, true},
          {parameters, new TestString("a-b"), false, true},
          {parameters, new TestString("\u00a0"), false, supportSpaces},
          {parameters, new TestString("\u00a1"), false, true},
          {parameters, new TestString("\u1fff"), false, true},
          {parameters, new TestString("\u2000"), false, supportSpaces},
          {parameters, new TestString("\u200f"), false, false},
          {parameters, new TestString("\u2010"), false, true},
          {parameters, new TestString("\u2027"), false, true},
          {parameters, new TestString("\u2028"), false, false},
          {parameters, new TestString("\u202f"), false, supportSpaces},
          {parameters, new TestString("\u2030"), false, true},
          {parameters, new TestString("\ud7ff"), false, true},
          {parameters, new TestString("\ue000"), false, true},
          {parameters, new TestString("\uffef"), false, true},
          {parameters, new TestString("\ufff0"), false, false},
          {parameters, new TestString("\uffff"), false, false},

          // Standalone high and low surrogates.
          {parameters, new TestString("\ud800"), false, false},
          {parameters, new TestString("\udbff"), false, false},
          {parameters, new TestString("\udc00"), false, false},
          {parameters, new TestString("\udfff"), false, false},

          // Single and double code points above 0x10000.
          {parameters, new TestString("\ud800\udc00"), true, true},
          {parameters, new TestString("\ud800\udcfa"), true, true},
          {parameters, new TestString("\ud800\udcfb"), false, true},
          {parameters, new TestString("\udbff\udfff"), false, true},
          {parameters, new TestString("\ud800\udc00\ud800\udcfa"), true, true},
          {parameters, new TestString("\ud800\udc00\udbff\udfff"), false, true}
        });
  }

  void runNameTesting(
      boolean validForJVM,
      JasminBuilder jasminBuilder,
      String mainClassName,
      String expectedResult,
      boolean validForArt,
      String expectedNameInFailingD8Message)
      throws Exception {

    if (parameters.isCfRuntime()) {
      if (validForJVM) {
        String javaResult = runOnJava(jasminBuilder, mainClassName);
        assertEquals(expectedResult, javaResult);
      } else {
        try {
          runOnJava(jasminBuilder, mainClassName);
          fail("Should have failed on JVM.");
        } catch (AssertionError | InvalidPathException e) {
          // Silent on expected failure.
        }
      }
      return;
    }

    if (validForArt) {
      String artResult =
          runOnArtD8(
              jasminBuilder,
              mainClassName,
              o -> o.setMinApiLevel(parameters.asDexRuntime().getMinApiLevel()));
      assertEquals(expectedResult, artResult);
    } else {
      // Make sure the compiler fails.
      try {
        runOnArtD8(
            jasminBuilder,
            mainClassName,
            o -> o.setMinApiLevel(parameters.asDexRuntime().getMinApiLevel()));
        fail("D8 should have rejected this case.");
      } catch (CompilationFailedException t) {
        assertTrue(t.getCause().getMessage().contains(expectedNameInFailingD8Message));
      }

      // Make sure ART also fail, if D8 rejects it.
      try {
        runOnArtD8(
            jasminBuilder,
            mainClassName,
            options -> {
              options.itemFactory.setSkipNameValidationForTesting(true);
              options.setMinApiLevel(parameters.asDexRuntime().getMinApiLevel());
            });
        fail("Art should have failed.");
      } catch (AssertionError e) {
        // Silent on expected failure.
      }
    }
  }
}
