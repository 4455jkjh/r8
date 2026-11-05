// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceIdentifierTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimized;

  @Parameter(2)
  public boolean enableResourceIdPruning;

  @Parameters(name = "{0}, optimized: {1}, enableResourceIdPruning: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addStringValue("foobar", "the foobar string")
        .addXmlWithStringReference("xmlfile.xml", "foobar")
        .addRClassInitializeWithDefaultValues(R.id.class, R.xml.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(optimized || parameters.getPartialCompilationTestParameters().isNone());
    AndroidTestResource testResources = getTestResources(temp);
    Path resourcesClass = AndroidResourceTestingUtils.resourcesClassAsDex(temp);
    byte[] withAndroidResourcesReference =
        AndroidResourceTestingUtils.transformResourcesReferences(FooBar.class);

    testForR8(parameters)
        .addProgramClassFileData(withAndroidResourcesReference)
        .addRunClasspathFiles(resourcesClass)
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(
            optimized,
            builder -> {
              Consumer<InternalOptions> setPruning =
                  options -> options.enableResourceIdPruning = enableResourceIdPruning;
              builder
                  .enableOptimizedShrinking()
                  .applyIf(
                      builder.isR8PartialTestBuilder(),
                      b -> b.addR8PartialR8OptionsModification(setPruning),
                      b -> b.addOptionsModification(setPruning));
            })
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("id", "id_in_live_xml");
              // Both modes keep resources looked up with constant names: safe mode through the
              // string pool, optimized shrinking by tracing the live getIdentifier() calls.
              resourceTableInspector.assertContainsResourceWithName("string", "foobar");
              resourceTableInspector.assertContainsResourceWithName("id", "reflectively_used_id");
              if (optimized && enableResourceIdPruning) {
                resourceTableInspector.assertDoesNotContainResourceWithName("id", "id_in_dead_xml");
                if (parameters.getPartialCompilationTestParameters().isNone()
                    || parameters.getPartialCompilationTestParameters().isIncludeAll()) {
                  resourceTableInspector.assertDoesNotContainResourceWithName(
                      "id", "dead_after_inlining_id");
                } else if (parameters.getPartialCompilationTestParameters().isExcludeAll()) {
                  resourceTableInspector.assertContainsResourceWithName(
                      "id", "dead_after_inlining_id");
                }
              } else {
                // Legacy mode and optimized mode without ID pruning retain all @id/* entries.
                resourceTableInspector.assertContainsResourceWithName("id", "id_in_dead_xml");
                resourceTableInspector.assertContainsResourceWithName(
                    "id", "dead_after_inlining_id");
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      Resources resources = new Resources();
      System.out.println(resources.getIdentifier("foobar", "string", "com.android.tools.r8"));
      System.out.println(
          resources.getIdentifier("reflectively_used_id", "id", "com.android.tools.r8"));
      if (alwaysFalse()) {
        System.out.println(
            resources.getIdentifier("dead_after_inlining_id", "id", "com.android.tools.r8"));
      }

      System.out.println(R.id.id_in_live_xml);
      System.out.println(R.xml.file_id_in_live_xml);
      System.out.println(R.id.id_in_dead_xml);
    }

    private static boolean alwaysFalse() {
      return false;
    }
  }

  public static class R {

    public static class xml {
      public static int file_id_in_live_xml;
      public static int file_id_in_dead_xml;
    }

    public static class id {
      public static int reflectively_used_id;
      public static int dead_after_inlining_id;
      public static int id_in_live_xml;
      public static int id_in_dead_xml;
    }
  }
}
