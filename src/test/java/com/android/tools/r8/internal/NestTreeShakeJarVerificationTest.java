// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestTreeShakeJarVerificationTest extends NestCompilationBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8,
        CompilationMode.RELEASE,
        null,
        ImmutableList.of(BASE + PG_CONF, BASE + PG_CONF_NO_OPT),
        null,
        ImmutableList.of(BASE + DEPLOY_JAR),
        parameters.getDexVm());
    assertEquals(0, filterKotlinMetadata(handler.warnings).count());
    // TODO(b/155536535): We find bad descriptors. See if we can still resolve them.
    assertEquals(0, filterKotlinMetadata(handler.infos).count());
  }

  private Stream<Diagnostic> filterKotlinMetadata(List<Diagnostic> warnings) {
    return warnings.stream()
        .filter(diagnostic -> diagnostic.getDiagnosticMessage().contains("kotlin.Metadata"));
  }
}
