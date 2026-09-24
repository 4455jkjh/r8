// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ComposeSamplesBenchmarks extends BenchmarkBase {

  private static final Path dir =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps/android/compose-samples");

  protected ComposeSamplesBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        CraneApp.config(),
        CraneAppPartial.config(),
        JetLaggedApp.config(),
        JetLaggedAppPartial.config(),
        JetNewsApp.config(),
        JetNewsAppPartial.config(),
        JetCasterApp.config(),
        JetCasterAppPartial.config(),
        JetChatApp.config(),
        JetChatAppPartial.config(),
        JetSnackApp.config(),
        JetSnackAppPartial.config(),
        OwlApp.config(),
        OwlAppPartial.config(),
        ReplyApp.config(),
        ReplyAppPartial.config());
  }

  private static void configureWithOpenInterfaceSuppression(R8FullTestBuilder testBuilder) {
    configureWithoutOpenInterfaceSuppression(testBuilder);
    testBuilder.addOptionsModification(
        options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static void configureWithOpenInterfaceSuppressionPartial(
      R8PartialTestBuilder testBuilder) {
    configureWithoutOpenInterfaceSuppressionPartial(testBuilder);
    testBuilder.addR8PartialR8OptionsModification(
        options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static void configureWithoutOpenInterfaceSuppression(R8FullTestBuilder testBuilder) {
    testBuilder
        // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
        .setMinApi(24)
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowDiagnosticMessages();
  }

  private static void configureWithoutOpenInterfaceSuppressionPartial(
      R8PartialTestBuilder testBuilder) {
    testBuilder
        // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
        .setMinApi(24)
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowDiagnosticMessages();
  }

  private static void configureJetCasterApp(R8FullTestBuilder testBuilder) {
    testBuilder
        .addDontWarn(
            "org.bouncycastle.jsse.BCSSLParameters",
            "org.bouncycastle.jsse.BCSSLSocket",
            "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider",
            "org.conscrypt.Conscrypt",
            "org.conscrypt.Conscrypt$Version",
            "org.openjsse.javax.net.ssl.SSLParameters",
            "org.openjsse.javax.net.ssl.SSLSocket",
            "org.openjsse.net.ssl.OpenJSSE",
            "org.slf4j.impl.StaticLoggerBinder")
        .allowDiagnosticInfoMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .addOptionsModification(
            options -> {
              options.getCfCodeAnalysisOptions().setAllowUnreachableCfBlocks(true);
              options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
            });
  }

  private static void configureJetCasterAppPartial(R8PartialTestBuilder testBuilder) {
    testBuilder
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .addR8PartialR8OptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  protected static AppDumpBenchmarkBuilder builder(String name, String app) {
    return builder(name, app, true);
  }

  protected static AppDumpBenchmarkBuilder builder(
      String name, String app, boolean enableResourceShrinking) {
    AppDumpBenchmarkBuilder builder =
        AppDumpBenchmarkBuilder.builder()
            .setName(name)
            .setDumpDependencyPath(dir.resolve(app))
            .setFromRevision(16457);
    if (enableResourceShrinking) {
      builder.setEnableResourceShrinking(true).setResourcesProvidedInFeature();
    }
    return builder;
  }

  public static class CraneApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("CraneApp", "crane", false).buildR8();
    }

    public CraneApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class CraneAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("CraneAppPartial", "crane", false).buildR8WithPartialShrinking();
    }

    public CraneAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetLaggedApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetLaggedApp", "jetlagged")
          .buildR8(ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppression);
    }

    public JetLaggedApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetLaggedAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetLaggedAppPartial", "jetlagged")
          .buildR8WithPartialShrinking(
              ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppressionPartial);
    }

    public JetLaggedAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetNewsApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetNewsApp", "jetnews")
          .buildR8(ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppression);
    }

    public JetNewsApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetNewsAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetNewsAppPartial", "jetnews")
          .buildR8WithPartialShrinking(
              ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppressionPartial);
    }

    public JetNewsAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetCasterApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetCasterApp", "jetcaster")
          .buildR8(ComposeSamplesBenchmarks::configureJetCasterApp);
    }

    public JetCasterApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetCasterAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetCasterAppPartial", "jetcaster")
          .buildR8WithPartialShrinking(ComposeSamplesBenchmarks::configureJetCasterAppPartial);
    }

    public JetCasterAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetChatApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetChatApp", "jetchat")
          .buildR8(ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppression);
    }

    public JetChatApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetChatAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetChatAppPartial", "jetchat")
          .buildR8WithPartialShrinking(
              ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppressionPartial);
    }

    public JetChatAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetSnackApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetSnackApp", "jetsnack")
          .buildR8(ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppression);
    }

    public JetSnackApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class JetSnackAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("JetSnackAppPartial", "jetsnack")
          .buildR8WithPartialShrinking(
              ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppressionPartial);
    }

    public JetSnackAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OwlApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OwlApp", "owl", false).buildR8();
    }

    public OwlApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OwlAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OwlAppPartial", "owl", false).buildR8WithPartialShrinking();
    }

    public OwlAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class ReplyApp extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ReplyApp", "reply")
          .buildR8(ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppression);
    }

    public ReplyApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class ReplyAppPartial extends ComposeSamplesBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ReplyAppPartial", "reply")
          .buildR8WithPartialShrinking(
              ComposeSamplesBenchmarks::configureWithOpenInterfaceSuppressionPartial);
    }

    public ReplyAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}
