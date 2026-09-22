// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleStringValue;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class EnumValueOptimizer extends CodeRewriterPass<AppInfoWithLiveness> {

  public EnumValueOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "EnumValueOptimizer";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    assert appView.enableWholeProgramOptimizations();
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (instruction.isInvokeVirtual()) {
        InvokeVirtual invoke = instruction.asInvokeVirtual();
        if (dexItemFactory.isArrayClone(invoke.getInvokedMethod())) {
          if (optimizeCloneValues(code, iterator, invoke, affectedValues)) {
            hasChanged = true;
          }
        } else {
          if (optimizeJavaLangEnumMethods(code, iterator, invoke, affectedValues)) {
            hasChanged = true;
          }
        }
      }
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean optimizeCloneValues(
      IRCode code,
      InstructionListIterator iterator,
      InvokeVirtual invoke,
      AffectedValues affectedValues) {
    // Check that this is calling array clone on an enum array.
    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexType holderElementType = invokedMethod.getHolderType().getArrayElementType();
    if (!holderElementType.isClassType()) {
      return false;
    }
    DexClass holderElementClass = appView.definitionFor(holderElementType);
    if (holderElementClass == null || !holderElementClass.isEnum()) {
      return false;
    }

    // Check that the cloned array is the $VALUES array.
    StaticGet staticGet =
        invoke.getReceiver().getAliasedValue().getDefinitionOrNull(Instruction::isStaticGet);
    if (staticGet == null) {
      return false;
    }
    ProgramField resolvedField =
        staticGet.resolveField(appView(), code.context()).getProgramField();
    AbstractValue abstractValue = resolvedField.getOptimizationInfo().getAbstractValue();
    if (!abstractValue.hasObjectState()
        || !abstractValue.getObjectState().isEnumValuesObjectState()) {
      return false;
    }

    // Check that the result of the clone operation is never mutated.
    // We cover the ArrayGet and ArrayLength to handle common loops.
    if (invoke.hasOutValue()) {
      if (invoke.outValue().hasDebugUsers() || invoke.outValue().hasPhiUsers()) {
        return false;
      }
      AliasedValueConfiguration configuration =
          AssumeAndCheckCastAliasedValueConfiguration.getInstance();
      for (Instruction user : invoke.outValue().aliasedUsers(configuration)) {
        if (user.isAssume() || user.isCheckCast()) {
          if (user.outValue().hasDebugUsers() || user.outValue().hasPhiUsers()) {
            return false;
          }
        } else if (!user.isArrayGet() && !user.isArrayLength()) {
          return false;
        }
      }
    }

    // Remove the call to array clone.
    if (invoke.hasOutValue()) {
      invoke.outValue().replaceUsers(invoke.getReceiver(), affectedValues);
    }
    iterator.removeOrReplaceByDebugLocalRead();
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean optimizeJavaLangEnumMethods(
      IRCode code,
      InstructionListIterator iterator,
      InvokeVirtual invoke,
      AffectedValues affectedValues) {
    Value receiver = invoke.getReceiver().getAliasedValue();
    if (!receiver.getType().isClassType()
        || !appView()
            .appInfo()
            .isSubtype(receiver.getType().asClassType().getClassType(), dexItemFactory.enumType)) {
      return false;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    boolean isOrdinalInvoke = invokedMethod.match(dexItemFactory.enumMembers.ordinalMethod);
    boolean isNameInvoke = invokedMethod.match(dexItemFactory.enumMembers.nameMethod);
    boolean isToStringInvoke = invokedMethod.match(dexItemFactory.enumMembers.toString);
    if (!isOrdinalInvoke && !isNameInvoke && !isToStringInvoke) {
      return false;
    }

    if (receiver.isPhi()) {
      return false;
    }

    StaticGet staticGet = receiver.getDefinition().asStaticGet();
    if (staticGet == null) {
      return false;
    }

    DexField field = staticGet.getField();
    DexEncodedField definition = field.lookupOnClass(appView.definitionForHolder(field));
    if (definition == null) {
      return false;
    }

    FieldOptimizationInfo optimizationInfo = definition.getOptimizationInfo();
    AbstractValue abstractValue = optimizationInfo.getAbstractValue();
    if (invoke.hasUnusedOutValue()) {
      // Remove the invoke if it is a call to Enum.name() or Enum.ordinal() as they don't have
      // any side effects. Enum.toString() can be overridden and calls to it could therefore
      // have arbitrary side effects.
      if (invoke.getReceiver().getType().isDefinitelyNotNull() && !isToStringInvoke) {
        assert isNameInvoke || isOrdinalInvoke;
        iterator.removeOrReplaceByDebugLocalRead();
        return true;
      }
      return false;
    }

    Value outValue = invoke.outValue();
    if (isOrdinalInvoke) {
      SingleNumberValue ordinalValue =
          getOrdinalValue(code, abstractValue, invoke.getReceiver().isNeverNull(), appView);
      if (ordinalValue != null) {
        iterator.replaceCurrentInstruction(new ConstNumber(outValue, ordinalValue.getValue()));
        return true;
      }
      return false;
    }

    SingleStringValue nameValue =
        getNameValue(code, abstractValue, invoke.getReceiver().isNeverNull());
    if (nameValue == null) {
      return false;
    }

    if (isNameInvoke) {
      replaceByName(code, affectedValues, iterator, nameValue);
      return true;
    }

    assert isToStringInvoke;

    DexClass enumClazz = appView.appInfo().definitionFor(field.type);
    if (!enumClazz.isFinal()) {
      return false;
    }

    // Since the value is a single field value, the type should be exact.
    assert abstractValue.isSingleFieldValue();
    ClassTypeElement enumFieldType =
        optimizationInfo
            .getDynamicType()
            .uncanonicalizeNotNullType(appView(), field.getType())
            .getExactClassType();
    if (enumFieldType == null) {
      assert false : "Expected to have an exact dynamic type for enum instance";
      return false;
    }

    DexEncodedMethod singleTarget =
        appView()
            .appInfo()
            .resolveMethodOnClassLegacy(
                enumFieldType.getClassType(), dexItemFactory.objectMembers.toString)
            .getSingleTarget();
    if (singleTarget != null
        && singleTarget.getReference() != dexItemFactory.enumMembers.toString) {
      return false;
    }

    replaceByName(code, affectedValues, iterator, nameValue);
    return true;
  }

  private void replaceByName(
      IRCode code,
      Set<Value> affectedValues,
      InstructionListIterator iterator,
      SingleStringValue nameValue) {
    Value newValue = code.createValue(TypeElement.stringClassType(appView, definitelyNotNull()));
    iterator.replaceCurrentInstruction(new ConstString(newValue, nameValue.getDexString()));
    newValue.addAffectedValuesTo(affectedValues);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return appView.hasLiveness()
        && options.enableEnumValueOptimization
        && code.metadata().mayHaveInvokeMethodWithReceiver();
  }

  private SingleStringValue getNameValue(
      IRCode code, AbstractValue abstractValue, boolean neverNull) {
    AbstractValue ordinalValue =
        getEnumFieldValue(
            code, abstractValue, dexItemFactory.enumMembers.nameField, neverNull, appView);
    return ordinalValue == null ? null : ordinalValue.asSingleStringValue();
  }

  static SingleNumberValue getOrdinalValue(
      IRCode code, AbstractValue abstractValue, boolean neverNull, AppView<?> appView) {
    AbstractValue ordinalValue =
        getEnumFieldValue(
            code,
            abstractValue,
            appView.dexItemFactory().enumMembers.ordinalField,
            neverNull,
            appView);
    return ordinalValue == null ? null : ordinalValue.asSingleNumberValue();
  }

  static AbstractValue getEnumFieldValue(
      IRCode code,
      AbstractValue abstractValue,
      DexField field,
      boolean neverNull,
      AppView<?> appView) {
    if (neverNull && abstractValue.isNullOrAbstractValue()) {
      abstractValue = abstractValue.asNullOrAbstractValue().getNonNullValue();
    }
    if (!abstractValue.isSingleFieldValue()) {
      return null;
    }
    DexEncodedField encodedField =
        appView.appInfo().resolveField(field, code.context()).getResolvedField();
    if (encodedField == null) {
      return null;
    }
    return abstractValue.asSingleFieldValue().getObjectState().getAbstractFieldValue(encodedField);
  }
}
