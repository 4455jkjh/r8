// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.unsafe;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
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
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Comparator;

public class SyntheticUnsafeClass {

  private static final String unsafeFieldName = "unsafe";
  private static final String getAndSetMethodName = "getAndSet";
  private static final String storeStoreFenceMethodName = "storeStoreFence";

  private final DexMethod classInitializer;
  private final DexMethod getAndSetMethod;
  private final DexMethod storeStoreFenceMethod;
  private final DexField instanceField;

  private SyntheticUnsafeClass(
      ProgramMethod classInitializer,
      ProgramMethod getAndSetMethod,
      ProgramMethod storeStoreFenceMethod,
      ProgramField instanceField) {
    this(
        classInitializer.getReference(),
        getAndSetMethod.getReference(),
        storeStoreFenceMethod != null ? storeStoreFenceMethod.getReference() : null,
        instanceField.getReference());
  }

  private SyntheticUnsafeClass(
      DexMethod classInitializer,
      DexMethod getAndSetMethod,
      DexMethod storeStoreFenceMethod,
      DexField instanceField) {
    this.classInitializer = classInitializer;
    this.getAndSetMethod = getAndSetMethod;
    this.storeStoreFenceMethod = storeStoreFenceMethod;
    this.instanceField = instanceField;
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

  public DexMethod getStoreStoreFenceMethod() {
    return storeStoreFenceMethod;
  }

  public DexType getUnsafeClass() {
    return classInitializer.getHolderType();
  }

  public SyntheticUnsafeClass rewrittenWithLens(GraphLens lens, GraphLens appliedLens) {
    return new SyntheticUnsafeClass(
        lens.getRenamedMethodSignature(classInitializer, appliedLens),
        lens.getRenamedMethodSignature(getAndSetMethod, appliedLens),
        storeStoreFenceMethod != null
            ? lens.getRenamedMethodSignature(storeStoreFenceMethod, appliedLens)
            : null,
        lens.getRenamedFieldSignature(instanceField, appliedLens));
  }

  public static boolean isEnabled(AppView<? extends AppInfoWithClassHierarchy> appView) {
    var options = appView.options();
    return !appView.appInfo().classes().isEmpty()
        && options.isGeneratingDex()
        && options.isOptimizing()
        && options.isShrinking()
        && options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private static boolean isStoreStoreFenceEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    var options = appView.options();
    var minApiLevel = options.getMinApiLevel();
    // >=N so that Unsafe#storeFence is present, <T since then we can use VarHandle#storeStoreFence.
    return minApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.N)
        && minApiLevel.isLessThan(AndroidApiLevel.T)
        && options.inlinerOptions().enableConstructorInliningWithFinalFieldsPreAndroidT;
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
    var unsafeField =
        unsafeClass.lookupProgramField(
            factory.createField(unsafeClass.getType(), factory.sunMiscUnsafeType, unsafeFieldName));
    assert unsafeField != null;
    var getAndSetMethod =
        unsafeClass.lookupProgramMethod(
            factory.createMethod(
                unsafeClass.getType(),
                factory.createProto(
                    factory.objectType,
                    factory.objectType,
                    factory.longType,
                    factory.objectType),
                getAndSetMethodName));
    assert getAndSetMethod != null;
    var storeStoreFenceMethod =
        unsafeClass.lookupProgramMethod(
            factory.createMethod(
                unsafeClass.getType(),
                factory.createProto(factory.voidType),
                storeStoreFenceMethodName));
    assert storeStoreFenceMethod != null || !isStoreStoreFenceEnabled(appView);
    appView.rebuildAppInfo(Timing.empty());
    appView.setSyntheticUnsafeClass(
        new SyntheticUnsafeClass(
            classInitializer, getAndSetMethod, storeStoreFenceMethod, unsafeField));
    appView
        .getKeepInfo()
        .mutate(
            keepInfo -> {
              keepInfo.joinClass(unsafeClass, Joiner::disallowOptimization);
              keepInfo.joinMethod(classInitializer, Joiner::disallowOptimization);
              keepInfo.joinMethod(getAndSetMethod, Joiner::disallowOptimization);
              if (storeStoreFenceMethod != null) {
                keepInfo.joinMethod(storeStoreFenceMethod, Joiner::disallowOptimization);
              }
            });
  }

  private static void buildUnsafeClass(
      AppView<AppInfoWithLiveness> appView, SyntheticProgramClassBuilder builder) {
    DexItemFactory factory = appView.dexItemFactory();
    var unsafeField =
        DexEncodedField.syntheticBuilder()
            .setField(
                factory.createField(builder.getType(), factory.sunMiscUnsafeType, unsafeFieldName))
            .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
            .setApiLevel(appView.computedMinApiLevel())
            // Avoid superfluous assert on API in the build call when API modeling is disabled.
            .disableAndroidApiLevelCheckIf(
                !appView.options().apiModelingOptions().isApiModelingEnabled())
            .build();
    builder.setStaticFields(ImmutableList.of(unsafeField));

    // Record new field.
    FieldAccessInfoCollectionModifier.builder()
        .addField(unsafeField.getReference())
        .build()
        .modify(appView);

    DexMethod getAndSetMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.objectType,
                factory.objectType,
                factory.longType,
                factory.objectType),
            getAndSetMethodName);
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setMethod(getAndSetMethod)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        SyntheticUnsafeMethods.SyntheticUnsafeMethodTemplates_getAndSet(
                            factory, method)));

    // Unsafe#storeFence is only present from API level 24. It is only used if constructor inlining
    // pre-Android T is enabled.
    if (isStoreStoreFenceEnabled(appView)) {
      DexMethod storeStoreFenceMethod =
          factory.createMethod(
              builder.getType(), factory.createProto(factory.voidType), storeStoreFenceMethodName);
      builder.addMethod(
          methodBuilder ->
              methodBuilder
                  .setMethod(storeStoreFenceMethod)
                  .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                  .setApiLevelForDefinition(appView.computedMinApiLevel())
                  .setApiLevelForCode(appView.computedMinApiLevel())
                  .setCode(
                      method ->
                          SyntheticUnsafeMethods.SyntheticUnsafeMethodTemplates_storeStoreFence(
                              factory, method)));
    }

    DexMethod clinit = factory.createClassInitializer(builder.getType());
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setMethod(clinit)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        SyntheticUnsafeMethods.SyntheticUnsafeMethodTemplates_classInitializer(
                            factory, method)));
  }

  private static DexProgramClass getDeterministicContext(AppView<AppInfoWithLiveness> appView) {
    return Collections.min(
        appView.appInfo().classes(), Comparator.comparing(DexProgramClass::getType));
  }
}
