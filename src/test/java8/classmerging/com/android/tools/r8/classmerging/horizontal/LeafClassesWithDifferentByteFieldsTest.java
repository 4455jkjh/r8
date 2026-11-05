// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

// IntAnd<k>Bytes has one int field and k byte fields. IntAnd2Bytes, IntAnd3Bytes and IntAnd4Bytes
// have an instance size of 16 bytes in ART, and IntAnd5Bytes and IntAnd6Bytes have an instance size
// of 24 bytes. Only classes with the same instance size are merged. The merged fields are the union
// of the fields of the classes, e.g., merging IntAnd2Bytes and IntAnd3Bytes gives one int field and
// 3 byte fields.
public class LeafClassesWithDifferentByteFieldsTest extends HorizontalClassMergingTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withoutCollapsedDexRuntimes().build();
  }

  public LeafClassesWithDifferentByteFieldsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8WithClassId() throws Exception {
    // All classes define print(), so the merged class needs a $r8$classId field. Merging
    // IntAnd2Bytes and IntAnd3Bytes gives 4 + 3 + 1 ($r8$classId) = 8 bytes of fields, but adding
    // IntAnd4Bytes would increase the instance size to 24 bytes.
    test(
        true,
        inspector ->
            inspector
                .assertIsCompleteMergeGroup(IntAnd2Bytes.class, IntAnd3Bytes.class)
                .assertIsCompleteMergeGroup(IntAnd5Bytes.class, IntAnd6Bytes.class)
                .assertNoOtherClassesMerged());
  }

  @Test
  public void testR8WithoutClassId() throws Exception {
    // The print() methods are renamed apart, so the merged class does not need a $r8$classId field.
    // Merging IntAnd2Bytes, IntAnd3Bytes and IntAnd4Bytes gives 4 + 4 = 8 bytes of fields.
    test(
        false,
        inspector ->
            inspector
                .assertIsCompleteMergeGroup(
                    IntAnd2Bytes.class, IntAnd3Bytes.class, IntAnd4Bytes.class)
                .assertIsCompleteMergeGroup(IntAnd5Bytes.class, IntAnd6Bytes.class)
                .assertNoOtherClassesMerged());
  }

  private void test(
      boolean requiresClassId, ThrowableConsumer<HorizontallyMergedClassesInspector> inspector)
      throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(
            transformPrint(IntAnd2Bytes.class, requiresClassId),
            transformPrint(IntAnd3Bytes.class, requiresClassId),
            transformPrint(IntAnd4Bytes.class, requiresClassId),
            transformPrint(IntAnd5Bytes.class, requiresClassId),
            transformPrint(IntAnd6Bytes.class, requiresClassId),
            transformPrint(Main.class, requiresClassId))
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(inspector)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class, Boolean.toString(isArt8OrAbove()))
        .assertSuccessWithOutputLines("6", "10", "15", "21", "28");
  }

  private boolean isArt8OrAbove() {
    return parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0);
  }

  // Unless the merged class needs a $r8$classId field, renames print() to a different name in each
  // class.
  private byte[] transformPrint(Class<?> clazz, boolean requiresClassId) throws IOException {
    if (requiresClassId) {
      return transformer(clazz).transform();
    }
    return transformer(clazz)
        .renameMethod(MethodPredicate.onName("print"), "print" + clazz.getSimpleName())
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) ->
                visitor.visitMethodInsn(
                    opcode,
                    owner,
                    name.equals("print")
                        ? "print" + owner.substring(owner.lastIndexOf('$') + 1)
                        : name,
                    descriptor,
                    isInterface))
        .transform();
  }

  @NeverClassInline
  public static class IntAnd2Bytes {

    private final int i;
    private final byte b1;
    private final byte b2;

    public IntAnd2Bytes(int i) {
      this.i = i;
      this.b1 = (byte) (i + 1);
      this.b2 = (byte) (i + 2);
    }

    @NeverInline
    public void print() {
      System.out.println(i + b1 + b2);
    }
  }

  @NeverClassInline
  public static class IntAnd3Bytes {

    private final int i;
    private final byte b1;
    private final byte b2;
    private final byte b3;

    public IntAnd3Bytes(int i) {
      this.i = i;
      this.b1 = (byte) (i + 1);
      this.b2 = (byte) (i + 2);
      this.b3 = (byte) (i + 3);
    }

    @NeverInline
    public void print() {
      System.out.println(i + b1 + b2 + b3);
    }
  }

  @NeverClassInline
  public static class IntAnd4Bytes {

    private final int i;
    private final byte b1;
    private final byte b2;
    private final byte b3;
    private final byte b4;

    public IntAnd4Bytes(int i) {
      this.i = i;
      this.b1 = (byte) (i + 1);
      this.b2 = (byte) (i + 2);
      this.b3 = (byte) (i + 3);
      this.b4 = (byte) (i + 4);
    }

    @NeverInline
    public void print() {
      System.out.println(i + b1 + b2 + b3 + b4);
    }
  }

  @NeverClassInline
  public static class IntAnd5Bytes {

    private final int i;
    private final byte b1;
    private final byte b2;
    private final byte b3;
    private final byte b4;
    private final byte b5;

    public IntAnd5Bytes(int i) {
      this.i = i;
      this.b1 = (byte) (i + 1);
      this.b2 = (byte) (i + 2);
      this.b3 = (byte) (i + 3);
      this.b4 = (byte) (i + 4);
      this.b5 = (byte) (i + 5);
    }

    @NeverInline
    public void print() {
      System.out.println(i + b1 + b2 + b3 + b4 + b5);
    }
  }

  @NeverClassInline
  public static class IntAnd6Bytes {

    private final int i;
    private final byte b1;
    private final byte b2;
    private final byte b3;
    private final byte b4;
    private final byte b5;
    private final byte b6;

    public IntAnd6Bytes(int i) {
      this.i = i;
      this.b1 = (byte) (i + 1);
      this.b2 = (byte) (i + 2);
      this.b3 = (byte) (i + 3);
      this.b4 = (byte) (i + 4);
      this.b5 = (byte) (i + 5);
      this.b6 = (byte) (i + 6);
    }

    @NeverInline
    public void print() {
      System.out.println(i + b1 + b2 + b3 + b4 + b5 + b6);
    }
  }

  public static class Main {

    @NeverInline
    private static int getObjectSize(Object o) throws Exception {
      Field objectSizeField = Class.class.getDeclaredField("objectSizeAllocFastPath");
      objectSizeField.setAccessible(true);
      return objectSizeField.getInt(o.getClass());
    }

    @NeverInline
    private static void checkObjectSize(Object o, int expectedObjectSize) throws Exception {
      int objectSize = getObjectSize(o);
      if (objectSize != expectedObjectSize) {
        throw new AssertionError(
            "Expected objectSize " + expectedObjectSize + ", got " + objectSize);
      }
    }

    public static void main(String[] args) throws Exception {
      IntAnd2Bytes o2 = new IntAnd2Bytes(args.length);
      o2.print();
      IntAnd3Bytes o3 = new IntAnd3Bytes(args.length);
      o3.print();
      IntAnd4Bytes o4 = new IntAnd4Bytes(args.length);
      o4.print();
      IntAnd5Bytes o5 = new IntAnd5Bytes(args.length);
      o5.print();
      IntAnd6Bytes o6 = new IntAnd6Bytes(args.length);
      o6.print();

      if (Boolean.parseBoolean(args[0])) {
        checkObjectSize(o2, 16);
        checkObjectSize(o3, 16);
        checkObjectSize(o4, 16);
        checkObjectSize(o5, 24);
        checkObjectSize(o6, 24);
      }
    }
  }
}
