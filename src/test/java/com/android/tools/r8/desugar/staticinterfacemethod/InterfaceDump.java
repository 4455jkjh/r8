// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generated by {@link jdk.internal.org.objectweb.asm.util.ASMifier} from the following source:
 * <pre>
 *   interface Interface {
 *
 *   }
 * </pre>
 */
public class InterfaceDump implements Opcodes {

  public static byte[] dump() {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_8, ACC_ABSTRACT + ACC_INTERFACE, "Interface", null, "java/lang/Object", null);

    cw.visitEnd();

    return cw.toByteArray();
  }
}

