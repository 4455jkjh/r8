// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordEqualsTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true", "true", "false", "true", "false", "true", "true", "false", "true", "false");

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeCfRuntime();
    assumeTrue(runtimeWithRecordsSupport(parameters.getRuntime()));
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void alwaysUseArtRecordSupportWhenPresent(TestCompilerBuilder<?, ?, ?, ?, ?> builder) {
    builder.applyIf(
        runtimeWithRecordsSupport(parameters.getRuntime())
            && !isRecordsFullyDesugaredForD8(parameters),
        b ->
            b.addOptionsModification(options -> options.emitRecordAnnotationsInDex = true)
                .addOptionsModification(options -> options.recordPartialDesugaring = false));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .apply(this::alwaysUseArtRecordSupportWhenPresent)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestClass.class)
        .apply(this::alwaysUseArtRecordSupportWhenPresent)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    record FloatRecord(float f) {}

    record DoubleRecord(double d) {}

    public static void main(String[] args) {
      FloatRecord fNan = new FloatRecord(Float.NaN);
      FloatRecord fNan2 = new FloatRecord(Float.NaN);
      System.out.println(fNan.equals(fNan));
      System.out.println(fNan.equals(fNan2));
      FloatRecord fZero = new FloatRecord(0.0f);
      FloatRecord fNegZero = new FloatRecord(-0.0f);
      System.out.println(fZero.equals(fNegZero));
      FloatRecord f1 = new FloatRecord(1.0f);
      FloatRecord f2 = new FloatRecord(1.0f);
      System.out.println(f1.equals(f2));
      FloatRecord f3 = new FloatRecord(2.0f);
      System.out.println(f1.equals(f3));

      DoubleRecord dNan = new DoubleRecord(Double.NaN);
      DoubleRecord dNan2 = new DoubleRecord(Double.NaN);
      System.out.println(dNan.equals(dNan));
      System.out.println(dNan.equals(dNan2));
      DoubleRecord dZero = new DoubleRecord(0.0d);
      DoubleRecord dNegZero = new DoubleRecord(-0.0d);
      System.out.println(dZero.equals(dNegZero));
      DoubleRecord d1 = new DoubleRecord(1.0d);
      DoubleRecord d2 = new DoubleRecord(1.0d);
      System.out.println(d1.equals(d2));
      DoubleRecord d3 = new DoubleRecord(2.0d);
      System.out.println(d1.equals(d3));
    }
  }
}
