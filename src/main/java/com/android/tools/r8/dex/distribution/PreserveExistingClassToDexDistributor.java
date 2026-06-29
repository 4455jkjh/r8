// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceRBTreeMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PreserveExistingClassToDexDistributor {

  private final AppView<?> appView;
  private final Set<DexProgramClass> classes;
  private final VirtualFileCycler cycler;

  public PreserveExistingClassToDexDistributor(
      List<VirtualFile> files,
      List<VirtualFile> filesForDistribution,
      AppView<?> appView,
      Set<DexProgramClass> classes,
      IntBox nextFileId) {
    this.appView = appView;
    this.classes = classes;
    this.cycler = new VirtualFileCycler(files, filesForDistribution, appView, nextFileId);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    Int2ReferenceMap<Set<DexProgramClass>> distribution = buildDistribution();
    distribute(distribution);
    DexDistributionRefinement.run(appView, cycler, executorService, timing);
  }

  private Int2ReferenceMap<Set<DexProgramClass>> buildDistribution() {
    Int2ReferenceMap<Set<DexProgramClass>> distribution = new Int2ReferenceRBTreeMap<>();
    for (DexProgramClass clazz : classes) {
      Origin origin = clazz.getOrigin();
      if (origin instanceof ArchiveEntryOrigin) {
        ArchiveEntryOrigin archiveEntryOrigin = (ArchiveEntryOrigin) origin;
        int dexIndex = getDexIndex(archiveEntryOrigin.getEntryName());
        if (dexIndex >= 0) {
          if (!distribution.containsKey(dexIndex)) {
            distribution.put(dexIndex, Sets.newIdentityHashSet());
          }
          distribution.get(dexIndex).add(clazz);
        } else {
          appView
              .reporter()
              .error(
                  new StringDiagnostic(
                      "Unable to find original dex file for " + clazz.getTypeName(), origin));
        }
      }
    }
    appView.reporter().failIfPendingErrors();
    return distribution;
  }

  private void distribute(Int2ReferenceMap<Set<DexProgramClass>> distribution) {
    VirtualFile virtualFile = cycler.nextOrCreate();
    for (Set<DexProgramClass> classes : distribution.values()) {
      for (DexProgramClass clazz : classes) {
        virtualFile.addClass(clazz);
      }
      if (virtualFile.isFull()) {
        appView.reporter().error("Unable to fit classes in " + virtualFile.getId());
      } else {
        virtualFile.commitTransaction();
        assert !cycler.hasNext();
        virtualFile = cycler.addFile();
      }
    }
    appView.reporter().failIfPendingErrors();
  }

  private static int getDexIndex(String entryName) {
    if (!entryName.startsWith("classes")) {
      return -1;
    }
    int dexIndex = entryName.lastIndexOf(".dex");
    if (dexIndex < 0) {
      return -1;
    }
    String substring = entryName.substring("classes".length(), dexIndex);
    if (substring.isEmpty()) {
      return 1;
    }
    try {
      return Integer.parseInt(substring);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
