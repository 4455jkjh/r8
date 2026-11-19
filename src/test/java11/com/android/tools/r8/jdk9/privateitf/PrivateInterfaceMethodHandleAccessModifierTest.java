// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.H_INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;

// Regression test for b/587727449.
@RunWith(Parameterized.class)
public class PrivateInterfaceMethodHandleAccessModifierTest extends TestBase {

  // ART compiled code (not R8, which never inlines const-method-handle) throws an
  // IllegalAccessError when a caller of a private INVOKE_DIRECT const-method-handle is inlined
  // into another class (cf. b/235807678). Use the interpreter when the target stays private.
  private static final String INTERPRETER_ONLY = "-Xint";

  private static final Class<?>[] PUBLICIZED_CLASSES = {Impl.class, SubImpl.class};
  private static final Class<?>[] OVERRIDE_CLASSES = {OverrideImpl.class};
  private static final Class<?>[] SUPERCLASS_CLASSES = {Base.class, Sub.class};
  private static final Class<?>[] SUPERCLASS_REVERSE_ORDER_CLASSES = {ZBase.class, SubZ.class};

  private static final String[] EXPECTED_PUBLICIZED = {"12", "12", "12"};
  private static final String[] EXPECTED_WITH_CLASS_TARGET = {"12", "999"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(apiLevelWithConstMethodHandleSupport())
        .build();
  }

  @Test
  public void testRuntime() throws Exception {
    testRuntime(PublicizedMain.class, PUBLICIZED_CLASSES, EXPECTED_PUBLICIZED);
    testRuntime(OverrideMain.class, OVERRIDE_CLASSES, EXPECTED_WITH_CLASS_TARGET);
    testRuntime(SuperclassMain.class, SUPERCLASS_CLASSES, EXPECTED_WITH_CLASS_TARGET);
    testRuntime(
        SuperclassReverseOrderMain.class,
        SUPERCLASS_REVERSE_ORDER_CLASSES,
        EXPECTED_WITH_CLASS_TARGET);
  }

  private void testRuntime(Class<?> main, Class<?>[] classes, String... expected) throws Exception {
    List<byte[]> program = getProgramClassFileData(main, classes);
    TestRunResult<?> result =
        parameters.isCfRuntime()
            ? testForJvm(parameters)
                .addProgramClassFileData(program)
                .run(parameters.getRuntime(), main)
            : testForD8(parameters)
                .addProgramClassFileData(program)
                .compile()
                .addVmArguments(INTERPRETER_ONLY)
                .run(parameters.getRuntime(), main);
    result.assertSuccessWithOutputLines(expected);
  }

  @Test
  public void testR8Publicized() throws Exception {
    testR8(
        PublicizedMain.class,
        PUBLICIZED_CLASSES,
        false,
        true,
        inspector -> {},
        EXPECTED_PUBLICIZED);
  }

  @Test
  public void testR8PublicizedDexVersion35UpToApi30() throws Exception {
    assumeTrue(
        parameters.isDexRuntime()
            && parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R));
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClassFileData(
                getProgramClassFileData(PublicizedMain.class, PUBLICIZED_CLASSES))
            .addKeepMainRule(PublicizedMain.class)
            .enableInliningAnnotations()
            .addOptionsModification(
                options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
            .compile()
            .inspect(inspector -> assertThat(getTarget(inspector), isPublic()));
    DexVersion35UpToApi30Test.assertDexVersion(DexVersion.V35, compileResult);
    compileResult
        .run(parameters.getRuntime(), PublicizedMain.class)
        .assertSuccessWithOutputLines(EXPECTED_PUBLICIZED);
  }

  @Test
  public void testR8Override() throws Exception {
    testR8(
        OverrideMain.class,
        OVERRIDE_CLASSES,
        false,
        false,
        inspector ->
            assertThat(
                inspector.clazz(OverrideImpl.class).uniqueMethodWithOriginalName("target"),
                isPresent()),
        EXPECTED_WITH_CLASS_TARGET);
  }

  @Test
  public void testR8Superclass() throws Exception {
    // Base is pruned after I, so Base.target is renamed when I.target is publicized.
    testR8(
        SuperclassMain.class,
        SUPERCLASS_CLASSES,
        true,
        true,
        inspector -> {
          MethodSubject baseTarget =
              inspector.clazz(Base.class).uniqueMethodWithOriginalName("target");
          assertThat(baseTarget, isPresent());
          assertNotEquals(getTarget(inspector).getFinalName(), baseTarget.getFinalName());
        },
        EXPECTED_WITH_CLASS_TARGET);
  }

  @Test
  public void testR8SuperclassReverseOrder() throws Exception {
    // ZBase is pruned before I, so ZBase.target blocks publicizing I.target.
    testR8(
        SuperclassReverseOrderMain.class,
        SUPERCLASS_REVERSE_ORDER_CLASSES,
        true,
        false,
        inspector ->
            assertThat(
                inspector.clazz(ZBase.class).uniqueMethodWithOriginalName("target"), isPresent()),
        EXPECTED_WITH_CLASS_TARGET);
  }

