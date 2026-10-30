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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
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
        .assertSuccessWithOutputLines("false", "false", "false", "false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Other.class, Leaf.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + Leaf.class.getTypeName() + " { public boolean same*(); }")
        .addDontObfuscate()
        .allowAccessModification(allowAccessModification)
        .setMode(mode)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject baseClass =
                  inspector.clazz(Reference.classFromDescriptor(NEW_BASE_DESCRIPTOR));
              MethodSubject getMeasuredSize =
                  baseClass.uniqueMethodWithOriginalName("getMeasuredSize");
              ClassSubject midClass = inspector.clazz(Mid.class);
              FieldSubject fieldSize =
                  (mode.isRelease() && (parameters.isDexRuntime() || allowAccessModification)
                          ? midClass
                          : baseClass)
                      .uniqueFieldWithOriginalName("fieldSize");
              assertThat(fieldSize, isPresent());
              assertTrue(
                  (mode.isRelease() && parameters.isDexRuntime()) || allowAccessModification
                      ? fieldSize.isPublic()
                      : fieldSize.isProtected());
              MethodSubject getSize =
                  (mode.isRelease() ? baseClass : midClass).uniqueMethodWithOriginalName("getSize");
              MethodSubject getOtherSize = midClass.uniqueMethodWithOriginalName("getOtherSize");
              MethodSubject getFieldSize = midClass.uniqueMethodWithOriginalName("getFieldSize");
              MethodSubject getOtherFieldSize =
                  midClass.uniqueMethodWithOriginalName("getOtherFieldSize");
              MethodSubject setFieldSize = midClass.uniqueMethodWithOriginalName("setFieldSize");
              MethodSubject setOtherFieldSize =
                  midClass.uniqueMethodWithOriginalName("setOtherFieldSize");
              if (mode.isRelease() && (parameters.isDexRuntime() || allowAccessModification)) {
                assertThat(getMeasuredSize, isAbsent());
                assertThat(getSize, isAbsent());
                assertThat(getOtherSize, isAbsent());
                assertThat(getFieldSize, isAbsent());
                assertThat(getOtherFieldSize, isAbsent());
                assertThat(setFieldSize, isAbsent());
                assertThat(setOtherFieldSize, isAbsent());
              } else {
                assertThat(getMeasuredSize, isPresent());
                assertTrue(
                    allowAccessModification
                        ? getMeasuredSize.isPublic()
                        : getMeasuredSize.isProtected());
                assertThat(getSize, isPresent());
                assertThat(getOtherSize, isPresent());
                assertThat(getFieldSize, isPresent());
                assertThat(getOtherFieldSize, isPresent());
                assertThat(setFieldSize, isPresent());
                assertThat(setOtherFieldSize, isPresent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "false", "false", "false");
  }

  @Test
  public void testR8ProtectApiSurface() throws Exception {
    assumeTrue(!allowAccessModification && mode.isRelease());
    testForR8(parameters)
        .addProgramClasses(Other.class, Leaf.class, Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + Leaf.class.getTypeName() + " { public boolean same*(); }")
        .addDontObfuscate()
        .apply(b -> b.getBuilder().setProtectApiSurface(true))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject baseClass =
                  inspector.clazz(Reference.classFromDescriptor(NEW_BASE_DESCRIPTOR));
              MethodSubject getMeasuredSize =
                  baseClass.uniqueMethodWithOriginalName("getMeasuredSize");
              assertThat(getMeasuredSize, isPresent());
              assertTrue(getMeasuredSize.isProtected());
              FieldSubject fieldSize = baseClass.uniqueFieldWithOriginalName("fieldSize");
              assertThat(fieldSize, isPresent());
              assertTrue(fieldSize.isProtected());
              ClassSubject midClass = inspector.clazz(Mid.class);
              MethodSubject getSize = baseClass.uniqueMethodWithOriginalName("getSize");
              MethodSubject getOtherSize = midClass.uniqueMethodWithOriginalName("getOtherSize");
              MethodSubject getFieldSize = midClass.uniqueMethodWithOriginalName("getFieldSize");
              MethodSubject getOtherFieldSize =
                  midClass.uniqueMethodWithOriginalName("getOtherFieldSize");
              MethodSubject setFieldSize = midClass.uniqueMethodWithOriginalName("setFieldSize");
              MethodSubject setOtherFieldSize =
                  midClass.uniqueMethodWithOriginalName("setOtherFieldSize");
              if (parameters.isDexRuntime()) {
                assertThat(getSize, isAbsent());
                assertThat(getOtherSize, isAbsent());
                assertThat(getFieldSize, isAbsent());
                assertThat(getOtherFieldSize, isAbsent());
                assertThat(setFieldSize, isAbsent());
                assertThat(setOtherFieldSize, isAbsent());
              } else {
                assertThat(getSize, isPresent());
                assertThat(getOtherSize, isPresent());
                assertThat(getFieldSize, isPresent());
                assertThat(getOtherFieldSize, isPresent());
                assertThat(setFieldSize, isPresent());
                assertThat(setOtherFieldSize, isPresent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "false", "false", "false");
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
    protected long fieldSize;

    protected Base(long size) {
      this.size = size;
      this.fieldSize = size;
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

    public final long getOtherSize(Mid other) {
      return other.getMeasuredSize();
    }

    public final long getFieldSize() {
      return fieldSize;
    }

    public final long getOtherFieldSize(Mid other) {
      return other.fieldSize;
    }

    public final void setFieldSize(long fieldSize) {
      this.fieldSize = fieldSize;
    }

    public final void setOtherFieldSize(Mid other, long fieldSize) {
      other.fieldSize = fieldSize;
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

    public boolean sameSizeIndirect() {
      return getSize() == getOtherSize(wrapped);
    }

    public boolean sameFieldSize() {
      setFieldSize(getFieldSize() + 1);
      wrapped.setFieldSize(wrapped.getFieldSize() + 1);
      return getFieldSize() == wrapped.getFieldSize();
    }

    public boolean sameFieldSizeIndirect() {
      setFieldSize(getFieldSize() + 1);
      setOtherFieldSize(wrapped, getOtherFieldSize(wrapped) + 1);
      return getFieldSize() == getOtherFieldSize(wrapped);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      Leaf leaf = new Leaf(args.length, new Other(args.length + 1));
      System.out.println(leaf.sameSize());
      System.out.println(leaf.sameSizeIndirect());
      System.out.println(leaf.sameFieldSize());
      System.out.println(leaf.sameFieldSizeIndirect());
    }
  }
}
