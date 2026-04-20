// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantJumboStringTest extends SmaliTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    smaliBuilder.addStaticMethod(
        "int",
        DEFAULT_METHOD_NAME,
        Collections.emptyList(),
        1,
        "    const-string/jumbo v0, \"FOO\"",
        "    invoke-virtual {v0}, Ljava/lang/String;->length()I",
        "    move-result v0",
        "    return v0");
    smaliBuilder.addMainMethod(0, "    return-void");

    testForD8(parameters.getBackend())
        .addProgramDexFileData(smaliBuilder.compile())
        .release()
        .run(parameters.getRuntime(), DEFAULT_CLASS_NAME)
        .assertSuccess()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(DEFAULT_CLASS_NAME);
              MethodSubject method = clazz.uniqueMethodWithOriginalName(DEFAULT_METHOD_NAME);
              assertThat(method, CodeMatchers.containsConstString("FOO", JumboStringMode.ALLOW));
              assertThat(
                  method, not(CodeMatchers.containsConstString("FOO", JumboStringMode.DISALLOW)));
            });
  }
}
