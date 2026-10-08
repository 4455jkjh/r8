// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.completeness.OutlineOptimizationProfileRewritingTest.Main;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/569565949. */
@RunWith(Parameterized.class)
public class ComposeOutlineWithSynthesizedOuterFrameTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResultBase<?> r8CompileResult =
        testForR8(parameters)
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addKeepAttributeLineNumberTable()
            .addKeepAttributeSourceFile()
            .addOptionsModification(
                options -> {
                  options.outline.threshold = 2;
                  options.outline.minSize = 2;
                })
            .collectSyntheticItems()
            .enableConstantArgumentAnnotations()
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .compile()
            .inspectWithSyntheticItems(
                (inspector, syntheticItems) -> {
                  ClassSubject outlineClassSubject =
                      inspector.clazz(syntheticItems.syntheticOutlineClass(Caller1.class, 0));
                  assertThat(outlineClassSubject, isPresent());
                });

    Path r8OutputMap =
        FileUtils.writeTextFile(
            temp.newFolder().toPath().resolve("map.txt"), r8CompileResult.getProguardMap());

    // TODO(b/569565949): Running D8 with --pg-map and --pg-map-output on R8 output should succeed.
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () ->
                testForD8(parameters.getBackend())
                    .addProgramFiles(r8CompileResult.writeToZip())
                    .setMinApi(parameters)
                    .release()
                    .internalEnableMappingOutput()
                    .apply(b -> b.getBuilder().setProguardMapInputFile(r8OutputMap))
                    .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
                    .compile());
    assertThat(
        exception.getCause().getMessage(),
        containsString("Could not find ranges for outline position '1'"));
  }

  static class Main {

    public static void main(String[] args) {
      Caller1.call(args.length, "a");
      Caller2.call("b", args.length);
    }
  }

  static class Greeter {

    @NeverInline
    static void step1() {
      System.out.println("step1");
    }

    @NeverInline
    static void step2() {
      System.out.println("step2");
    }
  }

  @NoHorizontalClassMerging
  static class Caller1 {

    @KeepConstantArguments
    @NeverInline
    static void call(int a, String b) {
      System.out.println(a + b);
      Greeter.step1();
      Greeter.step2();
    }
  }

  @NoHorizontalClassMerging
  static class Caller2 {

    @KeepConstantArguments
    @NeverInline
    static void call(String b, int a) {
      System.out.println(a + b);
      Greeter.step1();
      Greeter.step2();
    }
  }
}
