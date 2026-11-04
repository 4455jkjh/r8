// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MutableFieldAccessInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * EnumLite proto enums include the following code: <code>
 * private static final EnumLiteMap<ProtoEnum> internalValueMap =
 *   new EnumLiteMap<ProtoEnum>() {
 *     public ProtoEnum findValueByNumber(int number) {
 *       return ProtoEnum.forNumber(number);
 *     }
 *   };
 * </code>
 *
 * <p>If the field internalValueMap on the EnumLite is effectively unused (never read), the
 * anonymous subclass of EnumLiteMap is effectively dead. R8 figures that out however too late,
 * during the second round of tree shaking, and the bridge findValueByNumber in the anonymous
 * subclass of EnumLiteMap prevents the EnumLite to be unboxed.
 *
 * <p>This class prematurely clears the virtual methods of the anonymous subclasses of EnumLiteMap,
 * when it can prove that the internalValueMap field of the corresponding EnumLite is never read.
 */
public class EnumLiteProtoShrinker {

  private AppView<AppInfoWithLiveness> appView;
  private ProtoReferences references;
  private Set<DexType> deadEnumLiteMaps = Sets.newIdentityHashSet();
  private final Map<DexType, DexField> protoEnumLiteValueFields = new IdentityHashMap<>();

  public EnumLiteProtoShrinker(AppView<AppInfoWithLiveness> appView, ProtoReferences references) {
    this.appView = appView;
    this.references = references;
  }

  public Set<DexType> getDeadEnumLiteMaps() {
    return deadEnumLiteMaps;
  }

  public DexField getProtoEnumLiteValueField(DexType enumType) {
    return protoEnumLiteValueFields.get(enumType);
  }

  public boolean hasProtoEnumLiteValueField(DexType enumType) {
    return protoEnumLiteValueFields.containsKey(enumType);
  }

  private DexField createInternalValueMapField(DexType holder) {
    return appView
        .dexItemFactory()
        .createField(holder, references.enumLiteMapType, references.internalValueMapFieldName);
  }

  public void clearDeadEnumLiteMaps(PrunedItems.Builder prunedItemsBuilder) {
    assert appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled();
    // The optimization only enables further enums to be unboxed, no point to run it if enum
    // unboxing is disabled.
    if (!appView.options().enableEnumUnboxing) {
      return;
    }
    // The optimization relies on shrinking and member value propagation to actually clear
    // the anonymous subclasses of EnumLiteMap.
    if (!appView.options().isShrinking()) {
      return;
    }
    internalClearDeadEnumLiteMaps(prunedItemsBuilder);
  }

