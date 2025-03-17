// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.templates.CfUtilityMethodsForCodeOptimizations;
import com.android.tools.r8.synthesis.SyntheticItems;

public class UtilityMethodsForCodeOptimizations {

  public interface MethodSynthesizerConsumer {

    UtilityMethodForCodeOptimizations synthesizeMethod(
        AppView<?> appView,
        UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext);
  }

  public static UtilityMethodForCodeOptimizations synthesizeToStringIfNotNullMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.TO_STRING_IF_NOT_NULL,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(method -> getToStringIfNotNullCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityToStringIfNotNullMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getToStringIfNotNullCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_toStringIfNotNull(dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowClassCastExceptionIfNotNullMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    UniqueContext positionContext = methodProcessingContext.createUniqueContext();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_CCE_IF_NOT_NULL,
            positionContext,
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method ->
                            getThrowClassCastExceptionIfNotNullCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowClassCastExceptionIfNotNullCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwClassCastExceptionIfNotNull(
            dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeNonNullMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto =
        dexItemFactory.createProto(dexItemFactory.booleanType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    UniqueContext positionContext = methodProcessingContext.createUniqueContext();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.NON_NULL,
            positionContext,
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(method -> getNonNullCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getNonNullCodeTemplate(DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_isNonNull(dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowAbstractMethodErrorMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.abstractMethodErrorType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_AME,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method -> getThrowAbstractMethodErrorCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowAbstractMethodErrorMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowAbstractMethodErrorCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwAbstractMethodError(
            dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowIllegalAccessErrorMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.illegalAccessErrorType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_IAE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method -> getThrowIllegalAccessErrorCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowIllegalAccessErrorMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowIllegalAccessErrorCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwIllegalAccessError(
            dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowIncompatibleClassChangeErrorMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.icceType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_ICCE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method ->
                            getThrowIncompatibleClassChangeErrorCodeTemplate(
                                method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowIncompatibleClassChangeErrorMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowIncompatibleClassChangeErrorCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwIncompatibleClassChangeError(
            dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowNoSuchMethodErrorMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.noSuchMethodErrorType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_NSME,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method -> getThrowNoSuchMethodErrorCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowNoSuchMethodErrorMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowNoSuchMethodErrorCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwNoSuchMethodError(
            dexItemFactory, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowRuntimeExceptionWithMessageMethod(
      AppView<?> appView,
      UtilityMethodsForCodeOptimizationsEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto =
        dexItemFactory.createProto(dexItemFactory.runtimeExceptionType, dexItemFactory.stringType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            kinds -> kinds.THROW_RTE,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(
                        method ->
                            getThrowRuntimeExceptionWithMessageCodeTemplate(method, dexItemFactory))
                    .setProto(proto));
    eventConsumer.acceptUtilityThrowRuntimeExceptionWithMessageMethod(
        syntheticMethod, methodProcessingContext.getMethodContext());
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowRuntimeExceptionWithMessageCodeTemplate(
      DexMethod method, DexItemFactory dexItemFactory) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwRuntimeExceptionWithMessage(
            dexItemFactory, method);
  }

  public static class UtilityMethodForCodeOptimizations {

    private final ProgramMethod method;
    private boolean optimized;

    private UtilityMethodForCodeOptimizations(ProgramMethod method) {
      this.method = method;
    }

    public ProgramMethod getMethod() {
      assert optimized;
      return method;
    }

    public ProgramMethod uncheckedGetMethod() {
      return method;
    }

    public void optimize(MethodProcessor methodProcessor) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
      optimized = true;
    }
  }
}