  private void testR8(
      Class<?> main,
      Class<?>[] classes,
      boolean hasBaseClass,
      boolean isTargetPublicized,
      Consumer<CodeInspector> inspection,
      String... expected)
      throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData(main, classes))
        .addKeepMainRule(main)
        .enableInliningAnnotations()
        .applyIf(hasBaseClass, b -> b.enableNoVerticalClassMergingAnnotations())
        .compile()
        .inspect(
            inspector -> {
              assertThat(getTarget(inspector), isTargetPublicized ? isPublic() : isPrivate());
              inspection.accept(inspector);
            })
        .applyIf(
            parameters.isDexRuntime() && !isTargetPublicized,
            result -> result.addVmArguments(INTERPRETER_ONLY))
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutputLines(expected);
  }

  private static MethodSubject getTarget(CodeInspector inspector) {
    return inspector.clazz(I.class).uniqueMethodWithOriginalName("target");
  }

  private List<byte[]> getProgramClassFileData(Class<?> main, Class<?>... classes)
      throws Exception {
    String bootstrapDescriptor =
        MethodType.methodType(
                CallSite.class,
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                MethodHandle.class)
            .toMethodDescriptorString();
    Handle targetHandle = new Handle(H_INVOKESPECIAL, binaryName(I.class), "target", "(I)I", true);
    List<byte[]> result = new ArrayList<>();
    result.add(
        transformer(I.class)
            .clearNest()
            .removeInnerClasses()
            .transformLdcInsnInMethod(
                "specialHandle", (value, visitor) -> visitor.visitLdcInsn(targetHandle))
            .transformMethodInsnInMethod(
                "callTargetViaCallSite",
                (opcode, owner, name, descriptor, isInterface, visitor) ->
                    visitor.visitInvokeDynamicInsn(
                        name,
                        descriptor,
                        new Handle(
                            H_INVOKESTATIC,
                            binaryName(I.class),
                            "bootstrap",
                            bootstrapDescriptor,
                            true),
                        targetHandle))
            .transform());
    result.add(transformer(main).clearNest().removeInnerClasses().transform());
    for (Class<?> clazz : classes) {
      result.add(transformer(clazz).clearNest().removeInnerClasses().transform());
    }
    return result;
  }

  interface I {

    private int target(int x) {
      return x + 10;
    }

    static MethodHandle specialHandle() {
      // Replaced by a constant method handle for H_INVOKESPECIAL I.target(int).
      return (MethodHandle) (Object) "I.target";
    }

    @NeverInline
    static int callTarget(I receiver, int x) throws Throwable {
      return (int) specialHandle().invokeExact(receiver, x);
    }

    static int callTargetViaCallSite(I receiver, int x) throws Throwable {
      // Replaced by an invokedynamic with bootstrap argument H_INVOKESPECIAL I.target(int).
      return callTarget(receiver, x);
    }

    static CallSite bootstrap(
        MethodHandles.Lookup lookup, String name, MethodType type, MethodHandle target) {
      return new ConstantCallSite(target);
    }
  }

  static class Impl implements I {}

  static class SubImpl extends Impl {}

  static class OverrideImpl implements I {

    @NeverInline
    public int target(int x) {
      return x + 997;
    }
  }

  @NoVerticalClassMerging
  static class Base {

    @NeverInline
    public int target(int x) {
      return x + 997;
    }
  }

  static class Sub extends Base implements I {}

  @NoVerticalClassMerging
  static class ZBase {

    @NeverInline
    public int target(int x) {
      return x + 997;
    }
  }

  static class SubZ extends ZBase implements I {}

  static class PublicizedMain {

    public static void main(String[] args) throws Throwable {
      System.out.println(I.callTarget(new Impl(), 2));
      System.out.println(I.callTarget(new SubImpl(), 2));
      System.out.println(I.callTargetViaCallSite(new SubImpl(), 2));
    }
  }

  static class OverrideMain {

    public static void main(String[] args) throws Throwable {
      OverrideImpl impl = new OverrideImpl();
      System.out.println(I.callTarget(impl, 2));
      System.out.println(impl.target(args.length + 2));
    }
  }

  static class SuperclassMain {

    public static void main(String[] args) throws Throwable {
      Sub sub = new Sub();
      System.out.println(I.callTarget(sub, 2));
      System.out.println(sub.target(args.length + 2));
    }
  }

  static class SuperclassReverseOrderMain {

    public static void main(String[] args) throws Throwable {
      SubZ sub = new SubZ();
      System.out.println(I.callTarget(sub, 2));
      System.out.println(sub.target(args.length + 2));
    }
  }
}
