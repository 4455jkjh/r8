// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class KotlinMetadataDiscardedChecker {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final InternalOptions options;

  private final List<DexProgramClass> failed = new ArrayList<>();

  private KotlinMetadataDiscardedChecker(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  public static KotlinMetadataDiscardedChecker create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new KotlinMetadataDiscardedChecker(appView);
  }

  public List<DexProgramClass> run(ExecutorService executorService) throws ExecutionException {
    assert failed.isEmpty();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::checkClass,
        appView.options().getThreadingModule(),
        executorService);
    failed.sort((item, other) -> item.getReference().compareTo(other.getReference()));
    return failed;
  }

  private boolean isCheckKotlinMetadataDiscardedEnabled(DexProgramClass clazz) {
    return appView.getKeepInfo().getInfo(clazz).isCheckKotlinMetadataDiscardedEnabled(options);
  }

  private boolean hasKotlinMetadata(DexProgramClass clazz) {
    return !clazz.getKotlinInfo().isNoKotlinInformation()
        || clazz.annotations().hasAnnotation(appView.dexItemFactory().kotlinMetadataType);
  }

  private void checkClass(DexProgramClass clazz) {
    if (isCheckKotlinMetadataDiscardedEnabled(clazz) && hasKotlinMetadata(clazz)) {
      synchronized (failed) {
        failed.add(clazz);
      }
    }
  }
}
