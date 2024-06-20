// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.R8FullTestBuilder;
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
public class ComposeSamplesBenchmarks extends BenchmarkBase {

  private static final Path dir =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps/android/compose-samples");

  public ComposeSamplesBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        AppDumpBenchmarkBuilder.builder()
            .setName("CraneApp")
            .setDumpDependencyPath(dir.resolve("crane"))
            .setFromRevision(16457)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("JetLaggedApp")
            .setDumpDependencyPath(dir.resolve("jetlagged"))
            .setFromRevision(16457)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("JetNewsApp")
            .setDumpDependencyPath(dir.resolve("jetnews"))
            .setFromRevision(16457)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("JetCasterApp")
            .setDumpDependencyPath(dir.resolve("jetcaster"))
            .setFromRevision(16457)
            .buildR8(ComposeSamplesBenchmarks::configureJetCasterApp),
        AppDumpBenchmarkBuilder.builder()
            .setName("JetChatApp")
            .setDumpDependencyPath(dir.resolve("jetchat"))
            .setFromRevision(16457)
            .buildR8(ComposeSamplesBenchmarks::configureJetChatApp),
        AppDumpBenchmarkBuilder.builder()
            .setName("JetSnackApp")
            .setDumpDependencyPath(dir.resolve("jetsnack"))
            .setFromRevision(16457)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("OwlApp")
            .setDumpDependencyPath(dir.resolve("owl"))
            .setFromRevision(16457)
            .buildR8(),
        AppDumpBenchmarkBuilder.builder()
            .setName("ReplyApp")
            .setDumpDependencyPath(dir.resolve("reply"))
            .setFromRevision(16457)
            .buildR8());
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

  private static void configureJetChatApp(R8FullTestBuilder testBuilder) {
    testBuilder
        .allowDiagnosticInfoMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules();
  }
}
