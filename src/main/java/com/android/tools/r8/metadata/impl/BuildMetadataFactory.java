// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.Version;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.metadata.D8BuildMetadata;
import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class BuildMetadataFactory {

  public static D8BuildMetadata create(AppView<AppInfo> appView) {
    return D8BuildMetadataImpl.builder()
        .setOptions(new D8OptionsImpl(appView.options()))
        .setVersion(Version.LABEL)
        .build();
  }

  public static R8BuildMetadata create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      List<VirtualFile> virtualFiles) {
    Map<FeatureSplit, List<VirtualFile>> virtualFilesForFeatureSplit = new IdentityHashMap<>();
    for (VirtualFile virtualFile : virtualFiles) {
      FeatureSplit featureSplit = virtualFile.getFeatureSplitOrBase();
      virtualFilesForFeatureSplit
          .computeIfAbsent(featureSplit, ignoreKey(ArrayList::new))
          .add(virtualFile);
    }
    List<VirtualFile> baseVirtualFiles =
        virtualFilesForFeatureSplit.getOrDefault(FeatureSplit.BASE, Collections.emptyList());
    InternalOptions options = appView.options();
    return R8BuildMetadataImpl.builder()
        .setOptions(new R8OptionsImpl(options))
        .setBaselineProfileRewritingOptions(R8BaselineProfileRewritingOptionsImpl.create(options))
        .setCompilationInfo(R8CompilationInfoImpl.create(appView, executorService))
        .applyIf(
            options.isGeneratingDex(), builder -> builder.setDexFilesMetadata(baseVirtualFiles))
        .applyIf(
            options.hasFeatureSplitConfiguration(),
            builder ->
                builder.setFeatureSplitsMetadata(
                    R8FeatureSplitsMetadataImpl.create(appView, virtualFilesForFeatureSplit)))
        .setResourceOptimizationOptions(R8ResourceOptimizationOptionsImpl.create(options))
        .setStartupOptimizationOptions(
            R8StartupOptimizationOptionsImpl.create(options, baseVirtualFiles))
        .setStatsMetadata(R8StatsMetadataImpl.create(appView))
        .setVersion(Version.LABEL)
        .build();
  }
}
