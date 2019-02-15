// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

// The type arguments R8Command, Builder is not relevant for running Proguard.
public class ProguardTestBuilder
    extends TestShrinkerBuilder<
        R8Command, Builder, ProguardTestCompileResult, ProguardTestRunResult, ProguardTestBuilder> {

  // TODO(sgjesse): Share this with JvmTestBuilder.
  private static class ClassFileResource implements ProgramResource {

    private final Path file;
    private final String descriptor;
    private final Origin origin;

    ClassFileResource(Class<?> clazz) {
      this(
          ToolHelper.getClassFileForTestClass(clazz),
          DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()));
    }

    ClassFileResource(Path file, String descriptor) {
      this.file = file;
      this.descriptor = descriptor;
      origin = new PathOrigin(file);
    }

    @Override
    public Kind getKind() {
      return Kind.CF;
    }

    @Override
    public InputStream getByteStream() throws ResourceException {
      try {
        return Files.newInputStream(file);
      } catch (IOException e) {
        throw new ResourceException(getOrigin(), e);
      }
    }

    @Override
    public Set<String> getClassDescriptors() {
      return Collections.singleton(descriptor);
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }
  }

  // TODO(sgjesse): Share this with JvmTestBuilder.
  private static class ClassFileResourceProvider implements ProgramResourceProvider {

    private final List<ProgramResource> resources;

    public ClassFileResourceProvider(List<ProgramResource> resources) {
      this.resources = resources;
    }

    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      return resources;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return null;
    }
  }

  // Ordered list of injar entries.
  private List<Path> injars = new ArrayList<>();

  // Ordered list of libraryjar entries.
  private List<Path> libraryjars = new ArrayList<>();

  // Proguard configuration file lines.
  private List<String> config = new ArrayList<>();

  // Additional Proguard configuration files.
  private List<Path> proguardConfigFiles = new ArrayList<>();

  private ProguardTestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static ProguardTestBuilder create(TestState state) {
    return new ProguardTestBuilder(state, R8Command.builder(), Backend.CF);
  }

  @Override
  ProguardTestBuilder self() {
    return this;
  }

  @Override
  ProguardTestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    try {
      Path proguardOutputFolder = getState().getNewTempFolder();
      Path outJar = proguardOutputFolder.resolve("output.jar");
      Path configFile = proguardOutputFolder.resolve("configuration.txt");
      Path mapFile = proguardOutputFolder.resolve("mapping.txt");
      FileUtils.writeTextFile(configFile, config);
      List<String> command = new ArrayList<>();
      command.add(ToolHelper.getProguard6Script());
      // Without -forceprocessing Proguard just checks the creation time on the in/out jars.
      command.add("-forceprocessing");
      for (Path injar : injars) {
        command.add("-injars");
        command.add(injar.toString());
      }
      for (Path libraryjar : libraryjars) {
        command.add("-libraryjars");
        command.add(libraryjar.toString());
      }
      if (libraryjars.isEmpty()) {
        command.add("-libraryjars");
        // TODO(sgjesse): Add support for running with Android Jar.
        // command.add(ToolHelper.getAndroidJar(AndroidApiLevel.P).toString());
        command.add(ToolHelper.getJava8RuntimeJar().toString());
      }
      command.add("-include");
      command.add(configFile.toString());
      for (Path proguardConfigFile : proguardConfigFiles) {
        command.add("-include");
        command.add(proguardConfigFile.toAbsolutePath().toString());
      }
      command.add("-outjar");
      command.add(outJar.toString());
      command.add("-printmapping");
      command.add(mapFile.toString());
      if (!enableTreeShaking) {
        command.add("-dontshrink");
      }
      if (!enableMinification) {
        command.add("-dontobfuscate");
      }
      ProcessBuilder pbuilder = new ProcessBuilder(command);
      ProcessResult result = ToolHelper.runProcess(pbuilder);
      if (result.exitCode != 0) {
        throw new CompilationFailedException(result.toString());
      }
      String proguardMap =
          Files.exists(mapFile) ? FileUtils.readTextFile(mapFile, Charsets.UTF_8) : "";
      return new ProguardTestCompileResult(getState(), outJar, proguardMap);
    } catch (IOException e) {
      throw new CompilationFailedException(e);
    }
  }

  @Override
  public ProguardTestBuilder addDataEntryResources(DataEntryResource... resources) {
    throw new Unimplemented("No support for adding data entry resources");
  }

  @Override
  public ProguardTestBuilder addProgramClasses(Collection<Class<?>> classes) {
    // Adding a collection of classes will build a jar of exactly those classes so that no other
    // classes are made available via a too broad classpath directory.
    List<ProgramResource> resources = ListUtils.map(classes, ClassFileResource::new);
    AndroidApp build =
        AndroidApp.builder()
            .addProgramResourceProvider(new ClassFileResourceProvider(resources))
            .build();
    Path out;
    try {
      out = getState().getNewTempFolder().resolve("out.zip");
      build.writeToZip(out, OutputMode.ClassFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    injars.add(out);
    return self();
  }

  @Override
  public ProguardTestBuilder addProgramFiles(Collection<Path> files) {
    for (Path file : files) {
      if (FileUtils.isJarFile(file)) {
        injars.add(file);
      } else {
        throw new Unimplemented(
            "No support for adding class files directly (we need to compute the descriptor)");
      }
    }
    return self();
  }

  @Override
  public ProguardTestBuilder addProgramClassFileData(Collection<byte[]> classes) {
    throw new Unimplemented(
        "No support for adding class files directly (we need to compute the descriptor)");
  }

  @Override
  public ProguardTestBuilder addKeepRuleFiles(List<Path> proguardConfigFiles) {
    this.proguardConfigFiles.addAll(proguardConfigFiles);
    return self();
  }

  @Override
  public ProguardTestBuilder addKeepRules(Collection<String> rules) {
    config.addAll(rules);
    return self();
  }
  @Override
  public ProguardTestBuilder addLibraryFiles(Collection<Path> files) {
    for (Path file : files) {
      if (FileUtils.isJarFile(file)) {
        libraryjars.add(file);
      } else {
        throw new Unimplemented(
            "No support for adding class files directly (we need to compute the descriptor)");
      }
    }
    return self();
  }

  @Override
  public ProguardTestBuilder setProgramConsumer(ProgramConsumer programConsumer) {
    throw new Unimplemented("No support for program consumer");
  }

  @Override
  public ProguardTestBuilder setMinApi(AndroidApiLevel minApiLevel) {
    throw new Unimplemented("No support for setting min api");
  }

  @Override
  public ProguardTestBuilder addMainDexListFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding main dex list files");
  }

  @Override
  public ProguardTestBuilder setMainDexListConsumer(StringConsumer consumer) {
    throw new Unimplemented("No support for main dex list consumer");
  }

  @Override
  public ProguardTestBuilder setMode(CompilationMode mode) {
    throw new Unimplemented("No support for setting compilation mode");
  }

  @Override
  public ProguardTestBuilder noDesugaring() {
    throw new Unimplemented("No support for disabling desugaring");
  }

  @Override
  public DebugTestConfig debugConfig() {
    throw new Unimplemented("No support for debug config");
  }

  @Override
  public ProguardTestBuilder addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    throw new Unimplemented("No support for changing internal options");
  }
}
