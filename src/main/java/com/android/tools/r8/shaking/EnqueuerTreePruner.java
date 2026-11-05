// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;

public class EnqueuerTreePruner extends TreePruner {

  private final TreePrunerConfiguration configuration;

  public EnqueuerTreePruner(AppView<AppInfoWithLiveness> appView) {
    this(appView, DefaultTreePrunerConfiguration.getInstance());
  }

  public EnqueuerTreePruner(
      AppView<AppInfoWithLiveness> appView, TreePrunerConfiguration configuration) {
    super(appView, createUnusedItemsPrinter(appView));
    this.configuration = configuration;
  }

  private static UnusedItemsPrinter createUnusedItemsPrinter(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    return options.hasUsageInformationConsumer()
        ? new UnusedItemsPrinter(
            s ->
                ExceptionUtils.withConsumeResourceHandler(
                    options.reporter, options.usageInformationConsumer, s))
        : UnusedItemsPrinter.DONT_PRINT;
  }

  @Override
  public boolean isInstantiatedDirectly(DexProgramClass clazz) {
    return appView.appInfo().getObjectAllocationInfoCollection().isInstantiatedDirectly(clazz);
  }

  @Override
  public boolean isLiveMethod(DexMethod method) {
    return appView.appInfo().isLiveMethod(method);
  }

  @Override
  public boolean isLiveProgramClass(DexProgramClass clazz) {
    return appView.appInfo().isLiveProgramClass(clazz);
  }

  @Override
  public boolean isReachableOrReferencedField(DexEncodedField field) {
    return configuration.isReachableOrReferencedField(appView.appInfo(), field);
  }

  @Override
  public boolean isTargetedMethod(DexMethod method) {
    return appView.appInfo().isTargetedMethod(method);
  }

  @Override
  public boolean isTypeLive(DexType type) {
    return appView.appInfo().isNonProgramTypeOrLiveProgramType(type);
  }
}
