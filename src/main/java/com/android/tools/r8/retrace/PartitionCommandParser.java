// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Paths;

public class PartitionCommandParser {

  private static class ParserState {
    final PartitionCommand.Builder builder;
    final Origin origin;
    boolean isProguardMapProducerSet = false;

    public ParserState(PartitionCommand.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static CliParser<ParserState> createParser() {
    var header =
        StringUtils.lines(
            "Usage: partition [options] <proguard-map>",
            " where <proguard-map> is a generated mapping file and options are:");
    var parser = new CliParser<ParserState>(header);
    return parser
        .option1(
            "--output",
            "<partition-map>",
            "Output destination of partitioned map.",
            (b, arg) -> {
              if (arg.isEmpty()) {
                b.builder.getReporter().error(new StringDiagnostic("Empty argument for --output"));
              } else {
                b.builder.setPartitionMapConsumer(
                    PartitionMapZipContainer.createPartitionMapZipContainerConsumer(
                        Paths.get(arg)));
              }
            })
        .positional(
            (b, arg) -> {
              if (!b.isProguardMapProducerSet) {
                b.builder.setProguardMapProducer(ProguardMapProducer.fromPath(Paths.get(arg)));
                b.isProguardMapProducerSet = true;
              } else {
                StringDiagnostic error =
                    new StringDiagnostic(
                        "Too many arguments specified for builder at " + arg, b.origin);
                b.builder.getReporter().error(error);
              }
            })
        .option0("--version", "Print the version.", b -> b.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", b -> b.builder.setPrintHelp(true), "-h");
  }

  static String getUsageMessage() {
    return createParser().getUsageMessage();
  }

  public static PartitionCommand.Builder parse(String[] args, Origin origin) {
    PartitionCommand.Builder builder = PartitionCommand.builder();
    createParser()
        .parse(args, new ParserState(builder, origin), err -> builder.getReporter().error(err));
    return builder;
  }
}
