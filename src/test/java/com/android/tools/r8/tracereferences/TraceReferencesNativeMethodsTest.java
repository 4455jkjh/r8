// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesNativeMethodsTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public static Path inputJar;
  public static Origin class1MethodOrigin;
  public static Origin input2Method1Origin;
  public static Origin input2Method2Origin;

  @BeforeClass
  public static void createTestJars() throws Exception {
    Box<List<Origin>> origins = new Box<>();
    Path dir = getStaticTemp().newFolder().toPath();
    createJarAndOrigin(
        dir, ImmutableList.of(Class1.class, Class2.class), path -> inputJar = path, origins::set);
    class1MethodOrigin =
        new MethodOrigin(
            Reference.methodFromMethod(Class1.class.getMethod("m")), origins.get().get(0));
    input2Method1Origin =
        new MethodOrigin(
            Reference.methodFromMethod(Class2.class.getMethod("m1")), origins.get().get(1));
    input2Method2Origin =
        new MethodOrigin(
            Reference.methodFromMethod(Class2.class.getMethod("m2")), origins.get().get(1));
  }

  private static void createJarAndOrigin(
      Path dir,
      List<Class<?>> classes,
      Consumer<Path> pathConsumer,
      Consumer<List<Origin>> originConsumer)
      throws Exception {
    Path jarPath = dir.resolve("input.jar");
    List<Origin> origins = new ArrayList<>(classes.size());
    ZipBuilder builder = ZipBuilder.builder(jarPath);
    for (Class<?> clazz : classes) {
      Path classFile = ToolHelper.getClassFileForTestClass(clazz);
      builder.addFilesRelative(ToolHelper.getClassPathForTests(), classFile);
      Origin origin =
          new ArchiveEntryOrigin(
              ToolHelper.getClassPathForTests().relativize(classFile).toString(),
              new PathOrigin(jarPath));
      origins.add(origin);
    }
    builder.build();
    pathConsumer.accept(jarPath);
    originConsumer.accept(origins);
  }

  public TraceReferencesNativeMethodsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Throwable {
    NativeReferencesTestingConsumer nativeReferencesConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(inputJar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(nativeReferencesConsumer)
        .trace();

    nativeReferencesConsumer
        .expectNativeMethod(Reference.methodFromMethod(Class1.class.getMethod("m")))
        .expectNativeMethod(Reference.methodFromMethod(Class2.class.getMethod("m1")))
        .expectNativeMethod(Reference.methodFromMethod(Class2.class.getMethod("m2")))
        .thatsAll();
  }

  static class Class1 {
    public static native void m();
  }

  static class Class2 {
    public static native void m1();

    public native void m2();
  }
}
