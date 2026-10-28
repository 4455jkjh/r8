// Copyright (c) 2017, the Rex project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.BaseCompilerCommandUtils;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Simple test that compiles framework.jar with D8 a number of times with various number of threads
 * available to the compiler. This test also tests that no hidden marker is inserted into
 * classes.dex.
 */
@RunWith(Parameterized.class)
public class D8FrameworkDexPassthroughMarkerTest extends TestBase {

  @Parameters(name = "{0}, Min api = {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        Arrays.asList(AndroidApiLevel.N, AndroidApiLevel.O, AndroidApiLevel.O_MR1));
  }

  private final AndroidApiLevel minApi;

  public D8FrameworkDexPassthroughMarkerTest(TestParameters parameters, AndroidApiLevel minApi) {
    parameters.assertNoneRuntime();
    this.minApi = minApi;
  }

  @Test
  public void compile() throws Exception {
    Path frameworkJar =
        TestDeps.getArt5_1_1Dir()
            .resolve("product")
            .resolve("mako")
            .resolve("system")
            .resolve("framework")
            .resolve("framework.jar");
    D8Command.Builder command = D8Command.builder().addProgramFiles(frameworkJar);
    BaseCompilerCommandUtils.setMinApiLevel(command, minApi.asUnchecked());
    Marker marker = new Marker(Tool.D8)
        .setVersion("1.0.0")
        .setMinApi(minApi);
    AndroidApp app = ToolHelper.runD8(command, opts -> opts.setMarker(marker));
    DexApplication dexApp =
        new ApplicationReader(app, new InternalOptions(), Timing.empty()).read();
    Collection<Marker> markers = dexApp.dexItemFactory.extractMarkers();
    assertEquals(0, markers.size());
  }
}
