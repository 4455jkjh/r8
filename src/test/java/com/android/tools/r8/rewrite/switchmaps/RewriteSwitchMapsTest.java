// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switchmaps;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RewriteSwitchMapsTest extends TestBase {

  private final Backend backend;

  @Parameterized.Parameters(name = "{0}, backend: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParameters.builder().withNoneRuntime().build(), ToolHelper.getBackends());
  }

  public RewriteSwitchMapsTest(TestParameters parameters, Backend backend) {
    parameters.assertNoneRuntime();
    this.backend = backend;
  }

  private static final String JAR_FILE = "switchmaps.jar";
  private static final String SWITCHMAP_CLASS_NAME = "switchmaps.Switches$1";
  private static final List<String> PG_CONFIG = ImmutableList.of(
      "-keep class switchmaps.Switches { public static void main(...); }",
      "-dontobfuscate",
      "-keepattributes *");

  @Test
  public void checkSwitchMapsRemoved() throws Exception {
    testForR8(backend)
        .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR).resolve(JAR_FILE))
        .addKeepRules(PG_CONFIG)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(SWITCHMAP_CLASS_NAME), isAbsent());
            });
  }
}
