// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexReader;
import com.android.tools.r8.dex.DexSection;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

public class DexSegments {
  public static class Command extends BaseCommand {

    private final boolean csv;

    public static class Builder
        extends BaseCommand.Builder<Command, Builder> {

      private boolean csv = false;

      @Override
      Command.Builder self() {
        return this;
      }

      private Builder setCsv(boolean csv) {
        this.csv = csv;
        return self();
      }

      @Override
      protected Command makeCommand() {
        // If printing, ignore everything else.
        if (isPrintHelp()) {
          return new Command(true, false);
        }
        if (isPrintVersion()) {
          return new Command(false, true);
        }
        return new Command(getAppBuilder().build(), csv);
      }
    }

    static String usageMessage() {
      return CliParserUtils.getUsageMessage(createParser());
    }

    public static Command.Builder builder() {
      return new Command.Builder();
    }

    public static Command.Builder parse(String[] args) {
      Command.Builder builder = builder();
      createParser()
          .parse(
              args,
              builder,
              err ->
                  builder
                      .getReporter()
                      .error(new StringDiagnostic(err, CommandLineOrigin.INSTANCE)));
      return builder;
    }

    private static CliParser<Command.Builder> createParser() {
      var header =
          StringUtils.joinLines(
              "Usage: dexsegments [options] <input-files>", " where <input-files> are dex files");
      var parser = new CliParser<Command.Builder>(header);
      return parser
          .option0("--version", "Print the version of r8.", b -> b.setPrintVersion(true))
          .option0("--help", "Print this message.", b -> b.setPrintHelp(true), "-h")
          .option0("--csv", "Print segments in csv format.", b -> b.setCsv(true))
          .positional((b, arg) -> b.addProgramFiles(Paths.get(arg)));
    }

    private Command(AndroidApp inputApp, boolean csv) {
      super(inputApp);
      this.csv = csv;
    }

    private Command(boolean printHelp, boolean printVersion) {
      super(printHelp, printVersion);
      this.csv = false;
    }

    @Override
    InternalOptions getInternalOptions() {
      return new InternalOptions();
    }
  }

  public static void main(String[] args)
      throws IOException, CompilationFailedException, ResourceException {
    Command.Builder builder = Command.parse(args);
    Command cmd = builder.build();
    Result result = run(cmd);
    if (result == null) {
      return;
    }
    if (cmd.csv) {
      System.out.println("\"Name\",\"Size\",\"Items\"");
      result.forEach(
          (key, value) -> {
            System.out.println(
                "\"" + DexSection.typeName(key) + "\", " + value.size + ", " + value.items);
            if (key == Constants.TYPE_TYPE_LIST) {
              // Type items header is just a uint, and each element is a ushort. see
              // https://source.android.com/devices/tech/dalvik/dex-format#type-list.
              int typeItemsSize = (value.size - value.items * 4);
              System.out.println(
                  "\"TypeItems\", " + typeItemsSize + ", " + (typeItemsSize / 2) + "");
            }
          });
    } else {
      System.out.println("Segments in dex application (name: size / items):");
      // This output is parsed by tools/test_framework.py. Check the parsing there when updating.
      result.forEach(
          (key, value) -> {
            System.out.print(
                " - " + DexSection.typeName(key) + ": " + value.size + " / " + value.items);
            if (key == Constants.TYPE_TYPE_LIST) {
              // Type items header is just a uint, and each element is a ushort. see
              // https://source.android.com/devices/tech/dalvik/dex-format#type-list.
              int typeItemsSize = (value.size - value.items * 4);
              System.out.print(" (TypeItems: " + typeItemsSize + " / " + (typeItemsSize / 2) + ")");
            }
            System.out.println();
          });
    }
  }

  public static Result runForTesting(Command command) throws IOException, ResourceException {
    return run(command);
  }

  public static Result run(Command command) throws IOException, ResourceException {
    if (command.isPrintHelp()) {
      System.out.println(Command.usageMessage());
      return null;
    }
    if (command.isPrintVersion()) {
      System.out.println("DexSegments (R8) " + Version.LABEL);
      return null;
    }
    return run(command.getInputApp());
  }

  public static Result runForTesting(AndroidApp app) throws IOException, ResourceException {
    return run(app);
  }

  public static Result run(AndroidApp app) throws IOException, ResourceException {
    Int2ReferenceMap<SegmentInfo> result = new Int2ReferenceLinkedOpenHashMap<>();
    for (int benchmark : DexSection.getConstants()) {
      result.put(benchmark, new SegmentInfo());
    }
    for (ProgramResource resource : app.computeAllProgramResources()) {
      DexReader dexReader = new DexReader(resource);
      if (resource.getKind() == Kind.DEX) {
        for (DexSection dexSection : DexParser.parseMapFrom(dexReader)) {
          assert result.containsKey(dexSection.type) : dexSection.typeName();
          SegmentInfo info = result.get(dexSection.type);
          info.increment(dexSection.length, dexSection.size());
        }
      }
    }
    return new Result(result);
  }

  public static class Result {

    Int2ReferenceMap<SegmentInfo> segments;

    Result(Int2ReferenceMap<SegmentInfo> segments) {
      this.segments = segments;
    }

    public SegmentInfo get(int segment) {
      return segments.get(segment);
    }

    public SegmentInfo getCode() {
      return segments.get(Constants.TYPE_CODE_ITEM);
    }

    public SegmentInfo getDebugInfo() {
      return segments.get(Constants.TYPE_DEBUG_INFO_ITEM);
    }

    public void forEach(BiConsumer<? super Integer, ? super SegmentInfo> fn) {
      segments.forEach(fn);
    }
  }

  public static class SegmentInfo {
    private int items;
    private int size;

    SegmentInfo() {
      this.items = 0;
      this.size = 0;
    }

    void increment(int items, int size) {
      this.items += items;
      this.size += size;
    }

    public int getItemCount() {
      return items;
    }

    public int getSegmentSize() {
      return size;
    }
  }
}
