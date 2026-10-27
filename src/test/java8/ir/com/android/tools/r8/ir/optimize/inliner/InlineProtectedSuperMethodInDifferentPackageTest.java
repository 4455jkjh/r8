// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/577393730.
@RunWith(Parameterized.class)
public class InlineProtectedSuperMethodInDifferentPackageTest extends TestBase {

  private static final String NEW_BASE_DESCRIPTOR = "Lp1/Base;";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean allowAccessModification;

  @Parameter(2)
  public CompilationMode mode;

  @Parameters(name = "{0}, allowAccessModification: {1}, mode: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        CompilationMode.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(!allowAccessModification && mode.isRelease());
    testForJvm(parameters)
        .addProgramClasses(Other.class, Leaf.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Other.class, Leaf.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + Leaf.class.getTypeName() + " { public boolean sameSize(); }")
        .addDontObfuscate()
        .allowAccessModification(allowAccessModification)
        .setMode(mode)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject getMeasuredSize =
                  inspector
                      .clazz(Reference.classFromDescriptor(NEW_BASE_DESCRIPTOR))
                      .uniqueMethodWithOriginalName("getMeasuredSize");
              if (mode.isRelease() && (parameters.isDexRuntime() || allowAccessModification)) {
                assertThat(getMeasuredSize, isAbsent());
              } else {
                assertThat(getMeasuredSize, isPresent());
                assertTrue(
                    allowAccessModification
                        ? getMeasuredSize.isPublic()
                        : getMeasuredSize.isProtected());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/577393730): Should succeed on CF in release mode without allowAccessModification.
        .applyIf(
            parameters.isCfRuntime() && !allowAccessModification && mode.isRelease(),
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            r -> r.assertSuccessWithOutputLines("false"));
  }

  @Test
  public void testR8ProtectApiSurface() throws Exception {
    assumeTrue(!allowAccessModification && mode.isRelease());
    testForR8(parameters)
        .addProgramClasses(Other.class, Leaf.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + Leaf.class.getTypeName() + " { public boolean sameSize(); }")
        .addDontObfuscate()
        .apply(b -> b.getBuilder().setProtectApiSurface(true))
        .compile()
        .inspect(
            inspector -> {
              MethodSubject getMeasuredSize =
                  inspector
                      .clazz(Reference.classFromDescriptor(NEW_BASE_DESCRIPTOR))
                      .uniqueMethodWithOriginalName("getMeasuredSize");
              assertThat(getMeasuredSize, isPresent());
              assertTrue(getMeasuredSize.isProtected());
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/577393730): Should succeed on CF.
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            r -> r.assertSuccessWithOutputLines("false"));
  }

  private static List<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Base.class).setClassDescriptor(NEW_BASE_DESCRIPTOR).transform(),
        transformer(Mid.class)
            .setSuper(NEW_BASE_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(descriptor(Base.class), NEW_BASE_DESCRIPTOR)
            .transform());
  }

  public abstract static class /*p1.*/ Base {
    private long size;

    protected Base(long size) {
      this.size = size;
    }

    protected final long getMeasuredSize() {
      return size;
    }
  }

  public abstract static class Mid extends Base {
    protected Mid(long size) {
      super(size);
    }

    public final long getSize() {
      return getMeasuredSize();
    }
  }

  public static class Other extends Mid {
    public Other(long size) {
      super(size);
    }
  }

  public static class Leaf extends Mid {
    private final Mid wrapped;

    public Leaf(long size, Mid wrapped) {
      super(size);
      this.wrapped = wrapped;
    }

    public boolean sameSize() {
      return getSize() == wrapped.getSize();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      Leaf leaf = new Leaf(args.length, new Other(args.length + 1));
      System.out.println(leaf.sameSize());
    }
  }
}
