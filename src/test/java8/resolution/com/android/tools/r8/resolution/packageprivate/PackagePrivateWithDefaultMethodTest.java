// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.packageprivate.a.Abstract;
import com.android.tools.r8.resolution.packageprivate.a.I;
import com.android.tools.r8.resolution.packageprivate.a.J;
import com.android.tools.r8.resolution.packageprivate.a.NonAbstract;
import com.android.tools.r8.resolution.packageprivate.a.NonAbstractExtendingA;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class PackagePrivateWithDefaultMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateWithDefaultMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(
                    Abstract.class,
                    I.class,
                    A.class,
                    NonAbstractExtendingA.class,
                    J.class,
                    Main.class)
                .addClassProgramData(getNonAbstractWithoutDeclaredMethods())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Timing.empty(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(A.class, appInfo.dexItemFactory()));
    assertTrue(resolutionResult.isAccessibleFrom(context, appView).isFalse());
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appView);
    assertTrue(lookupResult.isLookupResultFailure());
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(
            Abstract.class, I.class, A.class, NonAbstractExtendingA.class, J.class, Main.class)
        .addProgramClassFileData(getNonAbstractWithoutDeclaredMethods())
        .run(parameters.getRuntime(), Main.class)
        .applyIfDexRuntime(
            version -> version.isOlderThanOrEqual(Version.V4_4_4),
            TestRunResult::assertFailure,
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            Abstract.class, I.class, A.class, NonAbstractExtendingA.class, J.class, Main.class)
        .addProgramClassFileData(getNonAbstractWithoutDeclaredMethods())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .applyIfDexRuntime(
            version -> version.isOlderThanOrEqual(Version.V4_4_4),
            TestRunResult::assertFailure,
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  private byte[] getNonAbstractWithoutDeclaredMethods() throws IOException {
    return transformer(NonAbstract.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (!name.equals("foo")) {
                  return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return null;
              }
            })
        .transform();
  }

  public static class A extends NonAbstract {}

  public static class Main {

    public static void main(String[] args) {
      NonAbstractExtendingA d = new NonAbstractExtendingA();
      Abstract.run(d);
      d.foo();
    }
  }
}
