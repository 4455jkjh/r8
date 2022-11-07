// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMembersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.function.BiFunction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeWriter implements Opcodes {

  public static void writeEdge(KeepEdge edge, ClassVisitor visitor) {
    writeEdge(edge, visitor::visitAnnotation);
  }

  public static void writeEdge(
      KeepEdge edge, BiFunction<String, Boolean, AnnotationVisitor> getVisitor) {
    new KeepEdgeWriter().writeEdge(edge, getVisitor.apply(Edge.DESCRIPTOR, false));
  }

  private void writeEdge(KeepEdge edge, AnnotationVisitor visitor) {
    writePreconditions(visitor, edge.getPreconditions());
    writeConsequences(visitor, edge.getConsequences());
    visitor.visitEnd();
  }

  private void writePreconditions(AnnotationVisitor visitor, KeepPreconditions preconditions) {
    if (preconditions.isAlways()) {
      return;
    }
    throw new Unimplemented();
  }

  private void writeConsequences(AnnotationVisitor visitor, KeepConsequences consequences) {
    assert !consequences.isEmpty();
    String ignoredArrayValueName = null;
    AnnotationVisitor arrayVisitor = visitor.visitArray(Edge.consequences);
    consequences.forEachTarget(
        target -> {
          AnnotationVisitor targetVisitor =
              arrayVisitor.visitAnnotation(ignoredArrayValueName, Target.DESCRIPTOR);
          // No options imply keep all.
          if (!target.getOptions().isKeepAll()) {
            throw new Unimplemented();
          }
          KeepItemPattern item = target.getItem();
          if (item.isAny()) {
            throw new Unimplemented();
          }
          KeepQualifiedClassNamePattern namePattern = item.getClassNamePattern();
          if (namePattern.isExact()) {
            Type typeConstant = Type.getType(namePattern.getExactDescriptor());
            targetVisitor.visit(Target.classConstant, typeConstant);
          } else {
            throw new Unimplemented();
          }
          if (!item.getExtendsPattern().isAny()) {
            throw new Unimplemented();
          }
          writeMembers(item.getMembersPattern(), targetVisitor);
          targetVisitor.visitEnd();
        });
    arrayVisitor.visitEnd();
  }

  private void writeMembers(KeepMembersPattern membersPattern, AnnotationVisitor targetVisitor) {
    if (membersPattern.isNone()) {
      // Default is "no methods".
      return;
    }
    if (membersPattern.isAll()) {
      throw new Unimplemented();
    }
    KeepMethodPattern method = membersPattern.asMethod();
    KeepMethodNamePattern methodNamePattern = method.getNamePattern();
    methodNamePattern.match(
        () -> {
          throw new Unimplemented();
        },
        exactMethodName -> {
          targetVisitor.visit(Target.methodName, exactMethodName);
          return null;
        });
    if (!method.getAccessPattern().isAny()) {
      throw new Unimplemented();
    }
    if (!method.getReturnTypePattern().isAny()) {
      throw new Unimplemented();
    }
    if (!method.getParametersPattern().isAny()) {
      throw new Unimplemented();
    }
  }
}
