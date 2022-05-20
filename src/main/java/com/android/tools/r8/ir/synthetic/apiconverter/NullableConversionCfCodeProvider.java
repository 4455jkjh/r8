// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic.apiconverter;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class NullableConversionCfCodeProvider extends SyntheticCfCodeProvider {

  protected NullableConversionCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  void generateNullCheck(List<CfInstruction> instructions) {
    CfLabel nullDest = new CfLabel();
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
    instructions.add(new CfConstNull());
    instructions.add(new CfReturn(ValueType.OBJECT));
    instructions.add(nullDest);
  }

  public static class ArrayConversionCfCodeProvider extends NullableConversionCfCodeProvider {

    private final DexType typeArray;
    private final DexType convertedTypeArray;
    private final DexMethod conversion;

    public ArrayConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexType typeArray,
        DexType convertedTypeArray,
        DexMethod conversion) {
      super(appView, holder);
      this.typeArray = typeArray;
      this.convertedTypeArray = convertedTypeArray;
      this.conversion = conversion;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      // if (arg == null) { return null; }
      generateNullCheck(instructions);
      instructions.add(CfFrame.builder().appendLocal(FrameType.initialized(typeArray)).build());

      CfFrame frame =
          CfFrame.builder()
              .appendLocal(FrameType.initialized(typeArray))
              .appendLocal(FrameType.initialized(factory.intType))
              .appendLocal(FrameType.initialized(convertedTypeArray))
              .appendLocal(FrameType.initialized(factory.intType))
              .build();

      // int t1 = arg.length;
      instructions.add(new CfLoad(ValueType.fromDexType(typeArray), 0));
      instructions.add(new CfArrayLength());
      instructions.add(new CfStore(ValueType.INT, 1));
      // ConvertedType[] t2 = new ConvertedType[t1];
      instructions.add(new CfLoad(ValueType.INT, 1));
      instructions.add(new CfNewArray(convertedTypeArray));
      instructions.add(new CfStore(ValueType.fromDexType(convertedTypeArray), 2));
      // int t3 = 0;
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfStore(ValueType.INT, 3));
      // while (t3 < t1) {
      CfLabel returnLabel = new CfLabel();
      CfLabel loopLabel = new CfLabel();
      instructions.add(loopLabel);
      instructions.add(frame);
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfLoad(ValueType.INT, 1));
      instructions.add(new CfIfCmp(If.Type.GE, ValueType.INT, returnLabel));
      // t2[t3] = convert(arg[t3]);
      instructions.add(new CfLoad(ValueType.fromDexType(convertedTypeArray), 2));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfLoad(ValueType.fromDexType(typeArray), 0));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfArrayLoad(MemberType.OBJECT));
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, conversion, false));
      instructions.add(new CfArrayStore(MemberType.OBJECT));
      // t3 = t3 + 1; }
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfConstNumber(1, ValueType.INT));
      instructions.add(new CfArithmeticBinop(Opcode.Add, NumericType.INT));
      instructions.add(new CfStore(ValueType.INT, 3));
      instructions.add(new CfGoto(loopLabel));
      // return t2;
      instructions.add(returnLabel);
      instructions.add(frame.clone());
      instructions.add(new CfLoad(ValueType.fromDexType(convertedTypeArray), 2));
      instructions.add(new CfReturn(ValueType.fromDexType(convertedTypeArray)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class EnumConversionCfCodeProvider extends NullableConversionCfCodeProvider {

    private final Iterable<DexEncodedField> enumFields;
    private final DexType enumType;
    private final DexType convertedType;

    public EnumConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        Iterable<DexEncodedField> enumFields,
        DexType enumType,
        DexType convertedType) {
      super(appView, holder);
      this.enumFields = enumFields;
      this.enumType = enumType;
      this.convertedType = convertedType;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      CfFrame frame = CfFrame.builder().appendLocal(FrameType.initialized(enumType)).build();

      // if (arg == null) { return null; }
      generateNullCheck(instructions);
      instructions.add(frame);

      // if (arg == enumType.enumField1) { return convertedType.enumField1; }
      Iterator<DexEncodedField> iterator = enumFields.iterator();
      while (iterator.hasNext()) {
        DexEncodedField enumField = iterator.next();
        CfLabel notEqual = new CfLabel();
        if (iterator.hasNext()) {
          instructions.add(new CfLoad(ValueType.fromDexType(enumType), 0));
          instructions.add(
              new CfStaticFieldRead(factory.createField(enumType, enumType, enumField.getName())));
          instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, notEqual));
        }
        instructions.add(
            new CfStaticFieldRead(
                factory.createField(convertedType, convertedType, enumField.getName())));
        instructions.add(new CfReturn(ValueType.fromDexType(convertedType)));
        if (iterator.hasNext()) {
          instructions.add(notEqual);
          instructions.add(frame.clone());
        }
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class WrapperConversionCfCodeProvider extends NullableConversionCfCodeProvider {

    DexField reverseWrapperField;
    DexField wrapperField;

    public WrapperConversionCfCodeProvider(
        AppView<?> appView, DexField reverseWrapperField, DexField wrapperField) {
      super(appView, wrapperField.holder);
      this.reverseWrapperField = reverseWrapperField;
      this.wrapperField = wrapperField;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      DexType argType = wrapperField.type;
      CfFrame frame = CfFrame.builder().appendLocal(FrameType.initialized(argType)).build();

      // if (arg == null) { return null };
      generateNullCheck(instructions);
      instructions.add(frame);

      // if (arg instanceOf ReverseWrapper) { return ((ReverseWrapper) arg).wrapperField};
      assert reverseWrapperField != null;
      CfLabel unwrapDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfInstanceOf(reverseWrapperField.holder));
      instructions.add(new CfIf(If.Type.EQ, ValueType.INT, unwrapDest));
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfCheckCast(reverseWrapperField.holder));
      instructions.add(new CfInstanceFieldRead(reverseWrapperField));
      instructions.add(new CfReturn(ValueType.fromDexType(reverseWrapperField.type)));
      instructions.add(unwrapDest);
      instructions.add(frame.clone());

      // return new Wrapper(wrappedValue);
      instructions.add(new CfNew(wrapperField.holder));
      instructions.add(CfStackInstruction.fromAsm(Opcodes.DUP));
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  wrapperField.holder,
                  factory.createProto(factory.voidType, argType),
                  factory.constructorMethodName),
              false));
      instructions.add(new CfReturn(ValueType.fromDexType(wrapperField.holder)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class CollectionConversionCfCodeProvider extends NullableConversionCfCodeProvider {

    private final DexType collectionType;
    private final DexMethod conversion;

    public CollectionConversionCfCodeProvider(
        AppView<?> appView, DexType holder, DexType collectionType, DexMethod conversion) {
      super(appView, holder);
      this.collectionType = collectionType;
      this.conversion = conversion;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      // if (arg == null) { return null; }
      generateNullCheck(instructions);
      instructions.add(
          CfFrame.builder().appendLocal(FrameType.initialized(collectionType)).build());

      CfFrame frame =
          CfFrame.builder()
              .appendLocal(FrameType.initialized(collectionType))
              .appendLocal(FrameType.initialized(collectionType))
              .appendLocal(FrameType.initialized(factory.iteratorType))
              .build();

      // Collection<E> t1 = new Collection<E>();
      if (collectionType == factory.setType) {
        DexType hashSetType = factory.createType("Ljava/util/HashSet;");
        instructions.add(new CfNew(hashSetType));
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESPECIAL,
                factory.createMethod(
                    hashSetType,
                    factory.createProto(factory.voidType),
                    factory.constructorMethodName),
                false));
      } else {
        assert collectionType == factory.listType;
        DexType arrayListType = factory.createType("Ljava/util/ArrayList;");
        instructions.add(new CfNew(arrayListType));
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESPECIAL,
                factory.createMethod(
                    arrayListType,
                    factory.createProto(factory.voidType),
                    factory.constructorMethodName),
                false));
      }
      instructions.add(new CfStore(ValueType.OBJECT, 1));

      // Iterator<E> t2 = receiver.iterator();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEINTERFACE,
              factory.createMethod(
                  factory.collectionType, factory.createProto(factory.iteratorType), "iterator"),
              true));
      instructions.add(new CfStore(ValueType.OBJECT, 2));

      // while(t2.hasNext())
      CfLabel returnLabel = new CfLabel();
      CfLabel loopLabel = new CfLabel();
      instructions.add(loopLabel);
      instructions.add(frame);
      instructions.add(new CfLoad(ValueType.fromDexType(factory.iteratorType), 2));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEINTERFACE,
              factory.createMethod(
                  factory.iteratorType, factory.createProto(factory.booleanType), "hasNext"),
              true));
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfIfCmp(If.Type.EQ, ValueType.INT, returnLabel));

      // {t1.add(convert(t2.next());}
      instructions.add(new CfLoad(ValueType.fromDexType(collectionType), 1));
      instructions.add(new CfLoad(ValueType.fromDexType(factory.iteratorType), 2));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEINTERFACE,
              factory.createMethod(
                  factory.iteratorType,
                  factory.createProto(conversion.getArgumentType(0, true)),
                  "next"),
              true));
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, conversion, false));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEINTERFACE,
              factory.createMethod(
                  factory.collectionType,
                  factory.createProto(factory.booleanType, factory.objectType),
                  "add"),
              true));
      instructions.add(new CfGoto(loopLabel));

      // return t1;
      instructions.add(returnLabel);
      instructions.add(frame.clone());
      instructions.add(new CfLoad(ValueType.fromDexType(collectionType), 1));
      instructions.add(new CfReturn(ValueType.fromDexType(collectionType)));

      return standardCfCodeFromInstructions(instructions);
    }
  }
}
