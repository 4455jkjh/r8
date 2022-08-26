// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.utils;

import static com.android.tools.r8.TestBase.transformer;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.experimental.startup.instrumentation.StartupInstrumentationOptions;
import com.android.tools.r8.experimental.startup.profile.StartupProfileParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.startup.HumanReadableARTProfileParserBuilder;
import com.android.tools.r8.startup.StartupClassBuilder;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.SyntheticStartupMethodBuilder;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.profile.ExternalSyntheticStartupMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public class StartupTestingUtils {

  private static String startupInstrumentationTag = "startup";

  private enum AppVariant {
    ORIGINAL,
    OPTIMIZED;

    boolean isOriginal() {
      return this == ORIGINAL;
    }
  }

  private static StartupProfileBuilder createStartupItemFactory(
      Consumer<ExternalStartupItem> startupItemConsumer) {
    return new StartupProfileBuilder() {
      @Override
      public StartupProfileBuilder addStartupClass(
          Consumer<StartupClassBuilder> startupClassBuilderConsumer) {
        ExternalStartupClass.Builder startupClassBuilder = ExternalStartupClass.builder();
        startupClassBuilderConsumer.accept(startupClassBuilder);
        startupItemConsumer.accept(startupClassBuilder.build());
        return this;
      }

      @Override
      public StartupProfileBuilder addStartupMethod(
          Consumer<StartupMethodBuilder> startupMethodBuilderConsumer) {
        ExternalStartupMethod.Builder startupMethodBuilder = ExternalStartupMethod.builder();
        startupMethodBuilderConsumer.accept(startupMethodBuilder);
        startupItemConsumer.accept(startupMethodBuilder.build());
        return this;
      }

      @Override
      public StartupProfileBuilder addSyntheticStartupMethod(
          Consumer<SyntheticStartupMethodBuilder> syntheticStartupMethodBuilderConsumer) {
        ExternalSyntheticStartupMethod.Builder syntheticStartupMethodBuilder =
            ExternalSyntheticStartupMethod.builder();
        syntheticStartupMethodBuilderConsumer.accept(syntheticStartupMethodBuilder);
        startupItemConsumer.accept(syntheticStartupMethodBuilder.build());
        return this;
      }

      @Override
      public StartupProfileBuilder addHumanReadableARTProfile(
          TextInputStream textInputStream,
          Consumer<HumanReadableARTProfileParserBuilder> parserBuilderConsumer) {
        throw new Unimplemented();
      }
    };
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOriginalAppUsingFile(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.ORIGINAL, false);
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOriginalAppUsingLogcat(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.ORIGINAL, true);
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOptimizedAppUsingLogcat(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.OPTIMIZED, true);
  }

  private static void enableStartupInstrumentation(
      D8TestBuilder testBuilder, TestParameters parameters, AppVariant appVariant, boolean logcat)
      throws IOException {
    testBuilder
        .addOptionsModification(
            options -> {
              StartupInstrumentationOptions startupInstrumentationOptions =
                  options
                      .getStartupInstrumentationOptions()
                      .setEnableStartupInstrumentation()
                      .setEnableGeneralizationOfSyntheticsToSyntheticContext(
                          appVariant.isOriginal());
              if (logcat) {
                startupInstrumentationOptions.setStartupInstrumentationTag(
                    startupInstrumentationTag);
              }
            })
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addLibraryClassFileData(getTransformedAndroidUtilLog());
  }

  public static Path getAndroidUtilLog(TemporaryFolder temporaryFolder)
      throws CompilationFailedException, IOException {
    return TestBase.testForD8(temporaryFolder)
        .addProgramClassFileData(getTransformedAndroidUtilLog())
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip();
  }

  public static void readStartupListFromFile(
      Path path, Consumer<ExternalStartupItem> startupItemConsumer) throws IOException {
    StartupProfileParser.create()
        .parseLines(
            Files.readAllLines(path).stream(),
            createStartupItemFactory(startupItemConsumer),
            error -> fail("Unexpected parse error: " + error));
  }

  public static ThrowingConsumer<D8TestRunResult, RuntimeException> removeStartupListFromStdout(
      Consumer<ExternalStartupItem> startupItemConsumer) {
    return runResult -> removeStartupListFromStdout(runResult, startupItemConsumer);
  }

  public static void removeStartupListFromStdout(
      D8TestRunResult runResult, Consumer<ExternalStartupItem> startupItemConsumer) {
    StartupProfileParser parser = StartupProfileParser.create();
    StringBuilder stdoutBuilder = new StringBuilder();
    String startupDescriptorPrefix = "[" + startupInstrumentationTag + "] ";
    for (String line : StringUtils.splitLines(runResult.getStdOut(), true)) {
      if (line.startsWith(startupDescriptorPrefix)) {
        String message = line.substring(startupDescriptorPrefix.length());
        parser.parseLine(
            message,
            createStartupItemFactory(startupItemConsumer),
            error -> fail("Unexpected parse error: " + error));
      } else {
        stdoutBuilder.append(line).append(System.lineSeparator());
      }
    }
    runResult.getResult().setStdout(stdoutBuilder.toString());
  }

  public static void setStartupConfiguration(
      TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder, List<ExternalStartupItem> startupItems) {
    testBuilder.addOptionsModification(
        options -> {
          StartupProfileProvider startupProfileProvider =
              new StartupProfileProvider() {
                @Override
                public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
                  for (ExternalStartupItem startupItem : startupItems) {
                    startupItem.apply(
                        startupClass ->
                            startupProfileBuilder.addStartupClass(
                                startupClassBuilder ->
                                    startupClassBuilder.setClassReference(
                                        startupClass.getReference())),
                        startupMethod ->
                            startupProfileBuilder.addStartupMethod(
                                startupMethodBuilder ->
                                    startupMethodBuilder.setMethodReference(
                                        startupMethod.getReference())),
                        syntheticStartupMethod ->
                            startupProfileBuilder.addSyntheticStartupMethod(
                                syntheticStartupMethodBuilder ->
                                    syntheticStartupMethodBuilder.setSyntheticContextReference(
                                        syntheticStartupMethod.getSyntheticContextReference())));
                  }
                }

                @Override
                public Origin getOrigin() {
                  return Origin.unknown();
                }
              };
          options
              .getStartupOptions()
              .setStartupProfileProviders(Collections.singleton(startupProfileProvider));
        });
  }

  private static byte[] getTransformedAndroidUtilLog() throws IOException {
    return transformer(Log.class).setClassDescriptor("Landroid/util/Log;").transform();
  }

  public static class Log {

    public static int i(String tag, String msg) {
      System.out.println("[" + tag + "] " + msg);
      return 42;
    }
  }
}
