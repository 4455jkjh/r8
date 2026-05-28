// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AssemblyWriter;
import com.android.tools.r8.graph.DexByteCodeWriter;
import com.android.tools.r8.graph.DexByteCodeWriter.OutputStreamProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.SmaliWriter;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ProgramResourceProviderUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.ConsumerUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class Disassemble {
  public static class DisassembleCommand extends BaseCommand {

    private final Path outputPath;
    private final StringResource proguardMap;

    public static class Builder extends BaseCommand.Builder<DisassembleCommand, Builder> {

      private Path outputPath = null;
      private Path proguardMapFile = null;
      private boolean useSmali = false;
      private boolean allInfo = false;
      private boolean noCode = false;
      private boolean useIr;

      private Set<ClassReference> classReferences = null;
      private Set<FieldReference> fieldReferences = null;
      private Set<MethodReference> methodReferences = null;

      @Override
      Builder self() {
        return this;
      }

      public Builder setProguardMapFile(Path path) {
        proguardMapFile = path;
        return this;
      }

      public Path getOutputPath() {
        return outputPath;
      }

      public Builder setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
      }

      public Builder setAllInfo(boolean allInfo) {
        this.allInfo = allInfo;
        return this;
      }

      public Builder setUseSmali(boolean useSmali) {
        this.useSmali = useSmali;
        return this;
      }

      public Builder setUseIr(boolean useIr) {
        this.useIr = useIr;
        return this;
      }

      public Builder setNoCode(boolean noCode) {
        this.noCode = noCode;
        return this;
      }

      public Builder addClassReference(ClassReference classReference) {
        if (classReferences == null) {
          classReferences = new HashSet<>();
        }
        classReferences.add(classReference);
        return this;
      }

      public Builder addFieldReference(FieldReference fieldReference) {
        if (fieldReferences == null) {
          fieldReferences = new HashSet<>();
        }
        fieldReferences.add(fieldReference);
        return this;
      }

      public Builder addMethodReference(MethodReference methodReference) {
        if (methodReferences == null) {
          methodReferences = new HashSet<>();
        }
        methodReferences.add(methodReference);
        return this;
      }

      @Override
      protected DisassembleCommand makeCommand() {
        // If printing versions ignore everything else.
        if (isPrintHelp() || isPrintVersion()) {
          return new DisassembleCommand(isPrintHelp(), isPrintVersion());
        }
        return new DisassembleCommand(
            getAppBuilder().build(),
            getOutputPath(),
            proguardMapFile == null ? null : StringResource.fromFile(proguardMapFile),
            allInfo,
            useSmali,
            useIr,
            noCode,
            classReferences,
            fieldReferences,
            methodReferences);
      }
    }

    static String usageMessage() {
      return createParser().getUsageMessage();
    }

    private final boolean allInfo;
    private final boolean useSmali;
    private final boolean useIr;
    private final boolean noCode;
    private final Set<ClassReference> classReferences;
    private final Set<FieldReference> fieldReferences;
    private final Set<MethodReference> methodReferences;

    public static Builder builder() {
      return new Builder();
    }

    public static Builder parse(String[] args) {
      Builder builder = builder();
      createParser()
          .parse(args, builder, err -> builder.getReporter().error(new StringDiagnostic(err)));
      return builder;
    }

    private static CliParser<Builder> createParser() {
      var header =
          StringUtils.joinLines(
              "Usage: disasm [options] <input-files>",
              " where <input-files> are dex files",
              " and options are:");
      var parser = new CliParser<Builder>(header);
      return parser
          .option0("--all", "Include all information in disassembly.", b -> b.setAllInfo(true))
          .option0("--smali", "Disassemble using smali syntax.", b -> b.setUseSmali(true))
          .option0("--ir", "Print IR before and after optimization.", b -> b.setUseIr(true))
          .option0("--nocode", "No printing of code objects.", b -> b.setNoCode(true))
          .option1(
              "--pg-map",
              "<file>",
              "Proguard map <file> for mapping names.",
              (b, arg) -> b.setProguardMapFile(Paths.get(arg)))
          .option1(
              "--output",
              "<file/dir>",
              "Specify a file or directory to write to.",
              (b, arg) -> b.setOutputPath(Paths.get(arg)))
          .option1(
              "--class",
              "<descriptor>",
              "Only disassemble the given class (e.g., Lcom/example/Class;).",
              (b, arg) -> b.addClassReference(Reference.classFromDescriptor(arg)))
          .option1(
              "--field",
              "<descriptor>",
              "Only disassemble the given field (e.g., Lcom/example/Class;->field:I).",
              (b, arg) -> b.addFieldReference(FieldReferenceUtils.parseSmaliString(arg)))
          .option1(
              "--method",
              "<descriptor>",
              "Only disassemble the given method (e.g., Lcom/example/Class;->method()V).",
              (b, arg) -> b.addMethodReference(MethodReferenceUtils.parseSmaliString(arg)))
          .option0("--version", "Print the version of r8.", b -> b.setPrintVersion(true))
          .option0("--help", "Print this message.", b -> b.setPrintHelp(true))
          .positional((b, arg) -> b.addProgramFiles(Paths.get(arg)));
    }

    private DisassembleCommand(
        AndroidApp inputApp,
        Path outputPath,
        StringResource proguardMap,
        boolean allInfo,
        boolean useSmali,
        boolean useIr,
        boolean noCode,
        Set<ClassReference> classReferences,
        Set<FieldReference> fieldReferences,
        Set<MethodReference> methodReferences) {
      super(inputApp);
      this.outputPath = outputPath;
      this.proguardMap = proguardMap;
      this.allInfo = allInfo;
      this.useSmali = useSmali;
      this.useIr = useIr;
      this.noCode = noCode;
      this.classReferences = classReferences;
      this.fieldReferences = fieldReferences;
      this.methodReferences = methodReferences;
    }

    private DisassembleCommand(boolean printHelp, boolean printVersion) {
      super(printHelp, printVersion);
      outputPath = null;
      proguardMap = null;
      allInfo = false;
      useSmali = false;
      useIr = false;
      noCode = false;
      classReferences = null;
      fieldReferences = null;
      methodReferences = null;
    }

    public Path getOutputPath() {
      return outputPath;
    }

    public boolean useSmali() {
      return useSmali;
    }

    public boolean useIr() {
      return useIr;
    }

    public boolean noCode() {
      return noCode;
    }

    public Set<ClassReference> getClassReferences() {
      return classReferences;
    }

    public Set<FieldReference> getFieldReferences() {
      return fieldReferences;
    }

    public Set<MethodReference> getMethodReferences() {
      return methodReferences;
    }

    @Override
    InternalOptions getInternalOptions() {
      InternalOptions internal = new InternalOptions();
      internal.useSmaliSyntax = useSmali;
      internal.readDebugSetFileEvent = true;
      return internal;
    }
  }

  public static void main(String[] args)
      throws IOException, ExecutionException, CompilationFailedException {
    DisassembleCommand.Builder builder = DisassembleCommand.parse(args);
    DisassembleCommand command = builder.build();
    if (command.isPrintHelp()) {
      System.out.println(DisassembleCommand.usageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("Disassemble (R8) " + Version.LABEL);
      return;
    }
    disassemble(command);
  }

  public static void disassemble(DisassembleCommand command) {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    try (OutputWriter outputWriter = getOutputWriter(command)) {
      for (ProgramResource computeAllProgramResource : app.computeAllProgramResources()) {
        disassembleResource(command, outputWriter, computeAllProgramResource, options);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OutputWriter getOutputWriter(DisassembleCommand command) throws Exception {
    if (command.getOutputPath() == null) {
      return SystemOutOutputWriter.create();
    } else if (Files.isDirectory(command.getOutputPath())) {
      return DirectoryWriter.create(
          command.getOutputPath(),
          command.useSmali() ? SmaliWriter.getFileEnding() : AssemblyWriter.getFileEnding());
    } else {
      return FileWriter.create(command.getOutputPath());
    }
  }

  private static void disassembleResource(
      DisassembleCommand command,
      OutputWriter outputWriter,
      ProgramResource programResource,
      InternalOptions options)
      throws IOException {
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    try {
      LazyLoadedDexApplication application =
          new ApplicationReader(
                  AndroidApp.builder()
                      .addProgramResourceProvider(
                          ProgramResourceProviderUtils.createSingleResourceProvider(
                              programResource))
                      .build(),
                  options,
                  Timing.empty())
              .read(command.proguardMap, executor);
      DexByteCodeWriter writer =
          command.useSmali()
              ? new SmaliWriter(
                  application,
                  options,
                  command.getClassReferences(),
                  command.getFieldReferences(),
                  command.getMethodReferences())
              : new AssemblyWriter(
                  application,
                  options,
                  command.allInfo,
                  command.useIr(),
                  !command.noCode(),
                  command.getClassReferences(),
                  command.getFieldReferences(),
                  command.getMethodReferences());
      if (outputWriter.extractMarkers()) {
        writer.writeMarkers(
            outputWriter.outputStreamProvider(application.getProguardMap()).get(null));
      }
      writer.write(
          outputWriter.outputStreamProvider(application.getProguardMap()), outputWriter.closer());
    } finally {
      executor.shutdown();
    }
  }

  private interface OutputWriter extends Closeable {
    boolean extractMarkers();

    OutputStreamProvider outputStreamProvider(ClassNameMapper classNameMapper);

    Consumer<PrintStream> closer();
  }

  private static class SystemOutOutputWriter implements OutputWriter {

    @Override
    public boolean extractMarkers() {
      return true;
    }

    @Override
    public OutputStreamProvider outputStreamProvider(ClassNameMapper classNameMapper) {
      return clazz -> System.out;
    }

    @Override
    public Consumer<PrintStream> closer() {
      return ConsumerUtils.emptyConsumer();
    }

    static SystemOutOutputWriter create() {
      return new SystemOutOutputWriter();
    }

    @Override
    public void close() {
      // Intentionally empty.
    }
  }

  private static class DirectoryWriter implements OutputWriter {

    private final Path parent;
    private final String fileEnding;

    public DirectoryWriter(Path parent, String fileEnding) {
      this.parent = parent;
      this.fileEnding = fileEnding;
    }

    @Override
    public boolean extractMarkers() {
      return false;
    }

    @Override
    public OutputStreamProvider outputStreamProvider(ClassNameMapper classNameMapper) {
      return DexByteCodeWriter.oneFilePerClass(classNameMapper, parent, fileEnding);
    }

    @Override
    public Consumer<PrintStream> closer() {
      return PrintStream::close;
    }

    private static DirectoryWriter create(Path path, String fileEnding) throws IOException {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      return new DirectoryWriter(path, fileEnding);
    }

    @Override
    public void close() {
      // Intentionally empty.
    }
  }

  private static class FileWriter implements OutputWriter {

    private final PrintStream fileStream;

    private FileWriter(PrintStream fileStream) {
      this.fileStream = fileStream;
    }

    @Override
    public boolean extractMarkers() {
      return true;
    }

    @Override
    public OutputStreamProvider outputStreamProvider(ClassNameMapper classNameMapper) {
      return clazz -> fileStream;
    }

    @Override
    public Consumer<PrintStream> closer() {
      // Per entry close per disassembled class is ignored to keep the print stream open until
      // everything has been written.
      return ConsumerUtils.emptyConsumer();
    }

    private static FileWriter create(Path path) throws IOException {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      return new FileWriter(new PrintStream(Files.newOutputStream(path)));
    }

    @Override
    public void close() {
      fileStream.flush();
      fileStream.close();
    }
  }
}
