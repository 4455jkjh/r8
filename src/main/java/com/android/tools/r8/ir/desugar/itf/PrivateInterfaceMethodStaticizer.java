// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Promotes private instance methods of interfaces to private static methods on the same interface,
 * taking the receiver as their first argument. The method name is suffixed with {@code $staticized}
 * to avoid collisions and to make it visible that desugaring changed the method flags.
 *
 * <p>ART does not allow invoke-direct on interface methods in DEX files before version 037. This
 * allows emitting DEX version 035 for programs with private interface instance methods (see {@link
 * InternalOptions#isDexVersion35UpToApi30Enabled()}). Call sites are rewritten to invoke-static
 * using {@link PrivateInterfaceMethodGraphLens}.
 */
public class PrivateInterfaceMethodStaticizer {

  private final AppView<?> appView;
  private final AppInfoWithClassHierarchy appInfo;
  private final DexItemFactory dexItemFactory;
  private final PrivateInterfaceMethodGraphLens.Builder lensBuilder =
      PrivateInterfaceMethodGraphLens.builder();

  private PrivateInterfaceMethodStaticizer(AppView<?> appView) {
    this.appView = appView;
    this.appInfo = appView.appInfoForDesugaring();
    this.dexItemFactory = appView.dexItemFactory();
  }

  public static void run(AppView<?> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (!appView.options().isDexVersion35UpToApi30Enabled()
        || appView.options().getR8PartialD8SubCompilationOptions() != null) {
      return;
    }
    try (Timing ignored = timing.begin("Staticize private interface methods")) {
      PrivateInterfaceMethodGraphLens lens =
          new PrivateInterfaceMethodStaticizer(appView).runInternal(executorService);
      if (lens != null) {
        if (appView.enableWholeProgramOptimizations()) {
          appView.withClassHierarchy().rewriteWithLens(lens, executorService, timing);
        } else {
          appView.withoutClassHierarchy().rewriteWithD8Lens(lens, timing);
          appView.setStartupProfile(appView.getStartupProfile().rewrittenWithLens(lens, timing));
        }
      }
    }
  }

  private PrivateInterfaceMethodGraphLens runInternal(ExecutorService executorService)
      throws ExecutionException {
    // Compute all replacements before mutating any method collection, since computing fresh names
    // performs resolution on other interfaces.
    Map<DexProgramClass, Map<DexMethod, DexEncodedMethod>> replacementsByClass =
        new ConcurrentHashMap<>();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          Map<DexMethod, DexEncodedMethod> replacements = computeReplacements(clazz);
          if (!replacements.isEmpty()) {
            replacementsByClass.put(clazz, replacements);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
    if (replacementsByClass.isEmpty()) {
      return null;
    }
    // Record the moves in a deterministic order.
    List<DexProgramClass> classes = new ArrayList<>(replacementsByClass.keySet());
    classes.sort(Comparator.comparing(DexProgramClass::getType));
    for (DexProgramClass clazz : classes) {
      Map<DexMethod, DexEncodedMethod> replacements = replacementsByClass.get(clazz);
      replacements.forEach(
          (method, newMethod) -> lensBuilder.recordMove(method, newMethod.getReference()));
      clazz
          .getMethodCollection()
          .replaceDirectMethods(method -> replacements.getOrDefault(method.getReference(), method));
    }
    PrivateInterfaceMethodGraphLens lens = lensBuilder.build(appView);
    rewriteEnclosingMethodAttributes(lens);
    return lens;
  }

  private Map<DexMethod, DexEncodedMethod> computeReplacements(DexProgramClass clazz) {
    if (!clazz.isInterface()) {
      return ImmutableMap.of();
    }
    Iterable<DexEncodedMethod> methodsToStaticize = clazz.directMethods(this::shouldStaticize);
    if (Iterables.isEmpty(methodsToStaticize)) {
      return ImmutableMap.of();
    }
    Set<DexMethod> methodsToStaticizeReferences = Sets.newIdentityHashSet();
    methodsToStaticize.forEach(method -> methodsToStaticizeReferences.add(method.getReference()));
    ImmutableMap.Builder<DexMethod, DexEncodedMethod> replacements = ImmutableMap.builder();
    Set<DexMethod> newReferences = Sets.newIdentityHashSet();
    for (DexEncodedMethod method : methodsToStaticize) {
      DexMethod newReference =
          dexItemFactory.createFreshMethodNameWithoutHolder(
              method.getName().toString() + "$staticized",
              dexItemFactory.prependHolderToProto(method.getReference()),
              clazz.getType(),
              candidate ->
                  !newReferences.contains(candidate)
                      && isFresh(clazz, candidate, methodsToStaticizeReferences));
      newReferences.add(newReference);
      replacements.put(method.getReference(), staticize(method, newReference));
    }
    return replacements.build();
  }

  // Kept methods are also staticized, like private interface methods are moved to the companion
  // class when desugaring interface methods. Interface constructors and private native or abstract
  // interface methods are invalid and skipped, so that the FileWriter reports a CompilationError
  // for them.
  private boolean shouldStaticize(DexEncodedMethod method) {
    return method.isNonStaticPrivateMethod() && !method.isInstanceInitializer() && method.hasCode();
  }

  // The new static method must not collide with a method on the holder, except for the methods that
  // are being staticized, and must not match any method in a super interface. Per JVMS 5.4.3.4 step
  // 2 (and ART Class::FindInterfaceMethod), invoke-interface resolution on the holder checks the
  // methods declared on the holder before its super interfaces, so an invoke-interface on the
  // holder targeting a super interface method would resolve to the private static method and throw
  // an IllegalAccessError or IncompatibleClassChangeError. Methods on Object cannot collide, since
  // the first parameter of the new static method is the holder.
  private boolean isFresh(
      DexProgramClass clazz, DexMethod candidate, Set<DexMethod> methodsToStaticizeReferences) {
    DexEncodedMethod existingMethod = clazz.lookupMethod(candidate);
    if (existingMethod != null
        && !methodsToStaticizeReferences.contains(existingMethod.getReference())) {
      return false;
    }
    // The resolution result may contain multiple results, e.g., for duplicate library definitions.
    BooleanBox matchesSuperInterfaceMethod = new BooleanBox();
    for (DexType superInterface : clazz.getInterfaces()) {
      appInfo
          .resolveMethodOnInterface(superInterface, candidate)
          .visitMethodResolutionResults(
              result -> matchesSuperInterfaceMethod.set(),
              failedResult ->
                  matchesSuperInterfaceMethod.or(failedResult.hasMethodsCausingError()));
    }
    return matchesSuperInterfaceMethod.isFalse();
  }

  private DexEncodedMethod staticize(DexEncodedMethod method, DexMethod newReference) {
    // Record the original method as an inline frame to preserve it in positions for retracing.
    DexEncodedMethod newMethod =
        method.toTypeSubstitutedMethodAsInlining(
            newReference,
            dexItemFactory,
            builder ->
                builder
                    .modifyAccessFlags(
                        flags -> {
                          flags.promoteToStatic();
                          flags.demoteFromSynchronized();
                        })
                    .setGenericSignature(MethodTypeSignature.noSignature())
                    .setAnnotations(
                        method.annotations().methodParametersWithFakeThisArguments(dexItemFactory))
                    .setParameterAnnotations(
                        method
                            .getParameterAnnotations()
                            .withParameterCount(method.getParameters().size())
                            .withFakeThisParameter()));
    // The Kotlin metadata describes the method before staticizing.
    newMethod.clearKotlinInfo();
    Code code = newMethod.getCode();
    assert code != null;
    if (code.isCfCode() || code.isDexCode() || code.isLirCode()) {
      newMethod.setCode(
          DexEncodedMethod.mutateOrCreateCodeWithFakeThisParameter(code, newReference, appView));
    }
    return newMethod;
  }

  private void rewriteEnclosingMethodAttributes(PrivateInterfaceMethodGraphLens lens) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.hasEnclosingMethodAttribute()
          && clazz.getEnclosingMethodAttribute().hasEnclosingMethod()) {
        DexMethod enclosingMethod = clazz.getEnclosingMethodAttribute().getEnclosingMethod();
        DexMethod newEnclosingMethod = lens.getNextMethodSignature(enclosingMethod);
        if (newEnclosingMethod.isNotIdenticalTo(enclosingMethod)) {
          clazz.setEnclosingMethodAttribute(new EnclosingMethodAttribute(newEnclosingMethod));
        }
      }
    }
  }
}
