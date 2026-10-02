// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfSwitch.Kind;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.dex.code.DexIgetOrIputOrSgetOrSput;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeMethodOrInvokeMethodRange;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.exceptions.Unimplemented;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.objectweb.asm.Opcodes;

public class LargeProtoEnumRewriter {
  private final AppView<?> appView;
  private final ExecutorService executorService;
  private final Timing timing;
  private final DexItemFactory factory;
  private final ProtoReferences references;

  private static class EnumConstantInfo {
    final DexString name;
    final DexField field;
    final int ordinal;
    final int wireValue;
    final DexField canonicalField;

    EnumConstantInfo(
        DexString name, DexField field, int ordinal, int wireValue, DexField canonicalField) {
      this.name = name;
      this.field = field;
      this.ordinal = ordinal;
      this.wireValue = wireValue;
      this.canonicalField = canonicalField;
    }
  }

  private static class WireValueInfo {
    final DexField field;
    final int wireValue;

    WireValueInfo(DexField field, int wireValue) {
      this.field = field;
      this.wireValue = wireValue;
    }
  }

  private LargeProtoEnumRewriter(
      AppView<?> appView, ExecutorService executorService, Timing timing) {
    this.appView = appView;
    this.timing = timing;
    this.executorService = executorService;
    this.factory = appView.dexItemFactory();
    this.references = getProtoReferences();
  }

  private ProtoReferences getProtoReferences() {
    if (appView.protoShrinker() != null) {
      return appView.protoShrinker().getProtoReferences();
    }
    ProtoReferences references = new ProtoReferences(appView.dexItemFactory());
    if (appView.definitionFor(references.enumLiteType) == null) {
      return null;
    }
    return references;
  }

  public static void run(AppView<?> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    new LargeProtoEnumRewriter(appView, executorService, timing).runInternal();
  }

  private void runInternal() throws ExecutionException {
    if (!appView.options().protoShrinking().isLargeProtoEnumRewritingEnabled()
        || references == null) {
      return;
    }
    Map<DexType, DexProgramClass> largeEnums = findLargeProtoEnums();
    if (largeEnums.isEmpty()) {
      return;
    }

    if (appView.options().isGeneratingClassFiles()) {
      for (DexProgramClass largeEnum : largeEnums.values()) {
        appView
            .reporter()
            .warning(
                new StringDiagnostic(
                    "Large enum "
                        + largeEnum.getType().getTypeName()
                        + " is not supported when generating class files",
                    largeEnum.getOrigin()));
      }
      return;
    }

    Map<DexField, DexField> fieldMapping = new IdentityHashMap<>();
    Map<DexMethod, DexMethod> methodMapping = new IdentityHashMap<>();
    Set<DexType> helperInterfacesToRemove = Sets.newIdentityHashSet();
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);

    for (DexProgramClass largeEnum : largeEnums.values()) {
      rewriteLargeEnum(
          largeEnum,
          fieldMapping,
          methodMapping,
          helperInterfacesToRemove,
          profileCollectionAdditions);
    }

    profileCollectionAdditions.commit(appView);

    // Remove helper interface classes from the application.
    DexApplication newApplication =
        appView
            .app()
            .builder()
            .removeProgramClasses(clazz -> helperInterfacesToRemove.contains(clazz.getType()))
            .build(timing);
    appView.rebuildAppInfo(timing, newApplication);

