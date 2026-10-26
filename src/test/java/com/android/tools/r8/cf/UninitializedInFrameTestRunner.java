// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UninitializedInFrameTestRunner extends TestBase {
  static final Class CLASS = UninitializedInFrameTest.class;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK21).build();
  }

  public UninitializedInFrameTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    test(ToolHelper.getClassAsBytes(CLASS));
  }

  @Test
  public void testDump() throws Exception {
    test(UninitializedInFrameDump.dump());
  }

  private void test(byte[] clazz) throws CompilationFailedException, IOException {
    Path input = temp.getRoot().toPath().resolve("input.jar");
    Path output = temp.getRoot().toPath().resolve("output.jar");

    ArchiveConsumer inputConsumer = new ArchiveConsumer(input);
    inputConsumer.accept(
        ByteDataView.of(clazz), DescriptorUtils.javaTypeToDescriptor(CLASS.getName()), null);
    inputConsumer.finished(null);
    ProcessResult runInput =
        ToolHelper.runJava(
            parameters.getRuntime().asCf(), ImmutableList.of(input), CLASS.getCanonicalName());
    if (runInput.exitCode != 0) {
      System.out.println(runInput);
    }
    Assert.assertEquals(0, runInput.exitCode);

    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addClassProgramData(clazz, Origin.unknown())
            .addLibraryFiles(TestDeps.getJava8RuntimeJar())
            .setProgramConsumer(new ArchiveConsumer(output))
            .build());
    ProcessResult runOutput =
        ToolHelper.runJava(
            parameters.getRuntime().asCf(), ImmutableList.of(output), CLASS.getCanonicalName());
    if (runOutput.exitCode != 0) {
      System.out.println(runOutput);
    }
    Assert.assertEquals(runInput.toString(), runOutput.toString());
  }
}
