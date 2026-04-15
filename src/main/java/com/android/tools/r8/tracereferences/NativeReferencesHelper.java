// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class NativeReferencesHelper {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final TraceReferencesNativeReferencesConsumer nativeReferencesConsumer;
  private final DiagnosticsHandler diagnostics;
  private final ExecutorService executorService;
  private final DexItemFactory factory;
  private final DexString loadLibrary;
  private final DexString load;

  public NativeReferencesHelper(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      TraceReferencesNativeReferencesConsumer nativeReferencesConsumer,
      DiagnosticsHandler diagnostics,
      ExecutorService executorService) {
    this.appView = appView;
    this.nativeReferencesConsumer = nativeReferencesConsumer;
    this.diagnostics = diagnostics;
    this.executorService = executorService;
    this.factory = appView.dexItemFactory();
    this.loadLibrary = factory.createString("loadLibrary");
    this.load = factory.createString("load");
  }

  public boolean isSystemLoadLibrary(DexMethod method) {
    return method.getHolderType().isIdenticalTo(factory.javaLangSystemType)
        && method.getName().isIdenticalTo(loadLibrary);
  }

  public boolean isSystemLoad(DexMethod method) {
    return method.getHolderType().isIdenticalTo(factory.javaLangSystemType)
        && method.getName().isIdenticalTo(load);
  }

  public void process(Collection<DexProgramClass> classes) throws ExecutionException {
    ThreadUtils.processItems(
        classes,
        clazz ->
            clazz.forEachProgramMethodMatching(
                method -> method.isNative() || methodMightCallSystemLoadOrSystemLoadLibrary(method),
                this::processMethod),
        appView.options().getThreadingModule(),
        executorService);
  }

  public void process(ProgramMethodSet methods) throws ExecutionException {
    ThreadUtils.processItems(
        methods, this::processMethod, appView.options().getThreadingModule(), executorService);
  }

  private void processMethod(ProgramMethod method) {
    if (method.getAccessFlags().isNative()) {
      nativeReferencesConsumer.acceptNativeMethod(
          appView
              .getNamingLens()
              .lookupMethod(method.getReference(), appView.dexItemFactory())
              .asMethodReference(),
          diagnostics);
      return;
    }
    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
    for (InvokeStatic invoke : code.<InvokeStatic>instructions(Instruction::isInvokeStatic)) {
      processInvoke(method, invoke);
    }
  }

  private void processInvoke(ProgramMethod method, InvokeStatic invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (isSystemLoadLibrary(invokedMethod) || isSystemLoad(invokedMethod)) {
      Value argument = invoke.getFirstArgument().getAliasedValue();
      MethodOrigin origin = new MethodOrigin(method.getMethodReference(), method.getOrigin());
      if (argument.isConstString()) {
        String name = argument.getDefinition().asConstString().getValue().toString();
        if (isSystemLoadLibrary(invokedMethod)) {
          nativeReferencesConsumer.acceptLoadLibrary(name, origin, diagnostics);
        } else {
          nativeReferencesConsumer.acceptLoad(name, origin, diagnostics);
        }
      } else {
        if (isSystemLoadLibrary(invokedMethod)) {
          nativeReferencesConsumer.acceptLoadLibraryAny(origin, diagnostics);
        } else {
          nativeReferencesConsumer.acceptLoadAny(origin, diagnostics);
        }
      }
    }
  }

  private boolean methodMightCallSystemLoadOrSystemLoadLibrary(DexEncodedMethod method) {
    if (!method.hasCode()) {
      return false;
    }
    if (!method.getCode().isLirCode()) {
      return true;
    }
    for (LirConstant constant : method.getCode().asLirCode().getConstantPool()) {
      if (constant instanceof DexMethod) {
        DexMethod methodConstant = (DexMethod) constant;
        if (isSystemLoadLibrary(methodConstant) || isSystemLoad(methodConstant)) {
          return true;
        }
      }
    }
    return false;
  }
}
