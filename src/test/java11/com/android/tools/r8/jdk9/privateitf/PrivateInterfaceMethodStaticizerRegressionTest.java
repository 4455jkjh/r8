// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.dex.code.DexConstMethodHandle;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMonitorEnter;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.NativePcBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.PcBasedDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.utils.ZipUtils;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Regression tests for corner cases of {@code PrivateInterfaceMethodStaticizer}. */
@RunWith(Parameterized.class)
public class PrivateInterfaceMethodStaticizerRegressionTest
    extends PrivateInterfaceMethodStaticizerTestBase {

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
            && isStaticized(parameters));
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
        isStaticized(parameters) ? "syncCheck$staticized" : "syncCheck",
        firstCodeInStartupDex.get());
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
              assertEquals(isStaticized(parameters), syncCheck.isStatic());
              assertEquals(!isStaticized(parameters), syncCheck.isSynchronized());
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
              assertEquals(isStaticized(parameters) ? 3 : 2, arity);
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

  @Test
  public void testR8DexInput() throws Exception {
    // On API > 30, private interface methods are not staticized and R8 DEX input does not set
    // isInterface on invoke-direct (IRBuilder.addInvokeRegisters).
    assumeTrue(compiler == Compiler.R8 && isStaticized(parameters));
    // The invoke-direct of the private interface method in the DEX input has no interface bit.
    Path dexInput =
        testForD8(parameters)
            .addProgramClassFileData(
                programClassFileData(SyncItf.class, SyncImpl.class, SyncMain.class))
            .addOptionsModification(
                options -> options.getTestingOptions().enableDexVersion35UpToApi30 = false)
            .compile()
            .writeToZip();
    // R8 ignores DEX in program archives, so pass the DEX file data directly.
    TestCompileResult<?, ?> compileResult =
        testForR8(parameters)
            .addProgramDexFileData(ZipUtils.readSingleEntry(dexInput, "classes.dex"))
            .addKeepMainRule(SyncMain.class)
            .addKeepRules(
                "-keepclassmembers interface " + SyncItf.class.getTypeName() + " {",
                "  private boolean syncCheck();",
                "}")
            .addOptionsModification(
                options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
            .compile()
            .inspect(
                inspector ->
                    findUniqueMethod(
                        inspector,
                        method ->
                            method
                                .streamInstructions()
                                .anyMatch(
                                    instruction ->
                                        instruction.isInvokeStatic()
                                            && instruction
                                                .getMethod()
                                                .getName()
                                                .isEqualTo("syncCheck$staticized"))));
    assertDexVersion(DexVersion.V35, compileResult);
    compileResult
        .run(parameters.getRuntime(), SyncMain.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testInvokeSuper() throws Exception {
    List<byte[]> programClassFileData = programClassFileData(SuperImpl.class);
    programClassFileData.add(
        programClassTransformer(SuperItf.class)
            .setAccessFlags(
                MethodPredicate.onName("target"),
                flags -> {
                  flags.unsetPublic();
                  flags.setPrivate();
                })
            .transform());
    boolean isStaticized = isStaticized(parameters);
    // The invoke-super of the private interface method fails at runtime, so only compile it.
    compile(
            SuperImpl.class,
            programClassFileData,
            builder ->
                builder.addKeepRules(
                    "-keepclassmembers interface " + SuperItf.class.getTypeName() + " {",
                    "  private int target();",
                    "}"))
        .inspect(
            inspector ->
                assertTrue(
                    inspector
                        .clazz(SuperImpl.class)
                        .uniqueMethodWithOriginalName("callTarget")
                        .streamInstructions()
                        .anyMatch(
                            instruction ->
                                isStaticized
                                    ? instruction.isInvokeStatic()
                                        && instruction
                                            .getMethod()
                                            .getName()
                                            .isEqualTo("target$staticized")
                                    : instruction.asDexInstruction().isInvokeSuper()
                                        && instruction.getMethod().getName().isEqualTo("target"))));
  }

  @Test
  public void testD8ReleaseDexInput() throws Exception {
    assumeTrue(compiler == Compiler.D8_DEX_INPUT);
    // Use R8 with forced pc based encoding to have PcBasedDebugInfo for annotated(int, int) in the
    // DEX input.
    Path dexInput =
        testForR8(parameters.getBackend())
            .setMinApi(AndroidApiLevel.N)
            .addProgramClassFileData(
                programClassFileData(
                    ParamAnno.class, AnnotatedItf.class, AnnotatedImpl.class, AnnotatedMain.class))
            .addKeepMainRule(AnnotatedMain.class)
            .addKeepRules(
                "-keepclassmembers interface " + AnnotatedItf.class.getTypeName() + " {",
                "  private int annotated(int, int);",
                "}")
            .addOptionsModification(
                options -> {
                  options.getTestingOptions().enableDexVersion35UpToApi30 = false;
                  options.getTestingOptions().forcePcBasedEncoding = true;
                })
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile()
            .inspect(
                inspector ->
                    assertTrue(
                        inspector
                            .clazz(AnnotatedItf.class)
                            .uniqueMethodWithOriginalName("annotated")
                            .getMethod()
                            .getCode()
                            .asDexCode()
                            .getDebugInfo()
                            .isPcBasedInfo()))
            .writeToZip();
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addProgramFiles(dexInput)
            .addOptionsModification(
                options -> {
                  options.getTestingOptions().enableDexVersion35UpToApi30 = true;
                  options.getTestingOptions().enableShareDebugInfoForDifferentParameterSizes = true;
                })
            .release()
            .compile();
    assertExpectedDexVersion(parameters, compileResult);
    compileResult
        .run(parameters.getRuntime(), AnnotatedMain.class)
        .assertSuccessWithOutputLines("3");
  }

  @Test
  public void testDebugInfoWithFakeThisParameter() {
    assumeTrue(compiler == Compiler.D8 && parameters.getApiLevel().isEqualTo(AndroidApiLevel.N));
    DexItemFactory factory = new DexItemFactory();
    DexType holder = factory.createType("LI;");
    // The staticized methods have the fake this parameter and two or zero original parameters.
    DexMethod method =
        factory.createMethod(
            holder,
            factory.createProto(factory.voidType, holder, factory.intType, factory.intType),
            "m$staticized");
    DexMethod methodWithoutParameters =
        factory.createMethod(
            holder, factory.createProto(factory.voidType, holder), "m0$staticized");
    assertDebugInfoWithFakeThisParameter(new PcBasedDebugInfo(2, 1), method, 3, 1, factory);
    assertDebugInfoWithFakeThisParameter(new PcBasedDebugInfo(0, 1), method, 3, 1, factory);
    assertDebugInfoWithFakeThisParameter(new NativePcBasedDebugInfo(2), method, 3, 0, factory);
    // Event based debug info may omit trailing unnamed parameters.
    assertDebugInfoWithFakeThisParameter(
        new EventBasedDebugInfo(7, DexString.EMPTY_ARRAY, DexDebugEvent.EMPTY_ARRAY),
        method,
        3,
        7,
        factory);
    assertDebugInfoWithFakeThisParameter(
        new PcBasedDebugInfo(0, 1), methodWithoutParameters, 1, 1, factory);
  }

  private static void assertDebugInfoWithFakeThisParameter(
      DexDebugInfo debugInfo,
      DexMethod method,
      int expectedParameterCount,
      int expectedStartLine,
      DexItemFactory factory) {
    // A throwing instruction followed by a return.
    DexInstruction returnVoid = new DexReturnVoid();
    returnVoid.setOffset(1);
    DexCode code =
        new DexCode(
            3,
            3,
            0,
            new DexInstruction[] {new DexMonitorEnter(0), returnVoid},
            Try.EMPTY_ARRAY,
            TryHandler.EMPTY_ARRAY,
            debugInfo);
    EventBasedDebugInfo newDebugInfo =
        code.debugInfoWithFakeThisParameter(method, factory).asEventBasedInfo();
    assertEquals(expectedParameterCount, newDebugInfo.getParameterCount());
    assertEquals(expectedStartLine, newDebugInfo.getStartLine());
    assertEquals("_this", newDebugInfo.parameters[0].toString());
  }

  @Test
  public void testInterfaceConstructor() {
    assumeTrue(compiler != Compiler.D8_DEX_INPUT && isStaticized(parameters));
    TestCompilerBuilder<?, ?, ?, ?, ?> builder =
        compiler == Compiler.D8
            ? testForD8(parameters)
            : testForR8(parameters)
                .addKeepRules(
                    "-keep interface ConstructorInterface { <init>(); private void m(); }");
    assertThrows(
        CompilationFailedException.class,
        () ->
            builder
                .addProgramClassFileData(createInterfaceWithConstructor())
                .addOptionsModification(
                    options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            diagnosticMessage(
                                containsString("Interface must not have constructors")))));
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
                    options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
                .apply(configuration)
                .compile();
        break;
      case D8_DEX_INPUT:
        Path dexInput =
            testForD8(parameters)
                .addProgramClassFileData(programClassFileData)
                .addOptionsModification(
                    options -> options.getTestingOptions().enableDexVersion35UpToApi30 = false)
                .compile()
                .writeToZip();
        compileResult =
            testForD8(parameters)
                .addProgramFiles(dexInput)
                .addOptionsModification(
                    options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
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
                    options -> options.getTestingOptions().enableDexVersion35UpToApi30 = true)
                .apply(configuration)
                .apply(r8Configuration)
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .compile();
        break;
    }
    assertExpectedDexVersion(parameters, compileResult);
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

  // Interface with a private instance initializer and a private instance method. Such interfaces
  // cannot be written in Java source.
  private static byte[] createInterfaceWithConstructor() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V11,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
        "ConstructorInterface",
        null,
        "java/lang/Object",
        null);
    for (String name : new String[] {"<init>", "m"}) {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, "()V", null, null);
      mv.visitCode();
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(0, 1);
      mv.visitEnd();
    }
    cw.visitEnd();
    return cw.toByteArray();
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

  @NoVerticalClassMerging
  interface SuperItf {

    // Made private by the transformer.
    default int target() {
      return 42;
    }
  }

  static class SuperImpl implements SuperItf {

    @NeverInline
    int callTarget() {
      return SuperItf.super.target();
    }

    public static void main(String[] args) {
      System.out.println(new SuperImpl().callTarget());
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
