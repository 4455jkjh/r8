// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.Version;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Keep
public class TraceReferences {

  public static void run(TraceReferencesCommand command) throws CompilationFailedException {
    InternalOptions options = new InternalOptions();
    options.loadAllClassDefinitions = true;
    ExceptionUtils.withCompilationHandler(
        command.getReporter(), () -> runInternal(command, options));
  }

  private static void forEachDescriptor(ProgramResourceProvider provider, Consumer<String> consumer)
      throws ResourceException, IOException {
    for (ProgramResource programResource : provider.getProgramResources()) {
      if (programResource.getKind() == Kind.DEX) {
        assert programResource.getClassDescriptors() == null;
        for (DexProgramClass clazz :
            new ApplicationReader(
                    AndroidApp.builder()
                        .addDexProgramData(ImmutableList.of(programResource.getBytes()))
                        .build(),
                    new InternalOptions(),
                    Timing.empty())
                .read()
                .classes()) {
          consumer.accept(clazz.getType().toDescriptorString());
        }
      } else {
        assert programResource.getClassDescriptors() != null;
        programResource.getClassDescriptors().forEach(consumer);
      }
    }
  }

  static void runForTesting(TraceReferencesCommand command, InternalOptions options)
      throws CompilationFailedException {
    ExceptionUtils.withCompilationHandler(
        command.getReporter(), () -> runInternal(command, options));
  }

  private static void runInternal(TraceReferencesCommand command, InternalOptions options)
      throws IOException, ResourceException {
    AndroidApp.Builder builder = AndroidApp.builder();
    command.getLibrary().forEach(builder::addLibraryResourceProvider);
    command.getTarget().forEach(builder::addClasspathResourceProvider);
    command.getSource().forEach(builder::addProgramResourceProvider);
    Set<String> targetDescriptors = new HashSet<>();
    command
        .getTarget()
        .forEach(provider -> targetDescriptors.addAll(provider.getClassDescriptors()));
    for (ProgramResourceProvider provider : command.getSource()) {
      forEachDescriptor(provider, targetDescriptors::remove);
    }
    Tracer tracer = new Tracer(targetDescriptors, builder.build(), command.getReporter(), options);
    tracer.run(command.getConsumer());
  }

  public static void run(String... args) throws CompilationFailedException {
    TraceReferencesCommand command =
        TraceReferencesCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      System.out.println(TraceReferencesCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("tracereferences " + Version.getVersionString());
      return;
    }
    run(command);
  }

  /**
   * Command-line entry to tracereferences.
   *
   * <p>See {@link TraceReferencesCommandParser#getUsageMessage()} or run {@code tracereferences
   * --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines(
              "Invalid invocation.", TraceReferencesCommandParser.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