    // Rewrite all references to the removed helper interfaces.
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          rewriteProgramClassInstructions(clazz, fieldMapping, methodMapping);
          updateInnerClassAttributes(clazz, largeEnums, helperInterfacesToRemove);
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private Map<DexType, DexProgramClass> findLargeProtoEnums() {
    Map<DexType, DexProgramClass> result = new LinkedHashMap<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isLargeProtoEnum(clazz, references)) {
        result.put(clazz.getType(), clazz);
      }
    }
    return result;
  }

  private boolean isLargeProtoEnum(DexProgramClass clazz, ProtoReferences references) {
    if (clazz.isAbstract() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
      return false;
    }
    if (clazz.getSuperType().isNotIdenticalTo(factory.objectType)) {
      return false;
    }
    if (clazz.lookupVirtualMethod(m -> m.getName().isIdenticalTo(references.getNumberMethodName))
        == null) {
      return false;
    }
    if (!clazz.getInterfaces().contains(references.enumLiteType)) {
      return false;
    }
    List<DexProgramClass> helperInterfaces = getHelperInterfaces(clazz);
    return helperInterfaces.size() >= 2;
  }

  private List<DexProgramClass> getHelperInterfaces(DexProgramClass clazz) {
    List<DexProgramClass> result = new ArrayList<>();
    for (DexType ifaceType : clazz.getInterfaces()) {
      DexProgramClass iface = asProgramClassOrNull(appView.definitionFor(ifaceType));
      if (isHelperInterface(clazz, iface)) {
        result.add(iface);
      }
    }
    return result;
  }

  private boolean isHelperInterface(DexProgramClass clazz, DexClass iface) {
    if (iface == null || !iface.isProgramClass() || !iface.isInterface()) {
      return false;
    }
    if (iface.getType().isIdenticalTo(references.enumLiteType)) {
      return false;
    }
    // Helper interfaces are named <EnumName><index>, e.g. Foo0, Foo1 for enum Foo.
    DexString descriptor = clazz.getType().getDescriptor();
    DexString prefix = descriptor.substring(0, descriptor.length() - 1, factory);
    if (!iface.getType().getDescriptor().startsWith(prefix)) {
      return false;
    }
    DexString suffix =
        iface
            .getType()
            .getDescriptor()
            .substring(
                prefix.length(),
                iface.getType().getDescriptor().length() - 1,
                appView.dexItemFactory());
    int helperIndex;
    try {
      helperIndex = Integer.parseInt(suffix.toString());
    } catch (NumberFormatException e) {
      return false;
    }

    // Helper interface must declare static helper methods: forNumber<N>, valueOf<N>, values<N>.
    if (iface.lookupDirectMethod(m -> m.getName().isEqualTo("forNumber" + helperIndex)) == null) {
      return false;
    }
    if (iface.lookupDirectMethod(m -> m.getName().isEqualTo("valueOf" + helperIndex)) == null) {
      return false;
    }
    if (iface.lookupDirectMethod(m -> m.getName().isEqualTo("values" + helperIndex)) == null) {
      return false;
    }

    // Helper interface must have at least one enum constant field of type clazz.
    // Every enum constant field <NAME> must have a corresponding <NAME>_VALUE int field
    // with a constant integer value (ConstantValue attribute).
    Set<DexString> intFields = SetUtils.newIdentityHashSet();
    for (DexEncodedField field : iface.staticFields()) {
      if (field.getType().isIdenticalTo(factory.intType)) {
        intFields.add(field.getName());
      }
    }
    boolean hasEnumFields = false;
    for (DexEncodedField field : iface.staticFields()) {
      if (field.getReference().getType().isIdenticalTo(clazz.getType())) {
        hasEnumFields = true;
        DexString valueFieldName = factory.createString(field.getName().toString() + "_VALUE");
        DexField valueFieldReference =
            factory.createField(iface.getType(), factory.intType, valueFieldName);
        DexEncodedField valueField = iface.lookupStaticField(valueFieldReference);
        if (valueField == null
            || !valueField.hasExplicitStaticValue()
            || !(valueField.getStaticValue() instanceof DexValueInt)) {
          return false;
        }
        intFields.remove(valueFieldName);
      }
    }
    return hasEnumFields && intFields.isEmpty();
  }

  private void rewriteLargeEnum(
      DexProgramClass clazz,
      Map<DexField, DexField> fieldMapping,
      Map<DexMethod, DexMethod> methodMapping,
      Set<DexType> helperInterfacesToRemove,
      ProfileCollectionAdditions profileCollectionAdditions) {
    ComputedApiLevel minApiLevel = appView.computedMinApiLevel();
    List<DexProgramClass> helperInterfaces = getHelperInterfaces(clazz);

    for (DexProgramClass iface : helperInterfaces) {
      helperInterfacesToRemove.add(iface.getType());
    }
    List<DexType> helperInterfaceTypes = ListUtils.map(helperInterfaces, DexClass::getType);
    clazz.removeNestMemberAttributes(
        member -> helperInterfaceTypes.contains(member.getNestMember()));

    Map<DexString, Integer> wireValueMap = new LinkedHashMap<>();
    List<WireValueInfo> wireValues = new ArrayList<>();
    Int2ReferenceMap<EnumConstantInfo> canonicalConstants = new Int2ReferenceLinkedOpenHashMap<>();
    List<EnumConstantInfo> aliasConstants = new ArrayList<>();

    // Collect wire value constants from helper interfaces.
    for (DexProgramClass iface : helperInterfaces) {
      for (DexEncodedField field : iface.staticFields()) {
        if (field.getReference().getType().isIdenticalTo(factory.intType)
            && field.getStaticValue() instanceof DexValueInt) {
          int wireValue = field.getStaticValue().asDexValueInt().getValue();
          wireValueMap.put(field.getName(), wireValue);
          DexField targetField = field.getReference().withHolder(clazz.getType(), factory);
          wireValues.add(new WireValueInfo(targetField, wireValue));
          fieldMapping.put(field.getReference(), targetField);
        }
      }
    }

    // Extract enum constants and aliases from helper interfaces.
    int ordinalCounter = 0;
    for (DexProgramClass iface : helperInterfaces) {
      for (DexEncodedField field : iface.staticFields()) {
        if (field.getReference().getType().isIdenticalTo(clazz.getType())) {
          DexString name = field.getName();
          DexField targetField = field.getReference().withHolder(clazz.getType(), factory);
          fieldMapping.put(field.getReference(), targetField);

          DexString valueFieldName = factory.createString(name.toString() + "_VALUE");
          int wireVal = wireValueMap.get(valueFieldName);

          // Check if this is an alias (when another constant already has this wire value).
          boolean isAlias = false;
          DexField canonicalTargetField = null;
          EnumConstantInfo existing = canonicalConstants.get(wireVal);
          if (existing != null) {
            isAlias = true;
            canonicalTargetField = existing.field;
          }

          if (isAlias) {
            EnumConstantInfo info =
                new EnumConstantInfo(name, targetField, -1, wireVal, canonicalTargetField);
            aliasConstants.add(info);
          } else {
            EnumConstantInfo info =
                new EnumConstantInfo(name, targetField, ordinalCounter++, wireVal, null);
            canonicalConstants.put(info.wireValue, info);
          }
        }
      }

      // Record helper methods on helper interface.
      for (DexEncodedMethod method : iface.methods()) {
        if (method.getName().startsWith("forNumber")) {
          methodMapping.put(
              method.getReference(),
              factory.createMethod(
                  clazz.getType(), method.getProto(), factory.createString("forNumber")));
        } else if (method.getName().startsWith("valueOf")) {
          methodMapping.put(
              method.getReference(),
              factory.createMethod(
                  clazz.getType(), method.getProto(), factory.createString("valueOf")));
        } else if (method.getName().startsWith("values")) {
          methodMapping.put(
              method.getReference(),
              method
                  .getReference()
                  .withHolderAndName(clazz.getType(), factory.createString("values"), factory));
        }
      }
    }

    // Check for UNRECOGNIZED.
    EnumConstantInfo unrecognizedConstant = null;
    DexEncodedField unrecognizedField =
        clazz.lookupUniqueStaticFieldWithName(factory.createString("UNRECOGNIZED"));
    if (unrecognizedField != null
        && unrecognizedField.getReference().getType().isIdenticalTo(clazz.getType())) {
      unrecognizedConstant =
          new EnumConstantInfo(
              unrecognizedField.getName(),
              unrecognizedField.getReference().withHolder(clazz.getType(), factory),
              ordinalCounter++,
              -1,
              null);
    }

    // Turn the class into an enum and remove the helper interfaces.
    clazz.setSuperType(factory.enumType);
    clazz.getAccessFlags().setEnum();
    clazz.getAccessFlags().setFinal();
    clazz.setInterfaces(clazz.getInterfaces().removeIf(helperInterfacesToRemove::contains));

    // Add value field.
    DexField valueField =
        factory.createField(clazz.getType(), factory.intType, factory.createString("value"));
    DexEncodedField encodedValueField =
        DexEncodedField.syntheticBuilder()
            .setField(valueField)
            .setAccessFlags(
                FieldAccessFlags.fromSharedAccessFlags(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))
            .setApiLevel(minApiLevel)
            .build();
    clazz.setInstanceFields(new DexEncodedField[] {encodedValueField});

    // Add enum constants.
    List<DexEncodedField> staticFields = new ArrayList<>();
    for (EnumConstantInfo enumInfo : canonicalConstants.values()) {
      staticFields.add(
          DexEncodedField.syntheticBuilder()
              .setField(enumInfo.field)
              .setAccessFlags(
                  FieldAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC
                          | Opcodes.ACC_STATIC
                          | Opcodes.ACC_FINAL
                          | Opcodes.ACC_ENUM))
              .setApiLevel(minApiLevel)
              .build());
    }

    // Add UNRECOGNIZED.
    if (unrecognizedConstant != null) {
      staticFields.add(
          DexEncodedField.syntheticBuilder()
              .setField(unrecognizedConstant.field)
              .setAccessFlags(
                  FieldAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC
                          | Opcodes.ACC_STATIC
                          | Opcodes.ACC_FINAL
                          | Opcodes.ACC_ENUM))
              .setApiLevel(minApiLevel)
              .build());
    }

    // Handle aliases.
    for (EnumConstantInfo alias : aliasConstants) {
      staticFields.add(
          DexEncodedField.syntheticBuilder()
              .setField(alias.field)
              .setAccessFlags(
                  FieldAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
              .setApiLevel(minApiLevel)
              .build());
    }

    // Wire value constants fields.
    for (WireValueInfo v : wireValues) {
      staticFields.add(
          DexEncodedField.syntheticBuilder()
              .setField(v.field)
              .setAccessFlags(
                  FieldAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
              .setStaticValue(DexValueInt.create(v.wireValue))
              .setApiLevel(minApiLevel)
              .build());
    }

    // Add $VALUES.
    DexType valuesArrayType = clazz.getType().toArrayType(factory);
    DexField valuesField =
        factory.createField(clazz.getType(), valuesArrayType, factory.createString("$VALUES"));
    staticFields.add(
        DexEncodedField.syntheticBuilder()
            .setField(valuesField)
            .setAccessFlags(
                FieldAccessFlags.fromSharedAccessFlags(
                    Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_STATIC
                        | Opcodes.ACC_FINAL
                        | Opcodes.ACC_SYNTHETIC))
            .setApiLevel(minApiLevel)
            .build());

    // Retain other pre-existing static fields on clazz (e.g. descriptor, internalValueMap).
    for (DexEncodedField staticField : clazz.staticFields()) {
      // The existing UNRECOGNIZED field is replaced, so skip that.
      if (unrecognizedConstant == null
          || !staticField.getReference().isIdenticalTo(unrecognizedConstant.field)) {
        staticFields.add(staticField);
      }
    }
    clazz.setStaticFields(staticFields);

    // Synthesize methods.
    List<DexEncodedMethod> directMethods = new ArrayList<>();
    List<DexEncodedMethod> virtualMethods = new ArrayList<>();

    // Constructor (String name, int ordinal, int value).
    Set<DexMethod> originalConstructors = Sets.newIdentityHashSet();
    clazz.forEachProgramInstanceInitializer(m -> originalConstructors.add(m.getReference()));
    DexMethod enumConstructor =
        factory.createInstanceInitializer(
            clazz.getType(), factory.stringType, factory.intType, factory.intType);

    {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfLoad(ValueType.OBJECT, 1));
      instructions.add(new CfLoad(ValueType.INT, 2));
      instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, factory.enumMembers.constructor, false));
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(CfFieldInstruction.create(Opcodes.PUTFIELD, valueField));
      instructions.add(new CfReturnVoid());

      CfCode code = new CfCode(clazz.getType(), 4, 4, instructions);
      directMethods.add(
          DexEncodedMethod.syntheticBuilder()
              .setMethod(enumConstructor)
              .setAccessFlags(MethodAccessFlags.fromSharedAccessFlags(Opcodes.ACC_PRIVATE, true))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate <clinit>.
    {
      List<CfInstruction> instructions = new ArrayList<>();
      // Initialize all enum instances. These used to be on the helper interfaces.
      for (EnumConstantInfo enumInfo : canonicalConstants.values()) {
        instructions.add(new CfNew(clazz.getType()));
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(new CfConstString(enumInfo.name));
        instructions.add(new CfConstNumber(enumInfo.ordinal, ValueType.INT));
        instructions.add(new CfConstNumber(enumInfo.wireValue, ValueType.INT));
        instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, enumConstructor, false));
        instructions.add(CfFieldInstruction.create(Opcodes.PUTSTATIC, enumInfo.field));
      }

      // Initialize UNRECOGNIZED, which was also in the <clinit> before.
      if (unrecognizedConstant != null) {
        instructions.add(new CfNew(clazz.getType()));
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(new CfConstString(unrecognizedConstant.name));
        instructions.add(new CfConstNumber(unrecognizedConstant.ordinal, ValueType.INT));
        instructions.add(new CfConstNumber(-1, ValueType.INT));
        instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, enumConstructor, false));
        instructions.add(CfFieldInstruction.create(Opcodes.PUTSTATIC, unrecognizedConstant.field));
      }

      // Initialize all enum aliases. These used to be on the helper interfaces.
      for (EnumConstantInfo alias : aliasConstants) {
        instructions.add(CfFieldInstruction.create(Opcodes.GETSTATIC, alias.canonicalField));
        instructions.add(CfFieldInstruction.create(Opcodes.PUTSTATIC, alias.field));
      }

      // Allocate and populate the $VALUES array. This used to be split across the helper
      // interfaces.
      List<EnumConstantInfo> valuesList = new ArrayList<>(canonicalConstants.values());
      if (unrecognizedConstant != null) {
        valuesList.add(unrecognizedConstant);
      }
      instructions.add(new CfConstNumber(valuesList.size(), ValueType.INT));
      instructions.add(new CfNewArray(valuesArrayType));
      for (int i = 0; i < valuesList.size(); i++) {
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(new CfConstNumber(i, ValueType.INT));
        instructions.add(CfFieldInstruction.create(Opcodes.GETSTATIC, valuesList.get(i).field));
        instructions.add(new CfArrayStore(MemberType.OBJECT));
      }
      instructions.add(CfFieldInstruction.create(Opcodes.PUTSTATIC, valuesField));

      // Append remaining instructions from existing <clinit>. These have not changed except
      // initializing UNRECOGNIZED, which is skipped.
      DexEncodedMethod existingClinit = clazz.getClassInitializer();
      int maxStack = 8;
      int maxLocals = 0;
      if (existingClinit != null && existingClinit.getCode() != null) {
        if (!existingClinit.getCode().isCfCode()) {
          throw new Unimplemented();
        }
        CfCode existingCode = existingClinit.getCode().asCfCode();
        maxStack = Math.max(maxStack, existingCode.getMaxStack());
        maxLocals = Math.max(maxLocals, existingCode.getMaxLocals());
        List<CfInstruction> existingInstructions = existingCode.getInstructions();
        for (int i = 0; i < existingInstructions.size(); i++) {
          CfInstruction instruction = existingInstructions.get(i);
          // This optimistically assumes that the only new-instance of the enum class type is the
          // allocation of UNRECOGNIZED.
          if (unrecognizedConstant != null
              && instruction.isNew()
              && instruction.asNew().getType().isIdenticalTo(clazz.getType())) {
            while (i < existingInstructions.size()) {
              CfInstruction current = existingInstructions.get(i);
              if (current.isFieldInstruction()
                  && current.asFieldInstruction().getAsmOpcode() == Opcodes.PUTSTATIC
                  && current
                      .asFieldInstruction()
                      .getField()
                      .isIdenticalTo(unrecognizedConstant.field)) {
                break;
              }
              i++;
            }
            continue;
          }
          instructions.add(instruction);
        }
      } else {
        instructions.add(new CfReturnVoid());
      }

      CfCode code = new CfCode(clazz.getType(), maxStack, maxLocals, instructions);
      directMethods.add(
          DexEncodedMethod.syntheticBuilder()
              .setMethod(factory.createClassInitializer(clazz.getType()))
              .setAccessFlags(MethodAccessFlags.fromSharedAccessFlags(Opcodes.ACC_STATIC, true))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate values(). This is now just cloning $VALUES.
    {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(CfFieldInstruction.create(Opcodes.GETSTATIC, valuesField));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEVIRTUAL,
              factory.createMethod(
                  valuesArrayType,
                  factory.createProto(factory.objectType),
                  factory.createString("clone")),
              false));
      instructions.add(new CfCheckCast(valuesArrayType));
      instructions.add(new CfReturn(ValueType.OBJECT));

      CfCode code = new CfCode(clazz.getType(), 4, 0, instructions);
      DexMethod valuesMethod =
          factory.createMethod(
              clazz.getType(),
              factory.createProto(valuesArrayType),
              factory.createString("values"));
      directMethods.add(
          createMethodBuilder(clazz, valuesMethod)
              .setMethod(valuesMethod)
              .setAccessFlags(
                  MethodAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate valueOf(String).
    {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfConstClass(clazz.getType()));
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, factory.enumMembers.valueOf, false));
      instructions.add(new CfCheckCast(clazz.getType()));
      instructions.add(new CfReturn(ValueType.OBJECT));

      CfCode code = new CfCode(clazz.getType(), 4, 1, instructions);
      DexMethod valueOfStringMethod =
          factory.createMethod(
              clazz.getType(),
              factory.createProto(clazz.getType(), factory.stringType),
              factory.createString("valueOf"));
      directMethods.add(
          createMethodBuilder(clazz, valueOfStringMethod)
              .setMethod(valueOfStringMethod)
              .setAccessFlags(
                  MethodAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate forNumber(int).
    {
      List<CfInstruction> instructions = new ArrayList<>();
      List<EnumConstantInfo> sorted = new ArrayList<>(canonicalConstants.values());
      sorted.sort(Comparator.comparingInt(c -> c.wireValue));

      int[] keys = new int[sorted.size()];
      List<CfLabel> targets = new ArrayList<>(sorted.size());
      for (int i = 0; i < sorted.size(); i++) {
        keys[i] = sorted.get(i).wireValue;
        targets.add(new CfLabel());
      }
      CfLabel defaultLabel = new CfLabel();

      instructions.add(new CfLoad(ValueType.INT, 0));
      instructions.add(new CfSwitch(Kind.LOOKUP, defaultLabel, keys, targets));
      for (int i = 0; i < sorted.size(); i++) {
        instructions.add(targets.get(i));
        instructions.add(CfFieldInstruction.create(Opcodes.GETSTATIC, sorted.get(i).field));
        instructions.add(new CfReturn(ValueType.OBJECT));
      }
      instructions.add(defaultLabel);
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.OBJECT));

      CfCode code = new CfCode(clazz.getType(), 4, 1, instructions);
      DexMethod forNumberMethod =
          factory.createMethod(
              clazz.getType(),
              factory.createProto(clazz.getType(), factory.intType),
              factory.createString("forNumber"));
      directMethods.add(
          createMethodBuilder(clazz, forNumberMethod)
              .setMethod(forNumberMethod)
              .setAccessFlags(
                  MethodAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate getNumber().
    {
      List<CfInstruction> instructions = new ArrayList<>();
      if (unrecognizedConstant != null) {
        CfLabel notUnrecognized = new CfLabel();
        instructions.add(new CfLoad(ValueType.OBJECT, 0));
        instructions.add(CfFieldInstruction.create(Opcodes.GETSTATIC, unrecognizedConstant.field));
        instructions.add(new CfIfCmp(IfType.NE, ValueType.OBJECT, notUnrecognized));
        instructions.add(new CfNew(factory.javaLangIllegalArgumentExceptionType));
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(
            new CfConstString(
                factory.createString("Can't get the number of an unknown enum value.")));
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESPECIAL,
                factory.illegalArgumentExceptionMethods.initWithMessage,
                false));
        instructions.add(new CfThrow());
        instructions.add(notUnrecognized);
      }
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(CfFieldInstruction.create(Opcodes.GETFIELD, valueField));
      instructions.add(new CfReturn(ValueType.INT));

      CfCode code = new CfCode(clazz.getType(), 5, 1, instructions);
      DexMethod getNumberMethod =
          factory.createMethod(
              clazz.getType(),
              factory.createProto(factory.intType),
              factory.createString("getNumber"));
      virtualMethods.add(
          createMethodBuilder(clazz, getNumberMethod)
              .setMethod(getNumberMethod)
              .setAccessFlags(
                  MethodAccessFlags.fromSharedAccessFlags(
                      Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate index().
    if (clazz.lookupVirtualMethod(m -> m.getName().toString().equals("index")) != null) {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEVIRTUAL,
              factory.createMethod(
                  clazz.getType(),
                  factory.createProto(factory.intType),
                  factory.createString("ordinal")),
              false));
      instructions.add(new CfReturn(ValueType.INT));

      CfCode code = new CfCode(clazz.getType(), 4, 1, instructions);
      DexMethod indexMethod =
          factory.createMethod(
              clazz.getType(), factory.createProto(factory.intType), factory.createString("index"));
      virtualMethods.add(
          createMethodBuilder(clazz, indexMethod)
              .setMethod(indexMethod)
              .setAccessFlags(MethodAccessFlags.fromSharedAccessFlags(Opcodes.ACC_PUBLIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate value().
    if (clazz.lookupVirtualMethod(m -> m.getName().toString().equals("value")) != null) {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(CfFieldInstruction.create(Opcodes.GETFIELD, valueField));
      instructions.add(new CfReturn(ValueType.INT));

      CfCode code = new CfCode(clazz.getType(), 4, 1, instructions);
      DexMethod valueMethod =
          factory.createMethod(
              clazz.getType(), factory.createProto(factory.intType), factory.createString("value"));
      virtualMethods.add(
          createMethodBuilder(clazz, valueMethod)
              .setMethod(valueMethod)
              .setAccessFlags(MethodAccessFlags.fromSharedAccessFlags(Opcodes.ACC_PUBLIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Generate getName().
    if (clazz.lookupVirtualMethod(m -> m.getName().toString().equals("getName")) != null) {
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEVIRTUAL,
              factory.createMethod(
                  clazz.getType(),
                  factory.createProto(factory.stringType),
                  factory.createString("name")),
              false));
      instructions.add(new CfReturn(ValueType.OBJECT));

      CfCode code = new CfCode(clazz.getType(), 4, 1, instructions);
      DexMethod getNameMethod =
          factory.createMethod(
              clazz.getType(),
              factory.createProto(factory.stringType),
              factory.createString("getName"));
      virtualMethods.add(
          createMethodBuilder(clazz, getNameMethod)
              .setMethod(getNameMethod)
              .setAccessFlags(MethodAccessFlags.fromSharedAccessFlags(Opcodes.ACC_PUBLIC, false))
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .setApiLevelForDefinition(minApiLevel)
              .setApiLevelForCode(minApiLevel)
              .build());
    }

    // Retain other methods on clazz that are not replaced.
    DexMethod valuesMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(clazz.getType().toArrayType(factory)),
            factory.createString("values"));
    DexMethod valueOfStringMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(clazz.getType(), factory.stringType),
            factory.createString("valueOf"));
    DexMethod forNumberMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(clazz.getType(), factory.intType),
            factory.createString("forNumber"));

    DexMethod getNumberMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(factory.intType),
            factory.createString("getNumber"));
    DexMethod indexMethodRef =
        factory.createMethod(
            clazz.getType(), factory.createProto(factory.intType), factory.createString("index"));
    DexMethod valueMethodRef =
        factory.createMethod(
            clazz.getType(), factory.createProto(factory.intType), factory.createString("value"));
    DexMethod getNameMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(factory.stringType),
            factory.createString("getName"));
    DexMethod nameMethodRef =
        factory.createMethod(
            clazz.getType(), factory.createProto(factory.stringType), factory.createString("name"));
    DexMethod ordinalMethodRef =
        factory.createMethod(
            clazz.getType(), factory.createProto(factory.intType), factory.createString("ordinal"));
    DexMethod toStringMethodRef =
        factory.createMethod(
            clazz.getType(),
            factory.createProto(factory.stringType),
            factory.createString("toString"));

    for (DexEncodedMethod directMethod : clazz.directMethods()) {
      if (directMethod.isInstanceInitializer() || directMethod.isClassInitializer()) {
        continue;
      }
      DexMethod ref = directMethod.getReference();
      if (ref.isIdenticalTo(valuesMethodRef)
          || ref.isIdenticalTo(valueOfStringMethodRef)
          || ref.isIdenticalTo(forNumberMethodRef)) {
        continue;
      }
      directMethods.add(directMethod);
    }
    for (DexEncodedMethod virtualMethod : clazz.virtualMethods()) {
      DexMethod ref = virtualMethod.getReference();
      if (ref.isIdenticalTo(getNumberMethodRef)
          || ref.isIdenticalTo(indexMethodRef)
          || ref.isIdenticalTo(valueMethodRef)
          || ref.isIdenticalTo(getNameMethodRef)
          || ref.isIdenticalTo(nameMethodRef)
          || ref.isIdenticalTo(ordinalMethodRef)
          || ref.isIdenticalTo(toStringMethodRef)) {
        continue;
      }
      virtualMethods.add(virtualMethod);
    }

    clazz.setDirectMethods(directMethods);
    clazz.setVirtualMethods(virtualMethods);

    // Except for the constructor all method signatures on the input proto enum class is also
    // present in the output proto enum class. The new constructor is added to the profile if
    // the original constructor was.
    originalConstructors.forEach(
        init ->
            profileCollectionAdditions.applyIfContextIsInProfile(
                init, builder -> builder.addMethodRule(enumConstructor)));
  }

  private void updateInnerClassAttributes(
      DexProgramClass clazz,
      Map<DexType, DexProgramClass> largeEnums,
      Set<DexType> helperInterfacesToRemove) {
    List<InnerClassAttribute> innerClasses = clazz.getInnerClasses();
    if (innerClasses == null || innerClasses.isEmpty()) {
      return;
    }
    List<InnerClassAttribute> newInnerClasses = new ArrayList<>(innerClasses.size());
    boolean changed = false;
    for (InnerClassAttribute attribute : innerClasses) {
      if (helperInterfacesToRemove.contains(attribute.getInner())) {
        changed = true;
        continue;
      }
      if (largeEnums.containsKey(attribute.getInner())) {
        int newAccess = attribute.getAccess() | Opcodes.ACC_ENUM | Opcodes.ACC_FINAL;
        newInnerClasses.add(
            new InnerClassAttribute(
                newAccess, attribute.getInner(), attribute.getOuter(), attribute.getInnerName()));
        changed = true;
      } else {
        newInnerClasses.add(attribute);
      }
    }
    if (changed) {
      clazz.setInnerClasses(newInnerClasses);
    }
  }

  private void rewriteProgramClassInstructions(
      DexProgramClass clazz,
      Map<DexField, DexField> fieldMapping,
      Map<DexMethod, DexMethod> methodMapping) {
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getCode() == null) {
        continue;
      }
      if (method.getCode().isCfCode()) {
        CfCode cfCode = method.getCode().asCfCode();
        List<CfInstruction> newInstructions =
            ListUtils.mapOrElse(
                cfCode.getInstructions(),
                instruction -> {
                  if (instruction.isFieldInstruction()) {
                    CfFieldInstruction fieldInst = instruction.asFieldInstruction();
                    DexField mappedField = fieldMapping.get(fieldInst.getField());
                    if (mappedField != null) {
                      return CfFieldInstruction.create(fieldInst.getAsmOpcode(), mappedField);
                    }
                  } else if (instruction.isInvoke()) {
                    CfInvoke invokeInst = instruction.asInvoke();
                    DexMethod mappedMethod = methodMapping.get(invokeInst.getMethod());
                    if (mappedMethod != null) {
                      return new CfInvoke(invokeInst.getOpcode(), mappedMethod, false);
                    }
                  }
                  return instruction;
                });
        if (newInstructions != cfCode.getInstructions()) {
          cfCode.setInstructions(newInstructions);
        }
      } else if (method.getCode().isDexCode()) {
        DexCode dexCode = method.getCode().asDexCode();
        for (int i = 0; i < dexCode.instructions.length; i++) {
          DexInstruction instruction = dexCode.instructions[i];
          if (instruction.isIgetOrIputOrSgetOrSput()) {
            DexIgetOrIputOrSgetOrSput fieldInst = instruction.asIgetOrIputOrSgetOrSput();
            DexField mapped = fieldMapping.get(fieldInst.getField());
            if (mapped != null) {
              dexCode.instructions[i] = fieldInst.withField(mapped).asDexInstruction();
            }
          } else if (instruction.isInvokeMethodOrInvokeMethodRange()) {
            DexInvokeMethodOrInvokeMethodRange invokeInst =
                instruction.asInvokeMethodOrInvokeMethodRange();
            DexMethod mapped = methodMapping.get(invokeInst.getMethod());
            if (mapped != null) {
              dexCode.instructions[i] = invokeInst.withMethod(mapped).asDexInstruction();
            }
          }
        }
      } else {
        throw new Unimplemented();
      }
    }
  }

  private DexEncodedMethod.Builder createMethodBuilder(DexProgramClass clazz, DexMethod method) {
    return clazz.lookupMethod(method) != null
        ? DexEncodedMethod.builder()
        : DexEncodedMethod.syntheticBuilder();
  }
}
