// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;

/**
 * Utility to determine if a given IR code object type checks.
 *
 * <p>NOTE: This is incomplete! The primary motivation for type checking the code is to be able to
 * prune code that does not type check in R8. Such code does not verify, and we therefore assume
 * that it is dead.
 *
 * <p>Pruning code that does not verify is necessary in order to be able to assert that the types
 * are sound using {@link Instruction#verifyTypes(AppInfo, GraphLense)}.
 */
public class TypeChecker {

  private final AppView<? extends AppInfo> appView;

  public TypeChecker(AppView<? extends AppInfo> appView) {
    this.appView = appView;
  }

  public boolean check(IRCode code) {
    InstructionIterator instructionIterator = code.instructionIterator();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      if (instruction.isInstancePut()) {
        if (!check(instruction.asInstancePut())) {
          return false;
        }
      } else if (instruction.isStaticPut()) {
        if (!check(instruction.asStaticPut())) {
          return false;
        }
      } else if (instruction.isThrow()) {
        if (!check(instruction.asThrow())) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean check(InstancePut instruction) {
    return checkFieldPut(instruction);
  }

  public boolean check(StaticPut instruction) {
    return checkFieldPut(instruction);
  }

  private boolean checkFieldPut(FieldInstruction instruction) {
    assert instruction.isFieldPut();
    Value value =
        instruction.isInstancePut()
            ? instruction.asInstancePut().value()
            : instruction.asStaticPut().inValue();
    TypeLatticeElement valueType = value.getTypeLattice();
    TypeLatticeElement fieldType =
        TypeLatticeElement.fromDexType(
            instruction.getField().type, valueType.nullability(), appView);
    if (isSubtypeOf(valueType, fieldType)) {
      return true;
    }

    if (fieldType.isClassType() && valueType.isReference()) {
      // Interface types are treated like Object according to the JVM spec.
      // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.10.1.2-100
      DexClass clazz = appView.definitionFor(instruction.getField().type);
      return clazz != null && clazz.isInterface();
    }

    return false;
  }

  public boolean check(Throw instruction) {
    TypeLatticeElement valueType = instruction.exception().getTypeLattice();
    TypeLatticeElement throwableType =
        TypeLatticeElement.fromDexType(
            appView.dexItemFactory().throwableType, valueType.nullability(), appView);
    return isSubtypeOf(valueType, throwableType);
  }

  private boolean isSubtypeOf(
      TypeLatticeElement expectedSubtype, TypeLatticeElement expectedSupertype) {
    return (expectedSubtype.isNullType() && expectedSupertype.isReference())
        || expectedSubtype.lessThanOrEqual(expectedSupertype, appView)
        || expectedSubtype.isBasedOnMissingClass(appView);
  }
}
