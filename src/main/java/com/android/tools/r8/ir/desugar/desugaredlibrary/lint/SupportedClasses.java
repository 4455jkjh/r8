// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SupportedClasses {
  private final Map<DexType, SupportedClass> supportedClasses;

  public void forEachClass(Consumer<SupportedClass> consumer) {
    supportedClasses.values().forEach(consumer);
  }

  SupportedClasses(Map<DexType, SupportedClass> supportedClasses) {
    this.supportedClasses = supportedClasses;
  }

  public static class SupportedClass {

    private final DexClass clazz;
    private final ClassAnnotation classAnnotation;
    private final List<DexEncodedMethod> supportedMethods;
    private final List<DexEncodedField> supportedFields;
    private final Map<DexMethod, MethodAnnotation> methodAnnotations;
    private final Map<DexField, FieldAnnotation> fieldAnnotations;

    private SupportedClass(
        DexClass clazz,
        ClassAnnotation classAnnotation,
        List<DexEncodedMethod> supportedMethods,
        List<DexEncodedField> supportedFields,
        Map<DexMethod, MethodAnnotation> methodAnnotations,
        Map<DexField, FieldAnnotation> fieldAnnotations) {
      this.clazz = clazz;
      this.classAnnotation = classAnnotation;
      this.supportedMethods = supportedMethods;
      this.supportedFields = supportedFields;
      this.methodAnnotations = methodAnnotations;
      this.fieldAnnotations = fieldAnnotations;
    }

    public DexType getType() {
      return clazz.type;
    }

    public DexClass getClazz() {
      return clazz;
    }

    public ClassAnnotation getClassAnnotation() {
      return classAnnotation;
    }

    public List<DexEncodedMethod> getSupportedMethods() {
      return supportedMethods;
    }

    public void forEachMethodAndAnnotation(
        BiConsumer<DexEncodedMethod, MethodAnnotation> biConsumer) {
      for (DexEncodedMethod supportedMethod : supportedMethods) {
        biConsumer.accept(supportedMethod, getMethodAnnotation(supportedMethod.getReference()));
      }
    }

    public MethodAnnotation getMethodAnnotation(DexMethod method) {
      return methodAnnotations.get(method);
    }

    public void forEachFieldAndAnnotation(BiConsumer<DexEncodedField, FieldAnnotation> biConsumer) {
      for (DexEncodedField supportedField : supportedFields) {
        biConsumer.accept(supportedField, getFieldAnnotation(supportedField.getReference()));
      }
    }

    public FieldAnnotation getFieldAnnotation(DexField field) {
      return fieldAnnotations.get(field);
    }

    static Builder builder(DexClass clazz) {
      return new Builder(clazz);
    }

    private static class Builder {

      private final DexClass clazz;
      private ClassAnnotation classAnnotation;
      private final List<DexEncodedMethod> supportedMethods = new ArrayList<>();
      private final List<DexEncodedField> supportedFields = new ArrayList<>();
      private final Map<DexMethod, MethodAnnotation> methodAnnotations = new HashMap<>();
      private final Map<DexField, FieldAnnotation> fieldAnnotations = new HashMap<>();

      private Builder(DexClass clazz) {
        this.clazz = clazz;
      }

      void forEachMethods(BiConsumer<DexClass, Collection<DexEncodedMethod>> biConsumer) {
        biConsumer.accept(clazz, supportedMethods);
      }

      void forEachMethod(BiConsumer<DexClass, DexEncodedMethod> biConsumer) {
        for (DexEncodedMethod dexEncodedMethod : supportedMethods) {
          biConsumer.accept(clazz, dexEncodedMethod);
        }
      }

      void forEachField(BiConsumer<DexClass, DexEncodedField> biConsumer) {
        for (DexEncodedField dexEncodedField : supportedFields) {
          biConsumer.accept(clazz, dexEncodedField);
        }
      }

      void addSupportedMethod(DexEncodedMethod method) {
        assert method.getHolderType() == clazz.type;
        supportedMethods.add(method);
      }

      void addSupportedField(DexEncodedField field) {
        assert field.getHolderType() == clazz.type;
        supportedFields.add(field);
      }

      void annotateClass(ClassAnnotation annotation) {
        assert annotation != null;
        assert classAnnotation == null || annotation == classAnnotation;
        classAnnotation = annotation;
      }

      void annotateMethod(DexMethod method, MethodAnnotation annotation) {
        assert method.getHolderType() == clazz.type;
        MethodAnnotation prev =
            methodAnnotations.getOrDefault(method, MethodAnnotation.getDefault());
        methodAnnotations.put(method, annotation.combine(prev));
      }

      void annotateField(DexField field, FieldAnnotation annotation) {
        assert field.getHolderType() == clazz.type;
        FieldAnnotation prev = fieldAnnotations.getOrDefault(field, FieldAnnotation.getDefault());
        fieldAnnotations.put(field, annotation.combine(prev));
      }

      MethodAnnotation getMethodAnnotation(DexMethod method) {
        return methodAnnotations.get(method);
      }

      SupportedClass build() {
        supportedMethods.sort(Comparator.comparing(DexEncodedMethod::getReference));
        supportedFields.sort(Comparator.comparing(DexEncodedField::getReference));
        return new SupportedClass(
            clazz,
            classAnnotation,
            ImmutableList.copyOf(supportedMethods),
            ImmutableList.copyOf(supportedFields),
            methodAnnotations,
            fieldAnnotations);
      }
    }
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    Map<DexType, SupportedClass.Builder> supportedClassBuilders = new IdentityHashMap<>();

    ClassAnnotation getClassAnnotation(DexType type) {
      SupportedClass.Builder builder = supportedClassBuilders.get(type);
      assert builder != null;
      return builder.classAnnotation;
    }

    void forEachClassAndMethods(BiConsumer<DexClass, Collection<DexEncodedMethod>> biConsumer) {
      supportedClassBuilders
          .values()
          .forEach(classBuilder -> classBuilder.forEachMethods(biConsumer));
    }

    void forEachClassAndMethod(BiConsumer<DexClass, DexEncodedMethod> biConsumer) {
      supportedClassBuilders
          .values()
          .forEach(classBuilder -> classBuilder.forEachMethod(biConsumer));
    }

    void forEachClassAndField(BiConsumer<DexClass, DexEncodedField> biConsumer) {
      supportedClassBuilders
          .values()
          .forEach(classBuilder -> classBuilder.forEachField(biConsumer));
    }

    void addSupportedMethod(DexClass holder, DexEncodedMethod method) {
      SupportedClass.Builder classBuilder =
          supportedClassBuilders.computeIfAbsent(
              holder.type, clazz -> SupportedClass.builder(holder));
      classBuilder.addSupportedMethod(method);
    }

    void addSupportedField(DexClass holder, DexEncodedField field) {
      SupportedClass.Builder classBuilder =
          supportedClassBuilders.computeIfAbsent(
              holder.type, clazz -> SupportedClass.builder(holder));
      classBuilder.addSupportedField(field);
    }

    void annotateClass(DexType type, ClassAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(type);
      assert classBuilder != null;
      classBuilder.annotateClass(annotation);
    }

    void annotateMethod(DexMethod method, MethodAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      assert classBuilder != null;
      classBuilder.annotateMethod(method, annotation);
    }

    void annotateField(DexField field, FieldAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(field.getHolderType());
      assert classBuilder != null;
      classBuilder.annotateField(field, annotation);
    }

    void annotateMethodIfPresent(DexMethod method, MethodAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      if (classBuilder == null) {
        return;
      }
      annotateMethod(method, annotation);
    }

    MethodAnnotation getMethodAnnotation(DexMethod method) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      assert classBuilder != null;
      return classBuilder.getMethodAnnotation(method);
    }

    SupportedClasses build() {
      Map<DexType, SupportedClass> map = new IdentityHashMap<>();
      supportedClassBuilders.forEach(
          (type, classBuilder) -> {
            map.put(type, classBuilder.build());
          });
      return new SupportedClasses(ImmutableSortedMap.copyOf(map));
    }
  }

  static class ClassAnnotation {

    private final boolean additionalMembersOnClass;
    private final boolean fullySupported;
    // Methods in latest android.jar but unsupported.
    private final List<DexMethod> unsupportedMethods;

    public ClassAnnotation(boolean fullySupported, List<DexMethod> unsupportedMethods) {
      this.additionalMembersOnClass = false;
      this.fullySupported = fullySupported;
      unsupportedMethods.sort(Comparator.naturalOrder());
      this.unsupportedMethods = ImmutableList.copyOf(unsupportedMethods);
    }

    private ClassAnnotation() {
      this.additionalMembersOnClass = true;
      this.fullySupported = false;
      this.unsupportedMethods = ImmutableList.of();
    }

    private static final ClassAnnotation ADDITIONNAL_MEMBERS_ON_CLASS = new ClassAnnotation();

    public static ClassAnnotation getAdditionnalMembersOnClass() {
      return ADDITIONNAL_MEMBERS_ON_CLASS;
    }

    public boolean isAdditionalMembersOnClass() {
      return additionalMembersOnClass;
    }

    public boolean isFullySupported() {
      return fullySupported;
    }

    public List<DexMethod> getUnsupportedMethods() {
      return unsupportedMethods;
    }
  }

  public abstract static class MemberAnnotation {
    final boolean unsupportedInMinApiRange;
    final int minRange;
    final int maxRange;

    MemberAnnotation(boolean unsupportedInMinApiRange, int minRange, int maxRange) {
      this.unsupportedInMinApiRange = unsupportedInMinApiRange;
      this.minRange = minRange;
      this.maxRange = maxRange;
    }

    public boolean isUnsupportedInMinApiRange() {
      return unsupportedInMinApiRange;
    }

    public int getMinRange() {
      return minRange;
    }

    public int getMaxRange() {
      return maxRange;
    }

    int combineRange(MemberAnnotation other) {
      int newMin, newMax;
      if (!unsupportedInMinApiRange && !other.unsupportedInMinApiRange) {
        newMin = newMax = -1;
      } else if (!unsupportedInMinApiRange || !other.unsupportedInMinApiRange) {
        newMin = unsupportedInMinApiRange ? minRange : other.minRange;
        newMax = unsupportedInMinApiRange ? maxRange : other.maxRange;
      } else {
        // Merge ranges if contiguous or throw.
        if (maxRange == other.minRange - 1) {
          newMin = minRange;
          newMax = other.maxRange;
        } else if (other.maxRange == minRange - 1) {
          newMin = other.minRange;
          newMax = maxRange;
        } else {
          // 20 is missing, so if maxRange or minRange are 19 the following is 21.
          if (maxRange == 19 && other.minRange == 21) {
            newMin = minRange;
            newMax = other.maxRange;
          } else if (other.maxRange == 19 && minRange == 21) {
            newMin = other.minRange;
            newMax = maxRange;
          } else {
            throw new RuntimeException("Cannot merge ranges.");
          }
        }
      }
      assert newMax < (1 << 15) && newMin < (1 << 15);
      return (newMax << 16) + newMin;
    }
  }

  public static class FieldAnnotation extends MemberAnnotation {

    private static final FieldAnnotation DEFAULT = new FieldAnnotation(false, -1, -1);

    FieldAnnotation(boolean unsupportedInMinApiRange, int minRange, int maxRange) {
      super(unsupportedInMinApiRange, minRange, maxRange);
    }

    public static FieldAnnotation getDefault() {
      return DEFAULT;
    }

    public static FieldAnnotation createMissingInMinApi(int api) {
      return new FieldAnnotation(true, api, api);
    }

    public FieldAnnotation combine(FieldAnnotation other) {
      if (this == getDefault()) {
        return other;
      }
      if (other == getDefault()) {
        return this;
      }
      int newRange = combineRange(other);
      int newMax = newRange >> 16;
      int newMin = newRange & 0xFF;
      return new FieldAnnotation(
          unsupportedInMinApiRange || other.unsupportedInMinApiRange, newMin, newMax);
    }
  }

  public static class MethodAnnotation extends MemberAnnotation {

    private static final MethodAnnotation COVARIANT_RETURN_SUPPORTED =
        new MethodAnnotation(false, false, true, false, -1, -1);
    private static final MethodAnnotation DEFAULT =
        new MethodAnnotation(false, false, false, false, -1, -1);
    private static final MethodAnnotation PARALLEL_STREAM_METHOD =
        new MethodAnnotation(true, false, false, false, -1, -1);
    private static final MethodAnnotation MISSING_FROM_LATEST_ANDROID_JAR =
        new MethodAnnotation(false, true, false, false, -1, -1);

    // ParallelStream methods are not supported when the runtime api level is strictly below 21.
    final boolean parallelStreamMethod;
    // Methods not in the latest android jar but still fully supported.
    final boolean missingFromLatestAndroidJar;
    // Methods not supported in a given min api range.
    final boolean covariantReturnSupported;
    MethodAnnotation(
        boolean parallelStreamMethod,
        boolean missingFromLatestAndroidJar,
        boolean covariantReturnSupported,
        boolean unsupportedInMinApiRange,
        int minRange,
        int maxRange) {
      super(unsupportedInMinApiRange, minRange, maxRange);
      this.parallelStreamMethod = parallelStreamMethod;
      this.missingFromLatestAndroidJar = missingFromLatestAndroidJar;
      this.covariantReturnSupported = covariantReturnSupported;
    }

    public static MethodAnnotation getCovariantReturnSupported() {
      return COVARIANT_RETURN_SUPPORTED;
    }

    public static MethodAnnotation getDefault() {
      return DEFAULT;
    }

    public static MethodAnnotation getParallelStreamMethod() {
      return PARALLEL_STREAM_METHOD;
    }

    public static MethodAnnotation getMissingFromLatestAndroidJar() {
      return MISSING_FROM_LATEST_ANDROID_JAR;
    }

    public static MethodAnnotation createMissingInMinApi(int api) {
      return new MethodAnnotation(false, false, false, true, api, api);
    }

    public boolean isCovariantReturnSupported() {
      return covariantReturnSupported;
    }

    public MethodAnnotation combine(MethodAnnotation other) {
      if (this == getDefault()) {
        return other;
      }
      if (other == getDefault()) {
        return this;
      }
      int newRange = combineRange(other);
      int newMax = newRange >> 16;
      int newMin = newRange & 0xFF;
      return new MethodAnnotation(
          parallelStreamMethod || other.parallelStreamMethod,
          missingFromLatestAndroidJar || other.missingFromLatestAndroidJar,
          covariantReturnSupported || other.covariantReturnSupported,
          unsupportedInMinApiRange || other.unsupportedInMinApiRange,
          newMin,
          newMax);
    }
  }
}
