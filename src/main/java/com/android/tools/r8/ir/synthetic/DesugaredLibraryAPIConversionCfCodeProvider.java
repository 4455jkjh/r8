// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.objectweb.asm.Opcodes;

public abstract class DesugaredLibraryAPIConversionCfCodeProvider extends SyntheticCfCodeProvider {

  DesugaredLibraryAPIConversionCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  DexType vivifiedTypeFor(DexType type) {
    return DesugaredLibraryAPIConverter.vivifiedTypeFor(type, appView);
  }

  public static class APIConverterVivifiedWrapperCfCodeProvider
      extends AbstractAPIConversionCfCodeProvider {

    private final DexField wrapperField;
    private final DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public APIConverterVivifiedWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizer,
        boolean itfCall,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, wrapperField.holder, forwardMethod, wrapperSynthesizer, itfCall);
      this.wrapperField = wrapperField;
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfInstanceFieldRead(wrapperField));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.getExistingProgramConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      DexType[] newParameters = forwardMethod.proto.parameters.values.clone();
      for (int i = 0; i < forwardMethod.proto.parameters.values.length; i++) {
        DexType param = forwardMethod.proto.parameters.values[i];
        if (wrapperSynthesizor.shouldConvert(param, forwardMethod)) {
          newParameters[i] = vivifiedTypeFor(param);
        }
      }

      DexType returnType = forwardMethod.proto.returnType;
      DexType forwardMethodReturnType =
          wrapperSynthesizor.shouldConvert(returnType, forwardMethod)
              ? vivifiedTypeFor(returnType)
              : returnType;

      DexProto newProto =
          appView.dexItemFactory().createProto(forwardMethodReturnType, newParameters);
      return appView.dexItemFactory().createMethod(wrapperField.type, newProto, forwardMethod.name);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }
  }

  public abstract static class AbstractAPIConversionCfCodeProvider
      extends DesugaredLibraryAPIConversionCfCodeProvider {

    DexMethod forwardMethod;
    DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
    boolean itfCall;

    public AbstractAPIConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexMethod forwardMethod,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall) {
      super(appView, holder);
      this.forwardMethod = forwardMethod;
      this.wrapperSynthesizor = wrapperSynthesizor;
      this.itfCall = itfCall;
    }

    abstract void generatePushReceiver(List<CfInstruction> instructions);

    abstract DexMethod ensureConversionMethod(DexType type, boolean destIsVivified);

    abstract DexMethod parameterConversion(DexType param);

    abstract DexMethod returnConversion(DexType param);

    abstract DexMethod getMethodToForwardTo();

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      generatePushReceiver(instructions);
      generateParameterConvertAndLoad(factory, instructions);
      generateForwardCall(instructions);
      generateConvertAndReturn(factory, instructions);
      return standardCfCodeFromInstructions(instructions);
    }

    private void generateConvertAndReturn(
        DexItemFactory factory, List<CfInstruction> instructions) {
      DexType returnType = forwardMethod.proto.returnType;
      if (wrapperSynthesizor.shouldConvert(returnType, forwardMethod)) {
        instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, returnConversion(returnType), false));
        returnType = vivifiedTypeFor(returnType);
      }
      if (returnType == factory.voidType) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
      }
    }

    private void generateForwardCall(List<CfInstruction> instructions) {
      if (itfCall) {
        instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, getMethodToForwardTo(), true));
      } else {
        instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, getMethodToForwardTo(), false));
      }
    }

    private void generateParameterConvertAndLoad(
        DexItemFactory factory, List<CfInstruction> instructions) {
      int stackIndex = 1;
      for (DexType param : forwardMethod.proto.parameters.values) {
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
        if (wrapperSynthesizor.shouldConvert(param, forwardMethod)) {
          instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, parameterConversion(param), false));
        }
        if (param == factory.longType || param == factory.doubleType) {
          stackIndex++;
        }
        stackIndex++;
      }
    }
  }

  public static class APICallbackWrapperCfCodeProvider extends AbstractAPIConversionCfCodeProvider {

    private final DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public APICallbackWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall,
        DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, forwardMethod.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(forwardMethod.holder), 0));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.ensureConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      return forwardMethod;
    }
  }

  public static class APIConverterWrapperCfCodeProvider
      extends AbstractAPIConversionCfCodeProvider {

    private final DexField wrapperField;
    private final DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public APIConverterWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, wrapperField.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.wrapperField = wrapperField;
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfInstanceFieldRead(wrapperField));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.getExistingProgramConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      return forwardMethod;
    }
  }

  public static class APIConverterWrapperConversionCfCodeProvider extends SyntheticCfCodeProvider {

    DexField reverseWrapperField;
    DexField wrapperField;

    public APIConverterWrapperConversionCfCodeProvider(
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
      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(argType))
              .build();

      // if (arg == null) { return null };
      CfLabel nullDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.OBJECT));
      instructions.add(nullDest);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

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
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

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

  public static class APIConversionCfCodeProvider extends SyntheticCfCodeProvider {

    private final CfInvoke initialInvoke;
    private final DexMethod returnConversion;
    private final DexMethod[] parameterConversions;

    public APIConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        CfInvoke initialInvoke,
        DexMethod returnConversion,
        DexMethod[] parameterConversions) {
      super(appView, holder);
      this.initialInvoke = initialInvoke;
      this.returnConversion = returnConversion;
      this.parameterConversions = parameterConversions;
    }

    @Override
    public CfCode generateCfCode() {
      DexMethod invokedMethod = initialInvoke.getMethod();
      DexMethod convertedMethod =
          DesugaredLibraryAPIConverter.getConvertedAPI(
              invokedMethod, returnConversion, parameterConversions, appView);

      List<CfInstruction> instructions = new ArrayList<>();

      boolean isStatic = initialInvoke.getOpcode() == Opcodes.INVOKESTATIC;
      if (!isStatic) {
        instructions.add(new CfLoad(ValueType.fromDexType(invokedMethod.holder), 0));
      }
      int receiverShift = BooleanUtils.intValue(!isStatic);
      int stackIndex = 0;
      for (int i = 0; i < invokedMethod.getArity(); i++) {
        DexType param = invokedMethod.getParameter(i);
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex + receiverShift));
        if (parameterConversions[i] != null) {
          instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, parameterConversions[i], false));
        }
        if (param == appView.dexItemFactory().longType
            || param == appView.dexItemFactory().doubleType) {
          stackIndex++;
        }
        stackIndex++;
      }

      // Actual call to converted value.
      instructions.add(
          new CfInvoke(initialInvoke.getOpcode(), convertedMethod, initialInvoke.isInterface()));

      // Return conversion.
      if (returnConversion != null) {
        instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, returnConversion, false));
      }

      if (invokedMethod.getReturnType().isVoidType()) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(invokedMethod.getReturnType())));
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class ArrayConversionCfCodeProvider extends SyntheticCfCodeProvider {

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
      instructions.add(new CfLoad(ValueType.fromDexType(typeArray), 0));
      instructions.add(new CfConstNull());
      CfLabel nonNull = new CfLabel();
      instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, nonNull));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.fromDexType(convertedTypeArray)));
      instructions.add(nonNull);
      instructions.add(
          new CfFrame(
              ImmutableInt2ReferenceSortedMap.<FrameType>builder()
                  .put(0, FrameType.initialized(typeArray))
                  .build(),
              ImmutableDeque.of()));

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(typeArray))
              .put(1, FrameType.initialized(factory.intType))
              .put(2, FrameType.initialized(convertedTypeArray))
              .put(3, FrameType.initialized(factory.intType))
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
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));
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
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));
      instructions.add(new CfLoad(ValueType.fromDexType(convertedTypeArray), 2));
      instructions.add(new CfReturn(ValueType.fromDexType(convertedTypeArray)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class EnumConversionCfCodeProvider extends SyntheticCfCodeProvider {

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

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(enumType))
              .build();

      // if (arg == null) { return null; }
      instructions.add(new CfLoad(ValueType.fromDexType(enumType), 0));
      instructions.add(new CfConstNull());
      CfLabel nonNull = new CfLabel();
      instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, nonNull));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.fromDexType(convertedType)));
      instructions.add(nonNull);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

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
          instructions.add(new CfFrame(locals, ImmutableDeque.of()));
        }
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class APIConverterConstructorCfCodeProvider extends SyntheticCfCodeProvider {

    private final DexField wrapperField;
    private final DexType superType;

    public APIConverterConstructorCfCodeProvider(
        AppView<?> appView, DexField wrapperField, DexType superType) {
      super(appView, wrapperField.holder);
      this.wrapperField = wrapperField;
      this.superType = superType;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  superType, factory.createProto(factory.voidType), factory.constructorMethodName),
              false));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.type), 1));
      instructions.add(new CfInstanceFieldWrite(wrapperField));
      instructions.add(new CfReturnVoid());
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class APIConverterThrowRuntimeExceptionCfCodeProvider
      extends SyntheticCfCodeProvider {

    DexString message;

    public APIConverterThrowRuntimeExceptionCfCodeProvider(
        AppView<?> appView, DexString message, DexType holder) {
      super(appView, holder);
      this.message = message;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfNew(factory.runtimeExceptionType));
      instructions.add(CfStackInstruction.fromAsm(Opcodes.DUP));
      instructions.add(new CfConstString(message));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  factory.runtimeExceptionType,
                  factory.createProto(factory.voidType, factory.stringType),
                  factory.constructorMethodName),
              false));
      instructions.add(new CfThrow());
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
