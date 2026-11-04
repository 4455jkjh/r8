// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.base.Suppliers;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Tests unboxing of enums where the $VALUES array is created in the synthetic {@code $values()}
 * helper emitted by javac since JDK 15, when the helper is too large to be inlined into the class
 * initializer.
 */
@RunWith(Parameterized.class)
public class LargeEnumValuesHelperUnboxingTest extends EnumUnboxingTestBase implements Opcodes {

  private static final int ENUM_CONSTANT_COUNT = 400;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("1000", "1199", "1399", "400");

  private enum ValuesHelperVariant {
    JAVAC,
    // One enum constant instantiates an anonymous subclass overriding getNumber().
    CONSTANT_SUBCLASS,
    // The helper has an additional side effect.
    SIDE_EFFECT,
    // The helper stores the first two constants at each other's index.
    OUT_OF_ORDER,
    // The helper stores two constants at the same index.
    DUPLICATE_INDEX,
    // The helper stores a static field that is not an enum constant field.
    NON_ENUM_FIELD,
    // The values() method also calls the helper, so it has two callers.
    SECOND_CALLER,
    // The class initializer calls the helper before all constants are assigned.
    CALLED_BEFORE_ALL_CONSTANTS_ASSIGNED,
    // The helper contains a try-catch handler.
    CATCH_HANDLER,
    // The $VALUES field is assigned in an intermediate helper called from <clinit>.
    CALLED_FROM_INTERMEDIATE_HELPER
  }

  private static final BiFunction<Integer, ValuesHelperVariant, byte[]> LARGE_ENUM_CLASS_DATA =
      memoizeBiFunction(LargeEnumValuesHelperUnboxingTest::generateLargeEnumClassFileData);
  private static final Supplier<byte[]> LARGE_ENUM_CONSTANT_SUBCLASS_DATA =
      Suppliers.memoize(LargeEnumValuesHelperUnboxingTest::generateLargeEnumConstantSubclassData);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testLargeEnum() throws Exception {
    testForR8WithLargeEnum(ValuesHelperVariant.JAVAC)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(LargeEnumValuesHelperUnboxingTest::assertLargeEnumAndValuesHelperAreAbsent);
  }

  @Test
  public void testLargeEnumStudioKeepRules() throws Exception {
    testForR8WithLargeEnum(ValuesHelperVariant.JAVAC)
        .addKeepRules(EnumKeepRules.STUDIO.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(LargeEnumValuesHelperUnboxingTest::assertLargeEnumAndValuesHelperAreAbsent);
  }

  @Test
  public void testSmallEnum() throws Exception {
    testForR8(parameters)
        .addProgramClasses(SmallEnum.class, SmallEnumMain.class)
        .addKeepMainRule(SmallEnumMain.class)
        .enableInliningAnnotations()
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(SmallEnum.class))
        .run(parameters.getRuntime(), SmallEnumMain.class)
        .assertSuccessWithOutputLines("1", "2", "2");
  }

