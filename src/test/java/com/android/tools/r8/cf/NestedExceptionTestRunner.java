// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestedExceptionTestRunner extends TestBase {
  static final Class CLASS = NestedExceptionTest.class;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK21).build();
  }

  public NestedExceptionTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNestedException() throws Exception {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(TestDeps.getJava8RuntimeJar())
            .setProgramConsumer(sink.wrapClassFileConsumer(null))
            .build());
    Path out = temp.newFolder().toPath().resolve("test.jar");
    AndroidApp compiledResources = sink.build();
    compiledResources.writeForTesting(out, OutputMode.ClassFile);
    assertEquals(
        0,
        ToolHelper.runJava(
                parameters.getRuntime().asCf(), ImmutableList.of(out), CLASS.getCanonicalName())
            .exitCode);
  }
}
