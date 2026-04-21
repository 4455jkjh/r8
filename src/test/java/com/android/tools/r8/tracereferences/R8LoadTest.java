// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8LoadTest extends TestBase {
  @Parameters(name = "{0}, keep System.loadLibrary method = {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepMethodsWithSystemLoadCalls;

  public static Path input1Jar;
  public static Origin input1MethodOrigin;
  public static Path input2Jar;
  public static Origin input2Method1Origin;
  public static Origin input2Method2Origin;
  public static Path loadUnknown1Jar;
  public static Origin loadUnknown1MethodOrigin;
  public static Path loadUnknown2Jar;
  public static Origin loadUnknown2Method1Origin;
  public static Origin loadUnknown2Method2Origin;

  @BeforeClass
  public static void createTestJars() throws Exception {
    Box<Origin> jarOrigin = new Box<>();
    Path dir = getStaticTemp().newFolder().toPath();
    createJarAndOrigin(dir, Class1.class, "input1.jar", path -> input1Jar = path, jarOrigin::set);
    input1MethodOrigin =
        new MethodOrigin(Reference.methodFromMethod(Class1.class.getMethod("m")), jarOrigin.get());
    createJarAndOrigin(dir, Class2.class, "input2.jar", path -> input2Jar = path, jarOrigin::set);
    input2Method1Origin =
        new MethodOrigin(Reference.methodFromMethod(Class2.class.getMethod("m1")), jarOrigin.get());
    input2Method2Origin =
        new MethodOrigin(Reference.methodFromMethod(Class2.class.getMethod("m2")), jarOrigin.get());
    createJarAndOrigin(
        dir,
        ClassLoadUnknownLibrary1.class,
        "loadunknown1.jar",
        path -> loadUnknown1Jar = path,
        jarOrigin::set);
    loadUnknown1MethodOrigin =
        new MethodOrigin(
            Reference.methodFromMethod(ClassLoadUnknownLibrary1.class.getMethod("m", String.class)),
            jarOrigin.get());
    createJarAndOrigin(
        dir,
        ClassLoadUnknownLibrary2.class,
        "loadunknown2.jar",
        path -> loadUnknown2Jar = path,
        jarOrigin::set);
    loadUnknown2Method1Origin =
        new MethodOrigin(
            Reference.methodFromMethod(
                ClassLoadUnknownLibrary2.class.getMethod("m1", String.class)),
            jarOrigin.get());
    loadUnknown2Method2Origin =
        new MethodOrigin(
            Reference.methodFromMethod(
                ClassLoadUnknownLibrary2.class.getMethod("m2", String.class)),
            jarOrigin.get());
  }

  private static void createJarAndOrigin(
      Path dir,
      Class<?> clazz,
      String filename,
      Consumer<Path> pathConsumer,
      Consumer<Origin> originConsumer)
      throws Exception {
    Path jarPath = dir.resolve(filename);
    Path classFile = ToolHelper.getClassFileForTestClass(clazz);
    ZipBuilder.builder(jarPath)
        .addFilesRelative(ToolHelper.getClassPathForTests(), classFile)
        .build();
    Origin origin =
        new ArchiveEntryOrigin(
            ZipUtils.zipEntryFromPath(ToolHelper.getClassPathForTests().relativize(classFile)),
            new PathOrigin(jarPath));
    pathConsumer.accept(jarPath);
    originConsumer.accept(origin);
  }

  @Test
  public void testSingleLoadLibrary() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(input1Jar)
        .addProgramClasses(UseClass1Method.class)
        .addKeepMainRule(UseClass1Method.class)
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .applyIf(keepMethodsWithSystemLoadCalls, b -> b.addKeepClassAndMembersRules(Class1.class))
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer.expectLoad("/path/to/library1", input1MethodOrigin).thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass1Method"))
          .thatsAll();
    }
  }

  @Test
  public void testMultipleLoadLibrary() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(input2Jar)
        .addProgramClasses(UseClass2Methods.class)
        .addKeepMainRule(UseClass2Methods.class)
        .applyIf(keepMethodsWithSystemLoadCalls, b -> b.addKeepClassAndMembersRules(Class2.class))
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", input2Method1Origin)
          .expectLoad("/path/to/library2", input2Method2Origin)
          .thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass2Methods"))
          .expectLoad("/path/to/library2", origin -> origin.toString().contains("UseClass2Methods"))
          .thatsAll();
    }
  }

  @Test
  public void testMultipleLoadDuplicate() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(input1Jar)
        .addProgramFiles(input2Jar)
        .addProgramClasses(UseClass1Method.class)
        .addProgramClasses(UseClass2Methods.class)
        .addKeepMainRule(UseClass1Method.class)
        .addKeepMainRule(UseClass2Methods.class)
        .applyIf(
            keepMethodsWithSystemLoadCalls,
            b ->
                b.addKeepClassAndMembersRules(Class1.class)
                    .addKeepClassAndMembersRules(Class2.class))
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", input1MethodOrigin)
          .expectLoad("/path/to/library1", input2Method1Origin)
          .expectLoad("/path/to/library2", input2Method2Origin)
          .thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass1Method"))
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass2Methods"))
          .expectLoad("/path/to/library2", origin -> origin.toString().contains("UseClass2Methods"))
          .thatsAll();
    }
  }

  @Test
  public void testUnknownLoadCall() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(loadUnknown1Jar)
        .addProgramClasses(UseClassLoadUnknownLibrary1.class)
        .addKeepMainRule(UseClassLoadUnknownLibrary1.class)
        .applyIf(
            keepMethodsWithSystemLoadCalls,
            b -> b.addKeepClassAndMembersRules(ClassLoadUnknownLibrary1.class))
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer.expectLoadAny(loadUnknown1MethodOrigin).thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary1"))
          .thatsAll();
    }
  }

  @Test
  public void testUnknownLoadLibraryCallMultiple() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(loadUnknown2Jar)
        .addProgramClasses(UseClassLoadUnknownLibrary2.class)
        .addKeepMainRule(UseClassLoadUnknownLibrary2.class)
        .applyIf(
            keepMethodsWithSystemLoadCalls,
            b -> b.addKeepClassAndMembersRules(ClassLoadUnknownLibrary2.class))
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer
          .expectLoadAny(loadUnknown2Method1Origin)
          .expectLoadAny(loadUnknown2Method2Origin)
          .thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary2"))
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary2"))
          .thatsAll();
    }
  }

  @Test
  public void testUnknownLoadLibraryAll() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(input1Jar)
        .addProgramFiles(input2Jar)
        .addProgramFiles(loadUnknown1Jar)
        .addProgramFiles(loadUnknown2Jar)
        .addProgramClasses(UseClass1Method.class)
        .addProgramClasses(UseClass2Methods.class)
        .addProgramClasses(UseClassLoadUnknownLibrary1.class)
        .addProgramClasses(UseClassLoadUnknownLibrary2.class)
        .addKeepMainRule(UseClass1Method.class)
        .addKeepMainRule(UseClass2Methods.class)
        .addKeepMainRule(UseClassLoadUnknownLibrary1.class)
        .addKeepMainRule(UseClassLoadUnknownLibrary2.class)
        .applyIf(
            keepMethodsWithSystemLoadCalls,
            b ->
                b.addKeepClassAndMembersRules(Class1.class)
                    .addKeepClassAndMembersRules(Class2.class)
                    .addKeepClassAndMembersRules(ClassLoadUnknownLibrary1.class)
                    .addKeepClassAndMembersRules(ClassLoadUnknownLibrary2.class))
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .compile();

    if (keepMethodsWithSystemLoadCalls) {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", input1MethodOrigin)
          .expectLoad("/path/to/library1", input2Method1Origin)
          .expectLoad("/path/to/library2", input2Method2Origin)
          .expectLoadAny(loadUnknown1MethodOrigin)
          .expectLoadAny(loadUnknown2Method1Origin)
          .expectLoadAny(loadUnknown2Method2Origin)
          .thatsAll();
    } else {
      nativeReferencesConsumer
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass1Method"))
          .expectLoad("/path/to/library1", origin -> origin.toString().contains("UseClass2Methods"))
          .expectLoad("/path/to/library2", origin -> origin.toString().contains("UseClass2Methods"))
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary1"))
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary2"))
          .expectLoadAny(origin -> origin.toString().contains("UseClassLoadUnknownLibrary2"))
          .thatsAll();
    }
  }

  static class Class1 {
    public static void m() {
      System.load("/path/to/library1");
    }
  }

  static class Class2 {
    public static void m1() {
      System.load("/path/to/library1");
    }

    public static void m2() {
      System.load("/path/to/library2");
    }
  }

  static class ClassLoadUnknownLibrary1 {
    public static void m(String s) {
      System.load(s);
    }
  }

  static class ClassLoadUnknownLibrary2 {
    public static void m1(String s) {
      System.load(s);
    }

    public static void m2(String s) {
      System.load(s);
    }
  }

  static class UseClass1Method {

    public static void main(String[] args) {
      Class1.m();
    }
  }

  static class UseClass2Methods {

    public static void main(String[] args) {
      Class2.m1();
      Class2.m2();
    }
  }

  static class UseClassLoadUnknownLibrary1 {

    public static void main(String[] args) {
      ClassLoadUnknownLibrary1.m(args[0]);
    }
  }

  static class UseClassLoadUnknownLibrary2 {

    public static void main(String[] args) {
      ClassLoadUnknownLibrary2.m1(args[0]);
      ClassLoadUnknownLibrary2.m2(args[0]);
    }
  }
}
