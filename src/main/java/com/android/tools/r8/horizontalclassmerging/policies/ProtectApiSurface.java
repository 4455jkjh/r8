// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ProtectApiSurface extends SingleClassPolicy {

  private final Set<DexProgramClass> apiClasses = Sets.newIdentityHashSet();

  public ProtectApiSurface(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    new Traversal(appView, immediateSubtypingInfo).run(appView.appInfo().classes());
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !apiClasses.contains(program);
  }

  @Override
  public void clear() {
    apiClasses.clear();
  }

  @Override
  public boolean shouldSkipPolicy() {
    return apiClasses.isEmpty();
  }

  @Override
  public String getName() {
    return "ProtectApiSurface";
  }

  private class Traversal extends DepthFirstTopDownClassHierarchyTraversal {

    private final InternalOptions options;
    private final Map<DexProgramClass, BottomUpTraversalState> states = new IdentityHashMap<>();

    Traversal(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
      super(appView, immediateSubtypingInfo);
      this.options = appView.options();
    }

    @Override
    public void visit(DexProgramClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void prune(DexProgramClass clazz) {
      boolean isKeptOrHasKeptSubclass = unsetBottomUpTraversalState(clazz);
      if (isKeptOrHasKeptSubclass) {
        apiClasses.add(clazz);
        immediateSubtypingInfo.forEachImmediateProgramSuperClass(
            clazz,
            superClass ->
                getOrCreateBottomUpTraversalState(superClass).setIsKeptOrHasKeptSubclass());
      }
    }

    private BottomUpTraversalState getOrCreateBottomUpTraversalState(DexProgramClass clazz) {
      return states.computeIfAbsent(
          clazz,
          c -> {
            BottomUpTraversalState newState = new BottomUpTraversalState(isKept(clazz));
            states.put(c, newState);
            return newState;
          });
    }

    private boolean isKept(DexProgramClass clazz) {
      KeepClassInfo keepInfo = appView.getKeepInfo(clazz);
      return !keepInfo.isMinificationAllowed(options) && !keepInfo.isShrinkingAllowed(options);
    }

    // Returns whether the current class is kept or has a kept subclass.
    private boolean unsetBottomUpTraversalState(DexProgramClass clazz) {
      BottomUpTraversalState state = states.remove(clazz);
      if (state != null) {
        return state.isKeptOrHasKeptSubclass;
      }
      return isKept(clazz);
    }
  }

  static class BottomUpTraversalState {

    boolean isKeptOrHasKeptSubclass;

    private BottomUpTraversalState(boolean isKept) {
      this.isKeptOrHasKeptSubclass = isKept;
    }

    void setIsKeptOrHasKeptSubclass() {
      isKeptOrHasKeptSubclass = true;
    }
  }
}
