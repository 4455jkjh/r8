// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;

public class InstrumentedReflectiveMethodList {

  private DexItemFactory factory;
  private ReflectiveReferences reflectiveReferences;

  public InstrumentedReflectiveMethodList(DexItemFactory factory) {
    this.factory = factory;
    reflectiveReferences = new ReflectiveReferences(factory);
  }

  public Set<DexMethod> getInstrumentedMethodsForTesting() {
    return getInstrumentedMethodsAndTargets().keySet();
  }

  Map<DexMethod, DexMethod> getInstrumentedMethodsAndTargets() {
    ImmutableMap.Builder<DexMethod, DexMethod> builder = ImmutableMap.builder();

    builder.put(
        factory.classMethods.newInstance,
        getMethodReferenceWithClassParameter("onClassNewInstance"));
    builder.put(
        factory.classMethods.getDeclaredMethod,
        getMethodReferenceWithClassMethodNameAndParameters("onClassGetDeclaredMethod"));
    builder.put(
        factory.classMethods.forName, getMethodReferenceWithStringParameter("onClassForName"));
    builder.put(
        factory.classMethods.getDeclaredField,
        getMethodReferenceWithClassAndStringParameter("onClassGetDeclaredField"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.createArrayType(1, factory.methodType)),
            "getDeclaredMethods"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredMethods"));
    builder.put(
        factory.classMethods.getName, getMethodReferenceWithClassParameter("onClassGetName"));
    builder.put(
        factory.classMethods.getCanonicalName,
        getMethodReferenceWithClassParameter("onClassGetCanonicalName"));
    builder.put(
        factory.classMethods.getSimpleName,
        getMethodReferenceWithClassParameter("onClassGetSimpleName"));
    builder.put(
        factory.classMethods.getTypeName,
        getMethodReferenceWithClassParameter("onClassGetTypeName"));
    builder.put(
        factory.classMethods.getSuperclass,
        getMethodReferenceWithClassParameter("onClassGetSuperclass"));

    DexProto toBoolean = factory.createProto(factory.booleanType);
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isAnnotation"),
        getMethodReferenceWithClassParameter("onClassIsAnnotation"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isAnonymousClass"),
        getMethodReferenceWithClassParameter("onClassIsAnonymousClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isArray"),
        getMethodReferenceWithClassParameter("onClassIsArray"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isEnum"),
        getMethodReferenceWithClassParameter("onClassIsEnum"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isHidden"),
        getMethodReferenceWithClassParameter("onClassIsHidden"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isInterface"),
        getMethodReferenceWithClassParameter("onClassIsInterface"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isLocalClass"),
        getMethodReferenceWithClassParameter("onClassIsLocalClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isMemberClass"),
        getMethodReferenceWithClassParameter("onClassIsMemberClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isPrimitive"),
        getMethodReferenceWithClassParameter("onClassIsPrimitive"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isRecord"),
        getMethodReferenceWithClassParameter("onClassIsRecord"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isSealed"),
        getMethodReferenceWithClassParameter("onClassIsSealed"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isSynthetic"),
        getMethodReferenceWithClassParameter("onClassIsSynthetic"));

    return builder.build();
  }

  private DexMethod getMethodReferenceWithClassParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.classType);
  }

  private DexMethod getMethodReferenceWithClassAndStringParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.classType, factory.stringType);
  }

  private DexMethod getMethodReferenceWithStringParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.stringType);
  }

  private DexMethod getMethodReferenceWithParameterTypes(String name, DexType... dexTypes) {
    return factory.createMethod(
        reflectiveReferences.reflectiveOracleType,
        factory.createProto(factory.voidType, dexTypes),
        name);
  }

  private DexMethod getMethodReferenceWithClassMethodNameAndParameters(String name) {
    return factory.createMethod(
        reflectiveReferences.reflectiveOracleType,
        factory.createProto(
            factory.voidType, factory.classType, factory.stringType, factory.classArrayType),
        name);
  }
}
