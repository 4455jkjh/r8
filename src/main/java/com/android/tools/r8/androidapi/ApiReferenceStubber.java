// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowExceptionCode;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ApiReferenceStubber {

  private class ReferencesToApiLevelUseRegistry extends UseRegistry<ProgramMethod> {

    public ReferencesToApiLevelUseRegistry(ProgramMethod context) {
      super(appView, context);
    }

    @Override
    public void registerInitClass(DexType type) {
      checkReferenceToLibraryClass(type);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      checkReferenceToLibraryClass(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      checkReferenceToLibraryClass(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      checkReferenceToLibraryClass(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      checkReferenceToLibraryClass(field);
    }

    @Override
    public void registerTypeReference(DexType type) {
      checkReferenceToLibraryClass(type);
    }

    private void checkReferenceToLibraryClass(DexReference reference) {
      DexType rewrittenType = appView.graphLens().lookupType(reference.getContextType());
      findReferencedLibraryClasses(rewrittenType, getContext().getContextClass());
    }
  }

  private final AppView<?> appView;
  private final Map<DexLibraryClass, Set<ProgramDefinition>> referencingContexts =
      new ConcurrentHashMap<>();
  private final Set<DexLibraryClass> libraryClassesToMock = Sets.newConcurrentHashSet();
  private final Set<DexType> seenTypes = Sets.newConcurrentHashSet();
  private final AndroidApiLevelCompute apiLevelCompute;
  private final DexItemFactory factory;

  public ApiReferenceStubber(AppView<?> appView) {
    this.appView = appView;
    apiLevelCompute = appView.apiLevelCompute();
    factory = appView.dexItemFactory();
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().isGeneratingClassFiles()
        || !appView.options().apiModelingOptions().enableStubbingOfClasses) {
      return;
    }
    ThreadUtils.processItems(appView.appInfo().classes(), this::processClass, executorService);
    if (libraryClassesToMock.isEmpty()) {
      return;
    }
    libraryClassesToMock.forEach(
        clazz ->
            mockMissingLibraryClass(
                clazz,
                ThrowExceptionCode.create(appView.dexItemFactory().noClassDefFoundErrorType)));
    // Commit the synthetic items.
    CommittedItems committedItems = appView.getSyntheticItems().commit(appView.appInfo().app());
    if (appView.hasLiveness()) {
      AppView<AppInfoWithLiveness> appInfoWithLivenessAppView = appView.withLiveness();
      appInfoWithLivenessAppView.setAppInfo(
          appInfoWithLivenessAppView.appInfo().rebuildWithLiveness(committedItems));
    } else if (appView.hasClassHierarchy()) {
      appView
          .withClassHierarchy()
          .setAppInfo(
              appView.appInfo().withClassHierarchy().rebuildWithClassHierarchy(committedItems));
    } else {
      appView
          .withoutClassHierarchy()
          .setAppInfo(
              new AppInfo(
                  appView.appInfo().getSyntheticItems().commit(appView.app()),
                  appView.appInfo().getMainDexInfo()));
    }
  }

  public void processClass(DexProgramClass clazz) {
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(clazz.getType(), kinds -> kinds.API_MODEL_OUTLINE)) {
      return;
    }
    findReferencedLibraryClasses(clazz.type, clazz);
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> method.registerCodeReferences(new ReferencesToApiLevelUseRegistry(method)));
  }

  private void findReferencedLibraryClasses(DexType type, DexProgramClass context) {
    if (!type.isClassType()) {
      return;
    }
    WorkList<DexType> workList = WorkList.newIdentityWorkList(type, seenTypes);
    while (workList.hasNext()) {
      DexClass clazz = appView.definitionFor(workList.next());
      if (clazz == null) {
        continue;
      }
      if (clazz.isLibraryClass()) {
        ComputedApiLevel androidApiLevel =
            apiLevelCompute.computeApiLevelForLibraryReference(
                clazz.type, ComputedApiLevel.unknown());
        if (androidApiLevel.isGreaterThan(appView.computedMinApiLevel())
            && !androidApiLevel.isUnknownApiLevel()) {
          libraryClassesToMock.add(clazz.asLibraryClass());
          referencingContexts
              .computeIfAbsent(clazz.asLibraryClass(), ignoreKey(Sets::newConcurrentHashSet))
              .add(context);
        }
      }
      workList.addIfNotSeen(clazz.allImmediateSupertypes());
    }
  }

  private void mockMissingLibraryClass(
      DexLibraryClass libraryClass,
      ThrowExceptionCode throwExceptionCode) {
    DexItemFactory factory = appView.dexItemFactory();
    if (libraryClass.getType() == factory.objectType
        || libraryClass.getType().toDescriptorString().startsWith("Ljava/")) {
      return;
    }
    // We cannot reliably create a stub that will have the same throwing
    // behavior for all VMs. We only create stubs for exceptions to allow them being present in
    // catch handlers. See b/b/258270051 for more information.
    if (!isThrowable(libraryClass)
        || appView.options().apiModelingOptions().stubNonThrowableClasses) {
      return;
    }
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .isSupported(libraryClass.getType())) {
      return;
    }
    Set<ProgramDefinition> contexts = referencingContexts.get(libraryClass);
    if (contexts == null) {
      throw new Unreachable("Attempt to create a global synthetic with no contexts");
    }
    appView
        .appInfo()
        .getSyntheticItems()
        .ensureGlobalClass(
            () -> new MissingGlobalSyntheticsConsumerDiagnostic("API stubbing"),
            kinds -> kinds.API_MODEL_STUB,
            libraryClass.getType(),
            contexts,
            appView,
            classBuilder -> {
              classBuilder
                  .setSuperType(libraryClass.getSuperType())
                  .setInterfaces(Arrays.asList(libraryClass.getInterfaces().values))
                  // Add throwing static initializer
                  .addMethod(
                      methodBuilder ->
                          methodBuilder
                              .setName(factory.classConstructorMethodName)
                              .setProto(factory.createProto(factory.voidType))
                              .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                              .setCode(method -> throwExceptionCode));
              if (libraryClass.isInterface()) {
                classBuilder.setInterface();
              }
              if (!libraryClass.isFinal()) {
                classBuilder.unsetFinal();
              }
            },
            ignored -> {});
  }

  private boolean isThrowable(DexLibraryClass libraryClass) {
    DexClass current = libraryClass;
    while (current.getSuperType() != null) {
      DexType superType = current.getSuperType();
      if (superType == factory.throwableType) {
        return true;
      }
      current = appView.definitionFor(current.getSuperType());
    }
    return false;
  }
}
