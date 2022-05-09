// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer.D8CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class ClassConverter {

  protected final AppView<?> appView;
  private final IRConverter converter;
  private final D8MethodProcessor methodProcessor;
  private final InterfaceProcessor interfaceProcessor;

  ClassConverter(
      AppView<?> appView,
      IRConverter converter,
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor) {
    this.appView = appView;
    this.converter = converter;
    this.methodProcessor = methodProcessor;
    this.interfaceProcessor = interfaceProcessor;
  }

  public static ClassConverter create(
      AppView<?> appView,
      IRConverter converter,
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor) {
    return appView.options().desugarSpecificOptions().allowAllDesugaredInput
        ? new LibraryDesugaredClassConverter(
            appView, converter, methodProcessor, interfaceProcessor)
        : new DefaultClassConverter(appView, converter, methodProcessor, interfaceProcessor);
  }

  public ClassConverterResult convertClasses(ExecutorService executorService)
      throws ExecutionException {
    ClassConverterResult.Builder resultBuilder = ClassConverterResult.builder();
    internalConvertClasses(resultBuilder, executorService);
    notifyAllClassesConverted();
    return resultBuilder.build();
  }

  private void internalConvertClasses(
      ClassConverterResult.Builder resultBuilder, ExecutorService executorService)
      throws ExecutionException {
    Collection<DexProgramClass> classes = appView.appInfo().classes();

    CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer =
        new CfClassSynthesizerDesugaringEventConsumer();
    converter.classSynthesisDesugaring(executorService, classSynthesizerEventConsumer);
    if (!classSynthesizerEventConsumer.getSynthesizedClasses().isEmpty()) {
      classes =
          ImmutableList.<DexProgramClass>builder()
              .addAll(classes)
              .addAll(classSynthesizerEventConsumer.getSynthesizedClasses())
              .build();
    }

    converter.prepareDesugaringForD8(executorService);

    while (!classes.isEmpty()) {
      Set<DexType> seenNestHosts = Sets.newIdentityHashSet();
      List<DexProgramClass> deferred = new ArrayList<>(classes.size() / 2);
      List<DexProgramClass> wave = new ArrayList<>(classes.size());
      for (DexProgramClass clazz : classes) {
        if (clazz.isInANest() && !seenNestHosts.add(clazz.getNestHost())) {
          deferred.add(clazz);
        } else {
          wave.add(clazz);

          // TODO(b/179755192): Avoid marking classes as scheduled by building up waves of methods.
          methodProcessor.addScheduled(clazz);
        }
      }

      D8CfInstructionDesugaringEventConsumer instructionDesugaringEventConsumer =
          CfInstructionDesugaringEventConsumer.createForD8(methodProcessor);

      // Process the wave and wait for all IR processing to complete.
      methodProcessor.newWave();
      checkWaveDeterminism(wave);
      ThreadUtils.processItems(
          wave, clazz -> convertClass(clazz, instructionDesugaringEventConsumer), executorService);
      methodProcessor.awaitMethodProcessing();

      // Finalize the desugaring of the processed classes. This may require processing (and
      // reprocessing) of some methods.
      List<ProgramMethod> needsProcessing =
          instructionDesugaringEventConsumer.finalizeDesugaring(appView, resultBuilder);
      if (!needsProcessing.isEmpty()) {
        // Create a new processor context to ensure unique method processing contexts.
        methodProcessor.newWave();

        // Process the methods that require reprocessing. These are all simple bridge methods and
        // should therefore not lead to additional desugaring.
        ThreadUtils.processItems(
            needsProcessing,
            method -> {
              DexEncodedMethod definition = method.getDefinition();
              if (definition.isProcessed()) {
                definition.markNotProcessed();
              }
              methodProcessor.processMethod(method, instructionDesugaringEventConsumer);
            },
            executorService);

        // Verify there is nothing to finalize once method processing finishes.
        methodProcessor.awaitMethodProcessing();
        assert instructionDesugaringEventConsumer.verifyNothingToFinalize();
      }

      classes = deferred;
    }
  }

  private void checkWaveDeterminism(Collection<DexProgramClass> wave) {
    appView
        .options()
        .testing
        .checkDeterminism(
            checker -> {
              // There is no constraint on the order within the wave so sort them to have a
              // deterministic log.
              List<DexProgramClass> sorted = new ArrayList<>(wave);
              sorted.sort(Comparator.comparing(DexClass::getType));
              checker.accept(
                  lineCallback -> {
                    for (DexProgramClass clazz : sorted) {
                      lineCallback.onLine(clazz.getType().toDescriptorString());
                    }
                  });
            });
  }

  abstract void convertClass(
      DexProgramClass clazz, D8CfInstructionDesugaringEventConsumer desugaringEventConsumer);

  void convertMethods(
      DexProgramClass clazz, D8CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
    converter.convertMethods(clazz, desugaringEventConsumer, methodProcessor, interfaceProcessor);
  }

  abstract void notifyAllClassesConverted();

  static class DefaultClassConverter extends ClassConverter {

    DefaultClassConverter(
        AppView<?> appView,
        IRConverter converter,
        D8MethodProcessor methodProcessor,
        InterfaceProcessor interfaceProcessor) {
      super(appView, converter, methodProcessor, interfaceProcessor);
    }

    @Override
    void convertClass(
        DexProgramClass clazz, D8CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
      convertMethods(clazz, desugaringEventConsumer);
    }

    @Override
    void notifyAllClassesConverted() {
      // Intentionally empty.
    }
  }

  static class LibraryDesugaredClassConverter extends ClassConverter {

    private final Set<DexType> alreadyLibraryDesugared = Sets.newConcurrentHashSet();

    LibraryDesugaredClassConverter(
        AppView<?> appView,
        IRConverter converter,
        D8MethodProcessor methodProcessor,
        InterfaceProcessor interfaceProcessor) {
      super(appView, converter, methodProcessor, interfaceProcessor);
    }

    @Override
    void convertClass(
        DexProgramClass clazz, D8CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
      // Classes which has already been through library desugaring will not go through IR
      // processing again.
      LibraryDesugaredChecker libraryDesugaredChecker = new LibraryDesugaredChecker(appView);
      if (libraryDesugaredChecker.isClassLibraryDesugared(clazz)) {
        alreadyLibraryDesugared.add(clazz.getType());
      } else {
        convertMethods(clazz, desugaringEventConsumer);
      }
    }

    @Override
    void notifyAllClassesConverted() {
      appView.setAlreadyLibraryDesugared(alreadyLibraryDesugared);
    }
  }
}
