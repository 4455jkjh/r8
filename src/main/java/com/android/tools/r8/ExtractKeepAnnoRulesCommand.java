// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Experimental API to extract keep rules from keep annotations. */
@KeepForApi
public class ExtractKeepAnnoRulesCommand extends BaseCommand {

  private final StringConsumer rulesConsumer;
  private final KeepRuleExtractorOptions extractorOptions;
  private final DexItemFactory factory;
  private final Reporter reporter;

  @KeepForApi
  public static class Builder extends BaseCommand.Builder<ExtractKeepAnnoRulesCommand, Builder> {

    private StringConsumer rulesConsumer = null;
    private KeepRuleExtractorOptions extractorOptions = KeepRuleExtractorOptions.getR8Options();

    private Builder() {}

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    @Override
    Builder self() {
      return this;
    }

    /** TBD - still experimental */
    public Builder setRulesOutputPath(Path rulesOutputPath) {
      rulesConsumer = new StringConsumer.FileConsumer(rulesOutputPath);
      return self();
    }

    /** TBD - still experimental */
    public Builder setRulesConsumer(StringConsumer rulesConsumer) {
      this.rulesConsumer = rulesConsumer;
      return self();
    }

    /** TBD - still experimental and still package private */
    Builder setExtractorOptions(KeepRuleExtractorOptions extractorOptions) {
      this.extractorOptions = extractorOptions;
      return self();
    }

    @Override
    protected ExtractKeepAnnoRulesCommand makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new ExtractKeepAnnoRulesCommand(isPrintHelp(), isPrintVersion());
      }

      return new ExtractKeepAnnoRulesCommand(
          new DexItemFactory(),
          getAppBuilder().build(),
          rulesConsumer,
          extractorOptions,
          getReporter());
    }
  }

  static String usageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static Builder parse(String[] args) {
    Builder builder = builder();
    createParser()
        .parse(
            args,
            builder,
            err -> builder.error(new StringDiagnostic(err, CommandLineOrigin.INSTANCE)));
    return builder;
  }

  public StringConsumer getRulesConsumer() {
    return rulesConsumer;
  }

  public KeepRuleExtractorOptions getExtractorOptions() {
    return extractorOptions;
  }

  Reporter getReporter() {
    return reporter;
  }

  private static CliParser<Builder> createParser() {
    var header = "Usage: EXPERIMENTAL tool to extract keep rules from keep annotations";
    var parser = new CliParser<Builder>(header);
    // TODO(b/425263915): Need to support more, including extracting for different versions and
    //  keepedge AST proto.
    return parser
        .option1(
            "--rules-output",
            "<file>",
            "Output the extracted keep rules.",
            (b, arg) -> b.setRulesOutputPath(Paths.get(arg)))
        .option1(
            "--rules-target",
            "<r8|pg>",
            "Optimizer rules are for (default 'r8').",
            (b, arg) -> {
              if (arg.equals("r8")) {
                b.setExtractorOptions(KeepRuleExtractorOptions.getR8Options());
              } else if (arg.equals("pg")) {
                b.setExtractorOptions(KeepRuleExtractorOptions.getPgOptions());
              } else {
                b.getReporter()
                    .fatalError(
                        new StringDiagnostic(
                            "Unsupported argument '" + arg + "' to --rules-target",
                            CommandLineOrigin.INSTANCE));
              }
            })
        .option0("--version", "Print the version.", b -> b.setPrintVersion(true))
        .option0("--help", "Print this message.", b -> b.setPrintHelp(true), "-h")
        .positional((b, arg) -> b.addProgramFiles(Paths.get(arg)));
  }

  private ExtractKeepAnnoRulesCommand(
      DexItemFactory factory,
      AndroidApp inputApp,
      StringConsumer rulesConsumer,
      KeepRuleExtractorOptions extractorOptions,
      Reporter reporter) {
    super(inputApp);
    this.factory = factory;
    this.rulesConsumer = rulesConsumer;
    this.extractorOptions = extractorOptions;
    this.reporter = reporter;
  }

  private ExtractKeepAnnoRulesCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    this.factory = new DexItemFactory();
    this.rulesConsumer = null;
    this.extractorOptions = null;
    this.reporter = new Reporter();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    internal.programConsumer = DexIndexedConsumer.emptyConsumer();
    return internal;
  }
}
