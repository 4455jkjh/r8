// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyRawXmlResourceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimized;

  @Parameters(name = "{0}, optimized: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRawXml("empty.xml", "")
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .compile();
  }

  public static class FooBar {

    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }
}