  @Test
  public void testSmallEnumWithoutInliningValuesHelper() throws Exception {
    // Prevents $values() on a 3-constant enum from being inlined so DEX exercises the
    // filled-new-array path directly.
    testForR8(parameters)
        .addProgramClasses(SmallHelperMain.class)
        .addProgramClassFileData(LARGE_ENUM_CLASS_DATA.apply(3, ValuesHelperVariant.JAVAC))
        .addKeepMainRule(SmallHelperMain.class)
        .addKeepRules(
            "-neverinline class " + LargeEnum.class.getTypeName() + " { static *** $values(); }")
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), SmallHelperMain.class)
        .assertSuccessWithOutputLines("1000", "1001", "1002", "3")
        .inspect(LargeEnumValuesHelperUnboxingTest::assertLargeEnumAndValuesHelperAreAbsent);
  }

  @Test
  public void testConstantSpecificSubclassWithValuesHelper() throws Exception {
    testForR8(parameters)
        .addProgramClasses(SmallHelperMain.class)
        .addProgramClassFileData(
            LARGE_ENUM_CLASS_DATA.apply(3, ValuesHelperVariant.CONSTANT_SUBCLASS),
            LARGE_ENUM_CONSTANT_SUBCLASS_DATA.get())
        .addKeepMainRule(SmallHelperMain.class)
        .addKeepRules(
            "-neverinline class " + LargeEnum.class.getTypeName() + " { static *** $values(); }")
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), SmallHelperMain.class)
        .assertSuccessWithOutputLines("1000", "2001", "1002", "3")
        .inspect(LargeEnumValuesHelperUnboxingTest::assertLargeEnumAndValuesHelperAreAbsent);
  }

  @Test
  public void testClinitHelperEnvironmentDependenceNotPostponed() throws Exception {
    testForR8(parameters)
        .addProgramClasses(
            EnvState.class,
            EnvBox.class,
            EnvHolder1D.class,
            EnvHolder2D.class,
            EnvHolderMultiBlock.class,
            EnvHolderMultiHopBlock.class,
            EnvHolderInstancePut.class,
            EnvHolder2DStoreOuterFirst.class,
            ClinitEnvMain.class)
        .addKeepMainRule(ClinitEnvMain.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), ClinitEnvMain.class)
        .assertSuccessWithOutputLines("11", "12", "13", "14", "15", "16");
  }

  @Test
  public void testSideEffect() throws Exception {
    testNotUnboxed(ValuesHelperVariant.SIDE_EFFECT);
  }

  @Test
  public void testOutOfOrder() throws Exception {
    testNotUnboxed(ValuesHelperVariant.OUT_OF_ORDER);
  }

  @Test
  public void testDuplicateIndex() throws Exception {
    testNotUnboxed(ValuesHelperVariant.DUPLICATE_INDEX);
  }

  @Test
  public void testNonEnumField() throws Exception {
    testNotUnboxed(ValuesHelperVariant.NON_ENUM_FIELD);
  }

  @Test
  public void testSecondCaller() throws Exception {
    testNotUnboxed(ValuesHelperVariant.SECOND_CALLER);
  }

  @Test
  public void testKeptHelper() throws Exception {
    testForR8WithLargeEnum(ValuesHelperVariant.JAVAC)
        .addKeepRules(
            "-keepclassmembers class "
                + LargeEnum.class.getTypeName()
                + " { static *** $values(); }")
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(inspector -> assertValuesHelperIsPresent(inspector.clazz(LargeEnum.class)));
  }

  @Test
  public void testCalledBeforeAllConstantsAssigned() throws Exception {
    testNotUnboxed(ValuesHelperVariant.CALLED_BEFORE_ALL_CONSTANTS_ASSIGNED);
  }

  @Test
  public void testCatchHandlerInHelper() throws Exception {
    testNotUnboxed(ValuesHelperVariant.CATCH_HANDLER);
  }

  @Test
  public void testCalledFromIntermediateHelper() throws Exception {
    testForR8WithLargeEnum(ValuesHelperVariant.CALLED_FROM_INTERMEDIATE_HELPER)
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              ClassSubject largeEnum = inspector.clazz(LargeEnum.class);
              assertValuesHelperIsPresent(largeEnum);
              assertThat(largeEnum.uniqueMethodWithOriginalName("initValues"), isPresent());
            });
  }

  private void testNotUnboxed(ValuesHelperVariant variant) throws Exception {
    testForR8WithLargeEnum(variant)
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(LargeEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(inspector -> assertValuesHelperIsPresent(inspector.clazz(LargeEnum.class)));
  }

  private static void assertLargeEnumAndValuesHelperAreAbsent(CodeInspector inspector) {
    assertThat(inspector.clazz(LargeEnum.class), isAbsent());
    // The $values() helper is not moved to the enum unboxing utility class either.
    inspector.forAllClasses(
        clazz -> assertThat(clazz.uniqueMethodWithOriginalName("$values"), isAbsent()));
  }

  // The helper is not inlined into the class initializer, since it exceeds the inlining budget.
  private static void assertValuesHelperIsPresent(ClassSubject largeEnum) {
    assertThat(largeEnum.uniqueMethodWithOriginalName("$values"), isPresent());
  }

  private R8TestBuilder<?, ?, ?> testForR8WithLargeEnum(ValuesHelperVariant variant)
      throws Exception {
    return testForR8(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(LARGE_ENUM_CLASS_DATA.apply(ENUM_CONSTANT_COUNT, variant))
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations();
  }

  /**
   * Generates bytecode for {@link LargeEnum} matching JDK 15+ javac output, where the creation of
   * the $VALUES array is factored into a {@code private static synthetic LargeEnum[] $values()}
   * helper that is called from {@code <clinit>}. With 400 constants the helper exceeds the inlining
   * budget of {@code <clinit>}.
   */
  private static byte[] generateLargeEnumClassFileData(int count, ValuesHelperVariant variant) {
    String internalName = binaryName(LargeEnum.class);
    String descriptor = descriptor(LargeEnum.class);
    String arrayDescriptor = "[" + descriptor;
    String valuesHelperDescriptor = "()" + arrayDescriptor;

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    int classAccess =
        ACC_PUBLIC
            | ACC_SUPER
            | ACC_ENUM
            | (variant == ValuesHelperVariant.CONSTANT_SUBCLASS ? 0 : ACC_FINAL);
    cw.visit(
        V1_8,
        classAccess,
        internalName,
        "Ljava/lang/Enum<" + descriptor + ">;",
        "java/lang/Enum",
        null);

    for (int i = 0; i < count; i++) {
      cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM, "E" + i, descriptor, null, null)
          .visitEnd();
    }
    if (variant == ValuesHelperVariant.NON_ENUM_FIELD) {
      cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "ALIAS", descriptor, null, null)
          .visitEnd();
    }
    int valuesAccess =
        ACC_PRIVATE
            | ACC_STATIC
            | ACC_SYNTHETIC
            | (variant == ValuesHelperVariant.CALLED_FROM_INTERMEDIATE_HELPER ? 0 : ACC_FINAL);
    cw.visitField(valuesAccess, "$VALUES", arrayDescriptor, null, null).visitEnd();
    cw.visitField(ACC_PRIVATE | ACC_FINAL, "number", "I", null, null).visitEnd();

    // public static LargeEnum[] values()
    {
      MethodVisitor mv =
          cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "()" + arrayDescriptor, null, null);
      mv.visitCode();
      if (variant == ValuesHelperVariant.SECOND_CALLER) {
        // if ($values().length != $VALUES.length) return null;
        Label notNull = new Label();
        mv.visitMethodInsn(INVOKESTATIC, internalName, "$values", valuesHelperDescriptor, false);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitFieldInsn(GETSTATIC, internalName, "$VALUES", arrayDescriptor);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitJumpInsn(IF_ICMPEQ, notNull);
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
        mv.visitLabel(notNull);
      }
      mv.visitFieldInsn(GETSTATIC, internalName, "$VALUES", arrayDescriptor);
      mv.visitMethodInsn(INVOKEVIRTUAL, arrayDescriptor, "clone", "()Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, arrayDescriptor);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // public static LargeEnum valueOf(String name)
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

    // LargeEnum(String name, int ordinal, int number)
    {
      int initAccess = variant == ValuesHelperVariant.CONSTANT_SUBCLASS ? 0 : ACC_PRIVATE;
      MethodVisitor mv =
          cw.visitMethod(initAccess, "<init>", "(Ljava/lang/String;II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitFieldInsn(PUTFIELD, internalName, "number", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // public int getNumber()
    {
      MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "getNumber", "()I", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, internalName, "number", "I");
      mv.visitInsn(IRETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // private static synthetic LargeEnum[] $values()
    {
      MethodVisitor mv =
          cw.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$values",
              valuesHelperDescriptor,
              null,
              null);
      if (variant == ValuesHelperVariant.CALLED_FROM_INTERMEDIATE_HELPER) {
        mv.visitAnnotation(descriptor(NeverInline.class), false).visitEnd();
      }
      mv.visitCode();
      Label tryStart = new Label();
      Label tryEnd = new Label();
      Label catchHandler = new Label();
      if (variant == ValuesHelperVariant.CATCH_HANDLER) {
        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/RuntimeException");
        mv.visitLabel(tryStart);
      }
      if (variant == ValuesHelperVariant.SIDE_EFFECT) {
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "yield", "()V", false);
      }
      mv.visitIntInsn(SIPUSH, count);
      mv.visitTypeInsn(ANEWARRAY, internalName);
      for (int i = 0; i < count; i++) {
        int index = i;
        String field = "E" + i;
        if (variant == ValuesHelperVariant.OUT_OF_ORDER && i < 2) {
          index = 1 - i;
        } else if (variant == ValuesHelperVariant.DUPLICATE_INDEX && i == 1) {
          index = 0;
        } else if (variant == ValuesHelperVariant.NON_ENUM_FIELD && i == 0) {
          field = "ALIAS";
        }
        mv.visitInsn(DUP);
        mv.visitIntInsn(SIPUSH, index);
        mv.visitFieldInsn(GETSTATIC, internalName, field, descriptor);
        mv.visitInsn(AASTORE);
      }
      if (variant == ValuesHelperVariant.CATCH_HANDLER) {
        mv.visitLabel(tryEnd);
      }
      mv.visitInsn(ARETURN);
      if (variant == ValuesHelperVariant.CATCH_HANDLER) {
        mv.visitLabel(catchHandler);
        mv.visitInsn(ATHROW);
      }
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    if (variant == ValuesHelperVariant.CALLED_FROM_INTERMEDIATE_HELPER) {
      // @NeverInline private static synthetic void initValues() { $VALUES = $values(); }
      MethodVisitor mv =
          cw.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "initValues", "()V", null, null);
      mv.visitAnnotation(descriptor(NeverInline.class), false).visitEnd();
      mv.visitCode();
      visitValuesHelperCall(mv, internalName, arrayDescriptor);
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // static <clinit>()
    {
      MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      mv.visitCode();
      String subclassInternalName = internalName + "$1";
      for (int i = 0; i < count; i++) {
        if (variant == ValuesHelperVariant.CALLED_BEFORE_ALL_CONSTANTS_ASSIGNED && i == count / 2) {
          visitValuesHelperCall(mv, internalName, arrayDescriptor);
        }
        String instanceOwner =
            variant == ValuesHelperVariant.CONSTANT_SUBCLASS && i == 1
                ? subclassInternalName
                : internalName;
        mv.visitTypeInsn(NEW, instanceOwner);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("E" + i);
        mv.visitIntInsn(SIPUSH, i);
        mv.visitIntInsn(SIPUSH, 1000 + i);
        mv.visitMethodInsn(
            INVOKESPECIAL, instanceOwner, "<init>", "(Ljava/lang/String;II)V", false);
        mv.visitFieldInsn(PUTSTATIC, internalName, "E" + i, descriptor);
      }
      if (variant == ValuesHelperVariant.NON_ENUM_FIELD) {
        mv.visitFieldInsn(GETSTATIC, internalName, "E0", descriptor);
        mv.visitFieldInsn(PUTSTATIC, internalName, "ALIAS", descriptor);
      }
      if (variant == ValuesHelperVariant.CALLED_FROM_INTERMEDIATE_HELPER) {
        mv.visitMethodInsn(INVOKESTATIC, internalName, "initValues", "()V", false);
      } else if (variant != ValuesHelperVariant.CALLED_BEFORE_ALL_CONSTANTS_ASSIGNED) {
        visitValuesHelperCall(mv, internalName, arrayDescriptor);
      }
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private static byte[] generateLargeEnumConstantSubclassData() {
    String enumInternalName = binaryName(LargeEnum.class);
    String subclassInternalName = enumInternalName + "$1";
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        V1_8, ACC_FINAL | ACC_SUPER | ACC_ENUM, subclassInternalName, null, enumInternalName, null);

    {
      MethodVisitor mv = cw.visitMethod(0, "<init>", "(Ljava/lang/String;II)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitMethodInsn(
          INVOKESPECIAL, enumInternalName, "<init>", "(Ljava/lang/String;II)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    {
      MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "getNumber", "()I", null, null);
      mv.visitCode();
      mv.visitIntInsn(SIPUSH, 2001);
      mv.visitInsn(IRETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void visitValuesHelperCall(
      MethodVisitor mv, String internalName, String arrayDescriptor) {
    mv.visitMethodInsn(INVOKESTATIC, internalName, "$values", "()" + arrayDescriptor, false);
    mv.visitFieldInsn(PUTSTATIC, internalName, "$VALUES", arrayDescriptor);
  }

  public static class EnvState {
    public static int env = 10;
  }

  public static class EnvBox {
    int value;
  }

  public static class EnvHolder1D {
    static final int[] VALUES = make1D();

    static void trigger() {}

    @NeverInline
    private static int[] make1D() {
      return new int[] {EnvState.env + 1};
    }
  }

  public static class EnvHolder2D {
    static final int[][] VALUES = make2D();

    static void trigger() {}

    @NeverInline
    private static int[][] make2D() {
      int[][] outer = new int[2][];
      int[] row = new int[2];
      row[0] = EnvState.env + 2;
      outer[0] = row;
      return outer;
    }
  }

  public static class EnvHolderMultiBlock {
    static int branchFlag;
    static final int[] VALUES;

    static {
      VALUES = makeMultiBlock();
      branchFlag = 1;
    }

    static void trigger() {}

    @NeverInline
    private static int[] makeMultiBlock() {
      int[] result = new int[2];
      if (branchFlag == 0) {
        result[0] = EnvState.env + 3;
      }
      return result;
    }
  }

  public static class EnvHolderMultiHopBlock {
    static int branchFlag1;
    static int branchFlag2;
    static final int[] VALUES;

    static {
      VALUES = makeMultiHopBlock();
      branchFlag1 = 1;
      branchFlag2 = 2;
    }

    static void trigger() {}

    @NeverInline
    private static int[] makeMultiHopBlock() {
      int[] result = new int[2];
      if (branchFlag1 == 0) {
        if (branchFlag2 == 0) {
          result[0] = EnvState.env + 4;
        }
      }
      return result;
    }
  }

  public static class EnvHolderInstancePut {
    static final EnvBox BOX = makeBox();

    static void trigger() {}

    @NeverInline
    private static EnvBox makeBox() {
      EnvBox box = new EnvBox();
      box.value = EnvState.env + 5;
      return box;
    }
  }

  public static class EnvHolder2DStoreOuterFirst {
    static final int[][] VALUES = make2DStoreOuterFirst();

    static void trigger() {}

    @NeverInline
    private static int[][] make2DStoreOuterFirst() {
      int[][] outer = new int[2][];
      int[] row = new int[2];
      outer[0] = row;
      row[0] = EnvState.env + 6;
      return outer;
    }
  }

  public static class ClinitEnvMain {
    public static void main(String[] args) {
      EnvHolder1D.trigger();
      EnvHolder2D.trigger();
      EnvHolderMultiBlock.trigger();
      EnvHolderMultiHopBlock.trigger();
      EnvHolderInstancePut.trigger();
      EnvHolder2DStoreOuterFirst.trigger();
      EnvState.env = 50;
      System.out.println(EnvHolder1D.VALUES[0]);
      System.out.println(EnvHolder2D.VALUES[0][0]);
      System.out.println(EnvHolderMultiBlock.VALUES[0]);
      System.out.println(EnvHolderMultiHopBlock.VALUES[0]);
      System.out.println(EnvHolderInstancePut.BOX.value);
      System.out.println(EnvHolder2DStoreOuterFirst.VALUES[0][0]);
    }
  }

  // Compile-time stub; replaced at test time by generateLargeEnumClassFileData().
  public enum LargeEnum {
    E0(1000),
    E1(1001),
    E2(1002),
    E199(1199),
    E399(1399);

    private final int number;

    LargeEnum(int number) {
      this.number = number;
    }

    public int getNumber() {
      return number;
    }
  }

  public static class Main {

    @NeverInline
    private static void printNumber(LargeEnum e) {
      System.out.println(e.getNumber());
    }

    public static void main(String[] args) {
      printNumber(LargeEnum.E0);
      printNumber(LargeEnum.E199);
      printNumber(LargeEnum.E399);
      System.out.println(LargeEnum.values().length);
    }
  }

  public static class SmallHelperMain {

    @NeverInline
    private static void printNumber(LargeEnum e) {
      System.out.println(e.getNumber());
    }

    public static void main(String[] args) {
      printNumber(LargeEnum.E0);
      printNumber(LargeEnum.E1);
      printNumber(LargeEnum.E2);
      System.out.println(LargeEnum.values().length);
    }
  }

  public enum SmallEnum {
    A,
    B,
    C
  }

  public static class SmallEnumMain {

    @NeverInline
    private static void printOrdinal(SmallEnum e) {
      System.out.println(e.ordinal());
    }

    public static void main(String[] args) {
      printOrdinal(SmallEnum.B);
      printOrdinal(SmallEnum.values()[2]);
      System.out.println(SmallEnum.values().length - 1);
    }
  }
}
