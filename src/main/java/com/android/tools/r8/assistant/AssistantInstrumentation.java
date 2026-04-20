// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class AssistantInstrumentation {

  public static void injectGlobalSynthetics(AppView<AppInfo> appView, Timing timing) {
    if (!appView.options().getAssistantOptions().enableAssistantInstrumentation) {
      return;
    }
    timing.begin("Assistant global synthetics");
    AssistantRuntimeMethods.registerSynthesizedCodeReferences(appView.dexItemFactory());
    AssistantInstrumentation instrumentation = new AssistantInstrumentation(appView);
    instrumentation.injectGlobalSynthetics(
        Collections.singleton(
            Collections.min(
                appView.appInfo().classes(), Comparator.comparing(DexProgramClass::getType))));
    timing.end();
  }

  public static void instrumentClasses(Timing timing, AppView<AppInfo> appView)
      throws ExecutionException {
    if (!appView.options().getAssistantOptions().enableAssistantInstrumentation) {
      return;
    }
    timing.begin("Assistant instrumentation");
    AssistantInstrumentation instrumentation = new AssistantInstrumentation(appView);
    instrumentation.instrumentClasses(timing);
    timing.end();
  }

  private final AppView<AppInfo> appView;
  private final DexItemFactory factory;

  private AssistantInstrumentation(AppView<AppInfo> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public static void ensureGlobalSynthetics(
      AppView<?> appView, Collection<? extends ProgramDefinition> contexts) {
    AssistantInstrumentation instrumentation =
        new AssistantInstrumentation((AppView<AppInfo>) appView);
    instrumentation.injectGlobalSynthetics(contexts);
  }

  private void injectGlobalSynthetics(Collection<? extends ProgramDefinition> contexts) {
    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_ORACLE,
        factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle;"),
        contexts,
        builder -> AssistantRuntimeMethods.generateReflectiveOracleClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_ORACLE_STACK,
        factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOracle$Stack;"),
        contexts,
        builder -> AssistantRuntimeMethods.generateStackClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_OPERATION_JSON_LOGGER,
        factory.createType(
            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationJsonLogger;"),
        contexts,
        builder ->
            AssistantRuntimeMethods.generateReflectiveOperationJsonLoggerClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_EVENT_TYPE,
        factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveEventType;"),
        contexts,
        builder -> AssistantRuntimeMethods.generateReflectiveEventTypeClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_OPERATION_RECEIVER,
        factory.createType("Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver;"),
        contexts,
        builder ->
            AssistantRuntimeMethods.generateReflectiveOperationReceiverClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_OPERATION_RECEIVER_CLASS_FLAG,
        factory.createType(
            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$ClassFlag;"),
        contexts,
        builder -> AssistantRuntimeMethods.generateClassFlagClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_REFLECTIVE_OPERATION_RECEIVER_NAME_LOOKUP_TYPE,
        factory.createType(
            "Lcom/android/tools/r8/assistant/runtime/ReflectiveOperationReceiver$NameLookupType;"),
        contexts,
        builder -> AssistantRuntimeMethods.generateNameLookupTypeClass(builder, factory));

    ensureGlobalClass(
        kinds -> kinds.ASSISTANT_EMPTY_REFLECTIVE_OPERATION_RECEIVER,
        factory.createType(
            "Lcom/android/tools/r8/assistant/runtime/EmptyReflectiveOperationReceiver;"),
        contexts,
        builder ->
            AssistantRuntimeMethods.generateEmptyReflectiveOperationReceiverClass(
                builder, factory));
  }

  private void ensureGlobalClass(
      SyntheticItems.SyntheticKindSelector kindSelector,
      DexType type,
      Collection<? extends ProgramDefinition> contexts,
      Consumer<com.android.tools.r8.synthesis.SyntheticProgramClassBuilder> fn) {
    appView
        .getSyntheticItems()
        .ensureGlobalClass(
            () -> new MissingGlobalSyntheticsConsumerDiagnostic("Assistant instrumentation"),
            kindSelector,
            type,
            contexts,
            appView,
            fn,
            clazz -> {});
  }

  private void instrumentClasses(Timing timing) throws ExecutionException {
    PrimaryD8L8IRConverter converter = new PrimaryD8L8IRConverter(appView, timing);
    ReflectiveInstrumentation reflectiveInstrumentation =
        new ReflectiveInstrumentation(appView, converter, timing);
    // TODO(b/394013779): Use executorService for parallel instrumentation.
    reflectiveInstrumentation.instrumentClasses();
  }
}
