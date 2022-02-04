// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeter.InvokeRetargetingResult.NO_REWRITING;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryRetargeter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  private final RetargetingInfo retargetingInfo;
  private final Map<DexMethod, DexMethod> staticRetarget;
  private final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    retargetingInfo = RetargetingInfo.get(appView);
    staticRetarget = retargetingInfo.getStaticRetarget();
    nonEmulatedVirtualRetarget = retargetingInfo.getNonEmulatedVirtualRetarget();
    emulatedVirtualRetarget = retargetingInfo.getEmulatedVirtualRetarget();
  }

  // Used by the ListOfBackportedMethods utility.
  public void visit(Consumer<DexMethod> consumer) {
    staticRetarget.keySet().forEach(consumer);
    nonEmulatedVirtualRetarget.keySet().forEach(consumer);
    emulatedVirtualRetarget.keySet().forEach(consumer);
  }

  public RetargetingInfo getRetargetingInfo() {
    return retargetingInfo;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    InvokeRetargetingResult invokeRetargetingResult = computeNewInvokeTarget(instruction, context);

    if (!invokeRetargetingResult.hasNewInvokeTarget()) {
      return null;
    }

    DexMethod newInvokeTarget = invokeRetargetingResult.getNewInvokeTarget(eventConsumer);
    return Collections.singletonList(
        new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, instruction.asInvoke().isInterface()));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return computeNewInvokeTarget(instruction, context).hasNewInvokeTarget();
  }

  private void ensureRetargetMethod(
      DexMethod retarget, DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    DexClass holderClass = appView.definitionFor(retarget.getHolderType());
    if (holderClass != null && holderClass.isProgramClass()) {
      assert holderClass.lookupMethod(retarget) != null;
      return;
    }
    appView
        .getSyntheticItems()
        .ensureFixedClasspathMethodFromType(
            retarget.getName(),
            retarget.getProto(),
            SyntheticKind.RETARGET_STUB,
            retarget.getHolderType(),
            appView,
            ignored -> {},
            eventConsumer::acceptDesugaredLibraryRetargeterDispatchClasspathClass,
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(null));
  }

  InvokeRetargetingResult ensureInvokeRetargetingResult(DexMethod retarget) {
    if (retarget == null) {
      return NO_REWRITING;
    }
    return new InvokeRetargetingResult(
        true,
        eventConsumer -> {
          ensureRetargetMethod(retarget, eventConsumer);
          return retarget;
        });
  }

  static class InvokeRetargetingResult {

    static InvokeRetargetingResult NO_REWRITING =
        new InvokeRetargetingResult(false, ignored -> null);

    private final boolean hasNewInvokeTarget;
    private final Function<DesugaredLibraryRetargeterInstructionEventConsumer, DexMethod>
        newInvokeTargetSupplier;

    static InvokeRetargetingResult createInvokeRetargetingResult(DexMethod retarget) {
      if (retarget == null) {
        return NO_REWRITING;
      }
      return new InvokeRetargetingResult(true, ignored -> retarget);
    }

    private InvokeRetargetingResult(
        boolean hasNewInvokeTarget,
        Function<DesugaredLibraryRetargeterInstructionEventConsumer, DexMethod>
            newInvokeTargetSupplier) {
      this.hasNewInvokeTarget = hasNewInvokeTarget;
      this.newInvokeTargetSupplier = newInvokeTargetSupplier;
    }

    public boolean hasNewInvokeTarget() {
      return hasNewInvokeTarget;
    }

    public DexMethod getNewInvokeTarget(
        DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
      assert hasNewInvokeTarget();
      return newInvokeTargetSupplier.apply(eventConsumer);
    }
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return NO_REWRITING;
    }
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .getDontRetarget()
        .contains(context.getContextType())) {
      return NO_REWRITING;
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod invokedMethod = cfInvoke.getMethod();
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethod(invokedMethod, cfInvoke.isInterface());
    // We are required to use the invokedMethod if it does not resolve due to the rewriting of
    // private methods absent from the library.
    DexMethod singleTarget =
        resolutionResult.isSingleResolution()
            ? resolutionResult.getSingleTarget().getReference()
            : invokedMethod;
    assert singleTarget != null;
    if (cfInvoke.isInvokeStatic()) {
      DexMethod retarget = staticRetarget.get(singleTarget);
      return retarget == null ? NO_REWRITING : ensureInvokeRetargetingResult(retarget);
    }
    InvokeRetargetingResult retarget = computeNonStaticRetarget(singleTarget, false);
    if (!retarget.hasNewInvokeTarget()) {
      return NO_REWRITING;
    }
    if (cfInvoke.isInvokeSuper(context.getHolderType())) {
      DexClassAndMethod superTarget = appInfo.lookupSuperTarget(invokedMethod, context);
      if (superTarget != null) {
        assert !superTarget.getDefinition().isStatic();
        return computeNonStaticRetarget(superTarget.getReference(), true);
      }
    }
    return retarget;
  }

  private InvokeRetargetingResult computeNonStaticRetarget(
      DexMethod singleTarget, boolean superInvoke) {
    EmulatedDispatchMethodDescriptor descriptor = emulatedVirtualRetarget.get(singleTarget);
    if (descriptor != null) {
      return new InvokeRetargetingResult(
          true,
          eventConsumer ->
              superInvoke
                  ? syntheticHelper.ensureForwardingMethod(descriptor)
                  : syntheticHelper.ensureEmulatedHolderDispatchMethod(descriptor, eventConsumer));
    }
    return ensureInvokeRetargetingResult(nonEmulatedVirtualRetarget.get(singleTarget));
  }
}
