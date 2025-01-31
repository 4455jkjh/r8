// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar;

import static com.android.tools.r8.utils.positions.LineNumberOptimizer.runAndWriteMap;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalGlobalSyntheticsProgramConsumer.InternalGlobalSyntheticsCfConsumer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OriginalSourceFiles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class CfApplicationWriter {

  private final DexApplication application;
  private final AppView<?> appView;
  private final InternalOptions options;
  private final Optional<Marker> marker;

  public CfApplicationWriter(AppView<?> appView, Marker marker) {
    this.application = appView.appInfo().app();
    this.appView = appView;
    this.options = appView.options();
    this.marker = Optional.ofNullable(marker);
  }

  public void write(ClassFileConsumer consumer, ExecutorService executorService) {
    assert !options.hasMappingFileSupport();
    write(consumer, executorService, null);
  }

  public void write(
      ClassFileConsumer consumer, ExecutorService executorService, AndroidApp inputApp) {
    application.timing.begin("CfApplicationWriter.write");
    try {
      writeApplication(inputApp, consumer, executorService);
    } finally {
      application.timing.end();
    }
  }

  private boolean includeMarker(Marker marker) {
    if (marker.isRelocator()) {
      return false;
    }
    assert marker.isCfBackend() || marker.isDexBackend();
    if (options.desugarSpecificOptions().noCfMarkerForDesugaredCode) {
      return !marker.isCfBackend() || !marker.isDesugared();
    }
    return true;
  }

  private void writeApplication(
      AndroidApp inputApp, ClassFileConsumer consumer, ExecutorService executorService) {
    ProguardMapId proguardMapId = null;
    if (options.hasMappingFileSupport()) {
      assert marker.isPresent();
      proguardMapId =
          runAndWriteMap(
              inputApp,
              appView,
              application.timing,
              OriginalSourceFiles.fromClasses(),
              DebugRepresentation.none(options));
      marker.get().setPgMapId(proguardMapId.getId());
    }
    Optional<String> markerString = marker.filter(this::includeMarker).map(Marker::toString);
    SourceFileEnvironment sourceFileEnvironment = null;
    if (options.sourceFileProvider != null) {
      sourceFileEnvironment = ApplicationWriter.createSourceFileEnvironment(proguardMapId);
    }
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(appView);
    Collection<DexProgramClass> classes = application.classes();
    Collection<DexProgramClass> globalSyntheticClasses = new ArrayList<>();
    if (options.intermediate && options.hasGlobalSyntheticsConsumer()) {
      Collection<DexProgramClass> allClasses = classes;
      classes = new ArrayList<>(allClasses.size());
      for (DexProgramClass clazz : allClasses) {
        if (appView.getSyntheticItems().isGlobalSyntheticClassTransitive(clazz)) {
          globalSyntheticClasses.add(clazz);
          Consumer<DexProgramClass> globalSyntheticCreatedCallback =
              appView.options().testing.globalSyntheticCreatedCallback;
          if (globalSyntheticCreatedCallback != null) {
            globalSyntheticCreatedCallback.accept(clazz);
          }
        } else {
          classes.add(clazz);
        }
      }
    }
    for (DexProgramClass clazz : classes) {
      new CfApplicationClassWriter(appView, clazz)
          .writeClassCatchingErrors(consumer, rewriter, markerString, sourceFileEnvironment);
    }
    if (!globalSyntheticClasses.isEmpty()) {
      InternalGlobalSyntheticsCfConsumer globalsConsumer =
          new InternalGlobalSyntheticsCfConsumer(options.getGlobalSyntheticsConsumer(), appView);
      for (DexProgramClass clazz : globalSyntheticClasses) {
        new CfApplicationClassWriter(appView, clazz)
            .writeClassCatchingErrors(
                globalsConsumer, rewriter, markerString, sourceFileEnvironment);
      }
      globalsConsumer.finished(appView);
    }
    ApplicationWriter.supplyAdditionalConsumers(appView, executorService, Collections.emptyList());
  }
}
