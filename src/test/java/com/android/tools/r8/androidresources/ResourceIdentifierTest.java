// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
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

  @Parameters(name = "{0}, optimized: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
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
    AndroidTestResource testResources = getTestResources(temp);
    Path resourcesClass = AndroidResourceTestingUtils.resourcesClassAsDex(temp);
    byte[] withAndroidResourcesReference =
        AndroidResourceTestingUtils.transformResourcesReferences(FooBar.class);

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(withAndroidResourcesReference)
        .addRunClasspathFiles(resourcesClass)
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(optimized, R8TestBuilder::enableOptimizedShrinking)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("id", "id_in_live_xml");
              if (optimized) {
                resourceTableInspector.assertDoesNotContainResourceWithName("string", "foobar");
                resourceTableInspector.assertDoesNotContainResourceWithName("id", "id_in_dead_xml");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "id", "reflectively_used_id");
              } else {
                resourceTableInspector.assertContainsResourceWithName("string", "foobar");
                resourceTableInspector.assertContainsResourceWithName("id", "id_in_dead_xml");
                resourceTableInspector.assertContainsResourceWithName("id", "reflectively_used_id");
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

      System.out.println(R.id.id_in_live_xml);
      System.out.println(R.xml.file_id_in_live_xml);
      System.out.println(R.id.id_in_dead_xml);
    }
  }

  public static class R {

    public static class xml {
      public static int file_id_in_live_xml;
      public static int file_id_in_dead_xml;
    }

    public static class id {
      public static int reflectively_used_id;
      public static int id_in_live_xml;
      public static int id_in_dead_xml;
    }
  }
}
