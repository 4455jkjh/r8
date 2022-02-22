// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

public class TreePruner {

  private final AppView<AppInfoWithLiveness> appView;
  private final TreePrunerConfiguration configuration;
  private final UnusedItemsPrinter unusedItemsPrinter;
  private final Set<DexType> prunedTypes = Sets.newIdentityHashSet();
  private final Set<DexMethod> methodsToKeepForConfigurationDebugging = Sets.newIdentityHashSet();

  public TreePruner(AppView<AppInfoWithLiveness> appView) {
    this(appView, DefaultTreePrunerConfiguration.getInstance());
  }

  public TreePruner(AppView<AppInfoWithLiveness> appView, TreePrunerConfiguration configuration) {
    InternalOptions options = appView.options();
    this.appView = appView;
    this.configuration = configuration;
    this.unusedItemsPrinter =
        options.hasUsageInformationConsumer()
            ? new UnusedItemsPrinter(
                s ->
                    ExceptionUtils.withConsumeResourceHandler(
                        options.reporter, options.usageInformationConsumer, s))
            : UnusedItemsPrinter.DONT_PRINT;
  }

  public DirectMappedDexApplication run(ExecutorService executorService) throws ExecutionException {
    DirectMappedDexApplication application = appView.appInfo().app().asDirect();
    Timing timing = application.timing;
    timing.begin("Pruning application...");
    try {
      DirectMappedDexApplication.Builder builder = removeUnused(application);
      DirectMappedDexApplication newApplication =
          prunedTypes.isEmpty() && !appView.options().configurationDebugging
              ? application
              : builder.build();
      fixupOptimizationInfo(newApplication, executorService);
      return newApplication;
    } finally {
      timing.end();
    }
  }

  private DirectMappedDexApplication.Builder removeUnused(DirectMappedDexApplication application) {
    return application
        .builder()
        .replaceProgramClasses(getNewProgramClasses(application.classesWithDeterministicOrder()));
  }

