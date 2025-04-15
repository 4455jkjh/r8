// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.L8ProgramEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider.EmulateDispatchType;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.LinkedHashMap;

public final class ProgramEmulatedInterfaceSynthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final InterfaceDesugaringSyntheticHelper helper;

  public static ProgramEmulatedInterfaceSynthesizer create(AppView<?> appView) {
    assert appView.options().getLibraryDesugaringOptions().isDesugaredLibraryCompilation();
    if (appView
        .options()
        .getLibraryDesugaringOptions()
        .getMachineDesugaredLibrarySpecification()
        .hasEmulatedInterfaces()) {
      return new ProgramEmulatedInterfaceSynthesizer(appView);
    }
    return null;
  }

  private ProgramEmulatedInterfaceSynthesizer(AppView<?> appView) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    this.appView = appView;
    this.helper =
        new InterfaceDesugaringSyntheticHelper(
            appView,
            libraryDesugaringOptions,
            libraryDesugaringOptions.getMachineDesugaredLibrarySpecification());
  }

  private void synthesizeProgramEmulatedInterface(
      DexProgramClass emulatedInterface,
      EmulatedInterfaceDescriptor emulatedInterfaceDescriptor,
      L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    appView
        .getSyntheticItems()
        .ensureFixedClass(
            kinds -> kinds.EMULATED_INTERFACE_CLASS,
            emulatedInterface,
            appView,
            builder ->
                synthesizeEmulateInterfaceMethods(
                    emulatedInterface, emulatedInterfaceDescriptor, builder, eventConsumer),
            eventConsumer::acceptProgramEmulatedInterface);
  }

  private void synthesizeEmulateInterfaceMethods(
      DexProgramClass emulatedInterface,
      EmulatedInterfaceDescriptor emulatedInterfaceDescriptor,
      SyntheticProgramClassBuilder builder,
      L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    emulatedInterface.forEachProgramVirtualMethodMatching(
        m -> emulatedInterfaceDescriptor.getEmulatedMethods().containsKey(m.getReference()),
        method ->
            builder.addMethod(
                methodBuilder ->
                    synthesizeEmulatedInterfaceMethod(
                        method,
                        emulatedInterfaceDescriptor.getEmulatedMethods().get(method.getReference()),
                        builder.getType(),
                        methodBuilder,
                        eventConsumer)));
  }

  private void synthesizeEmulatedInterfaceMethod(
      ProgramMethod method,
      EmulatedDispatchMethodDescriptor descriptor,
      DexType dispatchType,
      SyntheticMethodBuilder methodBuilder,
      L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    assert !method.getDefinition().isStatic();
    DexMethod emulatedMethod =
        helper.emulatedInterfaceDispatchMethod(
            descriptor.getEmulatedDispatchMethod(), dispatchType);
    DexMethod itfMethod = helper.emulatedInterfaceInterfaceMethod(descriptor.getInterfaceMethod());
    DexMethod companionMethod =
        helper.ensureEmulatedInterfaceForwardingMethod(
            descriptor.getForwardingMethod(), eventConsumer);
    LinkedHashMap<DexType, DexMethod> extraDispatchCases =
        resolveDispatchCases(descriptor, eventConsumer);
    methodBuilder
        .setName(emulatedMethod.getName())
        .setProto(emulatedMethod.getProto())
        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
        .setCode(
            emulatedInterfaceMethod ->
                new EmulateDispatchSyntheticCfCodeProvider(
                        emulatedMethod.getHolderType(),
                        companionMethod,
                        itfMethod,
                        extraDispatchCases,
                        EmulateDispatchType.ALL_STATIC,
                        appView)
                    .generateCfCode());
  }

  private LinkedHashMap<DexType, DexMethod> resolveDispatchCases(
      EmulatedDispatchMethodDescriptor descriptor,
      L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    LinkedHashMap<DexType, DexMethod> extraDispatchCases = new LinkedHashMap<>();
    descriptor
        .getDispatchCases()
        .forEach(
            (type, derivedMethod) ->
                extraDispatchCases.put(
                    type,
                    helper.ensureEmulatedInterfaceForwardingMethod(derivedMethod, eventConsumer)));
    return extraDispatchCases;
  }

  @Override
  public String uniqueIdentifier() {
    return "$emulatedInterface";
  }

  @Override
  public void synthesizeClasses(
      ClassSynthesisDesugaringContext processingContext,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    assert appView.options().getLibraryDesugaringOptions().isDesugaredLibraryCompilation();
    appView
        .options()
        .getLibraryDesugaringOptions()
        .getMachineDesugaredLibrarySpecification()
        .getEmulatedInterfaces()
        .forEach(
            (emulatedInterfaceType, emulatedInterfaceDescriptor) -> {
              DexClass emulatedInterfaceClazz = appView.definitionFor(emulatedInterfaceType);
              if (emulatedInterfaceClazz == null || !emulatedInterfaceClazz.isProgramClass()) {
                warnMissingEmulatedInterface(emulatedInterfaceType);
                return;
              }
              DexProgramClass emulatedInterface = emulatedInterfaceClazz.asProgramClass();
              assert emulatedInterface != null;
              if (!appView.isAlreadyLibraryDesugared(emulatedInterface)
                  && !emulatedInterfaceDescriptor.getEmulatedMethods().isEmpty()) {
                synthesizeProgramEmulatedInterface(
                    emulatedInterface, emulatedInterfaceDescriptor, eventConsumer);
              }
            });
  }

  private void warnMissingEmulatedInterface(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot emulate interface "
                + interfaceType.getName()
                + " because the interface is missing.");
    appView.options().reporter.warning(warning);
  }
}
