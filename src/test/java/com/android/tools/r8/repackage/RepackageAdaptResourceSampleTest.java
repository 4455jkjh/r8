// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageAdaptResourceSampleTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    DataResourceConsumerForTesting dataResourceConsumer = new DataResourceConsumerForTesting();
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addDataResources(
            DataEntryResource.fromString("foo/bar/baz/KeepClass.txt", Origin.unknown(), "content"))
        .addKeepMainRule("Main")
        .addKeepRules(
            "-keep class foo.bar.baz.KeepClass",
            "-adaptresourcefilenames foo/bar/baz/KeepClass.txt",
            "-repackageclasses ''")
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addOptionsModification(options -> options.dataResourceConsumer = dataResourceConsumer)
        .compile()
        .inspect(
            inspector -> {
              // KeepClass should remain in foo.bar.baz
              ClassSubject keepClass = inspector.clazz("foo.bar.baz.KeepClass");
              assertThat(keepClass, isPresent());
              assertEquals(
                  "foo.bar.baz", keepClass.getDexProgramClass().getType().getPackageName());

              // OtherClass should be repackaged to default package ""
              ClassSubject otherClass = inspector.clazz("foo.bar.OtherClass");
              assertThat(otherClass, isPresent());
              assertEquals("", otherClass.getDexProgramClass().getType().getPackageName());
            })
        .run(parameters.getRuntime(), "Main")
        .assertSuccessWithOutputLines("Hello", "world");

    // The resource should NOT be repackaged because KeepClass is kept.
    // It should remain "foo/bar/baz/KeepClass.txt".
    // If it is mangled, it might become "/baz/KeepClass.txt" or similar.
    assertThat(
        dataResourceConsumer.getAll().keySet(),
        UnorderedCollectionMatcher.matchesItemsOneToOne(
            ImmutableList.of("foo/bar/baz/KeepClass.txt")));
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class)
            .removeInnerClasses()
            .setClassDescriptor("LMain;")
            .replaceClassDescriptorInMethodInstructions(
                descriptor(KeepClass.class), "Lfoo/bar/baz/KeepClass;")
            .replaceClassDescriptorInMethodInstructions(
                descriptor(OtherClass.class), "Lfoo/bar/OtherClass;")
            .transform(),
        transformer(KeepClass.class)
            .removeInnerClasses()
            .setClassDescriptor("Lfoo/bar/baz/KeepClass;")
            .transform(),
        transformer(OtherClass.class)
            .removeInnerClasses()
            .setClassDescriptor("Lfoo/bar/OtherClass;")
            .transform());
  }

  static class Main {
    public static void main(String[] args) {
      KeepClass.hello();
      OtherClass.world();
    }
  }

  @NoHorizontalClassMerging
  public static class /*foo.bar.baz.*/ KeepClass {
    @NeverInline
    public static void hello() {
      System.out.println("Hello");
    }
  }

  @NoHorizontalClassMerging
  public static class /*foo.bar.*/ OtherClass {
    @NeverInline
    public static void world() {
      System.out.println("world");
    }
  }
}
