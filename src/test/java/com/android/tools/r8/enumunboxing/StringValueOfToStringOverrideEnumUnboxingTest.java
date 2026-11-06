// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class StringValueOfToStringOverrideEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "MyEnum1",
          "null",
          "true",
          "MyEnum2",
          "MyEnum1",
          "MyEnum2",
          "MyEnum1",
          "null",
          "true",
          "null",
          "false",
          "MyEnum2",
          "null",
          "true",
          "null",
          "false",
          "MyEnum1MyEnum2nullnull",
          "MyEnum1MyEnum2nullnull",
          "C",
          "overridden",
          "null",
          "super0",
          "subF",
          "null",
          "G",
          "1",
          "custom0",
          "custom1",
          "null",
          "singleOnly",
          "singleOnly",
          "null",
          "multi1",
          "multi1",
          "multi2",
          "null",
          "prefix:S1",
          "prefix:S2",
          "null",
          "start:ok0",
          "caught:boom",
          "start:null");

  private static final List<String> NULLABLE_EXPECTED =
      ImmutableList.of(
          "nv1",
          "null",
          "null",
          "no0",
          "null",
          "NS_A",
          "subNsB",
          "null",
          "sb:customSb0",
          "sb:null",
          "sbuf:customSbuf0",
          "sbuf:null",
          "nbr:nbrOk",
          "caught-re:boom",
          "caught-npe",
          "DEF_A",
          "null");

  private static final List<String> NON_CONST_NAME_EXPECTED =
      ImmutableList.of("nonConst0", "nonConst1", "null");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enumValueOptimization;

  @Parameter(2)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(enumValueOptimization);
    assumeTrue(enumKeepRules.isNone());
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), NullableMain.class)
        .assertSuccessWithOutputLines(NULLABLE_EXPECTED);
    testForJvm(parameters)
        .addProgramClasses(NonConstNameMain.class)
        .addProgramClassFileData(getTransformedNonConstNameEnum())
        .run(parameters.getRuntime(), NonConstNameMain.class)
        .assertSuccessWithOutputLines(NON_CONST_NAME_EXPECTED);
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(
                    MyEnum.class,
                    MySubclassedEnum.class,
                    MySuperAndSubOverrideEnum.class,
                    MyNameAndToStringEnum.class,
                    MySingleAbstractEnum.class,
                    MyMultiAbstractEnum.class,
                    MySuperDelegatingEnum.class,
                    MyThrowingEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "A",
            "NULL_STR",
            "false",
            canUseJavaUtilObjects(parameters) ? "B" : "MyEnum2",
            "A",
            "B",
            "A",
            "NULL_STR",
            "false",
            "null",
            "false",
            // Objects.toString() is backported below API level 19.
            canUseJavaUtilObjects(parameters) ? "B" : "MyEnum2",
            canUseJavaUtilObjects(parameters) ? "NULL_STR" : "null",
            canUseJavaUtilObjects(parameters) ? "false" : "true",
            "null",
            "false",
            "ABNULL_STRnull",
            "ABNULL_STRnull",
            "C",
            "D",
            "null",
            "E",
            "F",
            "null",
            "G",
            "1",
            "G",
            "H",
            "null",
            "singleOnly",
            "ONLY",
            "null",
            "multi1",
            "M1",
            "M2",
            "null",
            "S1",
            "S2",
            "null",
            "start:OK",
            "start:BOOM",
            "start:null");
  }

  @Test
  public void testNullableEnumOptOut() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(NullableMain.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(
                    MyNullableValueOfEnum.class,
                    MyNullableObjectsEnum.class,
                    MyNullableSubclassedEnum.class,
                    MyNullableStringBuilderEnum.class,
                    MyNullableStringBufferEnum.class,
                    MyNullableBuilderReceiverEnum.class,
                    MyDefaultToStringNullableEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .run(parameters.getRuntime(), NullableMain.class)
        .assertSuccessWithOutputLines(
            "NV_A",
            "NV_NULL_STR",
            "null",
            canUseJavaUtilObjects(parameters) ? "NO_A" : "no0",
            "null",
            "NS_A",
            "NS_B",
            "null",
            "sb:SB_A",
            "sb:null",
            "sbuf:SBUF_A",
            "sbuf:null",
            "nbr:NBR_OK",
            "nbr:NBR_BOOM",
            "caught-npe",
            "DEF_A",
            "null");
  }

  @Test
  public void testNonConstNameEnum() throws Exception {
    testForR8(parameters)
        .addProgramClasses(NonConstNameMain.class)
        .addProgramClassFileData(getTransformedNonConstNameEnum())
        .addKeepMainRule(NonConstNameMain.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(MyNonConstNameEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .run(parameters.getRuntime(), NonConstNameMain.class)
        .assertSuccessWithOutputLines(NON_CONST_NAME_EXPECTED);
  }

  private static byte[] getTransformedNonConstNameEnum() {
    return transformer(MyNonConstNameEnum.class)
        .transformMethodInsnInMethod(
            "<init>",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (owner.equals("java/lang/Enum") && name.equals("<init>")) {
                // Rewrite super(name, ordinal) to
                // super(String.valueOf((Object) name).trim(), ordinal).
                visitor.visitInsn(Opcodes.SWAP);
                visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/String",
                    "valueOf",
                    "(Ljava/lang/Object;)Ljava/lang/String;",
                    false);
                visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/String",
                    "trim",
                    "()Ljava/lang/String;",
                    false);
                visitor.visitInsn(Opcodes.SWAP);
              }
              visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  @NeverClassInline
  enum MyEnum {
    A(1),
    B(2),
    NULL_STR(3);

    private final int value;

    MyEnum(int value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value == 3 ? null : "MyEnum" + value;
    }
  }

  @NeverClassInline
  enum MySubclassedEnum {
    C,
    D {
      @Override
      public String toString() {
        return "overridden";
      }
    }
  }

  @NeverClassInline
  enum MySuperAndSubOverrideEnum {
    E,
    F {
      @Override
      public String toString() {
        return "subF";
      }
    };

    @Override
    public String toString() {
      return "super" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNameAndToStringEnum {
    G,
    H;

    @Override
    public String toString() {
      return "custom" + ordinal();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  enum MySingleAbstractEnum {
    ONLY {
      @Override
      public String toString() {
        return "singleOnly";
      }
    };

    @Override
    public abstract String toString();
  }

  @NeverClassInline
  enum MyMultiAbstractEnum {
    M1 {
      @Override
      public String toString() {
        return "multi1";
      }
    },
    M2 {
      @Override
      public String toString() {
        return "multi2";
      }
    };

    @Override
    public abstract String toString();
  }

  @NeverClassInline
  enum MySuperDelegatingEnum {
    S1,
    S2;

    @Override
    public String toString() {
      return "prefix:" + super.toString();
    }
  }

  @NeverClassInline
  enum MyThrowingEnum {
    OK,
    BOOM;

    @Override
    public String toString() {
      if (ordinal() == 1) {
        throw new RuntimeException("boom");
      }
      return "ok" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNonConstNameEnum {
    NC_A,
    NC_B;

    @NeverInline
    MyNonConstNameEnum() {}

    @Override
    public String toString() {
      return "nonConst" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNullableValueOfEnum {
    NV_A(1),
    NV_NULL_STR(2);

    private final int value;

    MyNullableValueOfEnum(int value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value == 2 ? null : "nv" + value;
    }
  }

  @NeverClassInline
  enum MyNullableObjectsEnum {
    NO_A;

    @Override
    public String toString() {
      return "no" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNullableSubclassedEnum {
    NS_A,
    NS_B {
      @Override
      public String toString() {
        return "subNsB";
      }
    }
  }

  @NeverClassInline
  enum MyNullableStringBuilderEnum {
    SB_A;

    @Override
    public String toString() {
      return "customSb" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNullableStringBufferEnum {
    SBUF_A;

    @Override
    public String toString() {
      return "customSbuf" + ordinal();
    }
  }

  @NeverClassInline
  enum MyNullableBuilderReceiverEnum {
    NBR_OK,
    NBR_BOOM;

    @Override
    public String toString() {
      if (ordinal() == 1) {
        throw new RuntimeException("boom");
      }
      return "nbrOk";
    }
  }

  @NeverClassInline
  enum MyDefaultToStringNullableEnum {
    DEF_A
  }

  static class NonConstNameMain {

    public static void main(String[] args) {
      System.out.println(getNonConstNameString(MyNonConstNameEnum.NC_A));
      System.out.println(getNonConstNameString(MyNonConstNameEnum.NC_B));
      System.out.println(getNonConstNameString(null));
    }

    @NeverInline
    private static String getNonConstNameString(MyNonConstNameEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }
  }

  static class NullableMain {

    public static void main(String[] args) {
      System.out.println(nullableValueOf(MyNullableValueOfEnum.NV_A));
      System.out.println(nullableValueOf(MyNullableValueOfEnum.NV_NULL_STR));
      System.out.println(nullableValueOf(null));
      System.out.println(nullableObjects(MyNullableObjectsEnum.NO_A));
      System.out.println(nullableObjects(null));
      System.out.println(nullableSubclassed(MyNullableSubclassedEnum.NS_A));
      System.out.println(nullableSubclassed(MyNullableSubclassedEnum.NS_B));
      System.out.println(nullableSubclassed(null));
      System.out.println(nullableStringBuilder(MyNullableStringBuilderEnum.SB_A));
      System.out.println(nullableStringBuilder(null));
      System.out.println(nullableStringBuffer(MyNullableStringBufferEnum.SBUF_A));
      System.out.println(nullableStringBuffer(null));
      printNullableBuilderReceiver(new StringBuilder("nbr:"), false);
      printNullableBuilderReceiver(new StringBuilder("nbr:"), true);
      printNullableBuilderReceiver(null, true);
      System.out.println(defaultNullable(MyDefaultToStringNullableEnum.DEF_A));
      System.out.println(defaultNullable(null));
    }

    @NeverInline
    private static String nullableValueOf(MyNullableValueOfEnum e) {
      return String.valueOf(e);
    }

    @NeverInline
    private static String nullableObjects(MyNullableObjectsEnum e) {
      return Objects.toString(e);
    }

    @NeverInline
    private static String nullableSubclassed(MyNullableSubclassedEnum e) {
      return String.valueOf(e);
    }

    @NeverInline
    private static String nullableStringBuilder(MyNullableStringBuilderEnum e) {
      return new StringBuilder("sb:").append(e).toString();
    }

    @NeverInline
    private static String nullableStringBuffer(MyNullableStringBufferEnum e) {
      return new StringBuffer("sbuf:").append(e).toString();
    }

    @NeverInline
    private static void printNullableBuilderReceiver(StringBuilder sb, boolean boom) {
      try {
        MyNullableBuilderReceiverEnum e =
            boom ? MyNullableBuilderReceiverEnum.NBR_BOOM : MyNullableBuilderReceiverEnum.NBR_OK;
        System.out.println(sb.append(e).toString());
      } catch (NullPointerException ex) {
        System.out.println("caught-npe");
      } catch (RuntimeException ex) {
        System.out.println("caught-re:" + ex.getMessage());
      }
    }

    @NeverInline
    private static String defaultNullable(MyDefaultToStringNullableEnum e) {
      return String.valueOf(e);
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(getStaticValueOf());
      String sStaticNullStr = getStaticNullStrValueOf();
      System.out.println(sStaticNullStr);
      System.out.println(Collections.singleton(sStaticNullStr).contains(null));
      System.out.println(getStaticObjectsToString());
      System.out.println(getStaticStringBuilder());
      System.out.println(getStaticStringBuffer());
      System.out.println(getString(MyEnum.A));
      String sNullStr = getString(MyEnum.NULL_STR);
      System.out.println(sNullStr);
      // Avoid == null, which R8 folds via libraryMethodsReturningNonNull on String.valueOf.
      System.out.println(Collections.singleton(sNullStr).contains(null));
      String sNull = getString(null);
      System.out.println(sNull);
      System.out.println(Collections.singleton(sNull).contains(null));
      System.out.println(getStringThroughObjects(MyEnum.B));
      String oNullStr = getStringThroughObjects(MyEnum.NULL_STR);
      System.out.println(oNullStr);
      System.out.println(oNullStr == null);
      String oNull = getStringThroughObjects(null);
      System.out.println(oNull);
      System.out.println(oNull == null);
      System.out.println(
          append(
                  append(append(append(new StringBuilder(), MyEnum.A), MyEnum.B), MyEnum.NULL_STR),
                  null)
              .toString());
      System.out.println(
          append(
                  append(append(append(new StringBuffer(), MyEnum.A), MyEnum.B), MyEnum.NULL_STR),
                  null)
              .toString());
      System.out.println(getSubclassedString(MySubclassedEnum.C));
      System.out.println(getSubclassedString(MySubclassedEnum.D));
      System.out.println(getSubclassedString(null));
      System.out.println(getSuperAndSubString(MySuperAndSubOverrideEnum.E));
      System.out.println(getSuperAndSubString(MySuperAndSubOverrideEnum.F));
      System.out.println(getSuperAndSubString(null));
      System.out.println(MyNameAndToStringEnum.G.name());
      System.out.println(MyNameAndToStringEnum.valueOf("H").ordinal());
      System.out.println(getNameAndToString(MyNameAndToStringEnum.G));
      System.out.println(getNameAndToString(MyNameAndToStringEnum.H));
      System.out.println(getNameAndToString(null));
      System.out.println(directSingleAbstract(MySingleAbstractEnum.ONLY));
      System.out.println(getSingleAbstractString(MySingleAbstractEnum.ONLY));
      System.out.println(getSingleAbstractString(null));
      System.out.println(directMultiAbstract(MyMultiAbstractEnum.M1));
      System.out.println(getMultiAbstractString(MyMultiAbstractEnum.M1));
      System.out.println(getMultiAbstractString(MyMultiAbstractEnum.M2));
      System.out.println(getMultiAbstractString(null));
      System.out.println(getSuperDelegatingString(MySuperDelegatingEnum.S1));
      System.out.println(getSuperDelegatingString(MySuperDelegatingEnum.S2));
      System.out.println(getSuperDelegatingString(null));
      printAppendThrowing(MyThrowingEnum.OK);
      printAppendThrowing(MyThrowingEnum.BOOM);
      printAppendThrowing(null);
    }

    @NeverInline
    private static String getStaticValueOf() {
      return String.valueOf(MyEnum.A);
    }

    @NeverInline
    private static String getStaticNullStrValueOf() {
      return String.valueOf(MyEnum.NULL_STR);
    }

    @NeverInline
    private static String getStaticObjectsToString() {
      return Objects.toString(MyEnum.B);
    }

    @NeverInline
    private static String getStaticStringBuilder() {
      return new StringBuilder().append(MyEnum.A).toString();
    }

    @NeverInline
    private static String getStaticStringBuffer() {
      return new StringBuffer().append(MyEnum.B).toString();
    }

    @NeverInline
    private static String getString(MyEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String getStringThroughObjects(MyEnum e) {
      return e != null ? Objects.toString(e) : "null";
    }

    @NeverInline
    private static StringBuilder append(StringBuilder sb, MyEnum e) {
      if (sb == null) {
        return null;
      }
      return e != null ? sb.append(e) : sb.append("null");
    }

    @NeverInline
    private static StringBuffer append(StringBuffer sb, MyEnum e) {
      if (sb == null) {
        return null;
      }
      return e != null ? sb.append(e) : sb.append("null");
    }

    @NeverInline
    private static String getSubclassedString(MySubclassedEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String getSuperAndSubString(MySuperAndSubOverrideEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String getNameAndToString(MyNameAndToStringEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String directSingleAbstract(MySingleAbstractEnum e) {
      return e.toString();
    }

    @NeverInline
    private static String getSingleAbstractString(MySingleAbstractEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String directMultiAbstract(MyMultiAbstractEnum e) {
      return e.toString();
    }

    @NeverInline
    private static String getMultiAbstractString(MyMultiAbstractEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static String getSuperDelegatingString(MySuperDelegatingEnum e) {
      return e != null ? String.valueOf(e) : "null";
    }

    @NeverInline
    private static void printAppendThrowing(MyThrowingEnum e) {
      try {
        StringBuilder sb = new StringBuilder("start:");
        if (e != null) {
          sb.append(e);
        } else {
          sb.append("null");
        }
        System.out.println(sb.toString());
      } catch (RuntimeException ex) {
        System.out.println("caught:" + ex.getMessage());
      }
    }
  }
}
