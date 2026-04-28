// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateAssistantRuntimeMethods.java.
// ***********************************************************************************

package com.android.tools.r8.assistant;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class AssistantRuntimeMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;");
    factory.createSynthesizedType(
        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;");
    factory.createSynthesizedType(
        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;");
    factory.createSynthesizedType(
        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;");
    factory.createSynthesizedType(
        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;");
    factory.createSynthesizedType(
        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;");
    factory.createSynthesizedType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;");
    factory.createSynthesizedType("Ljava/io/File;");
    factory.createSynthesizedType("Ljava/io/FileWriter;");
    factory.createSynthesizedType("Ljava/io/IOException;");
    factory.createSynthesizedType("Ljava/io/UncheckedIOException;");
    factory.createSynthesizedType("Ljava/lang/AssertionError;");
    factory.createSynthesizedType("Ljava/lang/Boolean;");
    factory.createSynthesizedType("Ljava/lang/ClassLoader;");
    factory.createSynthesizedType("Ljava/lang/Enum;");
    factory.createSynthesizedType("Ljava/lang/Exception;");
    factory.createSynthesizedType("Ljava/lang/Integer;");
    factory.createSynthesizedType("Ljava/lang/Long;");
    factory.createSynthesizedType("Ljava/lang/NoSuchFieldException;");
    factory.createSynthesizedType("Ljava/lang/NoSuchMethodException;");
    factory.createSynthesizedType("Ljava/lang/RuntimeException;");
    factory.createSynthesizedType("Ljava/lang/StackTraceElement;");
    factory.createSynthesizedType("Ljava/lang/System;");
    factory.createSynthesizedType("Ljava/lang/Void;");
    factory.createSynthesizedType("Ljava/lang/reflect/Field;");
    factory.createSynthesizedType("Ljava/lang/reflect/InvocationHandler;");
    factory.createSynthesizedType("Ljava/lang/reflect/Method;");
    factory.createSynthesizedType("Ljava/util/Arrays;");
    factory.createSynthesizedType("[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;");
    factory.createSynthesizedType(
        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;");
    factory.createSynthesizedType(
        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;");
    factory.createSynthesizedType("[Ljava/lang/Class;");
    factory.createSynthesizedType("[Ljava/lang/Object;");
    factory.createSynthesizedType("[Ljava/lang/StackTraceElement;");
    factory.createSynthesizedType("[Ljava/lang/String;");
  }

  public static void generateEmptyReflectiveOperationReceiverClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(ImmutableList.of());
    builder.setInstanceFields(ImmutableList.of());
    DexMethod constructor_0 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<init>"));
    DexMethod onAtomicFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicFieldUpdaterNewUpdater"));
    DexMethod onClassAsSubclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassAsSubclass"));
    DexMethod onClassCast =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassCast"));
    DexMethod onClassFlag =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))),
            factory.createString("onClassFlag"));
    DexMethod onClassForName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.booleanType,
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onClassForName"));
    DexMethod onClassGetComponentType =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetComponentType"));
    DexMethod onClassGetConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructor"));
    DexMethod onClassGetConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructors"));
    DexMethod onClassGetDeclaredConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructor"));
    DexMethod onClassGetDeclaredConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructors"));
    DexMethod onClassGetDeclaredField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetDeclaredField"));
    DexMethod onClassGetDeclaredFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredFields"));
    DexMethod onClassGetDeclaredMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethod"));
    DexMethod onClassGetDeclaredMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethods"));
    DexMethod onClassGetField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetField"));
    DexMethod onClassGetFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetFields"));
    DexMethod onClassGetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetMethod"));
    DexMethod onClassGetMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetMethods"));
    DexMethod onClassGetName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))),
            factory.createString("onClassGetName"));
    DexMethod onClassGetPackage =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetPackage"));
    DexMethod onClassGetSuperclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetSuperclass"));
    DexMethod onClassIsAssignableFrom =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAssignableFrom"));
    DexMethod onClassIsInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassIsInstance"));
    DexMethod onClassNewInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassNewInstance"));
    DexMethod onProxyNewProxyInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;")),
                factory.createType(factory.createString("[Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/reflect/InvocationHandler;"))),
            factory.createString("onProxyNewProxyInstance"));
    DexMethod onServiceLoaderLoad =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onServiceLoaderLoad"));
    DexMethod requiresStackInformation =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.booleanType),
            factory.createString("requiresStackInformation"));
    builder.setInterfaces(
        ImmutableList.of(
            factory.createType(
                factory.createString(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"))));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_0)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(EmptyReflectiveOperationReceiver_constructor_0(factory, constructor_0))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onAtomicFieldUpdaterNewUpdater(
                        factory, onAtomicFieldUpdaterNewUpdater))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassAsSubclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassAsSubclass(factory, onClassAsSubclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassCast)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(EmptyReflectiveOperationReceiver_onClassCast(factory, onClassCast))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassFlag)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(EmptyReflectiveOperationReceiver_onClassFlag(factory, onClassFlag))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassForName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(EmptyReflectiveOperationReceiver_onClassForName(factory, onClassForName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetComponentType)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetComponentType(
                        factory, onClassGetComponentType))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetConstructor(
                        factory, onClassGetConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetConstructors(
                        factory, onClassGetConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredConstructor(
                        factory, onClassGetDeclaredConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredConstructors(
                        factory, onClassGetDeclaredConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredField(
                        factory, onClassGetDeclaredField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredFields(
                        factory, onClassGetDeclaredFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredMethod(
                        factory, onClassGetDeclaredMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetDeclaredMethods(
                        factory, onClassGetDeclaredMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(EmptyReflectiveOperationReceiver_onClassGetField(factory, onClassGetField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetFields(factory, onClassGetFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetMethod(factory, onClassGetMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetMethods(factory, onClassGetMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(EmptyReflectiveOperationReceiver_onClassGetName(factory, onClassGetName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetPackage)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetPackage(factory, onClassGetPackage))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetSuperclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassGetSuperclass(
                        factory, onClassGetSuperclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAssignableFrom)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassIsAssignableFrom(
                        factory, onClassIsAssignableFrom))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassIsInstance(factory, onClassIsInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassNewInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onClassNewInstance(
                        factory, onClassNewInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onProxyNewProxyInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onProxyNewProxyInstance(
                        factory, onProxyNewProxyInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoad)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_onServiceLoaderLoad(
                        factory, onServiceLoaderLoad))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(requiresStackInformation)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    EmptyReflectiveOperationReceiver_requiresStackInformation(
                        factory, requiresStackInformation))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static void generateReflectiveEventTypeClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_NEW_INSTANCE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_METHOD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_METHODS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_FIELD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_FIELDS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_CONSTRUCTOR")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_DECLARED_CONSTRUCTORS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_METHOD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_METHODS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_FIELD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_FIELDS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_CONSTRUCTOR")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_CONSTRUCTORS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_FOR_NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_COMPONENT_TYPE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_PACKAGE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_IS_ASSIGNABLE_FROM")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_GET_SUPERCLASS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_AS_SUBCLASS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_IS_INSTANCE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_CAST")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("CLASS_FLAG")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("ATOMIC_FIELD_UPDATER_NEW_UPDATER")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("SERVICE_LOADER_LOAD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("PROXY_NEW_PROXY_INSTANCE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        factory.createString("$VALUES")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(ImmutableList.of());
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_2 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.intType),
            factory.createString("<init>"));
    DexMethod valueOf =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("valueOf"));
    DexMethod values =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"))),
            factory.createString("values"));
    builder.setSuperType(factory.createType(factory.createString("Ljava/lang/Enum;")));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveEventType_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_2)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(ReflectiveEventType_constructor_2(factory, constructor_2))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(valueOf)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveEventType_valueOf(factory, valueOf))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(values)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveEventType_values(factory, values))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(ImmutableList.of());
  }

  public static void generateReflectiveOperationJsonLoggerClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.booleanType,
                        factory.createString("$assertionsDisabled")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(factory.createString("Ljava/io/FileWriter;")),
                        factory.createString("output")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_0 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<init>"));
    DexMethod constructorToString =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("[Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("constructorToString"));
    DexMethod getApplicationId =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("getApplicationId"));
    DexMethod isIgnoredCaller =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"))),
            factory.createString("isIgnoredCaller"));
    DexMethod isIgnoredClass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("isIgnoredClass"));
    DexMethod isIgnoredTarget =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType,
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("isIgnoredTarget"));
    DexMethod methodToString =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("[Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("methodToString"));
    DexMethod onAtomicFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicFieldUpdaterNewUpdater"));
    DexMethod onClassAsSubclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassAsSubclass"));
    DexMethod onClassCast =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassCast"));
    DexMethod onClassFlag =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))),
            factory.createString("onClassFlag"));
    DexMethod onClassForName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.booleanType,
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onClassForName"));
    DexMethod onClassGetComponentType =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetComponentType"));
    DexMethod onClassGetConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructor"));
    DexMethod onClassGetConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructors"));
    DexMethod onClassGetDeclaredConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructor"));
    DexMethod onClassGetDeclaredConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructors"));
    DexMethod onClassGetDeclaredField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetDeclaredField"));
    DexMethod onClassGetDeclaredFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredFields"));
    DexMethod onClassGetDeclaredMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethod"));
    DexMethod onClassGetDeclaredMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethods"));
    DexMethod onClassGetField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetField"));
    DexMethod onClassGetFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetFields"));
    DexMethod onClassGetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetMethod"));
    DexMethod onClassGetMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetMethods"));
    DexMethod onClassGetName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))),
            factory.createString("onClassGetName"));
    DexMethod onClassGetPackage =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetPackage"));
    DexMethod onClassGetSuperclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetSuperclass"));
    DexMethod onClassIsAssignableFrom =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAssignableFrom"));
    DexMethod onClassIsInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassIsInstance"));
    DexMethod onClassNewInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassNewInstance"));
    DexMethod onProxyNewProxyInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;")),
                factory.createType(factory.createString("[Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/reflect/InvocationHandler;"))),
            factory.createString("onProxyNewProxyInstance"));
    DexMethod onServiceLoaderLoad =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onServiceLoaderLoad"));
    DexMethod output =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("[Ljava/lang/String;"))),
            factory.createString("output"));
    DexMethod printArray =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("[Ljava/lang/String;"))),
            factory.createString("printArray"));
    DexMethod printClass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("printClass"));
    DexMethod printClassLoader =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("printClassLoader"));
    DexMethod requiresStackInformation =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.booleanType),
            factory.createString("requiresStackInformation"));
    builder.setInterfaces(
        ImmutableList.of(
            factory.createType(
                factory.createString(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"))));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveOperationJsonLogger_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_0)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(ReflectiveOperationJsonLogger_constructor_0(factory, constructor_0))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructorToString)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_constructorToString(factory, constructorToString))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getApplicationId)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_getApplicationId(factory, getApplicationId))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(isIgnoredCaller)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_isIgnoredCaller(factory, isIgnoredCaller))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(isIgnoredClass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_isIgnoredClass(factory, isIgnoredClass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(isIgnoredTarget)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_isIgnoredTarget(factory, isIgnoredTarget))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(methodToString)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_methodToString(factory, methodToString))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onAtomicFieldUpdaterNewUpdater(
                        factory, onAtomicFieldUpdaterNewUpdater))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassAsSubclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassAsSubclass(factory, onClassAsSubclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassCast)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassCast(factory, onClassCast))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassFlag)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassFlag(factory, onClassFlag))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassForName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassForName(factory, onClassForName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetComponentType)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetComponentType(
                        factory, onClassGetComponentType))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetConstructor(
                        factory, onClassGetConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetConstructors(
                        factory, onClassGetConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredConstructor(
                        factory, onClassGetDeclaredConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredConstructors(
                        factory, onClassGetDeclaredConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredField(
                        factory, onClassGetDeclaredField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredFields(
                        factory, onClassGetDeclaredFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredMethod(
                        factory, onClassGetDeclaredMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetDeclaredMethods(
                        factory, onClassGetDeclaredMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassGetField(factory, onClassGetField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassGetFields(factory, onClassGetFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassGetMethod(factory, onClassGetMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetMethods(factory, onClassGetMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_onClassGetName(factory, onClassGetName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetPackage)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetPackage(factory, onClassGetPackage))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetSuperclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassGetSuperclass(
                        factory, onClassGetSuperclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAssignableFrom)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassIsAssignableFrom(
                        factory, onClassIsAssignableFrom))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassIsInstance(factory, onClassIsInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassNewInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onClassNewInstance(factory, onClassNewInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onProxyNewProxyInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onProxyNewProxyInstance(
                        factory, onProxyNewProxyInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoad)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_onServiceLoaderLoad(factory, onServiceLoaderLoad))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(output)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_output(factory, output))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(printArray)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_printArray(factory, printArray))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(printClass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_printClass(factory, printClass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(printClassLoader)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOperationJsonLogger_printClassLoader(factory, printClassLoader))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(requiresStackInformation)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOperationJsonLogger_requiresStackInformation(
                        factory, requiresStackInformation))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static void generateReflectiveOperationReceiverClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(ImmutableList.of());
    builder.setInstanceFields(ImmutableList.of());
    DexMethod onAtomicFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicFieldUpdaterNewUpdater"));
    DexMethod onClassAsSubclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassAsSubclass"));
    DexMethod onClassCast =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassCast"));
    DexMethod onClassFlag =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))),
            factory.createString("onClassFlag"));
    DexMethod onClassForName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.booleanType,
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onClassForName"));
    DexMethod onClassGetComponentType =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetComponentType"));
    DexMethod onClassGetConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructor"));
    DexMethod onClassGetConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructors"));
    DexMethod onClassGetDeclaredConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructor"));
    DexMethod onClassGetDeclaredConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructors"));
    DexMethod onClassGetDeclaredField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetDeclaredField"));
    DexMethod onClassGetDeclaredFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredFields"));
    DexMethod onClassGetDeclaredMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethod"));
    DexMethod onClassGetDeclaredMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethods"));
    DexMethod onClassGetField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetField"));
    DexMethod onClassGetFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetFields"));
    DexMethod onClassGetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetMethod"));
    DexMethod onClassGetMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetMethods"));
    DexMethod onClassGetName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))),
            factory.createString("onClassGetName"));
    DexMethod onClassGetPackage =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetPackage"));
    DexMethod onClassGetSuperclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetSuperclass"));
    DexMethod onClassIsAssignableFrom =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAssignableFrom"));
    DexMethod onClassIsInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassIsInstance"));
    DexMethod onClassNewInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassNewInstance"));
    DexMethod onProxyNewProxyInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;")),
                factory.createType(factory.createString("[Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/reflect/InvocationHandler;"))),
            factory.createString("onProxyNewProxyInstance"));
    DexMethod onServiceLoaderLoad =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onServiceLoaderLoad"));
    DexMethod requiresStackInformation =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.booleanType),
            factory.createString("requiresStackInformation"));
    builder.setInterface();
    builder.setDirectMethods(ImmutableList.of());
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassAsSubclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassCast)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassFlag)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassForName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetComponentType)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetPackage)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetSuperclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAssignableFrom)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassNewInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onProxyNewProxyInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoad)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(requiresStackInformation)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                        false))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static void generateClassFlagClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("ANNOTATION")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("ANONYMOUS_CLASS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("ARRAY")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("ENUM")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("HIDDEN")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("INTERFACE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("LOCAL_CLASS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("MEMBER_CLASS")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("PRIMITIVE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("RECORD")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("SEALED")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("SYNTHETIC")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                        factory.createString("$VALUES")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(ImmutableList.of());
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_2 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.intType),
            factory.createString("<init>"));
    DexMethod valueOf =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("valueOf"));
    DexMethod values =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))),
            factory.createString("values"));
    builder.setSuperType(factory.createType(factory.createString("Ljava/lang/Enum;")));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveOperationReceiver$ClassFlag_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_2)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(
                    ReflectiveOperationReceiver$ClassFlag_constructor_2(factory, constructor_2))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(valueOf)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOperationReceiver$ClassFlag_valueOf(factory, valueOf))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(values)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOperationReceiver$ClassFlag_values(factory, values))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(ImmutableList.of());
  }

  public static void generateNameLookupTypeClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                        factory.createString("NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                        factory.createString("SIMPLE_NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                        factory.createString("CANONICAL_NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                        factory.createString("TYPE_NAME")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                        factory.createString("$VALUES")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(ImmutableList.of());
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_2 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.intType),
            factory.createString("<init>"));
    DexMethod valueOf =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("valueOf"));
    DexMethod values =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))),
            factory.createString("values"));
    builder.setSuperType(factory.createType(factory.createString("Ljava/lang/Enum;")));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveOperationReceiver$NameLookupType_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_2)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(
                    ReflectiveOperationReceiver$NameLookupType_constructor_2(
                        factory, constructor_2))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(valueOf)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOperationReceiver$NameLookupType_valueOf(factory, valueOf))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(values)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOperationReceiver$NameLookupType_values(factory, values))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(ImmutableList.of());
  }

  public static void generateReflectiveOracleClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.objectType,
                        factory.createString("instanceLock")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(
                            factory.createString(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                        factory.createString("INSTANCE")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(ImmutableList.of());
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_0 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<init>"));
    DexMethod getInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"))),
            factory.createString("getInstance"));
    DexMethod getReceiver =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"))),
            factory.createString("getReceiver"));
    DexMethod onAtomicIntegerFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicIntegerFieldUpdaterNewUpdater"));
    DexMethod onAtomicLongFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicLongFieldUpdaterNewUpdater"));
    DexMethod onAtomicReferenceFieldUpdaterNewUpdater =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onAtomicReferenceFieldUpdaterNewUpdater"));
    DexMethod onClassAsSubclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassAsSubclass"));
    DexMethod onClassCast =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassCast"));
    DexMethod onClassForNameDefault =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassForNameDefault"));
    DexMethod onClassForNameWithLoader =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.booleanType,
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onClassForNameWithLoader"));
    DexMethod onClassGetCanonicalName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetCanonicalName"));
    DexMethod onClassGetComponentType =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetComponentType"));
    DexMethod onClassGetConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructor"));
    DexMethod onClassGetConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetConstructors"));
    DexMethod onClassGetDeclaredConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructor"));
    DexMethod onClassGetDeclaredConstructors =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredConstructors"));
    DexMethod onClassGetDeclaredField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetDeclaredField"));
    DexMethod onClassGetDeclaredFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredFields"));
    DexMethod onClassGetDeclaredMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethod"));
    DexMethod onClassGetDeclaredMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetDeclaredMethods"));
    DexMethod onClassGetField =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;"))),
            factory.createString("onClassGetField"));
    DexMethod onClassGetFields =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetFields"));
    DexMethod onClassGetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("[Ljava/lang/Class;"))),
            factory.createString("onClassGetMethod"));
    DexMethod onClassGetMethods =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetMethods"));
    DexMethod onClassGetName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetName"));
    DexMethod onClassGetPackage =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetPackage"));
    DexMethod onClassGetSimpleName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetSimpleName"));
    DexMethod onClassGetSuperclass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetSuperclass"));
    DexMethod onClassGetTypeName =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassGetTypeName"));
    DexMethod onClassIsAnnotation =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAnnotation"));
    DexMethod onClassIsAnonymousClass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAnonymousClass"));
    DexMethod onClassIsArray =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsArray"));
    DexMethod onClassIsAssignableFrom =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsAssignableFrom"));
    DexMethod onClassIsEnum =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsEnum"));
    DexMethod onClassIsHidden =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsHidden"));
    DexMethod onClassIsInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.objectType),
            factory.createString("onClassIsInstance"));
    DexMethod onClassIsInterface =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsInterface"));
    DexMethod onClassIsLocalClass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsLocalClass"));
    DexMethod onClassIsMemberClass =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsMemberClass"));
    DexMethod onClassIsPrimitive =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsPrimitive"));
    DexMethod onClassIsRecord =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsRecord"));
    DexMethod onClassIsSealed =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsSealed"));
    DexMethod onClassIsSynthetic =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassIsSynthetic"));
    DexMethod onClassNewInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onClassNewInstance"));
    DexMethod onProxyNewProxyInstance =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/ClassLoader;")),
                factory.createType(factory.createString("[Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/reflect/InvocationHandler;"))),
            factory.createString("onProxyNewProxyInstance"));
    DexMethod onServiceLoaderLoad =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onServiceLoaderLoad"));
    DexMethod onServiceLoaderLoadInstalled =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType, factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("onServiceLoaderLoadInstalled"));
    DexMethod onServiceLoaderLoadWithClassLoader =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/ClassLoader;"))),
            factory.createString("onServiceLoaderLoadWithClassLoader"));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveOracle_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_0)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(ReflectiveOracle_constructor_0(factory, constructor_0))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_getInstance(factory, getInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getReceiver)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_getReceiver(factory, getReceiver))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicIntegerFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onAtomicIntegerFieldUpdaterNewUpdater(
                        factory, onAtomicIntegerFieldUpdaterNewUpdater))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicLongFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onAtomicLongFieldUpdaterNewUpdater(
                        factory, onAtomicLongFieldUpdaterNewUpdater))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onAtomicReferenceFieldUpdaterNewUpdater)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onAtomicReferenceFieldUpdaterNewUpdater(
                        factory, onAtomicReferenceFieldUpdaterNewUpdater))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassAsSubclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassAsSubclass(factory, onClassAsSubclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassCast)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassCast(factory, onClassCast))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassForNameDefault)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassForNameDefault(factory, onClassForNameDefault))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassForNameWithLoader)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassForNameWithLoader(factory, onClassForNameWithLoader))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetCanonicalName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetCanonicalName(factory, onClassGetCanonicalName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetComponentType)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetComponentType(factory, onClassGetComponentType))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetConstructor(factory, onClassGetConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetConstructors(factory, onClassGetConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructor)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassGetDeclaredConstructor(
                        factory, onClassGetDeclaredConstructor))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredConstructors)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassGetDeclaredConstructors(
                        factory, onClassGetDeclaredConstructors))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetDeclaredField(factory, onClassGetDeclaredField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassGetDeclaredFields(factory, onClassGetDeclaredFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassGetDeclaredMethod(factory, onClassGetDeclaredMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetDeclaredMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onClassGetDeclaredMethods(factory, onClassGetDeclaredMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetField)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetField(factory, onClassGetField))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetFields)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetFields(factory, onClassGetFields))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethod)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetMethod(factory, onClassGetMethod))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetMethods)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetMethods(factory, onClassGetMethods))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetName(factory, onClassGetName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetPackage)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetPackage(factory, onClassGetPackage))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetSimpleName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetSimpleName(factory, onClassGetSimpleName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetSuperclass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetSuperclass(factory, onClassGetSuperclass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassGetTypeName)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassGetTypeName(factory, onClassGetTypeName))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAnnotation)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsAnnotation(factory, onClassIsAnnotation))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAnonymousClass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsAnonymousClass(factory, onClassIsAnonymousClass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsArray)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsArray(factory, onClassIsArray))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsAssignableFrom)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsAssignableFrom(factory, onClassIsAssignableFrom))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsEnum)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsEnum(factory, onClassIsEnum))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsHidden)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsHidden(factory, onClassIsHidden))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsInstance(factory, onClassIsInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsInterface)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsInterface(factory, onClassIsInterface))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsLocalClass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsLocalClass(factory, onClassIsLocalClass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsMemberClass)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsMemberClass(factory, onClassIsMemberClass))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsPrimitive)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsPrimitive(factory, onClassIsPrimitive))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsRecord)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsRecord(factory, onClassIsRecord))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsSealed)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsSealed(factory, onClassIsSealed))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassIsSynthetic)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassIsSynthetic(factory, onClassIsSynthetic))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onClassNewInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onClassNewInstance(factory, onClassNewInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onProxyNewProxyInstance)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onProxyNewProxyInstance(factory, onProxyNewProxyInstance))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoad)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle_onServiceLoaderLoad(factory, onServiceLoaderLoad))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoadInstalled)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onServiceLoaderLoadInstalled(
                        factory, onServiceLoaderLoadInstalled))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(onServiceLoaderLoadWithClassLoader)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(
                    ReflectiveOracle_onServiceLoaderLoadWithClassLoader(
                        factory, onServiceLoaderLoadWithClassLoader))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(ImmutableList.of());
  }

  public static void generateStackClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.booleanType,
                        factory.createString("$assertionsDisabled")))
                .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setInstanceFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(factory.createString("[Ljava/lang/StackTraceElement;")),
                        factory.createString("stackTraceElements")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    DexMethod clinit =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<clinit>"));
    DexMethod constructor_1 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("[Ljava/lang/StackTraceElement;"))),
            factory.createString("<init>"));
    DexMethod createStack =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(
                    factory.createString(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"))),
            factory.createString("createStack"));
    DexMethod getStackTraceElements =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("[Ljava/lang/StackTraceElement;"))),
            factory.createString("getStackTraceElements"));
    DexMethod stackTraceElementsAsString =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.createType(factory.createString("[Ljava/lang/String;"))),
            factory.createString("stackTraceElementsAsString"));
    DexMethod toStringStackTrace =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("Ljava/lang/String;")), factory.intType),
            factory.createString("toStringStackTrace"));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ReflectiveOracle$Stack_clinit(factory, clinit))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_1)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(ReflectiveOracle$Stack_constructor_1(factory, constructor_1))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(createStack)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC,
                        false))
                .setCode(ReflectiveOracle$Stack_createStack(factory, createStack))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getStackTraceElements)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOracle$Stack_getStackTraceElements(factory, getStackTraceElements))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(stackTraceElementsAsString)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(
                    ReflectiveOracle$Stack_stackTraceElementsAsString(
                        factory, stackTraceElementsAsString))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(toStringStackTrace)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(ReflectiveOracle$Stack_toStringStackTrace(factory, toStringStackTrace))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static CfCode EmptyReflectiveOperationReceiver_constructor_0(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onAtomicFieldUpdaterNewUpdater(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        5,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassAsSubclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassCast(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassFlag(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassForName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        5,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetComponentType(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredField(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        5,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredFields(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredMethod(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        6,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetDeclaredMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetField(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        5,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetFields(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetMethod(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        6,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetPackage(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassGetSuperclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassIsAssignableFrom(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassIsInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onClassNewInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onProxyNewProxyInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        5,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_onServiceLoaderLoad(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode EmptyReflectiveOperationReceiver_requiresStackInformation(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0, new CfConstNumber(0, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveEventType_clinit(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    CfLabel label19 = new CfLabel();
    CfLabel label20 = new CfLabel();
    CfLabel label21 = new CfLabel();
    CfLabel label22 = new CfLabel();
    CfLabel label23 = new CfLabel();
    CfLabel label24 = new CfLabel();
    CfLabel label25 = new CfLabel();
    CfLabel label26 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_NEW_INSTANCE")),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_NEW_INSTANCE"))),
            label1,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_METHOD")),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHOD"))),
            label2,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_METHODS")),
            new CfConstNumber(2, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHODS"))),
            label3,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_FIELD")),
            new CfConstNumber(3, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELD"))),
            label4,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_FIELDS")),
            new CfConstNumber(4, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELDS"))),
            label5,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_CONSTRUCTOR")),
            new CfConstNumber(5, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTOR"))),
            label6,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_DECLARED_CONSTRUCTORS")),
            new CfConstNumber(6, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTORS"))),
            label7,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_METHOD")),
            new CfConstNumber(7, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHOD"))),
            label8,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_METHODS")),
            new CfConstNumber(8, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHODS"))),
            label9,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_FIELD")),
            new CfConstNumber(9, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELD"))),
            label10,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_FIELDS")),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELDS"))),
            label11,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_CONSTRUCTOR")),
            new CfConstNumber(11, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTOR"))),
            label12,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_CONSTRUCTORS")),
            new CfConstNumber(12, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTORS"))),
            label13,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_NAME")),
            new CfConstNumber(13, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_NAME"))),
            label14,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_FOR_NAME")),
            new CfConstNumber(14, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FOR_NAME"))),
            label15,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_COMPONENT_TYPE")),
            new CfConstNumber(15, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_COMPONENT_TYPE"))),
            label16,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_PACKAGE")),
            new CfConstNumber(16, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_PACKAGE"))),
            label17,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_IS_ASSIGNABLE_FROM")),
            new CfConstNumber(17, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_ASSIGNABLE_FROM"))),
            label18,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_GET_SUPERCLASS")),
            new CfConstNumber(18, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_SUPERCLASS"))),
            label19,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_AS_SUBCLASS")),
            new CfConstNumber(19, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_AS_SUBCLASS"))),
            label20,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_IS_INSTANCE")),
            new CfConstNumber(20, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_INSTANCE"))),
            label21,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_CAST")),
            new CfConstNumber(21, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_CAST"))),
            label22,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CLASS_FLAG")),
            new CfConstNumber(22, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FLAG"))),
            label23,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("ATOMIC_FIELD_UPDATER_NEW_UPDATER")),
            new CfConstNumber(23, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("ATOMIC_FIELD_UPDATER_NEW_UPDATER"))),
            label24,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("SERVICE_LOADER_LOAD")),
            new CfConstNumber(24, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("SERVICE_LOADER_LOAD"))),
            label25,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("PROXY_NEW_PROXY_INSTANCE")),
            new CfConstNumber(25, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("PROXY_NEW_PROXY_INSTANCE"))),
            label26,
            new CfConstNumber(26, ValueType.INT),
            new CfNewArray(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_NEW_INSTANCE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHOD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHODS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(3, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(4, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELDS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(5, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTOR"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(6, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTORS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(7, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHOD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(8, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHODS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(9, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(10, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELDS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(11, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTOR"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(12, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTORS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(13, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(14, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FOR_NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(15, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_COMPONENT_TYPE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(16, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_PACKAGE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(17, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_ASSIGNABLE_FROM"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(18, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_SUPERCLASS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(19, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_AS_SUBCLASS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(20, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_INSTANCE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(21, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_CAST"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(22, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FLAG"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(23, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("ATOMIC_FIELD_UPDATER_NEW_UPDATER"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(24, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("SERVICE_LOADER_LOAD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(25, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("PROXY_NEW_PROXY_INSTANCE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("$VALUES"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveEventType_constructor_2(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveEventType_valueOf(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfConstClass(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(
                        factory.createType("Ljava/lang/Enum;"),
                        factory.classType,
                        factory.stringType),
                    factory.createString("valueOf")),
                false),
            new CfCheckCast(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveEventType_values(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("$VALUES"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.objectType),
                    factory.createString("clone")),
                false),
            new CfCheckCast(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_clinit(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfConstClass(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.booleanType),
                    factory.createString("desiredAssertionStatus")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.booleanType,
                    factory.createString("$assertionsDisabled"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_constructor_0(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            label1,
            new CfConstString(factory.createString("com.android.tools.r8.reflectiveJsonLogger")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/System;"),
                    factory.createProto(factory.stringType, factory.stringType),
                    factory.createString("getProperty")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label2,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.NE, ValueType.OBJECT, label7),
            label3,
            new CfNew(factory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("/sdcard/Android/media/")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 3),
            label4,
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType),
                    factory.createString("getApplicationId")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString("/additional_test_output")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label5,
            new CfNew(factory.createType("Ljava/io/File;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfConstString(factory.createString("reflection_log.json")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/File;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label6,
            new CfGoto(label8),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfNew(factory.createType("Ljava/io/File;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/File;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfNew(factory.createType("Ljava/io/FileWriter;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.createType("Ljava/io/File;")),
                    factory.createString("<init>")),
                false),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            label9,
            new CfGoto(label12),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/io/IOException;"))))),
            new CfStore(ValueType.OBJECT, 3),
            label11,
            new CfNew(factory.createType("Ljava/io/UncheckedIOException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/UncheckedIOException;"),
                    factory.createProto(
                        factory.voidType, factory.createType("Ljava/io/IOException;")),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;"))
                    })),
            new CfReturnVoid(),
            label13),
        ImmutableList.of(
            new CfTryCatch(
                label8,
                label9,
                ImmutableList.of(factory.createType("Ljava/io/IOException;")),
                ImmutableList.of(label10))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_constructorToString(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Ljava/lang/Void;"),
                    factory.classType,
                    factory.createString("TYPE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("<init>")),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("methodToString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_getApplicationId(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        6,
        ImmutableList.of(
            label0,
            new CfConstString(factory.createString("android.app.ActivityThread")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.classType, factory.stringType),
                    factory.createString("forName")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("currentApplication")),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredMethod")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(factory.voidType, factory.booleanType),
                    factory.createString("setAccessible")),
                false),
            label3,
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstNull(),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            new CfStore(ValueType.OBJECT, 3),
            label4,
            new CfLoad(ValueType.OBJECT, 3),
            new CfIf(IfType.NE, ValueType.OBJECT, label7),
            label5,
            new CfConstNull(),
            label6,
            new CfReturn(ValueType.OBJECT),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Method;")),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfConstString(factory.createString("getPackageName")),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getMethod")),
                false),
            new CfStore(ValueType.OBJECT, 4),
            label8,
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.OBJECT, 3),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            new CfStore(ValueType.OBJECT, 5),
            label9,
            new CfLoad(ValueType.OBJECT, 5),
            new CfCheckCast(factory.stringType),
            label10,
            new CfReturn(ValueType.OBJECT),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            new CfStore(ValueType.OBJECT, 1),
            label12,
            new CfConstNull(),
            new CfReturn(ValueType.OBJECT),
            label13),
        ImmutableList.of(
            new CfTryCatch(
                label0,
                label6,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label11)),
            new CfTryCatch(
                label7,
                label10,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label11))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_isIgnoredCaller(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.EQ, ValueType.OBJECT, label3),
            new CfLoad(ValueType.OBJECT, 1),
            label1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("getStackTraceElements")),
                false),
            new CfIf(IfType.EQ, ValueType.OBJECT, label3),
            new CfLoad(ValueType.OBJECT, 1),
            label2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("getStackTraceElements")),
                false),
            new CfArrayLength(),
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"))
                    })),
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"))
                    })),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("getStackTraceElements")),
                false),
            new CfConstNumber(0, ValueType.INT),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/StackTraceElement;"),
                    factory.createProto(factory.stringType),
                    factory.createString("getClassName")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label5,
            new CfLoad(ValueType.OBJECT, 2),
            new CfIf(IfType.EQ, ValueType.OBJECT, label13),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("java.")),
            label6,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("javax.")),
            label7,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("android.")),
            label8,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("androidx.")),
            label9,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("kotlin.")),
            label10,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString("kotlinx.")),
            label11,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label13),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label14),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfConstNumber(0, ValueType.INT),
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_isIgnoredClass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.stringType),
                    factory.createString("getName")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("isIgnoredTarget")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_isIgnoredTarget(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.EQ, ValueType.OBJECT, label3),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("java.")),
            label1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("javax.")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("android.")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfConstNumber(0, ValueType.INT),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfReturn(ValueType.INT),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_methodToString(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        7,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 4),
            new CfArrayLength(),
            new CfConstNumber(3, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStore(ValueType.OBJECT, 5),
            label1,
            new CfLoad(ValueType.OBJECT, 5),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label2,
            new CfLoad(ValueType.OBJECT, 5),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label3,
            new CfLoad(ValueType.OBJECT, 5),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayStore(MemberType.OBJECT),
            label4,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 6),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 6),
            new CfLoad(ValueType.OBJECT, 4),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label8),
            label6,
            new CfLoad(ValueType.OBJECT, 5),
            new CfLoad(ValueType.INT, 6),
            new CfConstNumber(3, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.INT, 6),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label7,
            new CfIinc(6, 1),
            new CfGoto(label5),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfLoad(ValueType.OBJECT, 5),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onAtomicFieldUpdaterNewUpdater(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("ATOMIC_FIELD_UPDATER_NEW_UPDATER"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(3, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            label3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 4),
            new CfArrayStore(MemberType.OBJECT),
            label4,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label5,
            new CfReturnVoid(),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassAsSubclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_AS_SUBCLASS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassCast(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_CAST"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassFlag(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FLAG"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.stringType),
                    factory.createString("name")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassForName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("isIgnoredTarget")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_FOR_NAME"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(3, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.INT, 3),
            label3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Boolean;"),
                    factory.createProto(factory.stringType, factory.booleanType),
                    factory.createString("toString")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 4),
            label4,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.stringType, factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("printClassLoader")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label5,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label6,
            new CfReturnVoid(),
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetComponentType(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_COMPONENT_TYPE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTOR"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"),
                        factory.classType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("constructorToString")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_CONSTRUCTORS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTOR"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"),
                        factory.classType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("constructorToString")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_CONSTRUCTORS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredField(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELD"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(3, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 4),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredFields(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_FIELDS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredMethod(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHOD"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("methodToString")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetDeclaredMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_DECLARED_METHODS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetField(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELD"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(3, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 4),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetFields(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_FIELDS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetMethod(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHOD"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 4),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/String;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("methodToString")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_METHODS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_NAME"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.stringType),
                    factory.createString("name")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetPackage(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_PACKAGE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassGetSuperclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_GET_SUPERCLASS"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassIsAssignableFrom(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_ASSIGNABLE_FROM"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassIsInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_IS_INSTANCE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            label3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            label4,
            new CfLoad(ValueType.OBJECT, 3),
            new CfIf(IfType.EQ, ValueType.OBJECT, label5),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfGoto(label6),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/String;")),
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/String;")),
                        FrameType.intType(),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"))))),
            new CfConstNull(),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/String;")),
                        FrameType.initializedNonNullReference(
                            factory.createType("[Ljava/lang/String;")),
                        FrameType.intType(),
                        FrameType.initializedNonNullReference(
                            factory.createType(
                                "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                        FrameType.initializedNonNullReference(factory.classType)))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label7,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label8,
            new CfReturnVoid(),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onClassNewInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("CLASS_NEW_INSTANCE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onProxyNewProxyInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        10,
        ImmutableList.of(
            label0,
            new CfConstNumber(1, ValueType.INT),
            new CfStore(ValueType.INT, 5),
            label1,
            new CfLoad(ValueType.OBJECT, 3),
            new CfStore(ValueType.OBJECT, 6),
            new CfLoad(ValueType.OBJECT, 6),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 7),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 8),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 8),
            new CfLoad(ValueType.INT, 7),
            new CfIfCmp(IfType.GE, ValueType.INT, label7),
            new CfLoad(ValueType.OBJECT, 6),
            new CfLoad(ValueType.INT, 8),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 9),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 9),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label6),
            label4,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 5),
            label5,
            new CfGoto(label7),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfIinc(8, 1),
            new CfGoto(label2),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 5),
            new CfIf(IfType.EQ, ValueType.INT, label9),
            label8,
            new CfReturnVoid(),
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfConstNumber(2, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStore(ValueType.OBJECT, 6),
            label10,
            new CfLoad(ValueType.OBJECT, 6),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.stringType, factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("printClassLoader")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label11,
            new CfLoad(ValueType.OBJECT, 6),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label12,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 7),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 7),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label16),
            label14,
            new CfLoad(ValueType.OBJECT, 6),
            new CfLoad(ValueType.INT, 7),
            new CfConstNumber(2, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.INT, 7),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label15,
            new CfIinc(7, 1),
            new CfGoto(label13),
            label16,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("PROXY_NEW_PROXY_INSTANCE"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 6),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label17,
            new CfReturnVoid(),
            label18),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_onServiceLoaderLoad(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        8,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isIgnoredClass")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("isIgnoredCaller")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    })),
            new CfReturnVoid(),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createString("SERVICE_LOADER_LOAD"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.stringType, factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("printClassLoader")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("[Ljava/lang/String;")),
                    factory.createString("output")),
                false),
            label3,
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_output(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("{\"event\": \"")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
                    factory.createProto(factory.stringType),
                    factory.createString("name")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("\"")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label3,
            new CfLoad(ValueType.OBJECT, 2),
            new CfIf(IfType.EQ, ValueType.OBJECT, label6),
            label4,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString(", \"stack\": ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label5,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(factory.createType("[Ljava/lang/String;")),
                    factory.createString("stackTraceElementsAsString")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType, factory.createType("[Ljava/lang/String;")),
                    factory.createString("printArray")),
                false),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.booleanType,
                    factory.createString("$assertionsDisabled"))),
            new CfIf(IfType.NE, ValueType.INT, label7),
            new CfLoad(ValueType.OBJECT, 3),
            new CfIf(IfType.NE, ValueType.OBJECT, label7),
            new CfNew(factory.createType("Ljava/lang/AssertionError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/AssertionError;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString(", \"args\": ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label8,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(
                        factory.voidType, factory.createType("[Ljava/lang/String;")),
                    factory.createString("printArray")),
                false),
            label9,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("}")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label10,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/System;"),
                    factory.createProto(factory.stringType),
                    factory.createString("lineSeparator")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label11,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType),
                    factory.createString("flush")),
                false),
            label12,
            new CfGoto(label15),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/io/IOException;"))))),
            new CfStore(ValueType.OBJECT, 4),
            label14,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.voidType, factory.throwableType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;")),
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfReturnVoid(),
            label16),
        ImmutableList.of(
            new CfTryCatch(
                label0,
                label12,
                ImmutableList.of(factory.createType("Ljava/io/IOException;")),
                ImmutableList.of(label13))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_printArray(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("[")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label1,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label9),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("\"")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label4,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label5,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("\"")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label6,
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfIfCmp(IfType.EQ, ValueType.INT, label8),
            label7,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.intType()
                    })),
            new CfIinc(2, 1),
            new CfGoto(label2),
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createString("output"))),
            new CfConstString(factory.createString("]")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("write")),
                false),
            label10,
            new CfReturnVoid(),
            label11),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_printClass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfConstString(factory.createString("null")),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.stringType),
                    factory.createString("getName")),
                false),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.stringType)))),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_printClassLoader(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfConstString(factory.createString("null")),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.stringType, factory.classType),
                    factory.createString("printClass")),
                false),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/ClassLoader;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.stringType)))),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationJsonLogger_requiresStackInformation(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0, new CfConstNumber(1, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$ClassFlag_clinit(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("ANNOTATION")),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANNOTATION"))),
            label1,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("ANONYMOUS_CLASS")),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANONYMOUS_CLASS"))),
            label2,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("ARRAY")),
            new CfConstNumber(2, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ARRAY"))),
            label3,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("ENUM")),
            new CfConstNumber(3, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ENUM"))),
            label4,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("HIDDEN")),
            new CfConstNumber(4, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("HIDDEN"))),
            label5,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("INTERFACE")),
            new CfConstNumber(5, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("INTERFACE"))),
            label6,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("LOCAL_CLASS")),
            new CfConstNumber(6, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("LOCAL_CLASS"))),
            label7,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("MEMBER_CLASS")),
            new CfConstNumber(7, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("MEMBER_CLASS"))),
            label8,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("PRIMITIVE")),
            new CfConstNumber(8, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("PRIMITIVE"))),
            label9,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("RECORD")),
            new CfConstNumber(9, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("RECORD"))),
            label10,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("SEALED")),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SEALED"))),
            label11,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("SYNTHETIC")),
            new CfConstNumber(11, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SYNTHETIC"))),
            label12,
            new CfConstNumber(12, ValueType.INT),
            new CfNewArray(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANNOTATION"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANONYMOUS_CLASS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ARRAY"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(3, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ENUM"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(4, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("HIDDEN"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(5, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("INTERFACE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(6, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("LOCAL_CLASS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(7, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("MEMBER_CLASS"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(8, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("PRIMITIVE"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(9, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("RECORD"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(10, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SEALED"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(11, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SYNTHETIC"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("$VALUES"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$ClassFlag_constructor_2(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$ClassFlag_valueOf(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfConstClass(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(
                        factory.createType("Ljava/lang/Enum;"),
                        factory.classType,
                        factory.stringType),
                    factory.createString("valueOf")),
                false),
            new CfCheckCast(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$ClassFlag_values(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("$VALUES"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createProto(factory.objectType),
                    factory.createString("clone")),
                false),
            new CfCheckCast(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$NameLookupType_clinit(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("NAME")),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("NAME"))),
            label1,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("SIMPLE_NAME")),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("SIMPLE_NAME"))),
            label2,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("CANONICAL_NAME")),
            new CfConstNumber(2, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("CANONICAL_NAME"))),
            label3,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("TYPE_NAME")),
            new CfConstNumber(3, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("TYPE_NAME"))),
            label4,
            new CfConstNumber(4, ValueType.INT),
            new CfNewArray(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(0, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(1, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("SIMPLE_NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(2, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("CANONICAL_NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNumber(3, ValueType.INT),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("TYPE_NAME"))),
            new CfArrayStore(MemberType.OBJECT),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("$VALUES"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$NameLookupType_constructor_2(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(factory.voidType, factory.stringType, factory.intType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$NameLookupType_valueOf(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfConstClass(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Enum;"),
                    factory.createProto(
                        factory.createType("Ljava/lang/Enum;"),
                        factory.classType,
                        factory.stringType),
                    factory.createString("valueOf")),
                false),
            new CfCheckCast(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOperationReceiver$NameLookupType_values(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("$VALUES"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType(
                        "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createProto(factory.objectType),
                    factory.createString("clone")),
                false),
            new CfCheckCast(
                factory.createType(
                    "[Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_clinit(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(factory.objectType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.objectType,
                    factory.createString("instanceLock"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_constructor_0(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_getInstance(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            new CfIf(IfType.NE, ValueType.OBJECT, label8),
            label1,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.objectType,
                    factory.createString("instanceLock"))),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfStore(ValueType.OBJECT, 0),
            new CfMonitor(MonitorType.ENTER),
            label2,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            new CfIf(IfType.NE, ValueType.OBJECT, label4),
            label3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getReceiver")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            new CfLoad(ValueType.OBJECT, 0),
            new CfMonitor(MonitorType.EXIT),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfMonitor(MonitorType.EXIT),
            label7,
            new CfLoad(ValueType.OBJECT, 1),
            new CfThrow(),
            label8,
            new CfFrame(),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(
            new CfTryCatch(
                label2, label5, ImmutableList.of(factory.throwableType), ImmutableList.of(label6)),
            new CfTryCatch(
                label6, label7, ImmutableList.of(factory.throwableType), ImmutableList.of(label6))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_getReceiver(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onAtomicIntegerFieldUpdaterNewUpdater(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.classType,
                    factory.createString("TYPE"))),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType),
                    factory.createString("onAtomicFieldUpdaterNewUpdater")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onAtomicLongFieldUpdaterNewUpdater(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Ljava/lang/Long;"),
                    factory.classType,
                    factory.createString("TYPE"))),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType),
                    factory.createString("onAtomicFieldUpdaterNewUpdater")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onAtomicReferenceFieldUpdaterNewUpdater(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        3,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType),
                    factory.createString("onAtomicFieldUpdaterNewUpdater")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassAsSubclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType),
                    factory.createString("onClassAsSubclass")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassCast(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.objectType),
                    factory.createString("onClassCast")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassForNameDefault(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfConstNull(),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.stringType,
                        factory.booleanType,
                        factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("onClassForName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassForNameWithLoader(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        3,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.stringType,
                        factory.booleanType,
                        factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("onClassForName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetCanonicalName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("CANONICAL_NAME"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                    factory.createString("onClassGetName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetComponentType(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetComponentType")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("onClassGetConstructor")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetConstructors")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredConstructor(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("onClassGetDeclaredConstructor")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredConstructors(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetDeclaredConstructors")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredField(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getDeclaredField")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.classType),
                    factory.createString("getType")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/NoSuchFieldException;"))))),
            new CfStore(ValueType.OBJECT, 3),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType),
                    factory.createString("onClassGetDeclaredField")),
                true),
            label5,
            new CfReturnVoid(),
            label6),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchFieldException;")),
                ImmutableList.of(label3))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredFields(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetDeclaredFields")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredMethod(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        5,
        ImmutableList.of(
            label0,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 3),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredMethod")),
                false),
            new CfStore(ValueType.OBJECT, 4),
            label2,
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(factory.classType),
                    factory.createString("getReturnType")),
                false),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/NoSuchMethodException;"))))),
            new CfStore(ValueType.OBJECT, 4),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            label6,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("onClassGetDeclaredMethod")),
                true),
            label7,
            new CfReturnVoid(),
            label8),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label3,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchMethodException;")),
                ImmutableList.of(label4))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetDeclaredMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetDeclaredMethods")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetField(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getField")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.classType),
                    factory.createString("getType")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/NoSuchFieldException;"))))),
            new CfStore(ValueType.OBJECT, 3),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType),
                    factory.createString("onClassGetField")),
                true),
            label5,
            new CfReturnVoid(),
            label6),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchFieldException;")),
                ImmutableList.of(label3))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetFields(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetFields")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetMethod(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        5,
        ImmutableList.of(
            label0,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 3),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getMethod")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(factory.classType),
                    factory.createString("getReturnType")),
                false),
            new CfStore(ValueType.OBJECT, 3),
            label2,
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/NoSuchMethodException;"))))),
            new CfStore(ValueType.OBJECT, 4),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.classType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Class;")),
                      FrameType.initializedNonNullReference(factory.classType)
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType,
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("onClassGetMethod")),
                true),
            label5,
            new CfReturnVoid(),
            label6),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchMethodException;")),
                ImmutableList.of(label3))),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetMethods(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetMethods")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetName(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("NAME"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                    factory.createString("onClassGetName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetPackage(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetPackage")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetSimpleName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("SIMPLE_NAME"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                    factory.createString("onClassGetName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetSuperclass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassGetSuperclass")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassGetTypeName(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
                    factory.createString("TYPE_NAME"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;")),
                    factory.createString("onClassGetName")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsAnnotation(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANNOTATION"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsAnonymousClass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ANONYMOUS_CLASS"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsArray(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ARRAY"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsAssignableFrom(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.classType),
                    factory.createString("onClassIsAssignableFrom")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsEnum(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("ENUM"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsHidden(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("HIDDEN"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.objectType),
                    factory.createString("onClassIsInstance")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsInterface(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("INTERFACE"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsLocalClass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("LOCAL_CLASS"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsMemberClass(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("MEMBER_CLASS"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsPrimitive(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("PRIMITIVE"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsRecord(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("RECORD"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsSealed(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SEALED"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassIsSynthetic(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
                    factory.createString("SYNTHETIC"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;")),
                    factory.createString("onClassFlag")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onClassNewInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType),
                    factory.createString("onClassNewInstance")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onProxyNewProxyInstance(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        3,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            label1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.createType("Ljava/lang/ClassLoader;"),
                        factory.createType("[Ljava/lang/Class;"),
                        factory.createType("Ljava/lang/reflect/InvocationHandler;")),
                    factory.createString("onProxyNewProxyInstance")),
                true),
            label2,
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onServiceLoaderLoad(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNull(),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("onServiceLoaderLoad")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onServiceLoaderLoadInstalled(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            label1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/ClassLoader;"),
                    factory.createProto(factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("getSystemClassLoader")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("onServiceLoaderLoad")),
                true),
            label2,
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle_onServiceLoaderLoadWithClassLoader(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;")),
                    factory.createString("getInstance")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                    factory.createString("createStack")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType(
                            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                        factory.classType,
                        factory.createType("Ljava/lang/ClassLoader;")),
                    factory.createString("onServiceLoaderLoad")),
                true),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_clinit(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfConstClass(
                factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.booleanType),
                    factory.createString("desiredAssertionStatus")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.booleanType,
                    factory.createString("$assertionsDisabled"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_constructor_1(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            label2,
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_createStack(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        1,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.booleanType,
                    factory.createString("$assertionsDisabled"))),
            new CfIf(IfType.NE, ValueType.INT, label1),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfNew(factory.createType("Ljava/lang/AssertionError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/AssertionError;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label1,
            new CfFrame(),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createString("INSTANCE"))),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("requiresStackInformation")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label4),
            label2,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("getStackTrace")),
                false),
            new CfStore(ValueType.OBJECT, 0),
            label3,
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(2, ValueType.INT),
            new CfLoad(ValueType.OBJECT, 0),
            new CfArrayLength(),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.createType("[Ljava/lang/Object;"),
                        factory.createType("[Ljava/lang/Object;"),
                        factory.intType,
                        factory.intType),
                    factory.createString("copyOfRange")),
                false),
            new CfCheckCast(factory.createType("[Ljava/lang/StackTraceElement;")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.voidType, factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT),
            label4,
            new CfFrame(),
            new CfNew(
                factory.createType(
                    "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstNull(),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createProto(
                        factory.voidType, factory.createType("[Ljava/lang/StackTraceElement;")),
                    factory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_getStackTraceElements(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_stackTraceElementsAsString(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfArrayLength(),
            new CfNewArray(factory.createType("[Ljava/lang/String;")),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;")),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfArrayLength(),
            new CfIfCmp(IfType.GE, ValueType.INT, label5),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfLoad(ValueType.INT, 2),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/StackTraceElement;"),
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfArrayStore(MemberType.OBJECT),
            label4,
            new CfIinc(2, 1),
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/String;"))
                    })),
            new CfLoad(ValueType.OBJECT, 1),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ReflectiveOracle$Stack_toStringStackTrace(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        7,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfIf(IfType.NE, ValueType.OBJECT, label2),
            label1,
            new CfConstString(factory.createString("Stack extraction not enabled.")),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.intType()
                    })),
            new CfNew(factory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType(
                        "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
                    factory.createType("[Ljava/lang/StackTraceElement;"),
                    factory.createString("stackTraceElements"))),
            new CfStore(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 4),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/StackTraceElement;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 5),
            new CfLoad(ValueType.INT, 4),
            new CfIfCmp(IfType.GE, ValueType.INT, label10),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.INT, 5),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 6),
            label5,
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(factory.createString(" at ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 6),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString("\n")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label6,
            new CfLoad(ValueType.INT, 1),
            new CfIf(IfType.NE, ValueType.INT, label8),
            label7,
            new CfGoto(label10),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/StackTraceElement;")),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/StackTraceElement;"))
                    })),
            new CfIinc(1, -1),
            label9,
            new CfIinc(5, 1),
            new CfGoto(label4),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringBuilderType)
                    })),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label11),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
