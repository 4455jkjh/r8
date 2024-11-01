// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public class TraceReferencesTestBuilder {

  private final TraceReferencesCommand.Builder builder;
  private final TraceReferencesInspector inspector = new TraceReferencesInspector();
  private final TestState state;

  public TraceReferencesTestBuilder(TestState state) {
    this.builder =
        TraceReferencesCommand.builder(state.getDiagnosticsHandler())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setConsumer(inspector);
    this.state = state;
  }

  public TraceReferencesTestBuilder addLibraryFiles(Collection<Path> files) {
    builder.addLibraryFiles(files);
    return this;
  }

  public TraceReferencesTestBuilder addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  public TraceReferencesTestBuilder addSourceFiles(Collection<Path> files) {
    builder.addSourceFiles(files);
    return this;
  }

  public TraceReferencesTestBuilder addSourceFiles(Path... files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public TraceReferencesTestBuilder addTargetFiles(Collection<Path> files) {
    builder.addTargetFiles(files);
    return this;
  }

  public TraceReferencesTestBuilder addTargetFiles(Path... files) {
    return addTargetFiles(Arrays.asList(files));
  }

  public TraceReferencesTestBuilder setConsumer(TraceReferencesConsumer consumer) {
    builder.setConsumer(consumer);
    return this;
  }

  public TraceReferencesTestBuilder addInnerClassesAsSourceClasses(Class<?> clazz)
      throws IOException {
    builder.addSourceFiles(
        ZipBuilder.builder(state.getNewTempFolder().resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFilesForInnerClasses(clazz))
            .build());
    return this;
  }

  public TraceReferencesTestBuilder addInnerClassesAsTargetClasses(Class<?> clazz)
      throws IOException {
    builder.addTargetFiles(
        ZipBuilder.builder(state.getNewTempFolder().resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFilesForInnerClasses(clazz))
            .build());
    return this;
  }

  public TraceReferencesTestResult trace() throws CompilationFailedException {
    TraceReferences.run(builder.build());
    return new TraceReferencesTestResult(inspector);
  }

  public <E extends Exception> TraceReferencesTestResult traceWithExpectedDiagnostics(
      DiagnosticsConsumer<E> diagnosticsConsumer) throws CompilationFailedException, E {
    TestDiagnosticMessages diagnosticsHandler = state.getDiagnosticsMessages();
    try {
      TraceReferencesTestResult result = trace();
      diagnosticsConsumer.accept(diagnosticsHandler);
      return result;
    } catch (CompilationFailedException e) {
      diagnosticsConsumer.accept(diagnosticsHandler);
      throw e;
    }
  }
}
