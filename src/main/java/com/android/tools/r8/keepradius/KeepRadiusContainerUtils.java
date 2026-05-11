// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.keepradius.proto.KeepRadiusContainer;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class KeepRadiusContainerUtils {

  public static <OS extends OutputStream> void writeToConsumer(
      KeepRadiusContainer container, ByteArrayConsumer<OS> consumer, Reporter reporter) {
    OS outputStream;
    try (OutputStream autoCloseable = outputStream = consumer.getOutputStream()) {
      container.writeTo(outputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    consumer.finished(outputStream, reporter);
  }

  public static void writeHtmlToConsumer(
      KeepRadiusContainer container, StringConsumer consumer, Reporter reporter) {
    consumer.accept(KeepRadiusHtmlReportGenerator.generateHtmlReport(container), reporter);
    consumer.finished(reporter);
  }

  public static void writeToFile(KeepRadiusContainer container, Path printKeepRadiusFile) {
    try (OutputStream output = Files.newOutputStream(printKeepRadiusFile)) {
      container.writeTo(output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
