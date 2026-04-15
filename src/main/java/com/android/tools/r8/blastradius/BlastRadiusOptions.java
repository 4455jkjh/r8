// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.nio.file.Path;
import java.util.function.Consumer;

public class BlastRadiusOptions {

  public BlastRadiusConsumer blastRadiusConsumer;
  public Consumer<BlastRadiusContainer> blastRadiusContainerConsumer;
  public final Path dataOutputDirectory =
      SystemPropertyUtils.parsePathFromSystemProperty(
          "com.android.tools.r8.dumpkeepradiustodirectory");
  public Path dataOutputPath =
      SystemPropertyUtils.parsePathFromSystemProperty("com.android.tools.r8.dumpkeepradiustofile");
  public final Path htmlOutputDirectory =
      SystemPropertyUtils.parsePathFromSystemProperty(
          "com.android.tools.r8.dumpkeepradiushtmltodirectory");
  public Path htmlOutputPath =
      SystemPropertyUtils.parsePathFromSystemProperty(
          "com.android.tools.r8.dumpkeepradiushtmltofile");
  public final boolean enableSubsumptionAnalysis =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.blastradius.enablesubsumptionanalysis", true);

  private final InternalOptions options;

  public BlastRadiusOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isEnabled() {
    if (options.getLibraryDesugaringOptions().isL8()) {
      return false;
    }
    return getBlastRadiusConsumer() != null || getBlastRadiusContainerConsumer() != null;
  }

  public BlastRadiusConsumer getBlastRadiusConsumer() {
    return blastRadiusConsumer;
  }

  public Consumer<BlastRadiusContainer> getBlastRadiusContainerConsumer() {
    Consumer<BlastRadiusContainer> result = blastRadiusContainerConsumer;
    Path dataOutputPath = getDataOutputPath();
    if (dataOutputPath != null) {
      Consumer<BlastRadiusContainer> writeDataToFile =
          container -> BlastRadiusContainerUtils.writeToFile(container, dataOutputPath);
      result = result != null ? result.andThen(writeDataToFile) : writeDataToFile;
    }
    Path htmlOutputPath = getHtmlOutputPath();
    if (htmlOutputPath != null) {
      Consumer<BlastRadiusContainer> writeHtmlToFile =
          container ->
              BlastRadiusContainerUtils.writeHtmlToConsumer(
                  container, new StringConsumer.FileConsumer(htmlOutputPath), options.reporter);
      result = result != null ? result.andThen(writeHtmlToFile) : writeHtmlToFile;
    }
    return result;
  }

  public Path getDataOutputPath() {
    if (dataOutputDirectory != null) {
      return dataOutputDirectory.resolve("blastradius" + System.nanoTime() + ".pb");
    }
    return dataOutputPath;
  }

  public Path getHtmlOutputPath() {
    if (htmlOutputDirectory != null) {
      return htmlOutputDirectory.resolve("report" + System.nanoTime() + ".html");
    }
    return htmlOutputPath;
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
