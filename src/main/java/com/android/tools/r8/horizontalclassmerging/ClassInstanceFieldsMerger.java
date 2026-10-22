// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens.Builder;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields.InstanceFieldInfo;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public interface ClassInstanceFieldsMerger {

  void setClassIdField(DexEncodedField classIdField);

  DexEncodedField[] merge();

  static ClassInstanceFieldsMerger create(
      AppView<?> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      HorizontalMergeGroup group) {
    if (appView.hasClassHierarchy()) {
      return new ClassInstanceFieldsMergerImpl(appView.withClassHierarchy(), lensBuilder, group);
    } else {
      assert group.getInstanceFieldMap().isEmpty();
      assert appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics(appView);
      return new ClassInstanceFieldsMerger() {
        @Override
        public void setClassIdField(DexEncodedField classIdField) {
          throw new UnsupportedOperationException("No instance field merging in D8");
        }

        @Override
        public DexEncodedField[] merge() {
          return DexEncodedField.EMPTY_ARRAY;
        }
      };
    }
  }

  /**
   * Maps each field of {@param source} to a field in {@param mergedFields}, the fields of the
   * merged class. Fields of {@param source} that have no matching field are added to {@param
   * mergedFields} and mapped to themselves.
   */
  static void mapFields(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DexProgramClass source,
      List<DexEncodedField> mergedFields,
      BiConsumer<DexEncodedField, DexEncodedField> consumer) {
    Map<InstanceFieldInfo, Deque<DexEncodedField>> availableFieldsByExactInfo =
        getAvailableFieldsByExactInfo(mergedFields);
    List<DexEncodedField> needsMerge = new ArrayList<>();

    // Pass 1: Match fields that have the exact same type.
    for (DexEncodedField oldField : source.instanceFields()) {
      InstanceFieldInfo info = InstanceFieldInfo.createExact(oldField);
      Deque<DexEncodedField> availableFieldsWithExactSameInfo =
          availableFieldsByExactInfo.get(info);
      if (availableFieldsWithExactSameInfo == null || availableFieldsWithExactSameInfo.isEmpty()) {
        needsMerge.add(oldField);
      } else {
        DexEncodedField newField = availableFieldsWithExactSameInfo.removeFirst();
        consumer.accept(oldField, newField);
        if (availableFieldsWithExactSameInfo.isEmpty()) {
          availableFieldsByExactInfo.remove(info);
        }
      }
    }

    // Pass 2: Match fields that do not have the same reference type, or add unmatched fields.
    Map<InstanceFieldInfo, Deque<DexEncodedField>> availableFieldsByRelaxedInfo =
        getAvailableFieldsByRelaxedInfo(appView, availableFieldsByExactInfo);
    for (DexEncodedField oldField : needsMerge) {
      Deque<DexEncodedField> availableFieldsWithRelaxedInfo =
          availableFieldsByRelaxedInfo.get(InstanceFieldInfo.createRelaxed(appView, oldField));
      if (availableFieldsWithRelaxedInfo != null && !availableFieldsWithRelaxedInfo.isEmpty()) {
        assert oldField.getType().isReferenceType();
        DexEncodedField newField = availableFieldsWithRelaxedInfo.removeFirst();
        assert newField.getType().isReferenceType();
        consumer.accept(oldField, newField);
      } else {
        mergedFields.add(oldField);
        consumer.accept(oldField, oldField);
      }
    }
  }

  static Map<InstanceFieldInfo, Deque<DexEncodedField>> getAvailableFieldsByExactInfo(
      Iterable<DexEncodedField> fields) {
    Map<InstanceFieldInfo, Deque<DexEncodedField>> availableFieldsByInfo = new LinkedHashMap<>();
    for (DexEncodedField field : fields) {
      availableFieldsByInfo
          .computeIfAbsent(InstanceFieldInfo.createExact(field), ignore -> new ArrayDeque<>())
          .add(field);
    }
    return availableFieldsByInfo;
  }

  static Map<InstanceFieldInfo, Deque<DexEncodedField>> getAvailableFieldsByRelaxedInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<InstanceFieldInfo, Deque<DexEncodedField>> availableFieldsByExactInfo) {
    Map<InstanceFieldInfo, Deque<DexEncodedField>> availableFieldsByRelaxedInfo =
        new LinkedHashMap<>();
    availableFieldsByExactInfo.forEach(
        (info, fields) ->
            availableFieldsByRelaxedInfo
                .computeIfAbsent(info.toInfoWithRelaxedType(appView), ignore -> new ArrayDeque<>())
                .addAll(fields));
    return availableFieldsByRelaxedInfo;
  }

  class ClassInstanceFieldsMergerImpl implements ClassInstanceFieldsMerger {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final HorizontalMergeGroup group;

    @SuppressWarnings("BadImport")
    private final Builder lensBuilder;

    private DexEncodedField classIdField;

    private final Set<DexField> committedFields = Sets.newIdentityHashSet();

    private ClassInstanceFieldsMergerImpl(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        HorizontalClassMergerGraphLens.Builder lensBuilder,
        HorizontalMergeGroup group) {
      this.appView = appView;
      this.group = group;
      this.lensBuilder = lensBuilder;
    }

    @Override
    public void setClassIdField(DexEncodedField classIdField) {
      this.classIdField = classIdField;
    }

    @Override
    public DexEncodedField[] merge() {
      assert group.hasInstanceFieldMap();
      List<DexEncodedField> newFields = new ArrayList<>();
      if (classIdField != null) {
        newFields.add(classIdField);
        committedFields.add(classIdField.getReference());
      }
      group
          .getInstanceFieldMap()
          .forEachManyToOneMapping(
              (fieldsToMerge, targetField) -> {
                DexEncodedField newField = mergeFields(targetField, fieldsToMerge);
                newFields.add(newField);
                committedFields.add(newField.getReference());
              });
      return newFields.toArray(DexEncodedField.EMPTY_ARRAY);
    }

    /**
     * Merges {@param fieldsToMerge}, which contains at most one field from each class in the group,
     * including {@param targetField}, into a single field on the target class.
     */
    private DexEncodedField mergeFields(
        DexEncodedField targetField, Set<DexEncodedField> fieldsToMerge) {
      fixAccessFlags(targetField, fieldsToMerge);

      DexType newFieldType =
          needsRelaxedType(targetField, fieldsToMerge)
              ? DexTypeUtils.computeApiSafeLeastUpperBound(
                  appView, Iterables.transform(fieldsToMerge, DexEncodedField::getType))
              : targetField.getType();
      DexField newFieldReference =
          appView
              .dexItemFactory()
              .createFreshFieldNameWithoutHolder(
                  group.getTarget().getType(),
                  newFieldType,
                  targetField.getName().toString(),
                  fieldReference -> isFresh(fieldReference, targetField));

      if (fieldsToMerge.size() > 1
          || targetField.getReference().isNotIdenticalTo(newFieldReference)) {
        lensBuilder.recordNewFieldSignature(
            Iterables.transform(fieldsToMerge, DexEncodedField::getReference),
            newFieldReference,
            targetField.getReference());
      }
      return targetField.toTypeSubstitutedField(appView, newFieldReference);
    }

    // A relaxed field type, or a field moved from a source class, could make the merged field
    // collide with another field of the target.
    private boolean isFresh(DexField fieldReference, DexEncodedField targetField) {
      if (committedFields.contains(fieldReference)) {
        return false;
      }
      DexEncodedField existingField = group.getTarget().lookupField(fieldReference);
      return existingField == null
          || existingField.getReference().isIdenticalTo(targetField.getReference());
    }

    private void fixAccessFlags(DexEncodedField newField, Collection<DexEncodedField> oldFields) {
      if (newField.isSynthetic() && Iterables.any(oldFields, oldField -> !oldField.isSynthetic())) {
        newField.getAccessFlags().demoteFromSynthetic();
      }
      assert Iterables.all(oldFields, oldField -> oldField.isFinal() == newField.isFinal());
      assert Iterables.all(oldFields, oldField -> oldField.isTransient() == newField.isTransient());
      assert Iterables.all(oldFields, oldField -> oldField.isVolatile() == newField.isVolatile());
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean needsRelaxedType(
        DexEncodedField targetField, Iterable<DexEncodedField> sourceFields) {
      return Iterables.any(
          sourceFields, sourceField -> sourceField.getType() != targetField.getType());
    }
  }
}
