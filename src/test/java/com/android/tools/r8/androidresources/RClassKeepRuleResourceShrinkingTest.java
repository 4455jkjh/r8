// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assume.assumeTrue;

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
public class RClassKeepRuleResourceShrinkingTest extends TestBase {

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
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    // We don't support running R8Partial with non optimized resource shrinking.
    assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    AndroidTestResource testResources = getTestResources(temp);
    testForR8(parameters)
        .addProgramClasses(FooBar.class)
        .applyIf(
            optimized,
            b -> {
              b.enableOptimizedShrinking();
              b.applyIf(
                  b.isR8PartialTestBuilder(),
                  r8pb ->
                      r8pb.addR8PartialR8OptionsModification(
                          o -> o.removeUnreadKeptRClassResources = true),
                  r8b -> r8b.addOptionsModification(o -> o.removeUnreadKeptRClassResources = true));
            })
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        // Keep the R class fields explicitly
        .addKeepRules("-keep class " + R.string.class.getTypeName() + " { *; }")
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.bar);
        System.out.println(R.string.foo);
      }
    }
  }
}

class R {

  public static class string {
    public static int bar;
    public static int foo;
    public static int unused_string;
  }
}
