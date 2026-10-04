// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class LibraryAnalyzerTestBuilder {

  public enum AarOrJar {
    AAR,
    JAR
  }

  private final LibraryAnalyzerCommand.Builder commandBuilder;
  private final TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
  private final TemporaryFolder temp;

  private AarOrJar aarOrJar;
  private AndroidApp.Builder androidAppBuilder = AndroidApp.builder();
  private List<String> keepRules = new ArrayList<>();
  private List<Collection<String>> secondaryKeepRules = new ArrayList<>();

  private LibraryAnalyzerTestBuilder(TemporaryFolder temp) {
    this.commandBuilder = LibraryAnalyzerCommand.builder(diagnostics);
    this.temp = temp;
  }

  public static LibraryAnalyzerTestBuilder create(TemporaryFolder temp) {
    return new LibraryAnalyzerTestBuilder(temp);
  }

  public <E extends Exception> LibraryAnalyzerTestBuilder apply(
      ThrowingConsumer<LibraryAnalyzerTestBuilder, E> fn) throws E {
    fn.accept(this);
    return this;
  }

  public LibraryAnalyzerTestBuilder addDefaultLibrary() {
    commandBuilder.addLibraryPath(ToolHelper.getMostRecentAndroidJar());
    return this;
  }

  public LibraryAnalyzerTestBuilder addKeepRules(String... keepRules) {
    return addKeepRules(Arrays.asList(keepRules));
  }

  public LibraryAnalyzerTestBuilder addKeepRules(Collection<String> keepRules) {
    this.keepRules.addAll(keepRules);
    return this;
  }

  public LibraryAnalyzerTestBuilder addSecondaryAarOrJar(String... keepRules) {
    return addSecondaryAarOrJar(Arrays.asList(keepRules));
  }

  public LibraryAnalyzerTestBuilder addSecondaryAarOrJar(Collection<String> keepRules) {
    this.secondaryKeepRules.add(keepRules);
    return this;
  }

  public LibraryAnalyzerTestBuilder addProgramClasses(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      androidAppBuilder.addProgramFile(
          ToolHelper.getClassFileForTestClass(clazz),
          Collections.singleton(javaTypeToDescriptor(clazz.getTypeName())));
    }
    return this;
  }

  public LibraryAnalyzerTestBuilder setAar() {
    return setAarOrJar(AarOrJar.AAR);
  }

  public LibraryAnalyzerTestBuilder setAarOrJar(AarOrJar aarOrJar) {
    assertNull(this.aarOrJar);
    this.aarOrJar = aarOrJar;
    return this;
  }

  public LibraryAnalyzerTestBuilder setJar() {
    return setAarOrJar(AarOrJar.JAR);
  }

  public LibraryAnalyzerTestBuilder setMinApi(AndroidApiLevel minApi) {
    commandBuilder.setMinApiLevel(minApi.getMajor(), minApi.getMinor());
    return this;
  }

  public LibraryAnalyzerTestBuilder setOutputConsumer(ByteArrayConsumer<?> outputConsumer) {
    commandBuilder.setOutputConsumer(outputConsumer);
    return this;
  }

  public LibraryAnalyzerCompileResult compile() throws CompilationFailedException {
    return compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  public <E extends Exception> LibraryAnalyzerCompileResult compileWithExpectedDiagnostics(
      DiagnosticsConsumer<E> diagnosticsConsumer) throws CompilationFailedException, E {
    assertNotNull("Must call setAar() or setJar() to specify input type.", aarOrJar);
    Box<LibraryAnalyzerResult> LibraryAnalyzerResult = new Box<>();
    if (aarOrJar == AarOrJar.AAR) {
      createAars().forEach(commandBuilder::addAarPath);
    } else {
      createJars().forEach(commandBuilder::addJarPath);
    }
    LibraryAnalyzerCommand command =
        commandBuilder.setInternalOutputConsumer(LibraryAnalyzerResult::set).build();
    LibraryAnalyzer.run(command);
    diagnosticsConsumer.accept(diagnostics);
    return new LibraryAnalyzerCompileResult(LibraryAnalyzerResult.get());
  }

  private List<Path> createAars() {
    List<Path> aarPaths = new ArrayList<>();
    aarPaths.add(createAar(androidAppBuilder.build(), keepRules));
    androidAppBuilder = null;
    keepRules = null;
    for (Collection<String> secondaryKeepRule : secondaryKeepRules) {
      aarPaths.add(createAar(AndroidApp.builder().build(), secondaryKeepRule));
    }
    secondaryKeepRules = null;
    return aarPaths;
  }

  private Path createAar(AndroidApp app, Collection<String> keepRules) {
    try {
      Path aarDir = temp.newFolder().toPath();
      Path classesJarPath = aarDir.resolve("classes.jar");
      app.writeToZipForTesting(classesJarPath, OutputMode.ClassFile);

      if (!keepRules.isEmpty()) {
        Path proguardTxtPath = aarDir.resolve("proguard.txt");
        Files.write(proguardTxtPath, keepRules, StandardCharsets.UTF_8);
      }

      Path aarPath = temp.newFolder().toPath().resolve("lib.aar");
      ZipUtils.zip(aarPath, aarDir);
      return aarPath;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<Path> createJars() {
    List<Path> jarPaths = new ArrayList<>();
    jarPaths.add(createJar(androidAppBuilder.build(), keepRules));
    androidAppBuilder = null;
    keepRules = null;
    for (Collection<String> secondaryKeepRule : secondaryKeepRules) {
      jarPaths.add(createJar(AndroidApp.builder().build(), secondaryKeepRule));
    }
    secondaryKeepRules = null;
    return jarPaths;
  }

  private Path createJar(AndroidApp app, Collection<String> keepRules) {
    try {
      Path jarDir = temp.newFolder().toPath();
      app.writeToDirectory(jarDir, OutputMode.ClassFile);

      if (!keepRules.isEmpty()) {
        Path libProPath = jarDir.resolve("META-INF/proguard/lib.pro");
        Files.createDirectories(libProPath.getParent());
        Files.write(libProPath, keepRules, StandardCharsets.UTF_8);
      }

      Path jarPath = temp.newFolder().toPath().resolve("lib.jar");
      ZipUtils.zip(jarPath, jarDir);
      return jarPath;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
