// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/569565949 and b/579315508. */
@RunWith(Parameterized.class)
public class ComposeOutlineWithSynthesizedOuterFrameTest extends TestBase {

  @Parameter(0)
  public boolean enableHorizontalClassMerging;

  @Parameter(1)
  public boolean keepConstantArguments;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, enableHorizontalClassMerging: {0}, keepConstantArguments: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build());
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
            .addConstantArgumentAnnotations()
            .enableConstantArgumentAnnotations(keepConstantArguments)
            .enableInliningAnnotations()
            .addNoHorizontalClassMergingAnnotations()
            .applyIf(
                enableHorizontalClassMerging,
                b ->
                    b.addHorizontallyMergedClassesInspector(
                        inspector ->
                            inspector.assertIsCompleteMergeGroup(Caller1.class, Caller2.class)),
                b ->
                    b.enableNoHorizontalClassMergingAnnotations()
                        .addHorizontallyMergedClassesInspector(
                            inspector ->
                                inspector.assertClassesNotMerged(Caller1.class, Caller2.class)))
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
    testForD8(parameters.getBackend())
        .addProgramFiles(r8CompileResult.writeToZip())
        .setMinApi(parameters)
        .release()
        .internalEnableMappingOutput()
        .apply(b -> b.getBuilder().setProguardMapInputFile(r8OutputMap))
        .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "step1",
            "step2",
            "init1:1",
            "step1",
            "step2",
            "init1:str:0",
            "step1",
            "step2",
            "init2:1",
            "step1",
            "step2",
            "0a",
            "step1",
            "step2",
            "0b",
            "step1",
            "step2");
  }

  static class Main {

    public static void main(String[] args) {
      consume(new Caller1(args.length));
      consume(new Caller1(args.length + 1));
      consume(new Caller1(String.valueOf(args.length)));
      consume(new Caller2(args.length));
      consume(new Caller2(args.length + 1));
      Caller1.call(args.length, "a");
      Caller2.call("b", args.length);
    }

    @NeverInline
    static void consume(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
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

    Caller1(int a) {
      if (a > 0) {
        System.out.println("init1:" + a);
      }
      Greeter.step1();
      Greeter.step2();
    }

    @NeverInline
    Caller1(String s) {
      System.out.println("init1:str:" + s);
    }

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

    Caller2(int a) {
      if (a > 0) {
        System.out.println("init2:" + a);
      }
      Greeter.step1();
      Greeter.step2();
    }

    @KeepConstantArguments
    @NeverInline
    static void call(String b, int a) {
      System.out.println(a + b);
      Greeter.step1();
      Greeter.step2();
    }
  }
}
