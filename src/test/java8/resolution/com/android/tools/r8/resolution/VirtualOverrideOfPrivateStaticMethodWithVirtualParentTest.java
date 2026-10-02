// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestMultiRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest extends TestBase {

  public interface I {
    default void f() {}
  }

  public static class Base {
    private // Made public using ASM.
    void f() {}
  }

  public static class A extends Base {
    private static void f() {}
  }

  public static class B extends A implements I {}

  public static class C extends B {
    @Override
    public void f() {
      System.out.println("Called C.f");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new C();
      b.f();
    }
  }

  public static class BaseDump implements Opcodes {

    public static byte[] dump() throws Exception {
      return transformer(Base.class).setPublic(Base.class.getDeclaredMethod("f")).transform();
    }
  }

  public static List<Class<?>> CLASSES =
      ImmutableList.of(A.class, B.class, C.class, I.class, Main.class);

  public static List<byte[]> getDumps() throws Exception {
    return ImmutableList.of(BaseDump.dump());
  }

  private static AppInfoWithLiveness appInfo;

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appInfo =
        computeAppViewWithLiveness(
                buildClasses(CLASSES)
                    .addClassProgramData(getDumps())
                    .addLibraryFile(getMostRecentAndroidJar())
                    .build(),
                Timing.empty(),
                Main.class)
            .appInfo();
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appInfo.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnA = buildMethod(A.class, "f");
  private final DexMethod methodOnB = buildMethod(B.class, "f");
  private final DexMethod methodOnC = buildMethod(C.class, "f");

  public VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() {
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnClassLegacy(methodOnB.holder, methodOnB);
    assertTrue(resolutionResult.isFailedResolution());
  }

  @Test
  public void runJvmAndD8() throws Exception {
    TestRunResult<?> runResult;
    if (parameters.isCfRuntime()) {
      runResult =
          testForJvm(parameters)
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(getDumps())
              .run(parameters.getRuntime(), Main.class);
    } else {
      runResult =
          testForD8()
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(getDumps())
              .setMinApi(parameters)
              .run(parameters.getRuntime(), Main.class);
    }
    checkResult(runResult, false);
  }

  @Test
  public void runR8() throws Exception {
    R8TestMultiRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSES)
            .addProgramClassFileData(getDumps())
            .addKeepMainRule(Main.class)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class);
    checkResult(runResult, true);
  }

  private void checkResult(TestRunResult<?> runResult, boolean isCorrectedByR8) {
    runResult.applyIfDexRuntime(
        // Do to incorrect resolution, some Art VMs will resolve to Base.f (ignoring A.f) and
        // thus virtual dispatch to C.f. See b/140013075.
        version ->
            !isCorrectedByR8
                && version.isNewerThan(Version.V4_4_4)
                && version.isOlderThanOrEqual(Version.V7_0_0),
        r -> r.assertSuccessWithOutputLines("Called C.f"),
        version -> !isCorrectedByR8 && version.isOlderThanOrEqual(Version.V4_4_4),
        r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
        r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }
}
