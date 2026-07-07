// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicyWithPreprocessing;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class NoInaccessibleFieldTypes extends SingleClassPolicyWithPreprocessing {

  private final AppView<AppInfoWithLiveness> appView;

  private final Set<DexProgramClass> pinnedClasses = ConcurrentHashMap.newKeySet();

  public NoInaccessibleFieldTypes(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !pinnedClasses.contains(program);
  }

  @Override
  public void preprocess(LinkedList<HorizontalMergeGroup> groups, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          // Scan all code to find field accesses that would become invalid if merged.
          clazz.forEachProgramMethodMatching(
              DexEncodedMethod::hasLirCode,
              method -> {
                LirCode<?> lirCode = method.getDefinition().getLirCode();
                lirCode.<DexField>forEachConstantPoolItemThatMatches(
                    LirConstant::isDexField,
                    fieldReference -> {
                      DexType fieldBaseType = fieldReference.getType().getBaseType();
                      if (!fieldBaseType.isClassType()) {
                        return;
                      }
                      ProgramField resolvedField =
                          appView.appInfo().resolveField(fieldReference).getProgramField();
                      if (resolvedField == null || resolvedField.getAccessFlags().isStatic()) {
                        return;
                      }
                      DexClass fieldBaseClass = appView.definitionFor(fieldBaseType);
                      if (fieldBaseClass != null
                          && AccessControl.isClassAccessible(fieldBaseClass, method, appView)
                              .isPossiblyFalse()) {
                        pinnedClasses.add(resolvedField.getHolder());
                      }
                    });
              });
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  @Override
  public String getName() {
    return "NoInaccessibleFieldTypes";
  }
}
