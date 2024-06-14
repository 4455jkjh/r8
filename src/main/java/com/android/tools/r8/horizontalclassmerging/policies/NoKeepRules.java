// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.DexDefinitionSignature;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Set;

public class NoKeepRules extends SingleClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final KeepInfoCollection keepInfo;
  private final InternalOptions options;

  private final Set<DexType> dontMergeTypes = Sets.newIdentityHashSet();

  public NoKeepRules(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.keepInfo = appView.getKeepInfo();
    this.options = appView.options();
    appView.appInfo().classes().forEach(this::processClass);
  }

  private void processClass(DexProgramClass clazz) {
    DexType type = clazz.getType();
    boolean pinHolder = isPinned(keepInfo.getClassInfo(clazz), clazz.getClassSignature());
    for (DexEncodedMember<?, ?> member : clazz.members()) {
      if (isPinned(keepInfo.getMemberInfo(member, clazz), member.getGenericSignature())) {
        pinHolder = true;
        Iterables.addAll(
            dontMergeTypes,
            Iterables.filter(
                member.getReference().getReferencedBaseTypes(appView.dexItemFactory()),
                DexType::isClassType));
      }
    }
    if (pinHolder) {
      dontMergeTypes.add(type);
    }
  }

  private boolean isPinned(KeepInfo<?, ?> keepInfo, DexDefinitionSignature<?> genericSignature) {
    return keepInfo.isPinned(options)
        || (genericSignature.hasSignature()
            && !options.isForceProguardCompatibilityEnabled()
            && !keepInfo.isSignatureRemovalAllowed(options));
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !dontMergeTypes.contains(program.getType());
  }

  @Override
  public String getName() {
    return "NoKeepRules";
  }
}
