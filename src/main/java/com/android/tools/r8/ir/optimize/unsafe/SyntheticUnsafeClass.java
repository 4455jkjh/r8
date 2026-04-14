// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.unsafe;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Comparator;
import org.objectweb.asm.Opcodes;

public class SyntheticUnsafeClass {

  private static final String unsafeFieldName = "unsafe";
  private static final String getUnsafeMethodName = "getUnsafe";
  private static final String getAndSetMethodName = "getAndSet";

  private final DexMethod classInitializer;
  private final DexMethod getUnsafeMethod;
  private final DexMethod getAndSetMethod;
  private final DexField instanceField;

  private SyntheticUnsafeClass(
      ProgramMethod classInitializer,
      ProgramMethod getAndSetMethod,
      ProgramMethod getUnsafeMethod,
      ProgramField instanceField) {
    this.classInitializer = classInitializer.getReference();
    this.getAndSetMethod = getAndSetMethod.getReference();
    this.getUnsafeMethod = getUnsafeMethod.getReference();
    this.instanceField = instanceField.getReference();
  }

  public DexMethod getClassInitializer() {
    return classInitializer;
  }

  public DexField getInstanceField() {
    return instanceField;
  }

  public DexMethod getGetAndSetMethod() {
    return getAndSetMethod;
  }

  public DexMethod getGetUnsafeMethod() {
    return getUnsafeMethod;
  }

  public DexType getUnsafeClass() {
    return classInitializer.getHolderType();
  }

  public static boolean isEnabled(AppView<? extends AppInfoWithClassHierarchy> appView) {
    var options = appView.options();
    return !appView.appInfo().classes().isEmpty()
        && options.isGeneratingDex()
        && options.isOptimizing()
        && options.isShrinking()
        && options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  public static void synthesize(AppView<AppInfoWithLiveness> appView) {
    if (!isEnabled(appView)) {
      return;
    }
    var context = getDeterministicContext(appView);
    var factory = appView.dexItemFactory();
    var unsafeClass =
        appView
            .getSyntheticItems()
            .createFixedClass(
                kinds -> kinds.UNSAFE_HELPER,
                context,
                appView,
                builder -> buildUnsafeClass(appView, builder));
    var classInitializer = unsafeClass.getProgramClassInitializer();
    var instanceField =
        unsafeClass.lookupProgramField(
            factory.createField(unsafeClass.getType(), factory.sunMiscUnsafeType, unsafeFieldName));
    assert instanceField != null;
    var getUnsafeMethod =
        unsafeClass.lookupProgramMethod(
            factory.createMethod(
                unsafeClass.getType(),
                factory.createProto(factory.sunMiscUnsafeType),
                getUnsafeMethodName));
    assert getUnsafeMethod != null;
    var getAndSetMethod =
        unsafeClass.lookupProgramMethod(
            factory.createMethod(
                unsafeClass.getType(),
                factory.createProto(
                    factory.objectType,
                    factory.sunMiscUnsafeType,
                    factory.objectType,
                    factory.longType,
                    factory.objectType),
                getAndSetMethodName));
    assert getAndSetMethod != null;
    appView.rebuildAppInfo();
    appView.setSyntheticUnsafeClass(
        new SyntheticUnsafeClass(
            classInitializer, getAndSetMethod, getUnsafeMethod, instanceField));
    appView
        .getKeepInfo()
        .mutate(
            keepInfo -> {
              keepInfo.ensureCompilerSynthesizedClass(unsafeClass);
              keepInfo.registerCompilerSynthesizedMethod(classInitializer);
              keepInfo.registerCompilerSynthesizedMethod(getAndSetMethod);
              keepInfo.registerCompilerSynthesizedMethod(getUnsafeMethod);
              keepInfo.joinClass(unsafeClass, Joiner::disallowOptimization);
              keepInfo.joinMethod(classInitializer, Joiner::disallowOptimization);
              keepInfo.joinMethod(getAndSetMethod, Joiner::disallowOptimization);
              keepInfo.joinMethod(getUnsafeMethod, Joiner::disallowOptimization);
            });
  }

  private static void buildUnsafeClass(
      AppView<AppInfoWithLiveness> appView, SyntheticProgramClassBuilder builder) {
    DexItemFactory factory = appView.dexItemFactory();
    DexField unsafeField =
        factory.createField(builder.getType(), factory.sunMiscUnsafeType, unsafeFieldName);
    var field =
        DexEncodedField.syntheticBuilder()
            .setField(unsafeField)
            .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
            .setApiLevel(appView.computedMinApiLevel())
            // Avoid superfluous assert on API in the build call when API modeling is disabled.
            .disableAndroidApiLevelCheckIf(
                !appView.options().apiModelingOptions().isApiModelingEnabled())
            .build();
    builder.setStaticFields(ImmutableList.of(field));
    var accessBuilder = FieldAccessInfoCollectionModifier.builder();
    accessBuilder.addField(unsafeField);
    accessBuilder.build().modify(appView);

    DexMethod getUnsafeMethod =
        factory.createMethod(
            builder.getType(), factory.createProto(factory.sunMiscUnsafeType), getUnsafeMethodName);
    builder.addMethod(
        methodBuilder -> {
          methodBuilder
              .setName(getUnsafeMethod.name)
              .setProto(getUnsafeMethod.proto)
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              .setApiLevelForDefinition(appView.computedMinApiLevel())
              .setApiLevelForCode(appView.computedMinApiLevel())
              .setCode(
                  method ->
                      SyntheticUnsafeMethods.SyntheticUnsafeMethodTemplates_getUnsafe(
                          factory, method));
          if (appView.options().isGeneratingClassFiles()) {
            methodBuilder.setClassFileVersion(
                appView.options().requiredCfVersionForConstClassInstructions());
          }
        });
    DexMethod getAndSetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.objectType,
                factory.sunMiscUnsafeType,
                factory.objectType,
                factory.longType,
                factory.objectType),
            getAndSetMethodName);
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setName(getAndSetMethod.name)
                .setProto(getAndSetMethod.proto)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        SyntheticUnsafeMethods.SyntheticUnsafeMethodTemplates_getAndSet(
                            factory, method)));
    DexMethod clinit = factory.createClassInitializer(builder.getType());
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setName(clinit.name)
                .setProto(clinit.proto)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        new CfCode(
                            method.holder,
                            1,
                            0,
                            ImmutableList.of(
                                new CfInvoke(Opcodes.INVOKESTATIC, getUnsafeMethod, false),
                                new CfStaticFieldWrite(unsafeField),
                                new CfReturnVoid()))));
  }

  private static DexProgramClass getDeterministicContext(AppView<AppInfoWithLiveness> appView) {
    return Collections.min(
        appView.appInfo().classes(), Comparator.comparing(DexProgramClass::getType));
  }
}
