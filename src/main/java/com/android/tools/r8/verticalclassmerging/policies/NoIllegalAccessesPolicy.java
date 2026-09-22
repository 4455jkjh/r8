// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.internal.LazyBox;
import com.android.tools.r8.utils.internal.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.IllegalAccessDetector;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoIllegalAccessesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoIllegalAccessesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return !mergeMayLeadToIllegalAccesses(group.getSource(), group.getTarget());
  }

  private boolean mergeMayLeadToIllegalAccesses(DexProgramClass source, DexProgramClass target) {
    if (source.isSamePackage(target)) {
      // When merging two classes from the same package, we only need to make sure that [source]
      // does not get less visible, since that could make a valid access to [source] from another
      // package illegal after [source] has been merged into [target].
      assert source.getAccessFlags().isPackagePrivateOrPublic();
      assert target.getAccessFlags().isPackagePrivateOrPublic();
      // TODO(b/287891322): Allow merging if `source` is only accessed from inside its own package.
      return source.getAccessFlags().isPublic() && target.getAccessFlags().isPackagePrivate();
    }

    // Check that all accesses to [source] and its members from inside the current package of
    // [source] will continue to work. This is guaranteed if [target] is public and all members of
    // [source] are either private or public.
    //
    // (Deliberately not checking all accesses to [source] since that would be expensive.)
    if (!target.isPublic()) {
      return true;
    }
    for (DexType sourceInterface : source.getInterfaces()) {
      DexClass sourceInterfaceClass = appView.definitionFor(sourceInterface);
      if (sourceInterfaceClass != null && !sourceInterfaceClass.isPublic()) {
        return true;
      }
    }
    for (DexEncodedField field : source.fields()) {
      if (!(field.isPublic() || field.isPrivate())) {
        return true;
      }
    }
    LazyBox<DexMethodSignatureSet> packagePrivateSuperMethods =
        new LazyBox<>(() -> getPackagePrivateSuperMethodsInTargetPackage(source, target));
    for (DexEncodedMethod method : source.methods()) {
      if (!(method.isPublic() || method.isPrivate())) {
        return true;
      }
      if (method.isPublic()) {
        DexEncodedMethod targetOverride = target.lookupVirtualMethod(method.getReference());
        if (targetOverride != null) {
          // Check if target narrows access.
          if (!targetOverride.isPublic()) {
            return true;
          }
        } else if (method.isNonPrivateVirtualMethod()
            && packagePrivateSuperMethods.computeIfAbsent().contains(method)) {
          // Check if merging introduces an unintended package-private override.
          return true;
        }
      }
    }
    // Check that all accesses from [source] to classes or members from the current package of
    // [source] will continue to work. This is guaranteed if the methods of [source] do not access
    // any private or protected classes or members from the current package of [source].
    TraversalContinuation<?, ?> result =
        source.traverseProgramMethods(
            method -> {
              boolean foundIllegalAccess =
                  method.registerCodeReferencesWithResult(
                      new IllegalAccessDetector(appView, method));
              if (foundIllegalAccess) {
                return TraversalContinuation.doBreak();
              }
              return TraversalContinuation.doContinue();
            },
            DexEncodedMethod::hasCode);
    return result.shouldBreak();
  }

  private DexMethodSignatureSet getPackagePrivateSuperMethodsInTargetPackage(
      DexProgramClass source, DexProgramClass target) {
    DexMethodSignatureSet packagePrivateSuperMethods = DexMethodSignatureSet.create();
    // These have to be tracked since children can widen access of parents package-private methods.
    DexMethodSignatureSet seenPublicOrProtectedMethods = DexMethodSignatureSet.create();
    DexClass current = appView.definitionFor(source.getSuperType());
    while (current != null && !current.isLibraryClass()) {
      if (current.isSamePackage(target)) {
        for (DexEncodedMethod superMethod : current.virtualMethods()) {
          if (superMethod.getAccessFlags().isPackagePrivate()) {
            if (!seenPublicOrProtectedMethods.contains(superMethod)) {
              packagePrivateSuperMethods.add(superMethod);
            }
          } else if (superMethod.isPublic() || superMethod.isProtectedMethod()) {
            seenPublicOrProtectedMethods.add(superMethod);
          }
        }
      }
      // Search always has to continue since inheritance can go in and out of packages.
      current = current.hasSuperType() ? appView.definitionFor(current.getSuperType()) : null;
    }
    return packagePrivateSuperMethods;
  }

  @Override
  public String getName() {
    return "NoIllegalAccessesPolicy";
  }
}
