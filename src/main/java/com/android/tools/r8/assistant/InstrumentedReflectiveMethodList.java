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

    // java.lang.Class methods

    builder.put(
        factory.classMethods.newInstance,
        getMethodReferenceWithClassParameter("onClassNewInstance"));
    builder.put(
        factory.classMethods.forName,
        getMethodReferenceWithStringParameter("onClassForNameDefault"));
    builder.put(
        factory.classMethods.forName3,
        getMethodReferenceWithParameterTypes(
            "onClassForNameWithLoader",
            factory.stringType,
            factory.booleanType,
            factory.classLoaderType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.classType, factory.classType),
            "asSubclass"),
        getMethodReferenceWithParameterTypes(
            "onClassAsSubclass", factory.classType, factory.classType));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.objectType, factory.objectType), "cast"),
        getMethodReferenceWithParameterTypes("onClassCast", factory.classType, factory.objectType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.booleanType, factory.objectType),
            "isInstance"),
        getMethodReferenceWithParameterTypes(
            "onClassIsInstance", factory.classType, factory.objectType));

    builder.put(
        factory.classMethods.getDeclaredMethod,
        getMethodReferenceWithClassMethodNameAndParameters("onClassGetDeclaredMethod"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.methodType.toArrayType(factory)),
            "getDeclaredMethods"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredMethods"));
    builder.put(
        factory.classMethods.getDeclaredField,
        getMethodReferenceWithClassAndStringParameter("onClassGetDeclaredField"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.fieldType.toArrayType(factory)),
            "getDeclaredFields"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredFields"));
    builder.put(
        factory.classMethods.getDeclaredConstructor,
        getMethodReferenceWithParameterTypes(
            "onClassGetDeclaredConstructor", factory.classType, factory.classArrayType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.constructorType.toArrayType(factory)),
            "getDeclaredConstructors"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredConstructors"));
    builder.put(
        factory.classMethods.getMethod,
        getMethodReferenceWithParameterTypes(
            "onClassGetMethod", factory.classType, factory.stringType, factory.classArrayType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.methodType.toArrayType(factory)),
            "getMethods"),
        getMethodReferenceWithClassParameter("onClassGetMethods"));
    builder.put(
        factory.classMethods.getField,
        getMethodReferenceWithParameterTypes(
            "onClassGetField", factory.classType, factory.stringType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.fieldType.toArrayType(factory)),
            "getFields"),
        getMethodReferenceWithClassParameter("onClassGetFields"));
    builder.put(
        factory.classMethods.getConstructor,
        getMethodReferenceWithParameterTypes(
            "onClassGetConstructor", factory.classType, factory.classArrayType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.constructorType.toArrayType(factory)),
            "getConstructors"),
        getMethodReferenceWithClassParameter("onClassGetConstructors"));

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
    DexType reflectType = factory.createType("Ljava/lang/reflect/Type;");
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(reflectType), "getGenericSuperclass"),
        getMethodReferenceWithClassParameter("onClassGetGenericSuperclass"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(reflectType.toArrayType(factory)),
            "getGenericInterfaces"),
        getMethodReferenceWithClassParameter("onClassGetGenericInterfaces"));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classType), "getComponentType"),
        getMethodReferenceWithClassParameter("onClassGetComponentType"));
    builder.put(
        factory.classMethods.getPackage, getMethodReferenceWithClassParameter("onClassGetPackage"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.booleanType, factory.classType),
            "isAssignableFrom"),
        getMethodReferenceWithParameterTypes(
            "onClassIsAssignableFrom", factory.classType, factory.classType));
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

    // AtomicFieldUpdater methods.

    builder.put(
        factory.atomicIntUpdaterMethods.newUpdater,
        getMethodReferenceWithClassAndStringParameter("onAtomicIntegerFieldUpdaterNewUpdater"));
    builder.put(
        factory.atomicLongUpdaterMethods.newUpdater,
        getMethodReferenceWithClassAndStringParameter("onAtomicLongFieldUpdaterNewUpdater"));
    builder.put(
        factory.atomicReferenceUpdaterMethods.newUpdater,
        getMethodReferenceWithParameterTypes(
            "onAtomicReferenceFieldUpdaterNewUpdater",
            factory.classType,
            factory.classType,
            factory.stringType));

    // ServiceLoader methods.

    builder.put(
        factory.serviceLoaderMethods.load,
        getMethodReferenceWithClassParameter("onServiceLoaderLoad"));
    builder.put(
        factory.serviceLoaderMethods.loadWithClassLoader,
        getMethodReferenceWithParameterTypes(
            "onServiceLoaderLoadWithClassLoader", factory.classType, factory.classLoaderType));
    builder.put(
        factory.serviceLoaderMethods.loadInstalled,
        getMethodReferenceWithClassParameter("onServiceLoaderLoadInstalled"));

    // Proxy methods.

    builder.put(
        factory.proxyMethods.newProxyInstance,
        getMethodReferenceWithParameterTypes(
            "onProxyNewProxyInstance",
            factory.classLoaderType,
            factory.classArrayType,
            factory.invocationHandlerType));

    // ClassLoader methods.

    builder.put(
        factory.createMethod(
            factory.classLoaderType,
            factory.createProto(factory.classType, factory.stringType),
            "loadClass"),
        getMethodReferenceWithParameterTypes(
            "onClassLoaderLoadClass", factory.classLoaderType, factory.stringType));

    // ResourceBundle methods.

    DexType resourceBundleType = factory.createType("Ljava/util/ResourceBundle;");
    DexType controlType = factory.createType("Ljava/util/ResourceBundle$Control;");
    DexType localeType = factory.javaUtilLocaleType;
    putResourceBundleGetBundle(
        builder, resourceBundleType, "onResourceBundleGetBundle", factory.stringType);
    putResourceBundleGetBundle(
        builder,
        resourceBundleType,
        "onResourceBundleGetBundleWithControl",
        factory.stringType,
        controlType);
    putResourceBundleGetBundle(
        builder,
        resourceBundleType,
        "onResourceBundleGetBundleWithLocale",
        factory.stringType,
        localeType);
    putResourceBundleGetBundle(
        builder,
        resourceBundleType,
        "onResourceBundleGetBundleWithLocaleAndControl",
        factory.stringType,
        localeType,
        controlType);
    putResourceBundleGetBundle(
        builder,
        resourceBundleType,
        "onResourceBundleGetBundleWithLocaleAndLoader",
        factory.stringType,
        localeType,
        factory.classLoaderType);
    putResourceBundleGetBundle(
        builder,
        resourceBundleType,
        "onResourceBundleGetBundleWithLocaleLoaderAndControl",
        factory.stringType,
        localeType,
        factory.classLoaderType,
        controlType);

    // Annotation reflection methods.
    DexType annotationType = factory.annotationType;
    DexType annotationArrayType = annotationType.toArrayType(factory);
    DexType annotationArray2DType = annotationArrayType.toArrayType(factory);
    DexType annotatedElementType = factory.createType("Ljava/lang/reflect/AnnotatedElement;");

    putAnnotationMethods(
        builder,
        factory.classType,
        annotationType,
        annotationArrayType,
        "onClassGetAnnotation",
        "onClassGetAnnotations",
        "onClassIsAnnotationPresent");
    putAnnotationMethods(
        builder,
        factory.fieldType,
        annotationType,
        annotationArrayType,
        "onFieldGetAnnotation",
        "onFieldGetAnnotations",
        "onFieldIsAnnotationPresent");
    putAnnotationMethods(
        builder,
        factory.methodType,
        annotationType,
        annotationArrayType,
        "onMethodGetAnnotation",
        "onMethodGetAnnotations",
        "onMethodIsAnnotationPresent");
    builder.put(
        factory.createMethod(
            factory.methodType,
            factory.createProto(annotationArray2DType),
            "getParameterAnnotations"),
        getMethodReferenceWithParameterTypes("onMethodGetAnnotations", factory.methodType));
    putAnnotationMethods(
        builder,
        factory.constructorType,
        annotationType,
        annotationArrayType,
        "onConstructorGetAnnotation",
        "onConstructorGetAnnotations",
        "onConstructorIsAnnotationPresent");
    putAnnotationMethods(
        builder,
        annotatedElementType,
        annotationType,
        annotationArrayType,
        "onAnnotatedElementGetAnnotation",
        "onAnnotatedElementGetAnnotations",
        "onAnnotatedElementIsAnnotationPresent");

    // Generic member signature methods.
    DexType reflectTypeArray = reflectType.toArrayType(factory);
    builder.put(
        factory.createMethod(
            factory.methodType, factory.createProto(reflectTypeArray), "getGenericParameterTypes"),
        getMethodReferenceWithParameterTypes(
            "onMethodGetGenericParameterTypes", factory.methodType));
    builder.put(
        factory.createMethod(
            factory.methodType, factory.createProto(reflectType), "getGenericReturnType"),
        getMethodReferenceWithParameterTypes("onMethodGetGenericReturnType", factory.methodType));
    builder.put(
        factory.createMethod(
            factory.methodType, factory.createProto(reflectTypeArray), "getGenericExceptionTypes"),
        getMethodReferenceWithParameterTypes(
            "onMethodGetGenericExceptionTypes", factory.methodType));
    builder.put(
        factory.createMethod(factory.fieldType, factory.createProto(reflectType), "getGenericType"),
        getMethodReferenceWithParameterTypes("onFieldGetGenericType", factory.fieldType));

    // Nested class discovery methods.
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classArrayType), "getDeclaredClasses"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredClasses"));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classArrayType), "getClasses"),
        getMethodReferenceWithClassParameter("onClassGetClasses"));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classType), "getDeclaringClass"),
        getMethodReferenceWithClassParameter("onClassGetDeclaringClass"));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classType), "getEnclosingClass"),
        getMethodReferenceWithClassParameter("onClassGetEnclosingClass"));

    // Enum reflection methods.
    builder.put(
        factory.createMethod(
            factory.enumType,
            factory.createProto(factory.enumType, factory.classType, factory.stringType),
            "valueOf"),
        getMethodReferenceWithClassAndStringParameter("onEnumValueOf"));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.objectArrayType), "getEnumConstants"),
        getMethodReferenceWithClassParameter("onClassGetEnumConstants"));

    // Unsafe.allocateInstance.
    builder.put(
        factory.createMethod(
            factory.sunMiscUnsafeType,
            factory.createProto(factory.objectType, factory.classType),
            "allocateInstance"),
        getMethodReferenceWithParameterTypes(
            "onUnsafeAllocateInstance", factory.objectType, factory.classType));

    return builder.build();
  }

  private void putAnnotationMethods(
      ImmutableMap.Builder<DexMethod, DexMethod> builder,
      DexType receiverType,
      DexType annotationType,
      DexType annotationArrayType,
      String onGetAnnotation,
      String onGetAnnotations,
      String onIsAnnotationPresent) {
    DexMethod getAnnotationHook =
        getMethodReferenceWithParameterTypes(onGetAnnotation, receiverType, factory.classType);
    builder.put(
        factory.createMethod(
            receiverType, factory.createProto(annotationType, factory.classType), "getAnnotation"),
        getAnnotationHook);
    builder.put(
        factory.createMethod(
            receiverType,
            factory.createProto(annotationType, factory.classType),
            "getDeclaredAnnotation"),
        getAnnotationHook);
    DexMethod getAnnotationsHook =
        getMethodReferenceWithParameterTypes(onGetAnnotations, receiverType);
    builder.put(
        factory.createMethod(
            receiverType, factory.createProto(annotationArrayType), "getAnnotations"),
        getAnnotationsHook);
    builder.put(
        factory.createMethod(
            receiverType, factory.createProto(annotationArrayType), "getDeclaredAnnotations"),
        getAnnotationsHook);
    builder.put(
        factory.createMethod(
            receiverType,
            factory.createProto(factory.booleanType, factory.classType),
            "isAnnotationPresent"),
        getMethodReferenceWithParameterTypes(
            onIsAnnotationPresent, receiverType, factory.classType));
  }

  // ResourceBundle.getBundle is static, so the stub takes the same parameters.
  private void putResourceBundleGetBundle(
      ImmutableMap.Builder<DexMethod, DexMethod> builder,
      DexType resourceBundleType,
      String stubName,
      DexType... parameterTypes) {
    builder.put(
        factory.createMethod(
            resourceBundleType,
            factory.createProto(resourceBundleType, parameterTypes),
            "getBundle"),
        getMethodReferenceWithParameterTypes(stubName, parameterTypes));
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
        factory.createProto(factory.intType, dexTypes),
        name);
  }

  private DexMethod getMethodReferenceWithClassMethodNameAndParameters(String name) {
    return factory.createMethod(
        reflectiveReferences.reflectiveOracleType,
        factory.createProto(
            factory.intType, factory.classType, factory.stringType, factory.classArrayType),
        name);
  }
}
