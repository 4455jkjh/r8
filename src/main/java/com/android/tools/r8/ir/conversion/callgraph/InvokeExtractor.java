// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class InvokeExtractor<N extends NodeBase<N>> extends UseRegistry<ProgramMethod> {

  protected final AppView<AppInfoWithLiveness> appView;
  protected final N currentMethod;
  protected final Function<ProgramMethod, N> nodeFactory;
  protected final Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache;
  protected final Predicate<ProgramMethod> targetTester;

  public InvokeExtractor(
      AppView<AppInfoWithLiveness> appView,
      N currentMethod,
      Function<ProgramMethod, N> nodeFactory,
      Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache,
      Predicate<ProgramMethod> targetTester) {
    super(appView, currentMethod.getProgramMethod());
    this.appView = appView;
    this.currentMethod = currentMethod;
    this.nodeFactory = nodeFactory;
    this.possibleProgramTargetsCache = possibleProgramTargetsCache;
    this.targetTester = targetTester;
  }

  protected void addCallEdge(ProgramMethod callee, boolean likelySpuriousCallEdge) {
    if (!targetTester.test(callee)) {
      return;
    }
    if (callee.getDefinition().isAbstract()) {
      // Not a valid target.
      return;
    }
    if (callee.getDefinition().isNative()) {
      // We don't care about calls to native methods.
      return;
    }
    if (!appView.getKeepInfo(callee).isOptimizationAllowed(appView.options())) {
      // Since the callee is kept and optimizations are disallowed, we cannot inline it into the
      // caller, and we also cannot collect any optimization info for the method. Therefore, we
      // drop the call edge to reduce the total number of call graph edges, which should lead to
      // fewer call graph cycles.
      return;
    }
    nodeFactory.apply(callee).addCallerConcurrently(currentMethod, likelySpuriousCallEdge);
  }

  private void processInvoke(Invoke.Type originalType, DexMethod originalMethod) {
    ProgramMethod context = currentMethod.getProgramMethod();
    MethodLookupResult result =
        appView
            .graphLens()
            .lookupMethod(originalMethod, context.getReference(), originalType, getCodeLens());
    DexMethod method = result.getReference();
    Invoke.Type type = result.getType();
    if (type == Invoke.Type.INTERFACE || type == Invoke.Type.VIRTUAL) {
      // For virtual and interface calls add all potential targets that could be called.
      MethodResolutionResult resolutionResult =
          appView.appInfo().resolveMethod(method, type == Invoke.Type.INTERFACE);
      DexClassAndMethod target = resolutionResult.getResolutionPair();
      if (target != null) {
        processInvokeWithDynamicDispatch(type, target, context);
      }
    } else {
      ProgramMethod singleTarget =
          appView.appInfo().lookupSingleProgramTarget(appView, type, method, context, appView);
      if (singleTarget != null) {
        processSingleTarget(singleTarget, context);
      }
    }
  }

  protected void processSingleTarget(ProgramMethod singleTarget, ProgramMethod context) {
    assert !context.getDefinition().isBridge()
        || singleTarget.getDefinition() != context.getDefinition();
    addCallEdge(singleTarget, false);
  }

  protected void processInvokeWithDynamicDispatch(
      Invoke.Type type, DexClassAndMethod encodedTarget, ProgramMethod context) {
    DexMethod target = encodedTarget.getReference();
    DexClass clazz = encodedTarget.getHolder();
    if (!appView.options().testing.addCallEdgesForLibraryInvokes) {
      if (clazz.isLibraryClass()) {
        // Likely to have many possible targets.
        return;
      }
    }

    boolean isInterface = type == Invoke.Type.INTERFACE;
    ProgramMethodSet possibleProgramTargets =
        possibleProgramTargetsCache.computeIfAbsent(
            target,
            method -> {
              MethodResolutionResult resolution =
                  appView.appInfo().resolveMethod(method, isInterface);
              if (resolution.isVirtualTarget()) {
                LookupResult lookupResult =
                    resolution.lookupVirtualDispatchTargets(context.getHolder(), appView.appInfo());
                if (lookupResult.isLookupResultSuccess()) {
                  ProgramMethodSet targets = ProgramMethodSet.create();
                  lookupResult
                      .asLookupResultSuccess()
                      .forEach(
                          lookupMethodTarget -> {
                            DexClassAndMethod methodTarget = lookupMethodTarget.getTarget();
                            if (methodTarget.isProgramMethod()) {
                              targets.add(methodTarget.asProgramMethod());
                            }
                          },
                          lambdaTarget -> {
                            // The call target will ultimately be the implementation method.
                            DexClassAndMethod implementationMethod =
                                lambdaTarget.getImplementationMethod();
                            if (implementationMethod.isProgramMethod()) {
                              targets.add(implementationMethod.asProgramMethod());
                            }
                          });
                  return targets;
                }
              }
              return null;
            });
    if (possibleProgramTargets != null) {
      boolean likelySpuriousCallEdge =
          possibleProgramTargets.size()
              >= appView.options().callGraphLikelySpuriousCallEdgeThreshold;
      for (ProgramMethod possibleTarget : possibleProgramTargets) {
        addCallEdge(possibleTarget, likelySpuriousCallEdge);
      }
    }
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    registerMethodHandle(
        callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    processInvoke(Invoke.Type.DIRECT, method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    processInvoke(Invoke.Type.INTERFACE, method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    processInvoke(Invoke.Type.STATIC, method);
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    processInvoke(Invoke.Type.SUPER, method);
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    processInvoke(Invoke.Type.VIRTUAL, method);
  }

  @Override
  public void registerInitClass(DexType type) {
    // Intentionally empty. This use registry is only tracing method calls.
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    // Intentionally empty. This use registry is only tracing method calls.
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    // Intentionally empty. This use registry is only tracing method calls.
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    // Intentionally empty. This use registry is only tracing method calls.
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    // Intentionally empty. This use registry is only tracing method calls.
  }

  @Override
  public void registerTypeReference(DexType type) {
    // Intentionally empty. This use registry is only tracing method calls.
  }
}
