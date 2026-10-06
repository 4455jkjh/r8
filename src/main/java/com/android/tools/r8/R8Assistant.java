// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.assistant.ReflectiveInstrumentation;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This is an experimental API for injecting reflective identification callbacks into dex code. This
 * API is subject to change.
 */
@KeepForApi
public class R8Assistant {

  public static void run(R8AssistantCommand command) throws CompilationFailedException {
    InternalOptions options = command.getInternalOptions();
    runForTest(command, options);
  }

  public static void runForTest(R8AssistantCommand command, InternalOptions options)
      throws CompilationFailedException {
    ExceptionUtils.withCompilationHandler(
        options.reporter,
        () -> runInternal(command, options, ThreadUtils.getExecutorService(options)));
  }

  public static void run(R8AssistantCommand command, ExecutorService executor)
      throws CompilationFailedException {
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withD8CompilationHandler(
        command.getReporter(),
        () -> {
          runInternal(command, options, executor);
        });
  }

  static void runInternal(
      R8AssistantCommand command, InternalOptions options, ExecutorService executorService)
      throws IOException {
    Timing timing = Timing.createRoot("R8 Assistant " + Version.LABEL, options, executorService);
    try {
      ApplicationReader applicationReader =
          new ApplicationReader(command.getInputApp(), options, timing);
      LazyLoadedDexApplication app = applicationReader.read(executorService);
      if (app.getFlags().hasReadProgramClassFromCf()) {
        throw options.reporter.fatalError("R8 assistant does not support class file input.");
      }
      assert !command.getInputApp().hasMainDexList();
      AppInfo appInfo =
          AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forSingleOutputMode());
      AppView<AppInfo> appView = AppView.createForD8(appInfo);
      PrimaryD8L8IRConverter converter = new PrimaryD8L8IRConverter(appView, timing);
      ReflectiveInstrumentation reflectiveInstrumentation =
          new ReflectiveInstrumentation(
              appView, converter, timing, command.getReflectiveReceiverDescriptor());
      reflectiveInstrumentation.instrumentClasses();
      if (command.getReflectiveReceiverDescriptor() != null) {
        reflectiveInstrumentation.updateReflectiveReceiver();
      }
      SyntheticFinalization.finalize(appView, timing, executorService);
      ApplicationWriter writer = ApplicationWriter.create(appView, options.getMarker());
      writer.write(executorService, timing);
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      options.signalFinishedToConsumers();
      if (options.isPrintTimesReportingEnabled()) {
        timing.report();
      }
    }
  }

  public static class Options {
    private Path inputApk;
    private Path outputApk;
    private Path runtimeDex;
    private Path tempDir;

    public Path getInputApk() {
      return inputApk;
    }

    public Options setInputApk(Path inputApk) {
      this.inputApk = inputApk;
      return this;
    }

    public Path getOutputApk() {
      return outputApk;
    }

    public Options setOutputApk(Path outputApk) {
      this.outputApk = outputApk;
      return this;
    }

    public Path getTempDir() {
      return tempDir;
    }

    public Options setTempDir(Path tempDir) {
      this.tempDir = tempDir;
      return this;
    }

    public Path getRuntimeDex() {
      return runtimeDex;
    }

    public Options setRuntimeDex(Path runtimeDex) {
      this.runtimeDex = runtimeDex;
      return this;
    }

    public void validate() {
      checkSpecified(inputApk, "Input APK");
      checkSpecified(outputApk, "Output APK");
      checkIsFile(inputApk, "Input APK");
      checkIsFileIfSpecified(runtimeDex, "Runtime DEX");
      if (Files.isDirectory(outputApk)) {
        throw new IllegalArgumentException("Output APK is a directory: " + outputApk);
      }
      if (inputApk.toAbsolutePath().normalize().equals(outputApk.toAbsolutePath().normalize())) {
        throw new IllegalArgumentException(
            "Input and output APK paths must be different: " + inputApk);
      }
      if (tempDir != null && Files.exists(tempDir) && !Files.isDirectory(tempDir)) {
        throw new IllegalArgumentException("Temp directory is not a directory: " + tempDir);
      }
    }

    private static void checkSpecified(Path path, String description) {
      if (path == null) {
        throw new IllegalArgumentException(description + " must be specified.");
      }
    }

    private static void checkIsFileIfSpecified(Path path, String description) {
      if (path != null) {
        checkIsFile(path, description);
      }
    }

    private static void checkIsFile(Path path, String description) {
      if (!Files.isRegularFile(path)) {
        throw new IllegalArgumentException(
            description + " does not exist or is not a file: " + path);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      printUsage();
      return;
    }
    Options options = parseArgs(args);
    if (options == null) {
      return;
    }
    runOnApk(options);
  }

  private static Options parseArgs(String[] args) {
    Options options = new Options();
    List<String> positional = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-h") || arg.equals("--help")) {
        printUsage();
        return null;
      } else if (arg.equals("--output") || arg.equals("-o")) {
        options.setOutputApk(nextPathArg(args, ++i, arg));
      } else if (arg.equals("--input") || arg.equals("-i")) {
        options.setInputApk(nextPathArg(args, ++i, arg));
      } else if (arg.equals("--runtime") || arg.equals("-r")) {
        options.setRuntimeDex(nextPathArg(args, ++i, arg));
      } else if (arg.equals("--temp-dir")) {
        options.setTempDir(nextPathArg(args, ++i, arg));
      } else if (arg.startsWith("-")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      } else {
        positional.add(arg);
      }
    }
    applyPositionalArgs(options, positional);
    return options;
  }

  /** Positional arguments are {@code [<input.apk> [<output.apk>]]} unless given as options. */
  private static void applyPositionalArgs(Options options, List<String> positional) {
    if (options.getInputApk() == null && !positional.isEmpty()) {
      options.setInputApk(Paths.get(positional.remove(0)));
    }
    if (options.getOutputApk() == null && !positional.isEmpty()) {
      options.setOutputApk(Paths.get(positional.remove(0)));
    }
    if (!positional.isEmpty()) {
      throw new IllegalArgumentException("Unexpected arguments: " + positional);
    }
  }

  private static Path nextPathArg(String[] args, int index, String flag) {
    if (index >= args.length) {
      throw new IllegalArgumentException("Missing value for " + flag);
    }
    return Paths.get(args[index]);
  }

  public static void runOnApk(Options options) throws Exception {
    options.validate();
    Path instrumentedDexZip = createTempDexZip(options.getTempDir());
    try {
      instrumentApkDex(options, instrumentedDexZip);
      replaceApkDex(options.getInputApk(), instrumentedDexZip, options.getOutputApk());
    } finally {
      Files.deleteIfExists(instrumentedDexZip);
    }
  }

  private static Path createTempDexZip(Path tempDir) throws IOException {
    if (tempDir == null) {
      return Files.createTempFile("r8_assistant_dex_", ".zip");
    }
    Files.createDirectories(tempDir);
    return Files.createTempFile(tempDir, "r8_assistant_dex_", ".zip");
  }

  /** Instruments the top-level classes*.dex files of the input APK and writes them to a zip. */
  private static void instrumentApkDex(Options options, Path outputDexZip)
      throws CompilationFailedException {
    R8AssistantCommand.Builder builder =
        R8AssistantCommand.builder()
            .addProgramResourceProvider(
                ArchiveProgramResourceProvider.fromArchive(
                    options.getInputApk(), R8Assistant::isDexEntry))
            .setOutput(outputDexZip, OutputMode.DexIndexed);
    if (options.getRuntimeDex() != null) {
      builder.setAssistantRuntime(options.getRuntimeDex());
    }
    run(builder.build());
  }

  /**
   * Writes a copy of the input APK with its DEX files replaced by the instrumented ones. The old
   * signature is dropped since it is no longer valid; the output must be re-signed.
   */
  private static void replaceApkDex(Path inputApk, Path dexZip, Path outputApk) throws IOException {
    Path parentDir = outputApk.toAbsolutePath().getParent();
    if (parentDir != null) {
      Files.createDirectories(parentDir);
    }
    try (ZipOutputStream out =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputApk)))) {
      ZipUtils.iter(
          inputApk,
          (entry, input) -> {
            String name = entry.getName();
            if (!isDexEntry(name) && !isSignatureFile(name)) {
              copyZipEntry(entry, input, out);
            }
          });
      ZipUtils.iter(dexZip, (entry, input) -> copyZipEntry(entry, input, out));
    }
  }

  private static boolean isDexEntry(String name) {
    return !name.contains("/") && ZipUtils.isDexFile(name);
  }

  private static boolean isSignatureFile(String name) {
    String upperName = name.toUpperCase(Locale.ROOT);
    return upperName.startsWith("META-INF/")
        && (upperName.endsWith(".SF")
            || upperName.endsWith(".RSA")
            || upperName.endsWith(".DSA")
            || upperName.endsWith(".EC")
            || upperName.equals("META-INF/MANIFEST.MF"));
  }

  private static void copyZipEntry(ZipEntry entry, InputStream input, ZipOutputStream out)
      throws IOException {
    out.putNextEntry(copyEntryHeader(entry));
    ByteStreams.copy(input, out);
    out.closeEntry();
  }

  /**
   * Copies the header of a zip entry, keeping STORED (uncompressed) entries uncompressed, since
   * Android requires e.g. resources.arsc and native libraries to be stored.
   */
  private static ZipEntry copyEntryHeader(ZipEntry entry) {
    ZipEntry newEntry = new ZipEntry(entry.getName());
    newEntry.setMethod(entry.getMethod());
    newEntry.setTime(entry.getTime());
    if (entry.getExtra() != null) {
      newEntry.setExtra(entry.getExtra());
    }
    if (entry.getMethod() == ZipEntry.STORED) {
      if (entry.getSize() >= 0 && entry.getCrc() != -1) {
        newEntry.setSize(entry.getSize());
        newEntry.setCompressedSize(entry.getSize());
        newEntry.setCrc(entry.getCrc());
      } else {
        newEntry.setMethod(ZipEntry.DEFLATED);
      }
    }
    return newEntry;
  }

  private static void printUsage() {
    System.out.println("Usage: r8-assistant <input.apk> --output <output.apk> [options]");
    System.out.println("Options:");
    System.out.println("  --output, -o <file>   Output path for instrumented APK");
    System.out.println("  --input, -i <file>    Input APK file");
    System.out.println("  --runtime, -r <path>  Path to assistant runtime DEX (optional)");
    System.out.println("  --temp-dir <dir>      Temporary directory for extraction");
    System.out.println("  --help, -h            Show this help message");
  }
}
