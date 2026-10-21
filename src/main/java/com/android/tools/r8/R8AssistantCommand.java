// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.UncheckedApiLevel;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;

/**
 * This is an experimental API for injecting reflective identification callbacks into dex code. This
 * API is subject to change.
 */
@KeepForApi
public class R8AssistantCommand extends BaseCompilerCommand {

  private static final String ASSISTANT_RUNTIME_DEX = "assistant-runtime.dex";

  private final String reflectiveReceiverDescriptor;

  @SuppressWarnings("InconsistentOverloads")
  private R8AssistantCommand(
      AndroidApp app,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      UncheckedApiLevel minApiLevel,
      Reporter reporter,
      String reflectiveReceiverDescriptor,
      Path apiDatabasePath) {
    super(
        app,
        mode,
        programConsumer,
        StringConsumer.emptyConsumer(),
        minApiLevel,
        reporter,
        DesugarState.ON,
        false,
        false,
        (a, b) -> true,
        Collections.emptyList(),
        Collections.emptyList(),
        ThreadUtils.NOT_SPECIFIED,
        DumpInputFlags.noDump(),
        null,
        null,
        false,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        false,
        apiDatabasePath);
    this.reflectiveReceiverDescriptor = reflectiveReceiverDescriptor;
  }

  /** Deprecation: Use {@link #builder()} or {@link #builder(DiagnosticsHandler)}. */
  @Deprecated
  public R8AssistantCommand(
      AndroidApp app,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      int minApiLevelMajor,
      Reporter reporter,
      String reflectiveReceiverDescriptor,
      Path apiDatabasePath) {
    this(
        app,
        mode,
        programConsumer,
        new UncheckedApiLevel(minApiLevelMajor),
        reporter,
        reflectiveReceiverDescriptor,
        apiDatabasePath);
  }

  public static Builder builder(DiagnosticsHandler reporter) {
    return new Builder(reporter);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  InternalOptions getInternalOptions() {
    DexItemFactory factory = new DexItemFactory();
    InternalOptions options = new InternalOptions(factory, getReporter());
    options.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getUncheckedMinApiLevel()));
    options.apiModelingOptions().apiDatabasePath = getApiDatabasePath();
    options.passthroughDexCode = true;
    options.enableMainDexListCheck = false;
    options.tool = Tool.R8Assistant;
    options.programConsumer = getProgramConsumer();
    return options;
  }

  public String getReflectiveReceiverDescriptor() {
    return reflectiveReceiverDescriptor;
  }

  /**
   * This is an experimental API for injecting reflective identification callbacks into dex code.
   * This API is subject to change.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<R8AssistantCommand, Builder> {

    private String reflectiveReceiverDescriptor;
    private Path assistantRuntimePath;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    @Override
    void validate() {
      if (!(getProgramConsumer() instanceof DexIndexedConsumer)) {
        getReporter().error("R8 assistant does not support CF output.");
      }
    }

    public Builder setAssistantRuntime(Path dexPath) {
      this.assistantRuntimePath = dexPath;
      return self();
    }

    public Builder setReflectiveReceiverClassDescriptor(String descriptor) {
      if (!DescriptorUtils.isClassDescriptor(descriptor)) {
        getReporter().error("Not a valid descriptor " + descriptor);
      }
      this.reflectiveReceiverDescriptor = descriptor;
      return self();
    }

    public Builder setReflectiveReceiverClass(Class<?> clazz) {
      return setReflectiveReceiverClassDescriptor(DescriptorUtils.javaClassToDescriptor(clazz));
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.RELEASE;
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    R8AssistantCommand makeCommand() {
      if (reflectiveReceiverDescriptor == null) {
        reflectiveReceiverDescriptor =
            DescriptorUtils.javaClassToDescriptor(ReflectiveOperationJsonLogger.class);
      }
      injectRuntime();
      return new R8AssistantCommand(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getUncheckedMinApiLevel(),
          getReporter(),
          reflectiveReceiverDescriptor,
          getApiDatabasePath());
    }

    private void injectRuntime() {
      if (assistantRuntimePath != null) {
        addProgramFiles(assistantRuntimePath);
        return;
      }
      byte[] runtimeDex = loadDefaultRuntimeDex();
      if (runtimeDex != null) {
        addDexProgramData(
            runtimeDex,
            new SynthesizedOrigin("Reflective instrumentation runtime DEX", R8Assistant.class));
      } else {
        getReporter()
            .error(
                new StringDiagnostic(
                    "Could not find assistant runtime DEX: " + ASSISTANT_RUNTIME_DEX));
      }
    }

    private byte[] loadDefaultRuntimeDex() {
      ClassLoader loader = R8AssistantCommand.class.getClassLoader();
      if (loader == null) {
        loader = ClassLoader.getSystemClassLoader();
      }
      try (InputStream inputStream = loader.getResourceAsStream(ASSISTANT_RUNTIME_DEX)) {
        if (inputStream != null) {
          return ByteStreams.toByteArray(inputStream);
        }
      } catch (IOException e) {
        getReporter().error(new ExceptionDiagnostic(e));
      }
      return null;
    }
  }
}
