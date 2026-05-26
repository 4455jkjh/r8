// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.google.common.collect.Sets;
import java.util.Set;

public class KotlinValueClassUnboxBoxOptimizer extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  public KotlinValueClassUnboxBoxOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "KotlinValueClassUnboxBoxOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    assert appView.hasClassHierarchy();
    return code.metadata().mayHaveNewInstance();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    Set<Instruction> instructionsToRemove = Sets.newIdentityHashSet();
    for (BasicBlock block : code.getBlocks()) {
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isNewInstance()) {
          NewInstance newInstance = instruction.asNewInstance();
          if (newInstance != null
              && optimizeValueClassBoxing(
                  code, instructionIterator, newInstance, instructionsToRemove)) {
            changed = true;
          }
        } else if (instructionsToRemove.remove(instruction)) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
      }
    }
    if (!instructionsToRemove.isEmpty()) {
      instructionsToRemove.forEach(Instruction::remove);
    }
    if (changed) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private boolean optimizeValueClassBoxing(
      IRCode code,
      InstructionListIterator instructionIterator,
      NewInstance newInstance,
      Set<Instruction> instructionsToRemove) {
    if (!isKotlinValueClass(newInstance.getType())) {
      return false;
    }

    InvokeDirect constructorInvoke = newInstance.getUniqueConstructorInvoke(dexItemFactory);
    if (constructorInvoke == null) {
      return false;
    }

    InstanceGet instanceGet;
    if (constructorInvoke.arguments().size() == 1) {
      if (constructorInvoke
          .getInvokedMethod()
          .isNotIdenticalTo(dexItemFactory.objectMembers.constructor)) {
        return false;
      }

      InstancePut instancePut = constructorInvoke.getNext().asInstancePut();
      if (instancePut == null || instancePut.object() != newInstance.outValue()) {
        return false;
      }

      Value inlinedConstructorArgument = instancePut.value().getAliasedValue();
      instanceGet = checkInstanceGet(newInstance, inlinedConstructorArgument);
      if (instanceGet == null) {
        return false;
      }

      // Check if the instance-put assigns the field.
      if (instancePut.getField().isNotIdenticalTo(instanceGet.getField())) {
        return false;
      }
    } else if (constructorInvoke.arguments().size() == 2) {
      Value constructorArgument = constructorInvoke.getLastArgument().getAliasedValue();
      instanceGet = checkInstanceGet(newInstance, constructorArgument);
      if (instanceGet == null) {
        return false;
      }

      // Check if the constructor assigns the field and nothing else.
      ProgramField resolvedField =
          instanceGet.resolveField(appView(), code.context()).getProgramField();
      ProgramMethod resolvedMethod =
          constructorInvoke.resolveMethod(appView, code.context()).getResolvedProgramMethod();
      if (resolvedField != null && resolvedMethod != null) {
        InstanceInitializerInfo instanceInitializerInfo =
            resolvedMethod.getOptimizationInfo().getInstanceInitializerInfo(constructorInvoke);
        InstanceFieldInitializationInfo fieldInitializationInfo =
            instanceInitializerInfo.fieldInitializationInfos().get(resolvedField);
        if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()
            || !fieldInitializationInfo.isArgumentInitializationInfo()
            || fieldInitializationInfo.asArgumentInitializationInfo().getArgumentIndex() != 1) {
          return false;
        }
      } else {
        return false;
      }
    } else {
      return false;
    }

    // Replace users of boxed value with unboxed value receiver.
    newInstance.outValue().replaceUsers(instanceGet.object());

    // Remove new-instance and add constructor call for removal.
    instructionIterator.removeOrReplaceByDebugLocalRead();
    instructionsToRemove.add(constructorInvoke);
    return true;
  }

  private InstanceGet checkInstanceGet(NewInstance newInstance, Value constructorArgument) {
    if (!constructorArgument.isDefinedByInstructionSatisfying(Instruction::isInstanceGet)) {
      return null;
    }

    InstanceGet instanceGet = constructorArgument.getDefinition().asInstanceGet();
    if (!instanceGet.getField().getHolderType().isIdenticalTo(newInstance.getType())) {
      return null;
    }

    Value object = instanceGet.object();
    if (!object.getType().isClassType(newInstance.getType())) {
      return null;
    }
    return instanceGet;
  }

  private boolean isKotlinValueClass(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    return clazz != null && clazz.annotations().hasAnnotation(dexItemFactory.kotlinJvmInlineType);
  }
}
