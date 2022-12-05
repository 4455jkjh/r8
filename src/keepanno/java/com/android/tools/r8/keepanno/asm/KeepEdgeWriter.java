// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.annotations.KeepConstants;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Condition;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Item;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern.KeepFieldNameExactPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern.KeepMethodNameExactPattern;
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
    String ignoredArrayValueName = null;
    AnnotationVisitor arrayVisitor = visitor.visitArray(Edge.preconditions);
    preconditions.forEach(
        condition -> {
          AnnotationVisitor conditionVisitor =
              arrayVisitor.visitAnnotation(ignoredArrayValueName, Condition.DESCRIPTOR);
          writeItem(conditionVisitor, condition.getItemPattern());
        });
    arrayVisitor.visitEnd();
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
          writeItem(targetVisitor, target.getItem());
        });
    arrayVisitor.visitEnd();
  }

  private void writeItem(AnnotationVisitor itemVisitor, KeepItemPattern item) {
    if (item.isAny()) {
      throw new Unimplemented();
    }
    KeepQualifiedClassNamePattern namePattern = item.getClassNamePattern();
    if (namePattern.isExact()) {
      Type typeConstant = Type.getType(namePattern.getExactDescriptor());
      itemVisitor.visit(KeepConstants.Item.classConstant, typeConstant);
    } else {
      throw new Unimplemented();
    }
    if (!item.getExtendsPattern().isAny()) {
      throw new Unimplemented();
    }
    writeMember(item.getMemberPattern(), itemVisitor);
    itemVisitor.visitEnd();
  }

  private void writeMember(KeepMemberPattern memberPattern, AnnotationVisitor targetVisitor) {
    if (memberPattern.isNone()) {
      // Default is "no methods".
      return;
    }
    if (memberPattern.isAll()) {
      throw new Unimplemented();
    }
    if (memberPattern.isMethod()) {
      writeMethod(memberPattern.asMethod(), targetVisitor);
    } else if (memberPattern.isField()) {
      writeField(memberPattern.asField(), targetVisitor);
    } else {
      throw new KeepEdgeException("Unexpected member pattern: " + memberPattern);
    }
  }

  private void writeField(KeepFieldPattern field, AnnotationVisitor targetVisitor) {
    KeepFieldNameExactPattern exactFieldName = field.getNamePattern().asExact();
    if (exactFieldName != null) {
      targetVisitor.visit(Item.fieldName, exactFieldName.getName());
    } else {
      throw new Unimplemented();
    }
    if (!field.getAccessPattern().isAny()) {
      throw new Unimplemented();
    }
    if (!field.getTypePattern().isAny()) {
      throw new Unimplemented();
    }
  }

  private void writeMethod(KeepMethodPattern method, AnnotationVisitor targetVisitor) {
    KeepMethodNameExactPattern exactMethodName = method.getNamePattern().asExact();
    if (exactMethodName != null) {
      targetVisitor.visit(Item.methodName, exactMethodName.getName());
    } else {
      throw new Unimplemented();
    }
    if (!method.getAccessPattern().isAny()) {
      throw new Unimplemented();
    }
    if (!method.getReturnTypePattern().isAny()) {
      if (exactMethodName != null
          && (exactMethodName.getName().equals("<init>")
              || exactMethodName.getName().equals("<clinit>"))
          && method.getReturnTypePattern().isVoid()) {
        // constructors have implicit void return.
      } else {
        throw new Unimplemented();
      }
    }
    if (!method.getParametersPattern().isAny()) {
      throw new Unimplemented();
    }
  }
}
