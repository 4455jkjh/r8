// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RClassStaticValuesIgnoreTest extends TestBase {

  enum Config {
    LEGACY,
    OPTIMIZED,
    OPTIMIZED_REMOVE_KEPT_RCLASS_RESOURCES;
  }

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public Config config;

  private static final String RClassDescriptor =
      descriptor(RClassStaticValuesIgnoreTest.class)
          .replace(RClassStaticValuesIgnoreTest.class.getSimpleName(), "R$string");

  @Parameters(name = "{0}, config: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(), Config.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  private byte[] getRClassWithReferenceToUnused() throws IOException {
    return transformer(ToBeRenamedToRDollarString.class)
        .setClassDescriptor(RClassDescriptor)
        .transform();
  }

  @Test
  public void testR8() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addProgramClassFileData(getRClassWithReferenceToUnused())
        .addKeepClassAndMembersRulesWithAllowObfuscation(
            DescriptorUtils.descriptorToJavaType(RClassDescriptor))
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(config != Config.LEGACY, R8TestBuilder::enableOptimizedShrinking)
        .applyIf(
            config == Config.OPTIMIZED_REMOVE_KEPT_RCLASS_RESOURCES,
            b -> {
              b.applyIf(
                  b.isR8PartialTestBuilder(),
                  r8pb ->
                      r8pb.addR8PartialR8OptionsModification(
                          o -> o.removeUnreadKeptRClassResources = true),
                  r8b -> r8b.addOptionsModification(o -> o.removeUnreadKeptRClassResources = true));
            })
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              if (config == Config.OPTIMIZED) {
                resourceTableInspector.assertContainsResourceWithName("string", "unused_string");
              } else {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.bar);
      }
    }
  }

  // Simulate R class usage of unused_string
  public static class ToBeRenamedToRDollarString {
    public static int use_the_unused = RClassStaticValuesIgnoreTest.R.string.unused_string;
  }

  public static class R {
    public static class string {

      public static int bar;
      public static int unused_string;
    }
  }
}
