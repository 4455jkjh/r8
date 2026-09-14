// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeInterfaceToSiblingMethodLibraryOverrideTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromIncluding(DexVm.Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, I.class, A.class, A2.class, B.class, Plugin.class)
        .run(parameters.getRuntime(), Main.class, Plugin.class.getName())
        .assertSuccessWithOutputLines("denied");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class, A.class, A2.class, B.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + B.class.getTypeName() + " { public <init>(); }")
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject checkMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("check");
              assertThat(checkMethodSubject, isPresent());
              // The if-guard in check() should have been preserved, but due to unsound joining of
              // A.getAsBoolean's opt-info, it is folded and deleted.
              assertFalse(
                  checkMethodSubject.streamInstructions().anyMatch(InstructionSubject::isIf));
            })
        .addRunClasspathClasses(Plugin.class)
        .run(parameters.getRuntime(), Main.class, Plugin.class.getName())
        // Guard was deleted, so "allowed" is printed instead of "denied".
        // TODO(b/557270156): Should succeed with expected output.
        .assertSuccessWithOutputLines("allowed");
  }

  @NoVerticalClassMerging
  public interface I {
    boolean getAsBoolean();
  }

  @NoVerticalClassMerging
  public static class A implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
      return true;
    }
  }

  public static class A2 extends A {
    @Override
    public boolean getAsBoolean() {
      return true;
    }
  }

  public static class B extends A implements I {}

  public static class Plugin extends B {
    @Override
    public boolean getAsBoolean() {
      return false;
    }
  }

  static class Main {
    public static void main(String[] args) throws Exception {
      if (args.length == 42) {
        new A2();
      }
      I plugin = (I) Class.forName(args[0]).getDeclaredConstructor().newInstance();
      check(plugin);
    }

    @NeverInline
    static void check(I i) {
      if (!i.getAsBoolean()) {
        System.out.println("denied");
        return;
      }
      System.out.println("allowed");
    }
  }
}