  private List<DexProgramClass> getNewProgramClasses(Collection<DexProgramClass> classes) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    InternalOptions options = appView.options();
    List<DexProgramClass> newClasses = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      if (options.configurationDebugging) {
        newClasses.add(clazz);
        pruneMembersAndAttributes(clazz);
        continue;
      }
      if (appInfo.isLiveProgramClass(clazz)) {
        newClasses.add(clazz);
        if (!appInfo.getObjectAllocationInfoCollection().isInstantiatedDirectly(clazz)
            && !options.forceProguardCompatibility) {
          // The class is only needed as a type but never instantiated. Make it abstract to reflect
          // this.
          if (clazz.isFinal()) {
            // We cannot mark this class abstract, as it is final (not supported on Android).
            // However, this might extend an abstract class and we might have removed the
            // corresponding methods in this class. This might happen if we only keep this
            // class around for its constants.
            // For now, we remove the final flag to still be able to mark it abstract.
            clazz.accessFlags.demoteFromFinal();
          }
          clazz.accessFlags.setAbstract();
        }
        // The class is used and must be kept. Remove the unused fields and methods from the class.
        pruneUnusedInterfaces(clazz);
        pruneMembersAndAttributes(clazz);
      } else {
        // The class is completely unused and we can remove it.
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing class: " + clazz);
        }
        prunedTypes.add(clazz.type);
        unusedItemsPrinter.registerUnusedClass(clazz);
      }
    }
    unusedItemsPrinter.finished();
    return newClasses;
  }

  private void pruneUnusedInterfaces(DexProgramClass clazz) {
    Set<DexType> reachableInterfaces = new LinkedHashSet<>();
    for (DexType type : clazz.getInterfaces()) {
      retainReachableInterfacesFrom(type, reachableInterfaces);
    }
    if (!reachableInterfaces.isEmpty()) {
      removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(
          clazz.superType, reachableInterfaces);
    }
    if (reachableInterfaces.isEmpty()) {
      clazz.interfaces = DexTypeList.empty();
    } else {
      clazz.interfaces = new DexTypeList(reachableInterfaces.toArray(DexType.EMPTY_ARRAY));
    }
  }

  private void removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(
      DexType type, Set<DexType> interfaces) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return;
    }
    for (DexType itf : clazz.interfaces) {
      if (interfaces.remove(itf) && interfaces.isEmpty()) {
        return;
      }
    }
    if (clazz.superType != null) {
      assert !interfaces.isEmpty();
      removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(clazz.superType, interfaces);
    }
  }

  private void retainReachableInterfacesFrom(DexType type, Set<DexType> reachableInterfaces) {
    if (isTypeLive(type)) {
      reachableInterfaces.add(type);
    } else {
      DexProgramClass unusedInterface = appView.definitionForProgramType(type);
      assert unusedInterface != null;
      assert unusedInterface.isInterface();
      for (DexType interfaceType : unusedInterface.interfaces.values) {
        retainReachableInterfacesFrom(interfaceType, reachableInterfaces);
      }
    }
  }

  private void pruneMembersAndAttributes(DexProgramClass clazz) {
    unusedItemsPrinter.visiting(clazz);
    DexEncodedMethod[] reachableDirectMethods = reachableMethods(clazz.directMethods(), clazz);
    if (reachableDirectMethods != null) {
      clazz.setDirectMethods(reachableDirectMethods);
    }
    DexEncodedMethod[] reachableVirtualMethods =
        reachableMethods(clazz.virtualMethods(), clazz);
    if (reachableVirtualMethods != null) {
      clazz.setVirtualMethods(reachableVirtualMethods);
    }
    DexEncodedField[] reachableInstanceFields = reachableFields(clazz.instanceFields());
    if (reachableInstanceFields != null) {
      clazz.setInstanceFields(reachableInstanceFields);
    }
    DexEncodedField[] reachableStaticFields = reachableFields(clazz.staticFields());
    if (reachableStaticFields != null) {
      clazz.setStaticFields(reachableStaticFields);
    }
    clazz.removeInnerClasses(this::isAttributeReferencingMissingOrPrunedType);
    clazz.removeEnclosingMethodAttribute(this::isAttributeReferencingPrunedItem);
    rewriteNestAttributes(clazz, this::isTypeLive, appView::definitionFor);
    unusedItemsPrinter.visited();
    assert verifyNoDeadFields(clazz);
  }

  public static void rewriteNestAttributes(
      DexProgramClass clazz, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    if (!clazz.isInANest() || !isLive.test(clazz.type)) {
      return;
    }
    if (clazz.isNestHost()) {
      clearDeadNestMembers(clazz, isLive, definition);
    } else {
      assert clazz.isNestMember();
      if (!isLive.test(clazz.getNestHost())) {
        claimNestOwnership(clazz, isLive, definition);
      }
    }
  }

  private boolean isTypeMissing(DexType type) {
    return appView.appInfo().getMissingClasses().contains(type);
  }

  private boolean isTypeLive(DexType type) {
    return appView.appInfo().isNonProgramTypeOrLiveProgramType(type);
  }

  private static void clearDeadNestMembers(
      DexClass nestHost, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    // null definition should raise a warning which is raised later on in Nest specific passes.
    nestHost
        .getNestMembersClassAttributes()
        .removeIf(
            nestMemberAttr ->
                definition.apply(nestMemberAttr.getNestMember()) != null
                    && !isLive.test(nestMemberAttr.getNestMember()));
  }

  private static void claimNestOwnership(
      DexClass newHost, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    DexClass previousHost = definition.apply(newHost.getNestHost());
    if (previousHost == null) {
      // Nest host will be cleared from all nest members in Nest specific passes.
      return;
    }
    newHost.clearNestHost();
    for (NestMemberClassAttribute attr : previousHost.getNestMembersClassAttributes()) {
      if (attr.getNestMember() != newHost.type && isLive.test(attr.getNestMember())) {
        DexClass nestMember = definition.apply(attr.getNestMember());
        if (nestMember != null) {
          nestMember.setNestHost(newHost.type);
        }
        // We still need to add it, even if the definition is null,
        // so the warning / error are correctly raised in Nest specific passes.
        newHost
            .getNestMembersClassAttributes()
            .add(new NestMemberClassAttribute(attr.getNestMember()));
      }
    }
  }

  private boolean isAttributeReferencingPrunedItem(EnclosingMethodAttribute attr) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    return (attr.getEnclosingClass() != null && !isTypeLive(attr.getEnclosingClass()))
        || (attr.getEnclosingMethod() != null && !appInfo.isLiveMethod(attr.getEnclosingMethod()));
  }

  private boolean isAttributeReferencingMissingOrPrunedType(InnerClassAttribute attr) {
    if (isTypeMissing(attr.getInner()) || !isTypeLive(attr.getInner())) {
      return true;
    }
    DexType context = attr.getLiveContext(appView);
    return context == null || isTypeMissing(context) || !isTypeLive(context);
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> int firstUnreachableIndex(
      List<D> items, Predicate<D> live) {
    for (int i = 0; i < items.size(); i++) {
      if (!live.test(items.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private DexEncodedMethod[] reachableMethods(
      Iterable<DexEncodedMethod> methods, DexProgramClass clazz) {
    return reachableMethods(IterableUtils.ensureUnmodifiableList(methods), clazz);
  }

  private DexEncodedMethod[] reachableMethods(
      List<DexEncodedMethod> methods, DexProgramClass clazz) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    InternalOptions options = appView.options();
    int firstUnreachable =
        firstUnreachableIndex(methods, method -> appInfo.isLiveMethod(method.getReference()));
    // Return the original array if all methods are used.
    if (firstUnreachable == -1) {
      for (DexEncodedMethod method : methods) {
        canonicalizeCode(method.asProgramMethod(clazz));
      }
      return null;
    }
    ArrayList<DexEncodedMethod> reachableMethods = new ArrayList<>(methods.size());
    for (int i = 0; i < methods.size(); i++) {
      DexEncodedMethod method = methods.get(i);
      if (appInfo.isLiveMethod(method.getReference())) {
        canonicalizeCode(method.asProgramMethod(clazz));
        reachableMethods.add(method);
      } else if (options.configurationDebugging) {
        // Keep the method but rewrite its body, if it has one.
        reachableMethods.add(
            method.shouldNotHaveCode() && !method.hasCode()
                ? method
                : method.toMethodThatLogsError(appView));
        methodsToKeepForConfigurationDebugging.add(method.getReference());
      } else if (appInfo.isTargetedMethod(method.getReference())) {
        // If the method is already abstract, and doesn't have code, let it be.
        if (method.shouldNotHaveCode() && !method.hasCode()) {
          reachableMethods.add(method);
          continue;
        }
        if (Log.ENABLED) {
          Log.debug(getClass(), "Making method %s abstract.", method.getReference());
        }
        // Private methods and static methods can only be targeted yet non-live as the result of
        // an invalid invoke. They will not actually be called at runtime but we have to keep them
        // as non-abstract (see above) to produce the same failure mode.
        new ProgramMethod(clazz, method).convertToAbstractOrThrowNullMethod(appView);
        reachableMethods.add(method);
      } else {
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing method %s.", method.getReference());
        }
        unusedItemsPrinter.registerUnusedMethod(method);
      }
    }
    return reachableMethods.isEmpty()
        ? DexEncodedMethod.EMPTY_ARRAY
        : reachableMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private void canonicalizeCode(ProgramMethod method) {
    if (method.getDefinition().hasCode()) {
      DefaultInstanceInitializerCode.canonicalizeCodeIfPossible(appView, method);
    }
  }

  private DexEncodedField[] reachableFields(List<DexEncodedField> fields) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    Predicate<DexEncodedField> isReachableOrReferencedField =
        field -> configuration.isReachableOrReferencedField(appInfo, field);
    int firstUnreachable = firstUnreachableIndex(fields, isReachableOrReferencedField);
    // Return the original array if all fields are used.
    if (firstUnreachable == -1) {
      return null;
    }
    if (Log.ENABLED) {
      Log.debug(getClass(), "Removing field %s.", fields.get(firstUnreachable));
    }
    unusedItemsPrinter.registerUnusedField(fields.get(firstUnreachable));
    ArrayList<DexEncodedField> reachableOrReferencedFields = new ArrayList<>(fields.size());
    for (int i = 0; i < firstUnreachable; i++) {
      reachableOrReferencedFields.add(fields.get(i));
    }
    for (int i = firstUnreachable + 1; i < fields.size(); i++) {
      DexEncodedField field = fields.get(i);
      if (isReachableOrReferencedField.test(field)) {
        reachableOrReferencedFields.add(field);
      } else {
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing field %s.", field.getReference());
        }
        unusedItemsPrinter.registerUnusedField(field);
      }
    }
    return reachableOrReferencedFields.isEmpty()
        ? DexEncodedField.EMPTY_ARRAY
        : reachableOrReferencedFields.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  public Set<DexType> getRemovedClasses() {
    return Collections.unmodifiableSet(prunedTypes);
  }

  public Collection<DexMethod> getMethodsToKeepForConfigurationDebugging() {
    return Collections.unmodifiableCollection(methodsToKeepForConfigurationDebugging);
  }

  private void fixupOptimizationInfo(
      DirectMappedDexApplication application, ExecutorService executorService)
      throws ExecutionException {
    // Clear the type elements cache due to redundant interface removal.
    appView.dexItemFactory().clearTypeElementsCache();

    OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();
    feedback.fixupOptimizationInfos(
        application.classes(),
        executorService,
        new OptimizationInfoFixer() {
          @Override
          public void fixup(DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo) {
            optimizationInfo.fixupClassTypeReferences(appView, appView.graphLens(), prunedTypes);
          }

          @Override
          public void fixup(
              DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo) {
            optimizationInfo.fixupClassTypeReferences(appView, appView.graphLens(), prunedTypes);
          }
        });

    // Verify that the fixup did not lead to the caching of any elements.
    assert appView.dexItemFactory().verifyNoCachedTypeElements();
  }

  private boolean verifyNoDeadFields(DexProgramClass clazz) {
    for (DexEncodedField field : clazz.fields()) {
      // Pinned field which type is never instantiated are always null, they are marked as dead
      // so their reads and writes are cleared, but they are still in the program.
      assert !field.getOptimizationInfo().isDead() || appView.appInfo().isPinned(field)
          : "Expected field `" + field.getReference().toSourceString() + "` to be absent";
    }
    return true;
  }
}
