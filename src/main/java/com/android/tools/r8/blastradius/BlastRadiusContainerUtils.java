// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BlastRadiusContainerUtils {

  public static <OS extends OutputStream> void writeToConsumer(
      BlastRadiusContainer container, ByteArrayConsumer<OS> consumer, Reporter reporter) {
    OS outputStream;
    try (OutputStream autoCloseable = outputStream = consumer.getOutputStream()) {
      container.writeTo(outputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    consumer.finished(outputStream, reporter);
  }

  public static void writeHtmlToConsumer(
      BlastRadiusContainer container, StringConsumer consumer, Reporter reporter) {
    consumer.accept(BlastRadiusHtmlReportGenerator.generateHtmlReport(container), reporter);
    consumer.finished(reporter);
  }

  public static void writeToFile(BlastRadiusContainer container, Path printBlastRadiusFile) {
    try (OutputStream output = Files.newOutputStream(printBlastRadiusFile)) {
      container.writeTo(output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
