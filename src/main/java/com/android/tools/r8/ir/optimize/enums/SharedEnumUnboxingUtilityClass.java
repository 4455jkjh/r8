// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder.SyntheticCodeGenerator;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.ConsumerUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;

public class SharedEnumUnboxingUtilityClass extends EnumUnboxingUtilityClass {

  private final DexProgramClass sharedUtilityClass;
  private final ProgramMethod valuesMethod;

  public SharedEnumUnboxingUtilityClass(
      DexProgramClass sharedUtilityClass,
      DexProgramClass synthesizingContext,
      ProgramMethod valuesMethod) {
    super(synthesizingContext);
    this.sharedUtilityClass = sharedUtilityClass;
    this.valuesMethod = valuesMethod;
  }

  public static Builder builder(
      AppView<AppInfoWithLiveness> appView,
      EnumDataMap enumDataMap,
      Set<DexProgramClass> enumsToUnbox,
      FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
    return new Builder(
        appView, enumDataMap, enumsToUnbox, fieldAccessInfoCollectionModifierBuilder);
  }

  @Override
  public void ensureMethods(AppView<AppInfoWithLiveness> appView) {
    ensureCheckNotZeroMethod(appView);
    ensureCheckNotZeroWithMessageMethod(appView);
    ensureCompareToMethod(appView);
    ensureEqualsMethod(appView);
    ensureOrdinalMethod(appView);
  }

