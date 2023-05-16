// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ThrowExceptionCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The only instructions we do not outline is constant classes, instance-of/checkcast and exception
 * guards. For program classes we also visit super types if these are library otherwise we will
 * visit the program super type's super types when visiting that program class.
 */
public class ApiReferenceStubber {

  private final AppView<?> appView;
  private final Map<DexLibraryClass, Set<ProgramDefinition>> referencingContexts =
      new ConcurrentHashMap<>();
  private final Set<DexLibraryClass> libraryClassesToMock = Sets.newConcurrentHashSet();
  private final Set<DexType> seenTypes = Sets.newConcurrentHashSet();
  private final AndroidApiLevelCompute apiLevelCompute;

  public ApiReferenceStubber(AppView<?> appView) {
    this.appView = appView;
    apiLevelCompute = appView.apiLevelCompute();
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().isGeneratingDex()
        && appView.options().apiModelingOptions().enableStubbingOfClasses) {
      Collection<DexProgramClass> classes =
          ListUtils.filter(
              appView.appInfo().classes(), DexProgramClass::originatesFromClassResource);
      // Finding super types is really fast so no need to pay the overhead of threading if the
      // number of classes is low.
      if (classes.size() > 2) {
        ThreadUtils.processItems(classes, this::processClass, executorService);
      } else {
        classes.forEach(this::processClass);
      }
    }
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
    assert clazz.originatesFromClassResource();
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(clazz.getType(), kinds -> kinds.API_MODEL_OUTLINE)) {
      return;
    }
    // We cannot reliably create a stub that will have the same throwing behavior for all VMs.
    // Only create stubs for exceptions to allow them being present in catch handlers and super
    // types of existing program classes. See b/258270051 and b/259076765 for more information.
    // Also, for L devices we can have verification issues if there are super invokes to missing
    // members on stubbed classes. See b/279780940 for more information.
    if (appView.options().getMinApiLevel().isGreaterThan(AndroidApiLevel.L)) {
      clazz
          .allImmediateSupertypes()
          .forEach(superType -> findReferencedLibraryClasses(superType, clazz));
    }
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> {
          Code code = method.getDefinition().getCode();
          if (!code.isDexCode()) {
            return;
          }
          for (TryHandler handler : code.asDexCode().getHandlers()) {
            for (TypeAddrPair pair : handler.pairs) {
              DexType rewrittenType = appView.graphLens().lookupType(pair.getType());
              findReferencedLibraryClasses(rewrittenType, clazz);
            }
          }
        });
  }

  private void findReferencedLibraryClasses(DexType type, DexProgramClass context) {
    if (!type.isClassType() || isJavaType(type)) {
      return;
    }
    WorkList<DexType> workList = WorkList.newIdentityWorkList(type, seenTypes);
    while (workList.hasNext()) {
      DexClass clazz = appView.definitionFor(workList.next());
      if (clazz == null || !clazz.isLibraryClass()) {
        continue;
      }
      ComputedApiLevel androidApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(
              clazz.type, ComputedApiLevel.unknown());
      if (androidApiLevel.isGreaterThan(appView.computedMinApiLevel())
          && androidApiLevel.isKnownApiLevel()) {
        workList.addIfNotSeen(clazz.allImmediateSupertypes());
        libraryClassesToMock.add(clazz.asLibraryClass());
        referencingContexts
            .computeIfAbsent(clazz.asLibraryClass(), ignoreKey(Sets::newConcurrentHashSet))
            .add(context);
      }
    }
  }

  private boolean isJavaType(DexType type) {
    return type == appView.dexItemFactory().objectType
        || type.getDescriptor().startsWith(appView.dexItemFactory().javaDescriptorPrefix);
  }

  private void mockMissingLibraryClass(
      DexLibraryClass libraryClass,
      ThrowExceptionCode throwExceptionCode) {
    DexItemFactory factory = appView.dexItemFactory();
    // Do not stub the anything starting with java (including the object type).
    if (isJavaType(libraryClass.getType())) {
      return;
    }
    // Check if desugared library will bridge the type.
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
}
