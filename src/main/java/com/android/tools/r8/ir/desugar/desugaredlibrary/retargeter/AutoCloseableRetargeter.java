// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterHelper.createCloseMethod;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterHelper.lookupSuperIncludingInterfaces;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider.EmulateDispatchType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class AutoCloseableRetargeter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final AutoCloseableRetargeterHelper data;

  public AutoCloseableRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.data =
        new AutoCloseableRetargeterHelper(
            appView.options().getMinApiLevel(), appView.dexItemFactory());
  }

  public DexMethod synthesizeDispatcher(
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      AutoCloseableRetargeterEventConsumer eventConsumer) {
    DexItemFactory factory = appView.dexItemFactory();
    LinkedHashMap<DexType, DexMethod> dispatchCases =
        data.synthesizeDispatchCases(appView, context, eventConsumer, methodProcessingContext);
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.AUTOCLOSEABLE_DISPATCHER,
                methodProcessingContext.createUniqueContext(),
                appView,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(factory.createProto(factory.voidType, factory.objectType))
                        .setCode(
                            methodSig ->
                                new EmulateDispatchSyntheticCfCodeProvider(
                                        methodSig.getHolderType(),
                                        data.createThrowUnsupportedException(
                                                appView,
                                                context,
                                                eventConsumer,
                                                methodProcessingContext::createUniqueContext)
                                            .getReference(),
                                        createCloseMethod(factory, factory.autoCloseableType),
                                        dispatchCases,
                                        EmulateDispatchType.AUTO_CLOSEABLE,
                                        appView)
                                    .generateCfCode()));
    eventConsumer.acceptAutoCloseableDispatchMethod(method, context);
    return method.getReference();
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    consumer.accept(Opcodes.INVOKEVIRTUAL);
    consumer.accept(Opcodes.INVOKESPECIAL);
    consumer.accept(Opcodes.INVOKEINTERFACE);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvoke() && !instruction.isInvokeStatic()) {
      return computeInvokeDescription(instruction, context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeInvokeDescription(
      CfInstruction instruction, ProgramMethod context) {
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(context.getHolderType(), kinds -> kinds.AUTOCLOSEABLE_DISPATCHER)) {
      return DesugarDescription.nothing();
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod invokedMethod = cfInvoke.getMethod();
    if (!data.hasCloseMethodName(invokedMethod) || invokedMethod.getArity() > 0) {
      return DesugarDescription.nothing();
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodLegacy(invokedMethod, cfInvoke.isInterface());
    if (!resolutionResult.isSingleResolution()) {
      return DesugarDescription.nothing();
    }
    assert resolutionResult.getSingleTarget() != null;
    DexMethod reference = resolutionResult.getSingleTarget().getReference();
    if (data.shouldEmulateMethod(reference)) {
      return computeNewTarget(reference, cfInvoke.isInvokeSuper(context.getHolderType()), context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeNewTarget(
      DexMethod singleTarget, boolean superInvoke, ProgramMethod context) {
    if (superInvoke) {
      DexClassAndMethod superMethod =
          lookupSuperIncludingInterfaces(appView, singleTarget, context.getContextClass());
      if (superMethod != null && superMethod.isLibraryMethod()) {
        DexType holderType = superMethod.getHolderType();
        if (data.superTargetsToRewrite().contains(holderType)) {
          return createWithTarget(
              singleTarget,
              (eventConsumer, methodProcessingContext) ->
                  data.synthesizeDispatchCase(
                      appView,
                      holderType,
                      context,
                      eventConsumer,
                      methodProcessingContext::createUniqueContext));
        }
      }
      return DesugarDescription.nothing();
    }
    return createWithTarget(
        singleTarget,
        (eventConsumer, methodProcessingContext) ->
            synthesizeDispatcher(context, methodProcessingContext, eventConsumer));
  }

  private DesugarDescription createWithTarget(
      DexMethod target,
      BiFunction<CfInstructionDesugaringEventConsumer, MethodProcessingContext, DexMethod>
          methodProvider) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugarings,
                dexItemFactory) -> {
              DexMethod newInvokeTarget =
                  methodProvider.apply(eventConsumer, methodProcessingContext);
              assert appView.definitionFor(newInvokeTarget.getHolderType()) != null;
              assert !appView.definitionFor(newInvokeTarget.getHolderType()).isInterface();
              List<CfInstruction> instructions = new ArrayList<>();
              if (appView.getSyntheticItems().isSynthetic(newInvokeTarget.getHolderType())) {
                instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, false));
              } else {
                instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, newInvokeTarget, false));
              }
              if (target.getReturnType().isVoidType()
                  && !newInvokeTarget.getReturnType().isVoidType()) {
                instructions.add(new CfStackInstruction(Opcode.Pop));
              }
              return instructions;
            })
        .build();
  }
}