  public ProgramMethod ensureCheckNotZeroMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.createString("checkNotZero"),
        dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.intType),
        method -> EnumUnboxingCfMethods.EnumUnboxingMethods_zeroCheck(appView.options(), method));
  }

  public ProgramMethod ensureCheckNotZeroWithMessageMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.createString("checkNotZero"),
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.intType, dexItemFactory.stringType),
        method ->
            EnumUnboxingCfMethods.EnumUnboxingMethods_zeroCheckMessage(appView.options(), method));
  }

  public ProgramMethod ensureCompareToMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.enumMembers.compareTo.getName(),
        dexItemFactory.createProto(
            dexItemFactory.intType, dexItemFactory.intType, dexItemFactory.intType),
        method -> EnumUnboxingCfMethods.EnumUnboxingMethods_compareTo(appView.options(), method));
  }

  public ProgramMethod ensureEqualsMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.enumMembers.equals.getName(),
        dexItemFactory.createProto(
            dexItemFactory.booleanType, dexItemFactory.intType, dexItemFactory.intType),
        method -> EnumUnboxingCfMethods.EnumUnboxingMethods_equals(appView.options(), method));
  }

  public ProgramMethod ensureOrdinalMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.enumMembers.ordinalMethod.getName(),
        dexItemFactory.createProto(dexItemFactory.intType, dexItemFactory.intType),
        method -> EnumUnboxingCfMethods.EnumUnboxingMethods_ordinal(appView.options(), method));
  }

  private ProgramMethod internalEnsureMethod(
      AppView<AppInfoWithLiveness> appView,
      DexString methodName,
      DexProto methodProto,
      SyntheticCodeGenerator codeGenerator) {
    // TODO(b/191957637): Consider creating free flowing static methods instead. The synthetic
    //  infrastructure needs to be augmented with a new method ensureFixedMethod() or
    //  ensureFixedFreeFlowingMethod() for this, if we want to create only one utility method (and
    //  not one per use context).
    return appView
        .getSyntheticItems()
        .ensureFixedClassMethod(
            methodName,
            methodProto,
            SyntheticKind.ENUM_UNBOXING_SHARED_UTILITY_CLASS,
            getSynthesizingContext(),
            appView,
            ConsumerUtils.emptyConsumer(),
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(codeGenerator)
                    .setClassFileVersion(CfVersion.V1_6));
  }

  @Override
  public DexProgramClass getDefinition() {
    return sharedUtilityClass;
  }

  public ProgramMethod getValuesMethod() {
    return valuesMethod;
  }

  public DexType getType() {
    return sharedUtilityClass.getType();
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;
    private final EnumDataMap enumDataMap;
    private final FieldAccessInfoCollectionModifier.Builder
        fieldAccessInfoCollectionModifierBuilder;
    private final DexProgramClass synthesizingContext;

    private DexEncodedMethod valuesMethod;

    private Builder(
        AppView<AppInfoWithLiveness> appView,
        EnumDataMap enumDataMap,
        Set<DexProgramClass> enumsToUnbox,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      DexProgramClass synthesizingContext = findDeterministicContextType(enumsToUnbox);
      this.appView = appView;
      this.dexItemFactory = appView.dexItemFactory();
      this.enumDataMap = enumDataMap;
      this.fieldAccessInfoCollectionModifierBuilder = fieldAccessInfoCollectionModifierBuilder;
      this.synthesizingContext = synthesizingContext;
    }

    SharedEnumUnboxingUtilityClass build() {
      DexProgramClass clazz = createClass();
      SharedEnumUnboxingUtilityClass sharedUtilityClass =
          new SharedEnumUnboxingUtilityClass(
              clazz, synthesizingContext, new ProgramMethod(clazz, valuesMethod));
      return sharedUtilityClass;
    }

    private DexProgramClass createClass() {
      DexProgramClass clazz =
          appView
              .getSyntheticItems()
              .createFixedClass(
                  SyntheticKind.ENUM_UNBOXING_SHARED_UTILITY_CLASS,
                  synthesizingContext,
                  appView,
                  classBuilder -> {
                    DexType sharedUtilityClassType = classBuilder.getType();
                    DexEncodedField valuesField = createValuesField(sharedUtilityClassType);
                    classBuilder
                        .setDirectMethods(
                            ImmutableList.of(
                                createClassInitializer(sharedUtilityClassType, valuesField),
                                createValuesMethod(sharedUtilityClassType, valuesField)))
                        .setStaticFields(ImmutableList.of(valuesField))
                        .setUseSortedMethodBacking(true);
                  });
      assert clazz.getAccessFlags().equals(ClassAccessFlags.createPublicFinalSynthetic());
      return clazz;
    }

    // Fields.

    private DexEncodedField createValuesField(DexType sharedUtilityClassType) {
      DexEncodedField valuesField =
          new DexEncodedField(
              dexItemFactory.createField(
                  sharedUtilityClassType, dexItemFactory.intArrayType, "$VALUES"),
              FieldAccessFlags.createPublicStaticFinalSynthetic(),
              FieldTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              DexEncodedField.NO_STATIC_VALUE,
              DexEncodedField.NOT_DEPRECATED,
              DexEncodedField.D8_R8_SYNTHESIZED);
      fieldAccessInfoCollectionModifierBuilder
          .recordFieldReadInUnknownContext(valuesField.getReference())
          .recordFieldWriteInUnknownContext(valuesField.getReference());
      return valuesField;
    }

    // Methods.

    private DexEncodedMethod createClassInitializer(
        DexType sharedUtilityClassType, DexEncodedField valuesField) {
      return new DexEncodedMethod(
          dexItemFactory.createClassInitializer(sharedUtilityClassType),
          MethodAccessFlags.createForClassInitializer(),
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          createClassInitializerCode(sharedUtilityClassType, valuesField),
          DexEncodedMethod.D8_R8_SYNTHESIZED,
          CfVersion.V1_6);
    }

    private CfCode createClassInitializerCode(
        DexType sharedUtilityClassType, DexEncodedField valuesField) {
      int maxValuesArraySize = enumDataMap.getMaxValuesSize();
      int numberOfInstructions = 4 + maxValuesArraySize * 4;
      List<CfInstruction> instructions = new ArrayList<>(numberOfInstructions);
      instructions.add(new CfConstNumber(maxValuesArraySize, ValueType.INT));
      instructions.add(new CfNewArray(dexItemFactory.intArrayType));
      for (int i = 0; i < maxValuesArraySize; i++) {
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(new CfConstNumber(i, ValueType.INT));
        // i + 1 because 0 represents the null value.
        instructions.add(new CfConstNumber(i + 1, ValueType.INT));
        instructions.add(new CfArrayStore(MemberType.INT));
      }
      instructions.add(new CfFieldInstruction(Opcodes.PUTSTATIC, valuesField.getReference()));
      instructions.add(new CfReturnVoid());

      int maxStack = 4;
      int maxLocals = 0;
      return new CfCode(
          sharedUtilityClassType,
          maxStack,
          maxLocals,
          instructions,
          Collections.emptyList(),
          Collections.emptyList());
    }

    private DexEncodedMethod createValuesMethod(
        DexType sharedUtilityClassType, DexEncodedField valuesField) {
      DexEncodedMethod valuesMethod =
          new DexEncodedMethod(
              dexItemFactory.createMethod(
                  sharedUtilityClassType,
                  dexItemFactory.createProto(dexItemFactory.intArrayType, dexItemFactory.intType),
                  "values"),
              MethodAccessFlags.createPublicStaticSynthetic(),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              createValuesMethodCode(sharedUtilityClassType, valuesField),
              DexEncodedMethod.D8_R8_SYNTHESIZED,
              CfVersion.V1_6);
      this.valuesMethod = valuesMethod;
      return valuesMethod;
    }

    private CfCode createValuesMethodCode(
        DexType sharedUtilityClassType, DexEncodedField valuesField) {
      int maxStack = 5;
      int maxLocals = 2;
      int argumentLocalSlot = 0;
      int resultLocalSlot = 1;
      return new CfCode(
          sharedUtilityClassType,
          maxStack,
          maxLocals,
          ImmutableList.of(
              // int[] result = new int[size];
              new CfLoad(ValueType.INT, argumentLocalSlot),
              new CfNewArray(dexItemFactory.intArrayType),
              new CfStore(ValueType.OBJECT, resultLocalSlot),
              // System.arraycopy(SharedUtilityClass.$VALUES, 0, result, 0, size);
              new CfFieldInstruction(Opcodes.GETSTATIC, valuesField.getReference()),
              new CfConstNumber(0, ValueType.INT),
              new CfLoad(ValueType.OBJECT, resultLocalSlot),
              new CfConstNumber(0, ValueType.INT),
              new CfLoad(ValueType.INT, argumentLocalSlot),
              new CfInvoke(
                  Opcodes.INVOKESTATIC, dexItemFactory.javaLangSystemMethods.arraycopy, false),
              // return result
              new CfLoad(ValueType.OBJECT, resultLocalSlot),
              new CfReturn(ValueType.OBJECT)),
          Collections.emptyList(),
          Collections.emptyList());
    }

    private static DexProgramClass findDeterministicContextType(Set<DexProgramClass> contexts) {
      DexProgramClass deterministicContext = null;
      for (DexProgramClass context : contexts) {
        if (deterministicContext == null) {
          deterministicContext = context;
        } else if (context.type.compareTo(deterministicContext.type) < 0) {
          deterministicContext = context;
        }
      }
      return deterministicContext;
    }
  }
}
