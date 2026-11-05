// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.threading.SynchronizedTaskCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class TypeReferenceEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;

  public TypeReferenceEnqueuer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (options.isOptimizing()
        && options.isRelease()
        && options.isShrinking()
        && options.testing.cfByteCodePassThrough == null) {
      try (Timing t0 = timing.begin("TurboEnqueuer")) {
        run(executorService, timing);
      }
    }
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    Set<DexProgramClass> seenClasses = trace(executorService, timing);
    if (seenClasses.size() < appView.appInfo().classes().size()) {
      new TypeReferenceEnqueuerTreePruner(appView, seenClasses).run(executorService, timing);
    }
  }

  private Set<DexProgramClass> trace(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Trace")) {
      TypeReferenceCollector collector = new TypeReferenceCollector(executorService);
      try (Timing t1 = timing.begin("Enqueue roots")) {
        enqueueRoots(collector);
        if (appView.options().partialSubCompilationConfiguration != null) {
          for (DexProgramClass clazz :
              appView
                  .options()
                  .partialSubCompilationConfiguration
                  .asR8()
                  .getDexingOutputClasses()) {
            collector.traceDexingOutputClass(clazz);
          }
        }
      }
      try (Timing t1 = timing.begin("Await tasks")) {
        return collector.await();
      }
    }
  }

  private void enqueueRoots(TypeReferenceCollector collector) {
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    InternalOptions options = appView.options();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!collector.seenTypes.contains(clazz.getType()) && isRoot(clazz, keepInfo, options)) {
        collector.addRoot(clazz);
      }
    }
  }

  private boolean isRoot(
      DexProgramClass clazz, KeepInfoCollection keepInfo, InternalOptions options) {
    if (!keepInfo.getClassInfo(clazz).isShrinkingAllowed(options)) {
      return true;
    }
    for (DexEncodedMember<?, ?> member : clazz.members()) {
      if (!keepInfo.getMemberInfo(member, clazz).isShrinkingAllowed(options)) {
        return true;
      }
    }
    return false;
  }

  private class TypeReferenceCollector implements IndexedItemCollection {

    private final LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(appView);
    private final Set<DexProgramClass> seenClasses = Sets.newConcurrentHashSet();
    private final Set<DexType> seenTypes = Sets.newConcurrentHashSet();
    private final SynchronizedTaskCollection<Void> tasks;

    private TypeReferenceCollector(ExecutorService executorService) {
      this.tasks = new SynchronizedTaskCollection<>(appView.options(), executorService);
    }

    private void addRoot(DexProgramClass clazz) {
      if (seenTypes.add(clazz.getType())) {
        trace(clazz);
      }
    }

    private void trace(DexProgramClass clazz) {
      boolean added = seenClasses.add(clazz);
      assert added;
      tasks.submitUnchecked(() -> traceClass(clazz));
    }

    private void traceDexingOutputClass(DexProgramClass clazz) {
      tasks.submitUnchecked(() -> traceClass(clazz));
    }

    private void traceClass(DexProgramClass clazz) {
      if (clazz.hasSuperType()) {
        addType(clazz.getSuperType());
      }
      clazz.getInterfaces().forEach(this::addType);
      clazz.annotations().collectIndexedItems(appView, this);
      for (RecordComponentInfo recordComponent : clazz.getRecordComponents()) {
        recordComponent.collectIndexedItems(appView, this);
      }
      clazz.forEachProgramField(field -> field.collectIndexedItems(appView, this));
      clazz.forEachProgramMethod(this::traceMethod);
    }

    private void traceMethod(ProgramMethod method) {
      DexEncodedMethod definition = method.getDefinition();
      addMethod(method.getReference());
      definition.annotations().collectIndexedItems(appView, this);
      definition.parameterAnnotationsList.collectIndexedItems(appView, this);
      if (definition.hasCode()) {
        Code code = definition.getCode();
        if (code.isLirCode()) {
          traceLirCode(code.asLirCode());
        } else {
          assert code.isDexWritableCode();
          GraphLens codeLens = code.getCodeLens(appView);
          code.asDexWritableCode().collectIndexedItems(appView, codeLens, this, method, rewriter);
        }
      }
    }

    private void traceLirCode(LirCode<Integer> code) {
      for (LirConstant constant : code.getConstantPool()) {
        constant.collectIndexedItems(appView, this);
      }
      if (code.hasTryCatchTable()) {
        code.getTryCatchTable()
            .forEachHandler((block, handlers) -> handlers.getGuards().forEach(this::addType));
      }
    }

    private Set<DexProgramClass> await() throws ExecutionException {
      tasks.await();
      return seenClasses;
    }

    @Override
    public boolean addClass(DexProgramClass clazz) {
      return true;
    }

    @Override
    public boolean addField(DexField field) {
      addType(field.getHolderType());
      addType(field.getType());
      return false;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      addType(method.getHolderType());
      addProto(method.getProto());
      return false;
    }

    @Override
    public boolean addString(DexString string) {
      return false;
    }

    @Override
    public boolean addProto(DexProto proto) {
      addType(proto.getReturnType());
      for (DexType parameterType : proto.getParameters()) {
        addType(parameterType);
      }
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      DexType baseType = type.getBaseType();
      if (baseType.isClassType() && seenTypes.add(baseType)) {
        DexProgramClass clazz =
            appView.appInfo().definitionForProgramTypeWithoutExistenceAssert(baseType);
        if (clazz != null) {
          trace(clazz);
        }
      }
      return false;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return true;
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return true;
    }
  }
}
