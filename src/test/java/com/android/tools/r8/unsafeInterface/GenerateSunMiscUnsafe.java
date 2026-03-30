// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.unsafeInterface;

import static org.junit.Assert.assertArrayEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.SunMiscUnsafeResourceProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

@RunWith(Parameterized.class)
public class GenerateSunMiscUnsafe extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void verifyBytes() {
    byte[] generatedBytes = getUnsafeClassBytes();
    byte[] bytes = SunMiscUnsafeResourceProvider.sunMiscUnsafeBytes();
    assertArrayEquals(
        "Did you forget to run the main method in this class after making changes to"
            + " sun.misc.Unsafe?",
        generatedBytes,
        bytes);
  }

  private static void publicNativeMethod(ClassWriter cw, String name, String descriptor) {
    cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, name, descriptor, null, null)
        .visitEnd();
  }

  private static byte[] getUnsafeClassBytes() {
    // If this assert fails then check these things before updating the assert:
    //   * Check if libcore/ojluni/src/main/java/sun/misc/Unsafe.java has new public methods,
    //     including new overloads.
    //     * If so, add the new methods here
    //       (and to SunMiscUnsafeApiTest but it will fail if you don't).
    //   * Verify that no existing methods have been removed.
    assert AndroidApiLevel.LATEST.isEqualTo(AndroidApiLevel.BAKLAVA_1);
    // Public signatures of sun.misc.Unsafe.
    ClassWriter cw = new ClassWriter(0);

    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
        "sun/misc/Unsafe",
        null,
        "java/lang/Object",
        null);

    cw.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "INVALID_FIELD_OFFSET",
            "I",
            null,
            0)
        .visitEnd();

    MethodVisitor mv;
    publicNativeMethod(cw, "addressSize", "()I");
    publicNativeMethod(cw, "allocateInstance", "(Ljava/lang/Class;)Ljava/lang/Object;");
    publicNativeMethod(cw, "allocateMemory", "(J)J");

    mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "arrayBaseOffset", "(Ljava/lang/Class;)I", null, null);
    returnZeroAndEnd(mv);

    mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "arrayIndexScale", "(Ljava/lang/Class;)I", null, null);
    returnZeroAndEnd(mv);

    publicNativeMethod(cw, "compareAndSwapInt", "(Ljava/lang/Object;JII)Z");
    publicNativeMethod(cw, "compareAndSwapLong", "(Ljava/lang/Object;JJJ)Z");
    publicNativeMethod(
        cw, "compareAndSwapObject", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z");
    publicNativeMethod(cw, "copyMemory", "(JJJ)V");
    publicNativeMethod(cw, "copyMemoryFromPrimitiveArray", "(Ljava/lang/Object;JJJ)V");
    publicNativeMethod(cw, "copyMemoryToPrimitiveArray", "(JLjava/lang/Object;JJ)V");

    publicNativeMethod(cw, "freeMemory", "(J)V");
    publicNativeMethod(cw, "fullFence", "()V");

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            "getAndAddInt",
            "(Ljava/lang/Object;JI)I",
            null,
            null);
    returnZeroAndEnd(mv);

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            "getAndAddLong",
            "(Ljava/lang/Object;JJ)J",
            null,
            null);
    returnZeroLongAndEnd(mv);

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            "getAndSetInt",
            "(Ljava/lang/Object;JI)I",
            null,
            null);
    returnZeroAndEnd(mv);

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            "getAndSetLong",
            "(Ljava/lang/Object;JJ)J",
            null,
            null);
    returnZeroLongAndEnd(mv);

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            "getAndSetObject",
            "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;",
            null,
            null);
    returnNullAndEnd(mv);

    publicNativeMethod(cw, "getBoolean", "(Ljava/lang/Object;J)Z");
    publicNativeMethod(cw, "getByte", "(Ljava/lang/Object;J)B");
    publicNativeMethod(cw, "getByte", "(J)B");
    publicNativeMethod(cw, "getChar", "(Ljava/lang/Object;J)C");
    publicNativeMethod(cw, "getChar", "(J)C");
    publicNativeMethod(cw, "getDouble", "(Ljava/lang/Object;J)D");
    publicNativeMethod(cw, "getDouble", "(J)D");
    publicNativeMethod(cw, "getFloat", "(Ljava/lang/Object;J)F");
    publicNativeMethod(cw, "getFloat", "(J)F");
    publicNativeMethod(cw, "getInt", "(Ljava/lang/Object;J)I");
    publicNativeMethod(cw, "getInt", "(J)I");
    publicNativeMethod(cw, "getIntVolatile", "(Ljava/lang/Object;J)I");
    publicNativeMethod(cw, "getLong", "(Ljava/lang/Object;J)J");
    publicNativeMethod(cw, "getLong", "(J)J");
    publicNativeMethod(cw, "getLongVolatile", "(Ljava/lang/Object;J)J");
    publicNativeMethod(cw, "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;");
    publicNativeMethod(cw, "getObjectVolatile", "(Ljava/lang/Object;J)Ljava/lang/Object;");
    publicNativeMethod(cw, "getShort", "(Ljava/lang/Object;J)S");
    publicNativeMethod(cw, "getShort", "(J)S");

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getUnsafe",
            "()Lsun/misc/Unsafe;",
            null,
            null);
    returnNullAndEnd(mv);

    publicNativeMethod(cw, "loadFence", "()V");

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC, "objectFieldOffset", "(Ljava/lang/reflect/Field;)J", null, null);
    returnZeroLongAndEnd(mv);

    publicNativeMethod(cw, "pageSize", "()I");
    publicNativeMethod(cw, "park", "(ZJ)V");
    publicNativeMethod(cw, "putBoolean", "(Ljava/lang/Object;JZ)V");
    publicNativeMethod(cw, "putByte", "(Ljava/lang/Object;JB)V");
    publicNativeMethod(cw, "putByte", "(JB)V");
    publicNativeMethod(cw, "putChar", "(Ljava/lang/Object;JC)V");
    publicNativeMethod(cw, "putChar", "(JC)V");
    publicNativeMethod(cw, "putDouble", "(Ljava/lang/Object;JD)V");
    publicNativeMethod(cw, "putDouble", "(JD)V");
    publicNativeMethod(cw, "putFloat", "(Ljava/lang/Object;JF)V");
    publicNativeMethod(cw, "putFloat", "(JF)V");
    publicNativeMethod(cw, "putInt", "(Ljava/lang/Object;JI)V");
    publicNativeMethod(cw, "putInt", "(JI)V");
    publicNativeMethod(cw, "putIntVolatile", "(Ljava/lang/Object;JI)V");
    publicNativeMethod(cw, "putLong", "(Ljava/lang/Object;JJ)V");
    publicNativeMethod(cw, "putLong", "(JJ)V");
    publicNativeMethod(cw, "putLongVolatile", "(Ljava/lang/Object;JJ)V");
    publicNativeMethod(cw, "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V");
    publicNativeMethod(cw, "putObjectVolatile", "(Ljava/lang/Object;JLjava/lang/Object;)V");
    publicNativeMethod(cw, "putOrderedInt", "(Ljava/lang/Object;JI)V");
    publicNativeMethod(cw, "putOrderedLong", "(Ljava/lang/Object;JJ)V");
    publicNativeMethod(cw, "putOrderedObject", "(Ljava/lang/Object;JLjava/lang/Object;)V");
    publicNativeMethod(cw, "putShort", "(Ljava/lang/Object;JS)V");
    publicNativeMethod(cw, "putShort", "(JS)V");
    publicNativeMethod(cw, "setMemory", "(JJB)V");
    publicNativeMethod(cw, "storeFence", "()V");
    publicNativeMethod(cw, "unpark", "(Ljava/lang/Object;)V");

    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void returnZeroLongAndEnd(MethodVisitor mv) {
    mv.visitCode();
    mv.visitInsn(Opcodes.LCONST_0);
    mv.visitInsn(Opcodes.LRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void returnZeroAndEnd(MethodVisitor mv) {
    mv.visitCode();
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void returnNullAndEnd(MethodVisitor mv) {
    mv.visitCode();
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static String readableBytecode(byte[] bytes) {
    ClassReader reader = new ClassReader(bytes);
    StringWriter writer = new StringWriter();
    reader.accept(new TraceClassVisitor(new PrintWriter(writer)), 0);
    return writer.toString();
  }

  public static void main(String[] args) {
    byte[] bytes = getUnsafeClassBytes();
    String encodedClass = Base64.getEncoder().encodeToString(bytes);

    // Print readable bytecode.
    System.out.println(readableBytecode(bytes));

    // Print source code.
    System.out.println("java.util.Base64.getDecoder().decode(\"" + encodedClass + "\")");
  }
}