  private void internalClearDeadEnumLiteMaps(PrunedItems.Builder prunedItemsBuilder) {
    boolean enableProtoEnumOptimization =
        appView.options().protoShrinking().isProtoEnumOptimizationEnabled();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isDeadEnumLiteMap(clazz)) {
        deadEnumLiteMaps.add(clazz.getType());
        // Clears the EnumLiteMap methods to avoid them being IR processed.
        clazz
            .virtualMethods()
            .forEach(method -> prunedItemsBuilder.addRemovedMethod(method.getReference()));
        clazz.setVirtualMethods(DexEncodedMethod.EMPTY_ARRAY);
      } else if (enableProtoEnumOptimization) {
        recordProtoEnumLiteCandidate(clazz, prunedItemsBuilder);
      }
    }
  }

  private void recordProtoEnumLiteCandidate(
      DexProgramClass clazz, PrunedItems.Builder prunedItemsBuilder) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    if (!clazz.isEnum()
        || !clazz.isFinal()
        || !clazz.getInterfaces().contains(references.enumLiteType)
        || !appInfo.isLiveProgramClass(clazz)) {
      return;
    }
    DexItemFactory factory = appView.dexItemFactory();
    InternalOptions options = appView.options();
    KeepInfoCollection keepInfo = appInfo.getKeepInfo();
    if (keepInfo.getClassInfo(clazz).isPinned(options)) {
      return;
    }
    DexField valueFieldRef =
        factory.createField(clazz.getType(), factory.intType, factory.valueString);
    DexEncodedField valueField = clazz.lookupInstanceField(valueFieldRef);
    if (valueField == null || keepInfo.getFieldInfo(valueField, clazz).isPinned(options)) {
      return;
    }
    ProgramMethod toStringMethod = clazz.lookupProgramMethod(factory.objectMembers.toString);
    if (toStringMethod != null) {
      if (keepInfo.getMethodInfo(toStringMethod).isPinned(options)
          || !isProtocGeneratedToString(toStringMethod)) {
        return;
      }
      prunedItemsBuilder.addRemovedMethod(toStringMethod.getReference());
      clazz.removeMethod(toStringMethod.getReference());
    }
    // The enum unboxer implements toString() on these enums by reading the value field. If nothing
    // else reads the field (e.g., getNumber() is unused), record a read so that the writes in
    // <init> are not removed as writes to an unread field before the unboxer needs the values.
    MutableFieldAccessInfo fieldAccessInfo =
        appInfo.getMutableFieldAccessInfoCollection().get(valueField.getReference());
    if (fieldAccessInfo != null && !fieldAccessInfo.isRead() && clazz.hasClassInitializer()) {
      fieldAccessInfo.recordRead(valueField.getReference(), clazz.getProgramClassInitializer());
    }
    protoEnumLiteValueFields.put(clazz.getType(), valueField.getReference());
  }

  private boolean isProtocGeneratedToString(ProgramMethod toStringMethod) {
    if (!toStringMethod.getDefinition().hasCode()) {
      return false;
    }
    boolean seenNumberPrefix = false;
    boolean seenNamePrefix = false;
    IRCode code = toStringMethod.buildIR(appView, MethodConversionOptions.nonConverting());
    for (ConstString constString : code.<ConstString>instructions(Instruction::isConstString)) {
      DexString value = constString.getValue();
      if (value.isIdenticalTo(references.enumLiteToStringNumberPrefix)) {
        seenNumberPrefix = true;
      } else if (value.isIdenticalTo(references.enumLiteToStringNamePrefix)) {
        seenNamePrefix = true;
      }
    }
    return seenNumberPrefix && seenNamePrefix;
  }

  public boolean isDeadEnumLiteMap(DexProgramClass clazz) {
    if (clazz.getInterfaces().contains(references.enumLiteMapType)) {
      DexProgramClass enumLite = computeCorrespondingEnumLite(clazz);
      if (enumLite != null) {
        DexClassAndField field =
            enumLite.lookupClassField(createInternalValueMapField(enumLite.getType()));
        if (field == null) {
          return false;
        }
        if (appView.appInfo().isFieldRead(field)) {
          return false;
        }
        return !appView.appInfo().isFieldWritten(field)
            || appView.appInfo().isStaticFieldWrittenOnlyInEnclosingStaticInitializer(field);
      }
    }
    return false;
  }

  /**
   * Each EnumLiteMap subclass has only two virtual methods findValueByNumber:
   *
   * <ul>
   *   <li>EnumLite findValueByNumber(int)
   *   <li>ConcreteEnumLiteSubType findValueByNumber(int)
   * </ul>
   *
   * <p>The method with the EnumLite return type is the bridge, we extract the concrete EnumLite
   * subtype from the other method return type.
   *
   * <p>We bail out if other virtual methods than the two expected ones are found and return null.
   */
  @SuppressWarnings("ReferenceEquality")
  private DexProgramClass computeCorrespondingEnumLite(DexProgramClass enumLiteMap) {
    if (enumLiteMap.getMethodCollection().numberOfVirtualMethods() != 2) {
      return null;
    }
    DexType enumLiteCandidate = null;
    for (DexEncodedMethod virtualMethod : enumLiteMap.virtualMethods()) {
      if (!matchesFindValueByNumberMethod(virtualMethod.getReference())) {
        return null;
      }
      if (virtualMethod.getReturnType() == references.enumLiteType) {
        continue;
      }
      if (enumLiteCandidate != null) {
        return null;
      }
      enumLiteCandidate = virtualMethod.getReturnType();
    }
    if (enumLiteCandidate == null) {
      return null;
    }
    DexProgramClass enumLite = appView.programDefinitionFor(enumLiteCandidate, enumLiteMap);
    if (enumLite != null
        && enumLite.isEnum()
        && enumLite.interfaces.contains(references.enumLiteType)) {
      return enumLite;
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean matchesFindValueByNumberMethod(DexMethod method) {
    return method.name == references.findValueByNumberName
        && method.getArity() == 1
        && method.getParameters().values[0] == appView.dexItemFactory().intType;
  }

  public void verifyDeadEnumLiteMapsAreDead() {
    for (DexType deadEnumLiteMap : deadEnumLiteMaps) {
      if (appView.appInfo().definitionForWithoutExistenceAssert(deadEnumLiteMap) != null) {
        throw new CompilationError(
            "EnumLite Proto Shrinker failure: Type "
                + deadEnumLiteMap
                + " was assumed to be dead during optimizations, but it is not.");
      }
    }
  }
}
