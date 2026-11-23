// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static com.android.tools.r8.jdk9.privateitf.DexVersion35UpToApi30Test.assertDexVersion;
import static com.android.tools.r8.jdk9.privateitf.DexVersion35UpToApi30Test.getDefaultDexVersion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.dex.code.DexConstMethodHandle;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.internal.Box;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Regression tests for corner cases of {@code PrivateInterfaceMethodStaticizer}. */
@RunWith(Parameterized.class)
public class PrivateInterfaceMethodStaticizerRegressionTest extends TestBase {

  enum Compiler {
    D8,
    D8_DEX_INPUT,
    R8
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Compiler compiler;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
            // Ensure that const-method-handle is tested with DEX 035.
            .withApiLevel(apiLevelWithConstMethodHandleSupport())
            .build(),
        Compiler.values());
  }

  private boolean isStaticized() {
    return parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R);
  }

  @Test
  public void testConstMethodHandle() throws Exception {
    testConstMethodHandle(false);
  }

  @Test
  public void testConstMethodHandleKeptPrivate() throws Exception {
    testConstMethodHandle(true);
  }

  private void testConstMethodHandle(boolean keepTargetPrivate) throws Exception {
    // The method handles are only rewritten when the private interface methods are staticized.
    // Otherwise, ART 17+ throws an IncompatibleClassChangeError for the H_INVOKEINTERFACE method
    // handle of the private interface method.
    assumeTrue(
        parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithConstMethodHandleSupport())
            && isStaticized());
    List<byte[]> programClassFileData =
        programClassFileData(
            MethodHandleImpl.class, SpecialMethodHandleMain.class, MethodHandleMain.class);
    programClassFileData.add(getTransformedMethodHandleItf());
    // R8 makes the target public, unless it is kept.
    boolean isTargetPrivate = compiler != Compiler.R8 || keepTargetPrivate;
    TestCompileResult<?, ?> compileResult =
        compile(
                MethodHandleMain.class,
                programClassFileData,
                builder ->
                    builder
                        .addKeepMainRule(SpecialMethodHandleMain.class)
                        .applyIf(
                            keepTargetPrivate,
                            b ->
                                b.addKeepRules(
                                    "-keepclassmembers,allowobfuscation interface "
                                        + MethodHandleItf.class.getTypeName()
                                        + " {",
                                    "  private int target(int);",
                                    "}")))
            .inspect(
                inspector -> {
                  List<DexMethodHandle> methodHandles = getConstMethodHandles(inspector);
                  assertEquals(2, methodHandles.size());
                  for (DexMethodHandle methodHandle : methodHandles) {
                    assertEquals(
                        isTargetPrivate
                            ? MethodHandleType.INVOKE_STATIC
                            : MethodHandleType.INVOKE_INTERFACE,
                        methodHandle.type);
                    if (methodHandle.isStaticHandle() && compiler != Compiler.R8) {
                      assertEquals(
                          "target$staticized", methodHandle.asMethod().getName().toString());
                    }
                  }
                });
    // ART 13+ compiled code throws an IllegalAccessError when a caller of a const-method-handle of
    // a private method is inlined into another class, also when the private interface method is
    // not staticized and the DEX version is 039 (cf. b/235807678). Use the interpreter then.
    if (isTargetPrivate) {
      compileResult.addVmArguments("-Xint");
    }
    compileResult
        .run(parameters.getRuntime(), MethodHandleMain.class)
        .assertSuccessWithOutputLines("11");
    compileResult
        .run(parameters.getRuntime(), SpecialMethodHandleMain.class)
        .assertSuccessWithOutputLines("12");
  }

  @Test
  public void testStartupProfile() throws Exception {
    MethodReference syncCheck =
        Reference.methodFromMethod(SyncItf.class.getDeclaredMethod("syncCheck"));
    StartupProfileProvider startupProfileProvider =
        new StartupProfileProvider() {
          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            startupProfileBuilder.addStartupMethod(
                startupMethodBuilder -> startupMethodBuilder.setMethodReference(syncCheck));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        };
    Box<String> firstCodeInStartupDex = new Box<>();
    compile(
            SyncMain.class,
            programClassFileData(SyncItf.class, SyncImpl.class, SyncMain.class),
            builder -> {
              if (builder.isD8TestBuilder()) {
                builder.asD8TestBuilder().addStartupProfileProviders(startupProfileProvider);
              } else {
                builder
                    .asR8TestBuilder()
                    .addStartupProfileProviders(startupProfileProvider)
                    .allowDiagnosticInfoMessages();
              }
              builder.addOptionsModification(
                  options ->
                      options
                          .getTestingOptions()
                          .setMixedSectionLayoutStrategyInspector(
                              (layoutStrategy, virtualFile) -> {
                                if (virtualFile.getId() == 0) {
                                  firstCodeInStartupDex.set(
                                      layoutStrategy
                                          .getCodeLayout()
                                          .iterator()
                                          .next()
                                          .getName()
                                          .toString());
                                }
                                return layoutStrategy;
                              }));
            },
            builder ->
                builder.addKeepRules(
                    "-keepclassmembers interface " + SyncItf.class.getTypeName() + " {",
                    "  private boolean syncCheck();",
                    "}"))
        .run(parameters.getRuntime(), SyncMain.class)
        // The syncCheck method is not synchronized in this test.
        .assertSuccessWithOutputLines("false");
    // The startup method rule is rewritten when the method is staticized, so that its code is laid
    // out first in the startup DEX file.
    assertEquals(
        isStaticized() ? "syncCheck$staticized" : "syncCheck", firstCodeInStartupDex.get());
  }

  @Test
  public void testSynchronizedPrivateInterfaceMethod() throws Exception {
    List<byte[]> programClassFileData = programClassFileData(SyncImpl.class, SyncMain.class);
    programClassFileData.add(
        programClassTransformer(SyncItf.class)
            .setAccessFlags(MethodPredicate.onName("syncCheck"), MethodAccessFlags::setSynchronized)
            .transform());
    compile(
            SyncMain.class,
            programClassFileData,
            builder ->
                builder.addKeepRules(
                    "-keepclassmembers interface " + SyncItf.class.getTypeName() + " {",
                    "  private boolean syncCheck();",
                    "}"))
        .inspect(
            inspector -> {
              FoundMethodSubject syncCheck =
                  findUniqueMethod(
                      inspector,
                      method ->
                          method.streamInstructions().anyMatch(InstructionSubject::isMonitorEnter));
              assertEquals(isStaticized(), syncCheck.isStatic());
              assertEquals(!isStaticized(), syncCheck.isSynchronized());
            })
        .run(parameters.getRuntime(), SyncMain.class)
        .assertSuccessWithOutputLines("true");
  }

  @Test
  public void testMissingParameterAnnotations() throws Exception {
    List<byte[]> programClassFileData =
        programClassFileData(ParamAnno.class, AnnotatedImpl.class, AnnotatedMain.class);
    programClassFileData.add(getTransformedAnnotatedItf());
    compile(
            AnnotatedMain.class,
            programClassFileData,
            builder ->
                builder
                    .addKeepClassRules(ParamAnno.class)
                    .addKeepRules(
                        "-keepclassmembers interface " + AnnotatedItf.class.getTypeName() + " {",
                        "  private int annotated(int, int);",
                        "}")
                    .addKeepRuntimeVisibleParameterAnnotations())
        .inspect(
            inspector -> {
              FoundMethodSubject annotated =
                  findUniqueMethod(
                      inspector, method -> !method.getMethod().getParameterAnnotations().isEmpty());
              int arity = annotated.getMethod().getParameters().size();
              assertEquals(isStaticized() ? 3 : 2, arity);
              ParameterAnnotationsList parameterAnnotations =
                  annotated.getMethod().getParameterAnnotations();
              assertEquals(arity, parameterAnnotations.size());
              for (int i = 0; i < arity - 1; i++) {
                assertTrue(parameterAnnotations.get(i).isEmpty());
              }
              DexAnnotationSet lastParameterAnnotations = parameterAnnotations.get(arity - 1);
              assertEquals(1, lastParameterAnnotations.size());
              assertEquals(
                  ParamAnno.class.getTypeName(),
                  lastParameterAnnotations.getFirst().getAnnotationType().getTypeName());
            })
        .run(parameters.getRuntime(), AnnotatedMain.class)
        .assertSuccessWithOutputLines("3");
  }

  private TestCompileResult<?, ?> compile(
      Class<?> mainClass,
      List<byte[]> programClassFileData,
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> r8Configuration)
      throws Exception {
    return compile(mainClass, programClassFileData, ThrowableConsumer.empty(), r8Configuration);
  }

  private TestCompileResult<?, ?> compile(
      Class<?> mainClass,
      List<byte[]> programClassFileData,
      ThrowableConsumer<TestCompilerBuilder<?, ?, ?, ?, ?>> configuration,
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> r8Configuration)
      throws Exception {
    TestCompileResult<?, ?> compileResult;
    switch (compiler) {
      case D8:
        compileResult =
            testForD8(parameters)
                .addProgramClassFileData(programClassFileData)
                .addOptionsModification(
                    options -> options.testing.enableDexVersion35UpToApi30 = true)
                .apply(configuration)
                .compile();
        break;
      case D8_DEX_INPUT:
        Path dexInput =
            testForD8(parameters)
                .addProgramClassFileData(programClassFileData)
                .addOptionsModification(
                    options -> options.testing.enableDexVersion35UpToApi30 = false)
                .compile()
                .writeToZip();
        compileResult =
            testForD8(parameters)
                .addProgramFiles(dexInput)
                .addOptionsModification(
                    options -> options.testing.enableDexVersion35UpToApi30 = true)
                .apply(configuration)
                .compile();
        break;
      default:
        assert compiler == Compiler.R8;
        compileResult =
            testForR8(parameters)
                .addProgramClassFileData(programClassFileData)
                .addKeepMainRule(mainClass)
                .addOptionsModification(
                    options -> options.testing.enableDexVersion35UpToApi30 = true)
                .apply(configuration)
                .apply(r8Configuration)
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .compile();
        break;
    }
    assertDexVersion(
        isStaticized() ? DexVersion.V35 : getDefaultDexVersion(parameters), compileResult);
    return compileResult;
  }

  // The tested classes do not need nest based access, so drop the nest and inner class attributes
  // to compile them without the other classes of the nest.
  private static ClassFileTransformer programClassTransformer(Class<?> clazz) throws Exception {
    return transformer(clazz).clearNest().removeInnerClasses();
  }

  private static List<byte[]> programClassFileData(Class<?>... classes) throws Exception {
    List<byte[]> result = new ArrayList<>();
    for (Class<?> clazz : classes) {
      result.add(programClassTransformer(clazz).transform());
    }
    return result;
  }

  private static List<DexMethodHandle> getConstMethodHandles(CodeInspector inspector) {
    List<DexMethodHandle> methodHandles = new ArrayList<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      for (DexEncodedMethod method :
          clazz.getDexProgramClass().methods(DexEncodedMethod::hasCode)) {
        for (DexInstruction instruction : method.getCode().asDexCode().instructions) {
          if (instruction instanceof DexConstMethodHandle) {
            methodHandles.add(((DexConstMethodHandle) instruction).getMethodHandle());
          }
        }
      }
    }
    return methodHandles;
  }

  private static FoundMethodSubject findUniqueMethod(
      CodeInspector inspector, Predicate<FoundMethodSubject> predicate) {
    List<FoundMethodSubject> methods =
        inspector.allClasses().stream()
            .flatMap(clazz -> clazz.allMethods(predicate).stream())
            .collect(Collectors.toList());
    assertEquals(1, methods.size());
    return methods.get(0);
  }

  private static byte[] getTransformedMethodHandleItf() throws Exception {
    return programClassTransformer(MethodHandleItf.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                MethodVisitor mv =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
                int tag;
                if (name.equals("interfaceHandle")) {
                  tag = Opcodes.H_INVOKEINTERFACE;
                } else if (name.equals("specialHandle")) {
                  tag = Opcodes.H_INVOKESPECIAL;
                } else {
                  return mv;
                }
                // Replace the body by a const-method-handle of the private interface method.
                mv.visitCode();
                mv.visitLdcInsn(
                    new Handle(tag, binaryName(MethodHandleItf.class), "target", "(I)I", true));
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(-1, -1);
                mv.visitEnd();
                return null;
              }
            })
        .transform();
  }

  private static byte[] getTransformedAnnotatedItf() throws Exception {
    // Only the last parameter of annotated(int, int) is in the RuntimeVisibleParameterAnnotations
    // attribute, i.e., the first parameter is missing.
    return programClassTransformer(AnnotatedItf.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                if (getContext().method.getMethodName().equals("annotated")) {
                  assertEquals(2, parameterCount);
                  parameterCount = 1;
                }
                super.visitAnnotableParameterCount(parameterCount, visible);
              }

              @Override
              public AnnotationVisitor visitParameterAnnotation(
                  int parameter, String descriptor, boolean visible) {
                if (getContext().method.getMethodName().equals("annotated")) {
                  assertEquals(1, parameter);
                  parameter = 0;
                }
                return super.visitParameterAnnotation(parameter, descriptor, visible);
              }
            })
        .transform();
  }

  @NoVerticalClassMerging
  interface MethodHandleItf {

    @NeverInline
    default int callInterfaceHandle() throws Throwable {
      return (int) interfaceHandle().invokeExact(this, 1);
    }

    @NeverInline
    default int callSpecialHandle() throws Throwable {
      return (int) specialHandle().invokeExact(this, 2);
    }

    // Replaced by a const-method-handle H_INVOKEINTERFACE of target(int).
    static MethodHandle interfaceHandle() {
      throw new RuntimeException();
    }

    // Replaced by a const-method-handle H_INVOKESPECIAL of target(int).
    static MethodHandle specialHandle() {
      throw new RuntimeException();
    }

    private int target(int i) {
      return i + 10;
    }
  }

  static class MethodHandleImpl implements MethodHandleItf {}

  static class MethodHandleMain {

    public static void main(String[] args) throws Throwable {
      System.out.println(new MethodHandleImpl().callInterfaceHandle());
    }
  }

  static class SpecialMethodHandleMain {

    public static void main(String[] args) throws Throwable {
      System.out.println(new MethodHandleImpl().callSpecialHandle());
    }
  }

  @NoVerticalClassMerging
  interface SyncItf {

    @NeverInline
    default boolean callSyncCheck() {
      return syncCheck();
    }

    // Made synchronized by the transformer.
    @NeverInline
    private boolean syncCheck() {
      return Thread.holdsLock(this) && !Thread.holdsLock(SyncItf.class);
    }
  }

  static class SyncImpl implements SyncItf {}

  static class SyncMain {

    public static void main(String[] args) {
      System.out.println(new SyncImpl().callSyncCheck());
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface ParamAnno {}

  @NoVerticalClassMerging
  interface AnnotatedItf {

    @NeverInline
    default int callAnnotated() {
      return annotated(1, 2);
    }

    @NeverInline
    private int annotated(int unannotated, @ParamAnno int annotated) {
      return unannotated + annotated;
    }
  }

  static class AnnotatedImpl implements AnnotatedItf {}

  static class AnnotatedMain {

    public static void main(String[] args) {
      System.out.println(new AnnotatedImpl().callAnnotated());
    }
  }
}
