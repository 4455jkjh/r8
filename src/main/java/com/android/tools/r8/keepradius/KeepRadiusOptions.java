// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.keepradius.proto.KeepRadiusContainer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.nio.file.Path;
import java.util.function.Consumer;

public class KeepRadiusOptions {

  public KeepRadiusConsumer keepRadiusConsumer;
  public Consumer<KeepRadiusContainer> keepRadiusContainerConsumer;
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
          "com.android.tools.r8.keepradius.enablesubsumptionanalysis", true);

  private final InternalOptions options;

  public KeepRadiusOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isEnabled() {
    if (options.getLibraryDesugaringOptions().isL8()) {
      return false;
    }
    return getKeepRadiusConsumer() != null || getKeepRadiusContainerConsumer() != null;
  }

  public KeepRadiusConsumer getKeepRadiusConsumer() {
    return keepRadiusConsumer;
  }

  public Consumer<KeepRadiusContainer> getKeepRadiusContainerConsumer() {
    Consumer<KeepRadiusContainer> result = keepRadiusContainerConsumer;
    Path dataOutputPath = getDataOutputPath();
    if (dataOutputPath != null) {
      Consumer<KeepRadiusContainer> writeDataToFile =
          container -> KeepRadiusContainerUtils.writeToFile(container, dataOutputPath);
      result = result != null ? result.andThen(writeDataToFile) : writeDataToFile;
    }
    Path htmlOutputPath = getHtmlOutputPath();
    if (htmlOutputPath != null) {
      Consumer<KeepRadiusContainer> writeHtmlToFile =
          container ->
              KeepRadiusContainerUtils.writeHtmlToConsumer(
                  container, new StringConsumer.FileConsumer(htmlOutputPath), options.reporter);
      result = result != null ? result.andThen(writeHtmlToFile) : writeHtmlToFile;
    }
    return result;
  }

  public Path getDataOutputPath() {
    if (dataOutputDirectory != null) {
      return dataOutputDirectory.resolve("keepradius" + System.nanoTime() + ".pb");
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

  public interface KeepRadiusConsumer {

    void accept(
        AppView<AppInfoWithClassHierarchy> appView,
        AppInfoWithLiveness appInfo,
        RootSetKeepRadius keepRadius);
  }
}
