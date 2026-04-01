// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class BlastRadiusOptions {

  public BlastRadiusConsumer blastRadiusConsumer;
  public Consumer<BlastRadiusContainer> blastRadiusContainerConsumer;
  public final String outputDirectory =
      System.getProperty("com.android.tools.r8.dumpblastradiustodirectory");
  public String outputPath = System.getProperty("com.android.tools.r8.dumpblastradiustofile");
  public final boolean enableSubsumptionAnalysis =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.blastradius.enablesubsumptionanalysis", true);

  private final InternalOptions options;

  public BlastRadiusOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isEnabled() {
    return getBlastRadiusConsumer() != null || getBlastRadiusContainerConsumer() != null;
  }

  public BlastRadiusConsumer getBlastRadiusConsumer() {
    return blastRadiusConsumer;
  }

  public Consumer<BlastRadiusContainer> getBlastRadiusContainerConsumer() {
    Consumer<BlastRadiusContainer> result = blastRadiusContainerConsumer;
    Path outputPath = getOutputPath();
    if (outputPath != null) {
      Consumer<BlastRadiusContainer> writeToFile =
          container -> BlastRadiusContainerUtils.writeToFile(container, outputPath);
      result = result != null ? result.andThen(writeToFile) : writeToFile;
    }
    return result;
  }

  public Path getOutputPath() {
    if (outputDirectory != null) {
      return Paths.get(outputDirectory).resolve("blastradius" + System.nanoTime() + ".pb");
    }
    if (outputPath != null) {
      return Paths.get(outputPath);
    }
    if (options.hasProguardConfiguration()) {
      ProguardConfiguration configuration = options.getProguardConfiguration();
      if (configuration.isPrintBlastRadius()) {
        return configuration.getPrintBlastRadiusFile();
      }
    }
    return null;
  }

  public boolean shouldExitEarly() {
    if (!isEnabled() || options.programConsumer != DexIndexedConsumer.emptyConsumer()) {
      return false;
    }
    // TODO(b/484870212): Consider failing compilation or return false if other consumers are set.
    return true;
  }

  public interface BlastRadiusConsumer {

    void accept(
        AppView<AppInfoWithClassHierarchy> appView,
        AppInfoWithLiveness appInfo,
        RootSetBlastRadius blastRadius);
  }
}
