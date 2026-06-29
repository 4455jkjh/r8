// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexToDexRuntimeInvisibleAnnotationsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean retainCompileTimeAnnotations;

  @Parameters(name = "{0}, retain: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult r8CompileResult =
        testForR8(Backend.DEX)
            .addInnerClasses(getClass())
            .addKeepAllClassesRule()
            .addKeepRuntimeInvisibleAnnotations()
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .inspect(
                inspector -> assertEquals(1, inspector.clazz(Main.class).annotations().size()));

    // Recompile using D8. Runtime invisible annotations should be stripped by default.
    // When we explicitly request to keep them, they should be present.
    testForD8(Backend.DEX)
        .addProgramResourceProviders(r8CompileResult.getOutputProgramResourceProviders())
        .addOptionsModification(
            options -> {
              assertFalse(options.retainCompileTimeAnnotations);
              options.retainCompileTimeAnnotations = retainCompileTimeAnnotations;
            })
        .release()
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    retainCompileTimeAnnotations ? 1 : 0,
                    inspector.clazz(Main.class).annotations().size()));
  }

  @RuntimeInvisibleAnnotation
  static class Main {

    public static void main(String[] args) {}
  }

  @Retention(RetentionPolicy.CLASS)
  @interface RuntimeInvisibleAnnotation {}
}
