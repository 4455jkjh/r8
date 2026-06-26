// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources.partial;

import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceShrinkingInPartialR8Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean removeUnreadKeptRClassResources;

  @Parameters(name = "{0}, removeUnreadKeptRClassResources: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
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
  public void testPartialWithRClassInR8() throws Exception {
    getR8PartialTestBuilder(false, InR8.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "string", "referencedFromD8Code");
              resourceTableInspector.assertContainsResourceWithName(
                  "string", "referencedFromR8Code");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
            })
        .run(parameters.getRuntime(), InR8.class)
        .assertSuccess();
  }

  @Test
  public void testPartialWithRClassInD8() throws Exception {
    getR8PartialTestBuilder(true, InR8.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "string", "referencedFromD8Code");
              resourceTableInspector.assertContainsResourceWithName(
                  "string", "referencedFromR8Code");
              if (removeUnreadKeptRClassResources) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
              } else {
                resourceTableInspector.assertContainsResourceWithName("string", "unused_string");
              }
            })
        .run(parameters.getRuntime(), InR8.class)
        .assertSuccess();
  }

  @Test
  public void testPartialWithRClassInD8UnusedInR8() throws Exception {
    getR8PartialTestBuilder(true, InR8UnusedInR8.class)
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName(
                  "string", "referencedFromD8Code");
              if (removeUnreadKeptRClassResources) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "referencedFromR8Code");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
              } else {
                resourceTableInspector.assertContainsResourceWithName(
                    "string", "referencedFromR8Code");
                resourceTableInspector.assertContainsResourceWithName("string", "unused_string");
              }
            })
        .run(parameters.getRuntime(), InR8UnusedInR8.class)
        .assertSuccess();
  }

  private R8PartialTestBuilder getR8PartialTestBuilder(boolean rClassInD8, Class<?> mainClass)
      throws Exception {
    return testForR8Partial(parameters.getBackend())
        .setMinApi(parameters)
        .addR8IncludedClasses(mainClass)
        .addR8ExcludedClasses(InD8.class)
        .addAndroidResources(getTestResources(temp))
        .enableOptimizedShrinking()
        .addR8PartialR8OptionsModification(
            options -> options.removeUnreadKeptRClassResources = removeUnreadKeptRClassResources)
        .applyIf(
            rClassInD8,
            // These classes are already added as program classes by the resource setup.
            b -> b.addR8ExcludedClasses(false, R.string.class),
            b -> b.addR8IncludedClasses(false, R.string.class))
        .addKeepMainRule(mainClass);
  }

  public static class InR8 {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.referencedFromR8Code);
        InD8.callMe();
      }
    }
  }

  public static class InR8UnusedInR8 {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        InD8.callMe();
      }
    }
  }

  public static class InD8 {
    public static void callMe() {
      System.out.println(R.string.referencedFromD8Code);
    }
  }

  public static class R {

    public static class string {
      public static int referencedFromD8Code;
      public static int referencedFromR8Code;
      public static int unused_string;
    }
  }
}
