// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.containsConstString;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.Internal.EnumLite;
import com.google.protobuf.Internal.EnumLiteMap;
import com.google.protobuf.Internal.EnumVerifier;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class ProtoEnumLiteToStringUnboxingTest extends TestBase implements Opcodes {

  private static final int LARGE_ENUM_CONSTANT_COUNT = 400;

  private static final Function<Integer, byte[]> LARGE_PROTO_ENUM_CLASS_DATA =
      memoizeFunction(ProtoEnumLiteToStringUnboxingTest::generateLargeProtoEnumClassFileData);

  private static final Function<String, byte[]> UNREACHABLE_MISSING_INTERFACE_ENUM_CLASS_DATA =
      memoizeFunction(
          ProtoEnumLiteToStringUnboxingTest::generateUnreachableEnumWithMissingInterfaceData);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static void addProtoLiteRuntime(R8TestBuilder<?, ?, ?> builder) {
    builder
        .addProgramFiles(ProtoRuntime.LEGACY.getProgramFiles())
        .addProgramClasses(ProtoHolderMessage.class, ProtoHolderMessage.Builder.class)
        .addKeepRules(
            "-keep,allowobfuscation class "
                + ProtoHolderMessage.class.getTypeName()
                + " { static *** DEFAULT_INSTANCE; }");
  }

  private R8TestBuilder<?, ?, ?> testForR8WithProtoLite(
      Class<?> mainClass, Class<?>... extraClasses) {
    return testForR8(parameters)
        .apply(ProtoEnumLiteToStringUnboxingTest::addProtoLiteRuntime)
        .addProgramClasses(mainClass)
        .addProgramClasses(extraClasses)
        .addKeepMainRule(mainClass)
        .enableInliningAnnotations()
        .enableProtoShrinking()
        .addOptionsModification(o -> o.protoShrinking().enableProtoEnumOptimization = true);
  }

  @Test
  public void testClosedEnumUnboxedAndStringsRemoved() throws Exception {
    testForR8WithProtoLite(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ClosedProtoEnum.class))
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputLines("1", "1", "2", "2", "3", "3")
        .inspect(
            inspector ->
                assertNoConstStrings(
                    inspector, "CLOSED_FOO", "CLOSED_BAR", "CLOSED_BAZ", " number=", " name="));
  }

  @Test
  public void testOpenEnumUnboxedAndStringsRemoved() throws Exception {
    testForR8WithProtoLite(
            OpenMain.class,
            OpenProtoEnum.class,
            OpenProtoEnum.OpenProtoEnumMap.class,
            OpenProtoEnum.OpenProtoEnumVerifier.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(OpenProtoEnum.class))
        .run(parameters.getRuntime(), OpenMain.class)
        .assertSuccessWithOutputLines("0", "0", "1", "1", "-1")
        .inspect(
            inspector ->
                assertNoConstStrings(
                    inspector, "OPEN_FOO", "OPEN_BAR", "UNRECOGNIZED", " number=", " name="));
  }

  @Test
  public void testInheritedToStringEnumUnboxedAndStringsRemoved() throws Exception {
    testForR8WithProtoLite(
            InheritedToStringMain.class,
            InheritedToStringProtoEnum.class,
            InheritedToStringProtoEnum.InheritedToStringProtoEnumMap.class,
            InheritedToStringProtoEnum.InheritedToStringProtoEnumVerifier.class)
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(InheritedToStringProtoEnum.class))
        .run(parameters.getRuntime(), InheritedToStringMain.class)
        .assertSuccessWithOutputLines("11", "11", "11", "22", "22", "22", "null")
        .inspect(inspector -> assertNoConstStrings(inspector, "INHERITED_FOO", "INHERITED_BAR"));
  }

  @Test
  public void testInheritedToStringEnumWithoutGetNumberCall() throws Exception {
    // Exercises optimizing inherited toString() when getNumber() has no callers and is pruned by
    // the initial TreePruner, while leaving non-unboxed escaping enums untouched.
    testForR8WithProtoLite(
            InheritedToStringWithoutGetNumberMain.class,
            InheritedToStringProtoEnum.class,
            InheritedToStringProtoEnum.InheritedToStringProtoEnumMap.class,
            InheritedToStringProtoEnum.InheritedToStringProtoEnumVerifier.class,
            EscapingInheritedToStringProtoEnum.class)
        .addEnumUnboxingInspector(
            inspector -> {
              inspector.assertUnboxed(InheritedToStringProtoEnum.class);
              inspector.assertNotUnboxed(EscapingInheritedToStringProtoEnum.class);
            })
        .run(parameters.getRuntime(), InheritedToStringWithoutGetNumberMain.class)
        .assertSuccessWithOutputLines("11", "22", "ESCAPING_FOO", "ESCAPING_BAR")
        .inspect(inspector -> assertNoConstStrings(inspector, "INHERITED_FOO", "INHERITED_BAR"));
  }

  @Test
  public void testOpenInheritedToStringEnumWithoutGetNumberCall() throws Exception {
    // Exercises optimizing inherited Enum.toString() on an open EnumLite enum when getNumber() has
    // no callers and is pruned by the initial TreePruner.
    testForR8WithProtoLite(
            OpenInheritedToStringWithoutGetNumberMain.class, OpenInheritedToStringProtoEnum.class)
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(OpenInheritedToStringProtoEnum.class))
        .run(parameters.getRuntime(), OpenInheritedToStringWithoutGetNumberMain.class)
        .assertSuccessWithOutputLines("50", "60", "-1")
        .inspect(
            inspector ->
                assertNoConstStrings(
                    inspector, "OPEN_INHERITED_FOO", "OPEN_INHERITED_BAR", "UNRECOGNIZED"));
  }

  @Test
  public void testZeroBasedAndNameOnlyEnumLiteUnboxed() throws Exception {
    testForR8WithProtoLite(
            ZeroBasedAndNameOnlyMain.class, ZeroBasedProtoEnum.class, NameOnlyProtoEnum.class)
        .addEnumUnboxingInspector(
            inspector -> {
              inspector.assertUnboxed(ZeroBasedProtoEnum.class);
              inspector.assertUnboxed(NameOnlyProtoEnum.class);
            })
        .run(parameters.getRuntime(), ZeroBasedAndNameOnlyMain.class)
        .assertSuccessWithOutputLines(
            "0", "0", "1", "1", "null", "0", "npe1", "npe2", "NAME_FOO", "1")
        .inspect(inspector -> assertNoConstStrings(inspector, "ZERO_FOO", "ZERO_BAR"));
  }

  @Test
  public void testLargeProtoEnumWithValuesHelperUnboxed() throws Exception {
    testForR8WithProtoLite(LargeProtoEnumMain.class, LargeProtoEnumMap.class)
        .addProgramClassFileData(LARGE_PROTO_ENUM_CLASS_DATA.apply(LARGE_ENUM_CONSTANT_COUNT))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeProtoEnum.class))
        .run(parameters.getRuntime(), LargeProtoEnumMain.class)
        .assertSuccessWithOutputLines("1000", "1000", "1199", "1199", "1399", "1399", "400")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(LargeProtoEnum.class), isAbsent());
              inspector.forAllClasses(
                  clazz -> assertThat(clazz.uniqueMethodWithOriginalName("$values"), isAbsent()));
              assertNoConstStrings(inspector, "E0", "E199", "E399", " number=", " name=");
            });
  }

  @Test
  public void testNotRewrittenByDefaultWithProtoShrinking() throws Exception {
    testForR8(parameters)
        .apply(ProtoEnumLiteToStringUnboxingTest::addProtoLiteRuntime)
        .addProgramClasses(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addKeepMainRule(ClosedMain.class)
        .enableInliningAnnotations()
        .enableProtoShrinking()
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=1 name=CLOSED_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "CLOSED_FOO", "CLOSED_BAR"));
  }

  @Test
  public void testNotRewrittenWithoutProtoShrinking() throws Exception {
    testForR8(parameters)
        .apply(ProtoEnumLiteToStringUnboxingTest::addProtoLiteRuntime)
        .addProgramClasses(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addKeepMainRule(ClosedMain.class)
        .enableInliningAnnotations()
        .addOptionsModification(o -> o.protoShrinking().enableProtoEnumOptimization = true)
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=1 name=CLOSED_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "CLOSED_FOO", "CLOSED_BAR"));
  }

  @Test
  public void testKeptEnumClassNotRewritten() throws Exception {
    testForR8WithProtoLite(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addKeepClassRules(ClosedProtoEnum.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(ClosedProtoEnum.class))
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=1 name=CLOSED_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "CLOSED_FOO", "CLOSED_BAR"));
  }

  @Test
  public void testKeptToStringNotRewritten() throws Exception {
    testForR8WithProtoLite(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addKeepRules(
            "-keepclassmembers class "
                + ClosedProtoEnum.class.getTypeName()
                + " { public java.lang.String toString(); }")
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=1 name=CLOSED_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "CLOSED_FOO", "CLOSED_BAR"));
  }

  @Test
  public void testKeptValueFieldNotRewritten() throws Exception {
    testForR8WithProtoLite(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addKeepRules(
            "-keepclassmembers class " + ClosedProtoEnum.class.getTypeName() + " { int value; }")
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(ClosedProtoEnum.class))
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=1 name=CLOSED_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "CLOSED_FOO", "CLOSED_BAR"));
  }

  @Test
  public void testCustomToStringNotRewritten() throws Exception {
    testForR8WithProtoLite(CustomMain.class, CustomToStringProtoEnum.class)
        .run(parameters.getRuntime(), CustomMain.class)
        .assertSuccessWithOutputLines("custom:CUSTOM_FOO:10", "custom:CUSTOM_BAR:20")
        .inspect(inspector -> assertHasConstStrings(inspector, "CUSTOM_FOO", "CUSTOM_BAR"));
  }

  @Test
  public void testUnreachableEnumWithMissingSuperInterface() throws Exception {
    testForR8WithProtoLite(
            ClosedMain.class,
            ClosedProtoEnum.class,
            ClosedProtoEnum.ClosedProtoEnumMap.class,
            ClosedProtoEnum.ClosedProtoEnumVerifier.class)
        .addProgramClassFileData(
            UNREACHABLE_MISSING_INTERFACE_ENUM_CLASS_DATA.apply("missing/UnreferencedInterface"))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ClosedProtoEnum.class))
        .run(parameters.getRuntime(), ClosedMain.class)
        .assertSuccessWithOutputLines("1", "1", "2", "2", "3", "3");
  }

  @Test
  public void testNonEnumLiteNotRewritten() throws Exception {
    testForR8WithProtoLite(NonEnumLiteMain.class, NonEnumLiteEnum.class)
        .run(parameters.getRuntime(), NonEnumLiteMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=7 name=PLAIN_FOO>"))
        .inspect(inspector -> assertHasConstStrings(inspector, "PLAIN_FOO", "PLAIN_BAR"));
  }

  @Test
  public void testNonFinalEnumNotRewritten() throws Exception {
    List<Class<?>> syntheticConstructorClasses =
        Arrays.asList(NonFinalProtoEnum.class, ConstantBodyGetNumberProtoEnum.class).stream()
            .flatMap(clazz -> Arrays.stream(clazz.getDeclaredConstructors()))
            .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
            .filter(Class::isSynthetic)
            .distinct()
            .collect(Collectors.toList());
    testForR8WithProtoLite(
            NonFinalMain.class, NonFinalProtoEnum.class, ConstantBodyGetNumberProtoEnum.class)
        .addInnerClasses(NonFinalProtoEnum.class, ConstantBodyGetNumberProtoEnum.class)
        .addProgramClasses(syntheticConstructorClasses)
        .run(parameters.getRuntime(), NonFinalMain.class)
        .assertSuccessWithOutputThatMatches(containsString(" number=5 name=NON_FINAL_FOO>"))
        .assertSuccessWithOutputThatMatches(
            containsString(
                StringUtils.lines(
                    "99", "custom-super:CONST_SUB_FOO", "100", "custom-super:CONST_SUB_BAR")))
        .inspect(
            inspector ->
                assertHasConstStrings(
                    inspector, "NON_FINAL_FOO", "NON_FINAL_BAR", "CONST_SUB_FOO", "CONST_SUB_BAR"));
  }

  private static void assertHasConstStrings(CodeInspector inspector, String... values) {
    for (String value : values) {
      assertTrue(
          "Expected const-string: " + value,
          inspector.allClasses().stream()
              .anyMatch(
                  clazz ->
                      clazz.allMethods().stream()
                          .anyMatch(method -> containsConstString(value).matches(method))));
    }
  }

  private static void assertNoConstStrings(CodeInspector inspector, String... values) {
    assertFalse(inspector.allClasses().isEmpty());
    inspector.forAllClasses(
        clazz ->
            clazz.forAllMethods(
                method -> {
                  for (String value : values) {
                    assertThat(method, not(containsConstString(value)));
                  }
                }));
  }

  private static byte[] generateUnreachableEnumWithMissingInterfaceData(
      String missingInterfaceInternalName) {
    String internalName = "com/android/tools/r8/internal/proto/DeadMissingInterfaceEnum";
    String descriptor = "L" + internalName + ";";
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        V1_8,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_ENUM,
        internalName,
        "Ljava/lang/Enum<" + descriptor + ">;",
        "java/lang/Enum",
        new String[] {binaryName(EnumLite.class), missingInterfaceInternalName});
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static byte[] generateLargeProtoEnumClassFileData(int count) {
    String internalName = binaryName(LargeProtoEnum.class);
    String descriptor = descriptor(LargeProtoEnum.class);
    String arrayDescriptor = "[" + descriptor;
    String valuesHelperDescriptor = "()" + arrayDescriptor;
    String mapDescriptor = descriptor(EnumLiteMap.class);
    String mapImplInternalName = binaryName(LargeProtoEnumMap.class);

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        V1_8,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_ENUM,
        internalName,
        "Ljava/lang/Enum<" + descriptor + ">;",
        "java/lang/Enum",
        new String[] {binaryName(EnumLite.class)});

    for (int i = 0; i < count; i++) {
      cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM, "E" + i, descriptor, null, null)
          .visitEnd();
    }
    cw.visitField(
            ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "internalValueMap", mapDescriptor, null, null)
        .visitEnd();
    cw.visitField(
            ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
            "$VALUES",
            arrayDescriptor,
            null,
            null)
        .visitEnd();
    cw.visitField(ACC_PRIVATE | ACC_FINAL, "value", "I", null, null).visitEnd();

    // public static LargeProtoEnum[] values()
    {
      MethodVisitor mv =
          cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", valuesHelperDescriptor, null, null);
      mv.visitCode();
      mv.visitFieldInsn(GETSTATIC, internalName, "$VALUES", arrayDescriptor);
      mv.visitMethodInsn(INVOKEVIRTUAL, arrayDescriptor, "clone", "()Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, arrayDescriptor);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // public static LargeProtoEnum valueOf(String name)
    {
      MethodVisitor mv =
          cw.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + descriptor, null, null);
      mv.visitCode();
      mv.visitLdcInsn(Type.getType(descriptor));
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/Enum",
          "valueOf",
          "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",
          false);
      mv.visitTypeInsn(CHECKCAST, internalName);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // private LargeProtoEnum(String name, int ordinal, int value)
    {
      MethodVisitor mv =
          cw.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitFieldInsn(PUTFIELD, internalName, "value", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // public final int getNumber()
    {
      MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "getNumber", "()I", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, internalName, "value", "I");
      mv.visitInsn(IRETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // public String toString() matching google3 protoc output
    {
      MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
      mv.visitCode();
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitVarInsn(ASTORE, 1);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitIntInsn(BIPUSH, '@');
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(C)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(
          INVOKESTATIC, "java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I", false);
      mv.visitMethodInsn(
          INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitLdcInsn(" number=");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKEVIRTUAL, internalName, "getNumber", "()I", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(I)Ljava/lang/StringBuilder;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitLdcInsn(" name=");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKEVIRTUAL, internalName, "name", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitIntInsn(BIPUSH, '>');
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(C)Ljava/lang/StringBuilder;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // private static synthetic LargeProtoEnum[] $values()
    {
      MethodVisitor mv =
          cw.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$values",
              valuesHelperDescriptor,
              null,
              null);
      mv.visitCode();
      mv.visitIntInsn(SIPUSH, count);
      mv.visitTypeInsn(ANEWARRAY, internalName);
      for (int i = 0; i < count; i++) {
        mv.visitInsn(DUP);
        mv.visitIntInsn(SIPUSH, i);
        mv.visitFieldInsn(GETSTATIC, internalName, "E" + i, descriptor);
        mv.visitInsn(AASTORE);
      }
      mv.visitInsn(ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // static <clinit>()
    {
      MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      mv.visitCode();
      for (int i = 0; i < count; i++) {
        mv.visitTypeInsn(NEW, internalName);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("E" + i);
        mv.visitIntInsn(SIPUSH, i);
        mv.visitIntInsn(SIPUSH, 1000 + i);
        mv.visitMethodInsn(INVOKESPECIAL, internalName, "<init>", "(Ljava/lang/String;II)V", false);
        mv.visitFieldInsn(PUTSTATIC, internalName, "E" + i, descriptor);
      }
      mv.visitMethodInsn(INVOKESTATIC, internalName, "$values", valuesHelperDescriptor, false);
      mv.visitFieldInsn(PUTSTATIC, internalName, "$VALUES", arrayDescriptor);
      mv.visitTypeInsn(NEW, mapImplInternalName);
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, mapImplInternalName, "<init>", "()V", false);
      mv.visitFieldInsn(PUTSTATIC, internalName, "internalValueMap", mapDescriptor);
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  static final class ProtoHolderMessage
      extends GeneratedMessageLite<ProtoHolderMessage, ProtoHolderMessage.Builder> {

    static final ProtoHolderMessage DEFAULT_INSTANCE = new ProtoHolderMessage();

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
      if (method == MethodToInvoke.NEW_MUTABLE_INSTANCE) {
        return new ProtoHolderMessage();
      }
      if (method == MethodToInvoke.NEW_BUILDER) {
        return new Builder();
      }
      if (method == MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED) {
        return (byte) 1;
      }
      if (method == MethodToInvoke.GET_DEFAULT_INSTANCE) {
        return DEFAULT_INSTANCE;
      }
      return null;
    }

    static final class Builder extends GeneratedMessageLite.Builder<ProtoHolderMessage, Builder> {
      Builder() {
        super(DEFAULT_INSTANCE);
      }
    }
  }

  // Mirrors the google3 protoc output for a closed (proto2) enum.
  public enum ClosedProtoEnum implements EnumLite {
    CLOSED_FOO(1),
    CLOSED_BAR(2),
    CLOSED_BAZ(3);

    private static final EnumLiteMap<ClosedProtoEnum> internalValueMap = new ClosedProtoEnumMap();

    static final class ClosedProtoEnumMap implements EnumLiteMap<ClosedProtoEnum> {
      @Override
      public ClosedProtoEnum findValueByNumber(int number) {
        return ClosedProtoEnum.forNumber(number);
      }
    }

    static final class ClosedProtoEnumVerifier implements EnumVerifier {
      static final EnumVerifier INSTANCE = new ClosedProtoEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return ClosedProtoEnum.forNumber(number) != null;
      }
    }

    @Override
    public final int getNumber() {
      return value;
    }

    public static ClosedProtoEnum forNumber(int value) {
      switch (value) {
        case 1:
          return CLOSED_FOO;
        case 2:
          return CLOSED_BAR;
        case 3:
          return CLOSED_BAZ;
        default:
          return null;
      }
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder("<");
      result
          .append(getClass().getName())
          .append('@')
          .append(Integer.toHexString(System.identityHashCode(this)));
      result.append(" number=").append(getNumber());
      return result.append(" name=").append(name()).append('>').toString();
    }

    private final int value;

    private ClosedProtoEnum(int value) {
      this.value = value;
    }
  }

  // Mirrors the google3 protoc output for an open (proto3) enum.
  public enum OpenProtoEnum implements EnumLite {
    OPEN_FOO(0),
    OPEN_BAR(1),
    UNRECOGNIZED(-1);

    private static final EnumLiteMap<OpenProtoEnum> internalValueMap = new OpenProtoEnumMap();

    static final class OpenProtoEnumMap implements EnumLiteMap<OpenProtoEnum> {
      @Override
      public OpenProtoEnum findValueByNumber(int number) {
        return OpenProtoEnum.forNumber(number);
      }
    }

    static final class OpenProtoEnumVerifier implements EnumVerifier {
      static final EnumVerifier INSTANCE = new OpenProtoEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return OpenProtoEnum.forNumber(number) != null;
      }
    }

    @Override
    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static OpenProtoEnum forNumber(int value) {
      switch (value) {
        case 0:
          return OPEN_FOO;
        case 1:
          return OPEN_BAR;
        default:
          return null;
      }
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder("<");
      result
          .append(getClass().getName())
          .append('@')
          .append(Integer.toHexString(System.identityHashCode(this)));
      if (this != UNRECOGNIZED) {
        result.append(" number=").append(getNumber());
      }
      return result.append(" name=").append(name()).append('>').toString();
    }

    private final int value;

    private OpenProtoEnum(int value) {
      this.value = value;
    }
  }

  // Mirrors the OSS protoc output, which inherits Enum.toString().
  public enum InheritedToStringProtoEnum implements EnumLite {
    INHERITED_FOO(11),
    INHERITED_BAR(22);

    private static final EnumLiteMap<InheritedToStringProtoEnum> internalValueMap =
        new InheritedToStringProtoEnumMap();

    static final class InheritedToStringProtoEnumMap
        implements EnumLiteMap<InheritedToStringProtoEnum> {
      @Override
      public InheritedToStringProtoEnum findValueByNumber(int number) {
        return InheritedToStringProtoEnum.forNumber(number);
      }
    }

    static final class InheritedToStringProtoEnumVerifier implements EnumVerifier {
      static final EnumVerifier INSTANCE = new InheritedToStringProtoEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return InheritedToStringProtoEnum.forNumber(number) != null;
      }
    }

    @Override
    public final int getNumber() {
      return value;
    }

    public static InheritedToStringProtoEnum forNumber(int value) {
      switch (value) {
        case 11:
          return INHERITED_FOO;
        case 22:
          return INHERITED_BAR;
        default:
          return null;
      }
    }

    private final int value;

    private InheritedToStringProtoEnum(int value) {
      this.value = value;
    }
  }

  public enum EscapingInheritedToStringProtoEnum implements EnumLite {
    ESCAPING_FOO(33),
    ESCAPING_BAR(44);

    @Override
    public final int getNumber() {
      return value;
    }

    private final int value;

    private EscapingInheritedToStringProtoEnum(int value) {
      this.value = value;
    }
  }

  public enum OpenInheritedToStringProtoEnum implements EnumLite {
    OPEN_INHERITED_FOO(50),
    OPEN_INHERITED_BAR(60),
    UNRECOGNIZED(-1);

    @Override
    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    private final int value;

    private OpenInheritedToStringProtoEnum(int value) {
      this.value = value;
    }
  }

  public enum ZeroBasedProtoEnum implements EnumLite {
    ZERO_FOO(0),
    ZERO_BAR(1);

    @Override
    public final int getNumber() {
      return value;
    }

    private final int value;

    private ZeroBasedProtoEnum(int value) {
      this.value = value;
    }
  }

  public enum NameOnlyProtoEnum implements EnumLite {
    NAME_FOO(10),
    NAME_BAR(20);

    @Override
    public final int getNumber() {
      return value;
    }

    private final int value;

    private NameOnlyProtoEnum(int value) {
      this.value = value;
    }
  }

  // Compile-time stub; replaced at test time by generateLargeProtoEnumClassFileData().
  public enum LargeProtoEnum implements EnumLite {
    E0(1000),
    E199(1199),
    E399(1399);

    private final int value;

    LargeProtoEnum(int value) {
      this.value = value;
    }

    @Override
    public final int getNumber() {
      return value;
    }
  }

  static final class LargeProtoEnumMap implements EnumLiteMap<LargeProtoEnum> {
    @Override
    public LargeProtoEnum findValueByNumber(int number) {
      return number == 1000 ? LargeProtoEnum.E0 : null;
    }
  }

  public enum CustomToStringProtoEnum implements EnumLite {
    CUSTOM_FOO(10),
    CUSTOM_BAR(20);

    private final int value;

    CustomToStringProtoEnum(int value) {
      this.value = value;
    }

    @Override
    public final int getNumber() {
      return value;
    }

    @Override
    public String toString() {
      return "custom:" + name() + ":" + getNumber();
    }
  }

  // Same toString() as protoc, but does not implement EnumLite.
  public enum NonEnumLiteEnum {
    PLAIN_FOO(7),
    PLAIN_BAR(8);

    public final int getNumber() {
      return value;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder("<");
      result
          .append(getClass().getName())
          .append('@')
          .append(Integer.toHexString(System.identityHashCode(this)));
      result.append(" number=").append(getNumber());
      return result.append(" name=").append(name()).append('>').toString();
    }

    private final int value;

    private NonEnumLiteEnum(int value) {
      this.value = value;
    }
  }

  // Same toString() as protoc, but the enum class is not final since a constant has a body.
  public enum NonFinalProtoEnum implements EnumLite {
    NON_FINAL_FOO(5) {
      @Override
      public String toString() {
        return super.toString();
      }
    },
    NON_FINAL_BAR(6);

    @Override
    public final int getNumber() {
      return value;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder("<");
      result
          .append(getClass().getName())
          .append('@')
          .append(Integer.toHexString(System.identityHashCode(this)));
      result.append(" number=").append(getNumber());
      return result.append(" name=").append(name()).append('>').toString();
    }

    private final int value;

    NonFinalProtoEnum(int value) {
      this.value = value;
    }
  }

  // Enum with a custom toString() whose constant-specific subclass (ACC_ENUM | ACC_FINAL in javac
  // bytecode) defines its own int value field and getNumber() without overriding toString().
  public enum ConstantBodyGetNumberProtoEnum implements EnumLite {
    CONST_SUB_FOO {
      private final int value;

      {
        this.value = 99;
      }

      @Override
      public int getNumber() {
        return value;
      }
    },
    CONST_SUB_BAR {
      @Override
      public int getNumber() {
        return 100;
      }
    };

    @Override
    public String toString() {
      return "custom-super:" + name();
    }

    ConstantBodyGetNumberProtoEnum() {}
  }

  public static class ClosedMain {
    @NeverInline
    private static void printEnum(ClosedProtoEnum e) {
      System.out.println(e.getNumber());
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printEnum(ClosedProtoEnum.forNumber(1));
      printEnum(ClosedProtoEnum.forNumber(2));
      printEnum(ClosedProtoEnum.forNumber(3));
    }
  }

  public static class OpenMain {
    @NeverInline
    private static void printEnum(OpenProtoEnum e) {
      System.out.println(e.getNumber());
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printEnum(OpenProtoEnum.forNumber(0));
      printEnum(OpenProtoEnum.forNumber(1));
      System.out.println(OpenProtoEnum.UNRECOGNIZED.toString());
    }
  }

  public static class InheritedToStringMain {
    @NeverInline
    private static void printEnum(InheritedToStringProtoEnum e) {
      System.out.println(e.getNumber());
      System.out.println(e.toString());
      System.out.println(String.valueOf(e));
    }

    @NeverInline
    private static void printNullable(InheritedToStringProtoEnum e) {
      System.out.println(String.valueOf(e));
    }

    public static void main(String[] args) {
      printEnum(InheritedToStringProtoEnum.forNumber(11));
      printEnum(InheritedToStringProtoEnum.forNumber(22));
      printNullable(null);
    }
  }

  public static class InheritedToStringWithoutGetNumberMain {
    @NeverInline
    private static void printUnboxed(InheritedToStringProtoEnum e) {
      System.out.println(e.toString());
    }

    @NeverInline
    private static void printAsObject(Object o) {
      System.out.println(o.toString());
    }

    public static void main(String[] args) {
      printUnboxed(InheritedToStringProtoEnum.INHERITED_FOO);
      printUnboxed(InheritedToStringProtoEnum.INHERITED_BAR);
      printAsObject(EscapingInheritedToStringProtoEnum.ESCAPING_FOO);
      printAsObject(EscapingInheritedToStringProtoEnum.ESCAPING_BAR);
    }
  }

  public static class OpenInheritedToStringWithoutGetNumberMain {
    @NeverInline
    private static void printUnboxed(OpenInheritedToStringProtoEnum e) {
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printUnboxed(OpenInheritedToStringProtoEnum.OPEN_INHERITED_FOO);
      printUnboxed(OpenInheritedToStringProtoEnum.OPEN_INHERITED_BAR);
      printUnboxed(OpenInheritedToStringProtoEnum.UNRECOGNIZED);
    }
  }

  public static class ZeroBasedAndNameOnlyMain {
    @NeverInline
    private static void printZeroBased(ZeroBasedProtoEnum e) {
      System.out.println(e.toString());
      System.out.println(String.valueOf(e));
    }

    @NeverInline
    private static void printNullable(ZeroBasedProtoEnum e) {
      System.out.println(String.valueOf(e));
    }

    @NeverInline
    private static void printInTryCatchAndUnusedOutValue(ZeroBasedProtoEnum e) {
      try {
        String s = e.toString();
        System.out.println(s);
      } catch (RuntimeException ex) {
        System.out.println("npe1");
      }
      try {
        e.toString();
      } catch (NullPointerException ex) {
        System.out.println("npe2");
      }
    }

    @NeverInline
    private static void printNameOnly(NameOnlyProtoEnum e, String lookupName) {
      System.out.println(e.name());
      System.out.println(Enum.valueOf(NameOnlyProtoEnum.class, lookupName).ordinal());
    }

    public static void main(String[] args) {
      printZeroBased(ZeroBasedProtoEnum.ZERO_FOO);
      printZeroBased(ZeroBasedProtoEnum.ZERO_BAR);
      printNullable(null);
      printInTryCatchAndUnusedOutValue(ZeroBasedProtoEnum.ZERO_FOO);
      printInTryCatchAndUnusedOutValue(null);
      printNameOnly(NameOnlyProtoEnum.NAME_FOO, "NAME_BAR");
    }
  }

  public static class LargeProtoEnumMain {
    @NeverInline
    private static void printEnum(LargeProtoEnum e) {
      System.out.println(e.getNumber());
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printEnum(LargeProtoEnum.E0);
      printEnum(LargeProtoEnum.E199);
      printEnum(LargeProtoEnum.E399);
      System.out.println(LargeProtoEnum.values().length);
    }
  }

  public static class CustomMain {
    @NeverInline
    private static void printCustom(CustomToStringProtoEnum e) {
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printCustom(CustomToStringProtoEnum.CUSTOM_FOO);
      printCustom(CustomToStringProtoEnum.CUSTOM_BAR);
    }
  }

  public static class NonEnumLiteMain {
    @NeverInline
    private static void printEnum(NonEnumLiteEnum e) {
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printEnum(NonEnumLiteEnum.PLAIN_FOO);
      printEnum(NonEnumLiteEnum.PLAIN_BAR);
    }
  }

  public static class NonFinalMain {
    @NeverInline
    private static void printEnum(NonFinalProtoEnum e) {
      System.out.println(e.toString());
    }

    @NeverInline
    private static void printConstantBodyEnum(ConstantBodyGetNumberProtoEnum e) {
      System.out.println(e.getNumber());
      System.out.println(e.toString());
    }

    public static void main(String[] args) {
      printEnum(NonFinalProtoEnum.NON_FINAL_FOO);
      printEnum(NonFinalProtoEnum.NON_FINAL_BAR);
      printConstantBodyEnum(ConstantBodyGetNumberProtoEnum.CONST_SUB_FOO);
      printConstantBodyEnum(ConstantBodyGetNumberProtoEnum.CONST_SUB_BAR);
    }
  }
}
