// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.keepanno.ast;

/**
 * Utility class for referencing the various keep annotations and their structure.
 *
 * <p>Use of these references avoids polluting the Java namespace with imports of the java
 * annotations which overlap in name with the actual semantic AST types.
 */
public final class AnnotationConstants {
  public static final class Edge {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepEdge;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepEdge;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String description = "description";
    public static final String bindings = "bindings";
    public static final String preconditions = "preconditions";
    public static final String consequences = "consequences";
  }

  public static final class ForApi {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepForApi;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepForApi;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String description = "description";
    public static final String additionalTargets = "additionalTargets";
    public static final String memberAccess = "memberAccess";
  }

  public static final class UsesReflection {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/UsesReflection;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsesReflection;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String description = "description";
    public static final String value = "value";
    public static final String additionalPreconditions = "additionalPreconditions";
  }

  public static final class UsedByReflection {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/UsedByReflection;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsedByReflection;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String description = "description";
    public static final String preconditions = "preconditions";
    public static final String additionalTargets = "additionalTargets";
  }

  public static final class UsedByNative {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/UsedByNative;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsedByNative;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }
    // Content is the same as UsedByReflection.
  }

  public static final class CheckRemoved {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/CheckRemoved;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/CheckRemoved;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }
  }

  public static final class CheckOptimizedOut {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/CheckOptimizedOut;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/CheckOptimizedOut;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }
  }

  public static final class UsesReflectionToConstruct {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/UsesReflectionToConstruct;";
    private static final String DESCRIPTOR_CONTAINER =
        "Landroidx/annotation/keep/UsesReflectionToConstruct$Container;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsesReflectionToConstruct;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static boolean isKotlinRepeatableContainerDescriptor(String descriptor) {
      return DESCRIPTOR_CONTAINER.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String classSelectionGroup = "class-selection";
    public static final String classConstant = "classConstant";
    public static final String className = "className";
    public static final String includeSubclasses = "includeSubclasses";
    public static final String constructorParametersGroup = "constructor-parameters";
    public static final String parameterTypes = "parameterTypes";
    public static final String parameterTypeNames = "parameterTypeNames";
  }

  public static final class UsesReflectionToAccessMethod {
    private static final String DESCRIPTOR =
        "Landroidx/annotation/keep/UsesReflectionToAccessMethod;";
    private static final String DESCRIPTOR_CONTAINER =
        "Landroidx/annotation/keep/UsesReflectionToAccessMethod$Container;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsesReflectionToAccessMethod;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static boolean isKotlinRepeatableContainerDescriptor(String descriptor) {
      return DESCRIPTOR_CONTAINER.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String classSelectionGroup = "class-selection";
    public static final String classConstant = "classConstant";
    public static final String className = "className";
    public static final String includeSubclasses = "includeSubclasses";
    public static final String methodName = "methodName";
    public static final String constructorParametersGroup = "constructor-parameters";
    public static final String parameterTypes = "parameterTypes";
    public static final String parameterTypeNames = "parameterTypeNames";
    public static final String returnSelectionGroup = "return-selection";
    public static final String returnType = "returnType";
    public static final String returnTypeName = "returnTypeName";
  }

  public static final class UsesReflectionToAccessField {
    private static final String DESCRIPTOR =
        "Landroidx/annotation/keep/UsesReflectionToAccessField;";
    private static final String DESCRIPTOR_CONTAINER =
        "Landroidx/annotation/keep/UsesReflectionToAccessField$Container;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/UsesReflectionToAccessField;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static boolean isKotlinRepeatableContainerDescriptor(String descriptor) {
      return DESCRIPTOR_CONTAINER.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String classSelectionGroup = "class-selection";
    public static final String classConstant = "classConstant";
    public static final String className = "className";
    public static final String includeSubclasses = "includeSubclasses";
    public static final String fieldName = "fieldName";
    public static final String fieldTypeSelectionGroup = "field-type-selection";
    public static final String fieldType = "fieldType";
    public static final String fieldTypeName = "fieldTypeName";
  }

  /** Item properties common to binding items, conditions and targets. */
  public static final class Item {
    public static final String classGroup = "class";
    public static final String classFromBinding = "classFromBinding";
    public static final String memberGroup = "member";
    public static final String memberFromBinding = "memberFromBinding";
    public static final String classNameGroup = "class-name";
    public static final String className = "className";
    public static final String classConstant = "classConstant";
    public static final String classNamePattern = "classNamePattern";
    public static final String instanceOfGroup = "instance-of";
    public static final String instanceOfClassName = "instanceOfClassName";
    public static final String instanceOfClassNameExclusive = "instanceOfClassNameExclusive";
    public static final String instanceOfClassConstant = "instanceOfClassConstant";
    public static final String instanceOfClassConstantExclusive =
        "instanceOfClassConstantExclusive";
    public static final String instanceOfPattern = "instanceOfPattern";
    public static final String classAnnotatedByGroup = "class-annotated-by";
    public static final String classAnnotatedByClassName = "classAnnotatedByClassName";
    public static final String classAnnotatedByClassConstant = "classAnnotatedByClassConstant";
    public static final String classAnnotatedByClassNamePattern =
        "classAnnotatedByClassNamePattern";
    public static final String memberAnnotatedByGroup = "member-annotated-by";
    public static final String memberAnnotatedByClassName = "memberAnnotatedByClassName";
    public static final String memberAnnotatedByClassConstant = "memberAnnotatedByClassConstant";
    public static final String memberAnnotatedByClassNamePattern =
        "memberAnnotatedByClassNamePattern";
    public static final String memberAccess = "memberAccess";
    public static final String methodAnnotatedByGroup = "method-annotated-by";
    public static final String methodAnnotatedByClassName = "methodAnnotatedByClassName";
    public static final String methodAnnotatedByClassConstant = "methodAnnotatedByClassConstant";
    public static final String methodAnnotatedByClassNamePattern =
        "methodAnnotatedByClassNamePattern";
    public static final String methodAccess = "methodAccess";
    public static final String methodNameGroup = "method-name";
    public static final String methodName = "methodName";
    public static final String methodNamePattern = "methodNamePattern";
    public static final String returnTypeGroup = "return-type";
    public static final String methodReturnType = "methodReturnType";
    public static final String methodReturnTypeConstant = "methodReturnTypeConstant";
    public static final String methodReturnTypePattern = "methodReturnTypePattern";
    public static final String parametersGroup = "parameters";
    public static final String methodParameters = "methodParameters";
    public static final String methodParameterTypePatterns = "methodParameterTypePatterns";
    public static final String fieldAnnotatedByGroup = "field-annotated-by";
    public static final String fieldAnnotatedByClassName = "fieldAnnotatedByClassName";
    public static final String fieldAnnotatedByClassConstant = "fieldAnnotatedByClassConstant";
    public static final String fieldAnnotatedByClassNamePattern =
        "fieldAnnotatedByClassNamePattern";
    public static final String fieldAccess = "fieldAccess";
    public static final String fieldNameGroup = "field-name";
    public static final String fieldName = "fieldName";
    public static final String fieldNamePattern = "fieldNamePattern";
    public static final String fieldTypeGroup = "field-type";
    public static final String fieldType = "fieldType";
    public static final String fieldTypeConstant = "fieldTypeConstant";
    public static final String fieldTypePattern = "fieldTypePattern";
  }

  public static final class Binding {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepBinding;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepBinding;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String bindingName = "bindingName";
  }

  public static final class Condition {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepCondition;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepCondition;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }
  }

  public static final class Target {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepTarget;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepTarget;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String kind = "kind";
    public static final String constraintsGroup = "constraints";
    public static final String constraints = "constraints";
    public static final String constraintAdditions = "constraintAdditions";
    public static final String constrainAnnotations = "constrainAnnotations";
  }

  public static final class Kind {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepItemKind;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepItemKind;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String ONLY_CLASS = "ONLY_CLASS";
    public static final String ONLY_MEMBERS = "ONLY_MEMBERS";
    public static final String ONLY_METHODS = "ONLY_METHODS";
    public static final String ONLY_FIELDS = "ONLY_FIELDS";
    public static final String CLASS_AND_MEMBERS = "CLASS_AND_MEMBERS";
    public static final String CLASS_AND_METHODS = "CLASS_AND_METHODS";
    public static final String CLASS_AND_FIELDS = "CLASS_AND_FIELDS";
  }

  public static final class Constraints {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/KeepConstraint;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/KeepConstraint;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String LOOKUP = "LOOKUP";
    public static final String NAME = "NAME";
    public static final String VISIBILITY_RELAX = "VISIBILITY_RELAX";
    public static final String VISIBILITY_RESTRICT = "VISIBILITY_RESTRICT";
    public static final String VISIBILITY_INVARIANT = "VISIBILITY_INVARIANT";
    public static final String CLASS_INSTANTIATE = "CLASS_INSTANTIATE";
    public static final String METHOD_INVOKE = "METHOD_INVOKE";
    public static final String FIELD_GET = "FIELD_GET";
    public static final String FIELD_SET = "FIELD_SET";
    public static final String METHOD_REPLACE = "METHOD_REPLACE";
    public static final String FIELD_REPLACE = "FIELD_REPLACE";
    public static final String NEVER_INLINE = "NEVER_INLINE";
    public static final String CLASS_OPEN_HIERARCHY = "CLASS_OPEN_HIERARCHY";
    public static final String GENERIC_SIGNATURE = "GENERIC_SIGNATURE";
  }

  public static final class MemberAccess {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/MemberAccessFlags;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/MemberAccessFlags;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String NEGATION_PREFIX = "NON_";
    public static final String PUBLIC = "PUBLIC";
    public static final String PROTECTED = "PROTECTED";
    public static final String PACKAGE_PRIVATE = "PACKAGE_PRIVATE";
    public static final String PRIVATE = "PRIVATE";
    public static final String STATIC = "STATIC";
    public static final String FINAL = "FINAL";
    public static final String SYNTHETIC = "SYNTHETIC";
  }

  public static final class MethodAccess {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/MethodAccessFlags;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/MethodAccessFlags;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String SYNCHRONIZED = "SYNCHRONIZED";
    public static final String BRIDGE = "BRIDGE";
    public static final String NATIVE = "NATIVE";
    public static final String ABSTRACT = "ABSTRACT";
    public static final String STRICT_FP = "STRICT_FP";
  }

  public static final class FieldAccess {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/FieldAccessFlags;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/FieldAccessFlags;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String VOLATILE = "VOLATILE";
    public static final String TRANSIENT = "TRANSIENT";
  }

  public static final class StringPattern {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/StringPattern;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/StringPattern;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String stringExactPatternGroup = "string-exact-pattern";
    public static final String exact = "exact";
    public static final String startsWith = "startsWith";
    public static final String endsWith = "endsWith";
  }

  public static final class TypePattern {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/TypePattern;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/TypePattern;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String typePatternGroup = "type-pattern";
    public static final String name = "name";
    public static final String constant = "constant";
    public static final String classNamePattern = "classNamePattern";
    public static final String instanceOfPattern = "instanceOfPattern";
  }

  public static final class ClassNamePattern {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/ClassNamePattern;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/ClassNamePattern;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String classNameGroup = "class-name";
    public static final String name = "name";
    public static final String constant = "constant";
    public static final String classUnqualifiedNameGroup = "class-unqualified-name";
    public static final String unqualifiedName = "unqualifiedName";
    public static final String unqualifiedNamePattern = "unqualifiedNamePattern";
    public static final String packageName = "packageName";
  }

  public static final class InstanceOfPattern {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/InstanceOfPattern;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/InstanceOfPattern;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String inclusive = "inclusive";
    public static final String classNamePattern = "classNamePattern";
  }

  public static final class AnnotationPattern {
    private static final String DESCRIPTOR = "Landroidx/annotation/keep/AnnotationPattern;";
    private static final String DESCRIPTOR_LEGACY =
        "Lcom/android/tools/r8/keepanno/annotations/AnnotationPattern;";

    public static boolean isDescriptor(String descriptor) {
      return DESCRIPTOR.equals(descriptor) || DESCRIPTOR_LEGACY.equals(descriptor);
    }

    public static String getDescriptor() {
      return DESCRIPTOR;
    }

    public static final String annotationNameGroup = "annotation-name";
    public static final String name = "name";
    public static final String constant = "constant";
    public static final String namePattern = "namePattern";
    public static final String retention = "retention";
  }
}
