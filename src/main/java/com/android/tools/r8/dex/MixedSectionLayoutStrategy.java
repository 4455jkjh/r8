// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.dex.FileWriter.MixedSectionOffsets;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;

public abstract class MixedSectionLayoutStrategy {

  public static MixedSectionLayoutStrategy create(
      AppView<?> appView, MixedSectionOffsets mixedSectionOffsets, VirtualFile virtualFile) {
    StartupOrder startupOrderForWriting =
        appView.options().getStartupOptions().isStartupLayoutOptimizationsEnabled()
                && virtualFile.getId() == 0
                && appView.hasClassHierarchy()
            ? appView
                .appInfoWithClassHierarchy()
                .getStartupOrder()
                .toStartupOrderForWriting(appView)
            : StartupOrder.empty();
    MixedSectionLayoutStrategy mixedSectionLayoutStrategy =
        startupOrderForWriting.isEmpty()
            ? new DefaultMixedSectionLayoutStrategy(appView, mixedSectionOffsets)
            : new StartupMixedSectionLayoutStrategy(
                appView, mixedSectionOffsets, startupOrderForWriting, virtualFile);
    return wrapForTesting(appView, mixedSectionLayoutStrategy, virtualFile);
  }

  private static MixedSectionLayoutStrategy wrapForTesting(
      AppView<?> appView,
      MixedSectionLayoutStrategy mixedSectionLayoutStrategy,
      VirtualFile virtualFile) {
    return appView
        .testing()
        .mixedSectionLayoutStrategyInspector
        .apply(mixedSectionLayoutStrategy, virtualFile);
  }

  public abstract Collection<DexAnnotation> getAnnotationLayout();

  public abstract Collection<DexAnnotationDirectory> getAnnotationDirectoryLayout();

  public abstract Collection<DexAnnotationSet> getAnnotationSetLayout();

  public abstract Collection<ParameterAnnotationsList> getAnnotationSetRefListLayout();

  public abstract Collection<DexProgramClass> getClassDataLayout();

  public abstract Collection<ProgramMethod> getCodeLayout();

  public abstract Collection<DexEncodedArray> getEncodedArrayLayout();

  public abstract Collection<DexString> getStringDataLayout();

  public abstract Collection<DexTypeList> getTypeListLayout();
}
