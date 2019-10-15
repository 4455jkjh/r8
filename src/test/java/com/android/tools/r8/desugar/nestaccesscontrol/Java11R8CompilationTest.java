// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Java11R8CompilationTest extends TestBase {

  public Java11R8CompilationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V5_1_1).build();
  }

  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static void assertNoNests(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().noneMatch(subj -> subj.getDexClass().isInANest()));
  }

  @Test
  public void testR8CompiledWithR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR_11)
        .addKeepRuleFiles(MAIN_KEEP)
        .addKeepRules("-dontwarn java.lang.ClassValue")
        .compile()
        .inspect(this::assertNotEmpty)
        .inspect(Java11R8CompilationTest::assertNoNests);
  }

  private void assertNotEmpty(CodeInspector inspector) {
    assertTrue(inspector.allClasses().size() > 0);
  }
}
