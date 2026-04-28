// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.globalsynthetics;

import static com.android.tools.r8.ToolHelper.getAndroidJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GlobalSyntheticGeneratorAGPUseTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Path globals = temp.newFile("all.globals").toPath();
    GlobalSyntheticsGenerator.run(
        GlobalSyntheticsGeneratorCommand.builder()
            .addLibraryFiles(getAndroidJar(AndroidApiLevel.LATEST))
            .setGlobalSyntheticsOutput(globals)
            .build());

    Path globalsDex = temp.newFile("globals.zip").toPath();
    D8.run(
        D8Command.builder()
            .addLibraryFiles(getAndroidJar(AndroidApiLevel.LATEST))
            .setMinApiLevel(21)
            .addGlobalSyntheticsFiles(globals)
            .setOutput(globalsDex, OutputMode.DexIndexed)
            .build());

    CodeInspector inspector = new CodeInspector(globalsDex);
    assertThat(inspector.clazz("java.lang.Record"), isPresent());
    // Added in API level 24.
    assertThat(inspector.clazz("android.os.HardwarePropertiesManager"), isPresent());
    // Added in API level 36.
    assertThat(inspector.clazz("android.os.Build$VERSION_CODES_FULL"), isPresent());
    // Class com.android.tools.r8.annotations.LambdaMethod is always generated.
    assertThat(inspector.clazz("com.android.tools.r8.annotations.LambdaMethod"), isPresent());
  }
}
