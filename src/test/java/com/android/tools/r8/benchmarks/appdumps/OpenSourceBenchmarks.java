// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.CompilationFailedException;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class OpenSourceBenchmarks extends BenchmarkBase {

  private static final Path dir = Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps/android");

  protected OpenSourceBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        FeederApp.config(),
        FeederAppPartial.config(),
        BookStoryApp.config(),
        BookStoryAppPartial.config(),
        ReadYouApp.config(),
        ReadYouAppPartial.config(),
        FossifyFileManagerApp.config(),
        FossifyFileManagerAppPartial.config(),
        NewPipeKotlinApp.config(),
        NewPipeKotlinAppPartial.config(),
        TuskyApp.config(),
        TuskyAppPartial.config(),
        KeePassDXApp.config(),
        KeePassDXAppPartial.config(),
        OmniNotesApp.config(),
        OmniNotesAppPartial.config(),
        OpenTracksApp.config(),
        OpenTracksAppPartial.config());
  }

  private static void configureWithoutOpenInterfaceSuppression(R8FullTestBuilder testBuilder) {
    testBuilder
        .addDontWarn("*")
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .addOptionsModification(
            options -> options.getCfCodeAnalysisOptions().setAllowUnreachableCfBlocks(true));
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    configureWithoutOpenInterfaceSuppression(testBuilder);
    testBuilder.addOptionsModification(
        options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static void configureMinApi24WithoutOpenInterfaceSuppression(
      R8FullTestBuilder testBuilder) {
    configureWithoutOpenInterfaceSuppression(testBuilder);
    // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
    testBuilder.setMinApi(24);
  }

  private static void configureMinApi24(R8FullTestBuilder testBuilder) {
    configure(testBuilder);
    // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
    testBuilder.setMinApi(24);
  }

  private static void configurePartialWithoutOpenInterfaceSuppression(
      R8PartialTestBuilder testBuilder) {
    testBuilder
        .addDontWarn("*")
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .addR8PartialR8OptionsModification(
            options -> options.getCfCodeAnalysisOptions().setAllowUnreachableCfBlocks(true));
  }

  private static void configurePartial(R8PartialTestBuilder testBuilder) {
    configurePartialWithoutOpenInterfaceSuppression(testBuilder);
    testBuilder.addR8PartialR8OptionsModification(
        options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
  }

  private static void configureMinApi24PartialWithoutOpenInterfaceSuppression(
      R8PartialTestBuilder testBuilder) {
    configurePartialWithoutOpenInterfaceSuppression(testBuilder);
    // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
    testBuilder.setMinApi(24);
  }

  private static void configureMinApi24Partial(R8PartialTestBuilder testBuilder) {
    configurePartial(testBuilder);
    // TODO(b/480100735): Remove once we have figured out how to deal with keepanno.
    testBuilder.setMinApi(24);
  }

  protected static AppDumpBenchmarkBuilder builder(String name, String app) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setEnableResourceShrinking(true)
        .setDumpDependencyPath(dir.resolve(app))
        .setFromRevision(16457);
  }

  public static class FeederApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("FeederApp", "feeder").buildR8(OpenSourceBenchmarks::configure);
    }

    public FeederApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class FeederAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("FeederAppPartial", "feeder")
          .buildR8WithPartialShrinking(OpenSourceBenchmarks::configurePartial);
    }

    public FeederAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class BookStoryApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("BookStoryApp", "bookstory").buildR8(OpenSourceBenchmarks::configure);
    }

    public BookStoryApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }

    @Test
    @Override
    public void testBenchmarks() throws Exception {
      assumeFalse(ToolHelper.isWindows());
      RuntimeException e = assertThrows(RuntimeException.class, super::testBenchmarks);
      assertThat(e.getCause(), instanceOf(CompilationFailedException.class));
      assertThat(e.getCause().getCause(), instanceOf(AssertionError.class));
    }
  }

  public static class BookStoryAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("BookStoryAppPartial", "bookstory")
          .buildR8WithPartialShrinking(OpenSourceBenchmarks::configurePartial);
    }

    public BookStoryAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }

    @Test
    @Override
    public void testBenchmarks() throws Exception {
      assumeFalse(ToolHelper.isWindows());
      RuntimeException e = assertThrows(RuntimeException.class, super::testBenchmarks);
      assertThat(e.getCause(), instanceOf(CompilationFailedException.class));
      assertThat(e.getCause().getCause(), instanceOf(AssertionError.class));
    }
  }

  public static class ReadYouApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ReadYouApp", "readyou")
          .setRemoveDontObfuscate()
          .buildR8(OpenSourceBenchmarks::configure);
    }

    public ReadYouApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }

    @Test
    @Override
    public void testBenchmarks() throws Exception {
      assumeFalse(ToolHelper.isWindows());
      RuntimeException e =
          assertThrows(
              RuntimeException.class,
              () -> {
                // InvokeExtractor only triggers the assertion when a caller of XmlResourceParser
                // is processed by CallGraphBuilder before callers of XmlPullParser populate
                // possibleProgramTargetsCache. Since DexApplication.classes() randomly shuffles
                // the class order when assertions are enabled, retry to avoid flakiness.
                for (int i = 0; i < 10; i++) {
                  super.testBenchmarks();
                }
              });
      assertThat(e.getCause(), instanceOf(CompilationFailedException.class));
      assertThat(e.getCause().getCause(), instanceOf(AssertionError.class));
    }
  }

  public static class ReadYouAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ReadYouAppPartial", "readyou")
          .setRemoveDontObfuscate()
          .buildR8WithPartialShrinking(OpenSourceBenchmarks::configurePartial);
    }

    public ReadYouAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class FossifyFileManagerApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("FossifyFileManagerApp", "fossify-filemanager")
          // The host dex2oat boot image has no framework classes, so android.* types are
          // unresolved. Glide's DrawableCrossFadeTransition.transition merges Drawable and
          // ColorDrawable, which the verifier then rejects as a hard failure when the merge is
          // used as a Drawable. On device the framework classes resolve and the method verifies.
          .setEnableDex2OatVerification(false)
          .buildR8(OpenSourceBenchmarks::configure);
    }

    public FossifyFileManagerApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class FossifyFileManagerAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("FossifyFileManagerAppPartial", "fossify-filemanager")
          // The host dex2oat boot image has no framework classes, so android.* types are
          // unresolved. Glide's DrawableCrossFadeTransition.transition merges Drawable and
          // ColorDrawable, which the verifier then rejects as a hard failure when the merge is
          // used as a Drawable. On device the framework classes resolve and the method verifies.
          .setEnableDex2OatVerification(false)
          .buildR8WithPartialShrinking(OpenSourceBenchmarks::configurePartial);
    }

    public FossifyFileManagerAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NewPipeKotlinApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NewPipeKotlinApp", "newpipe")
          .setRemoveDontObfuscate()
          .buildR8(OpenSourceBenchmarks::configureMinApi24);
    }

    public NewPipeKotlinApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NewPipeKotlinAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NewPipeKotlinAppPartial", "newpipe")
          .setRemoveDontObfuscate()
          .buildR8WithPartialShrinking(OpenSourceBenchmarks::configureMinApi24Partial);
    }

    public NewPipeKotlinAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }

    @Test
    @Override
    public void testBenchmarks() throws Exception {
      assumeFalse(ToolHelper.isWindows());
      RuntimeException e = assertThrows(RuntimeException.class, super::testBenchmarks);
      assertThat(e.getCause(), instanceOf(CompilationFailedException.class));
      assertThat(e.getCause().getCause(), instanceOf(AssertionError.class));
    }
  }

  public static class TuskyApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TuskyApp", "tusky")
          .buildR8(OpenSourceBenchmarks::configureWithoutOpenInterfaceSuppression);
    }

    public TuskyApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class TuskyAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TuskyAppPartial", "tusky")
          .buildR8WithPartialShrinking(
              OpenSourceBenchmarks::configurePartialWithoutOpenInterfaceSuppression);
    }

    public TuskyAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class KeePassDXApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("KeePassDXApp", "keepassdx")
          .setRemoveDontOptimize()
          .buildR8(OpenSourceBenchmarks::configureMinApi24WithoutOpenInterfaceSuppression);
    }

    public KeePassDXApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class KeePassDXAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("KeePassDXAppPartial", "keepassdx")
          .setRemoveDontOptimize()
          .buildR8WithPartialShrinking(
              OpenSourceBenchmarks::configureMinApi24PartialWithoutOpenInterfaceSuppression);
    }

    public KeePassDXAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OmniNotesApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OmniNotesApp", "omninotes")
          .setRemoveDontOptimize()
          .setRemoveDontObfuscate()
          // The host dex2oat boot image has no framework classes, so android.* types are
          // unresolved. Glide's DrawableCrossFadeTransition.transition merges Drawable and
          // ColorDrawable, which the verifier then rejects as a hard failure when the merge is
          // used as a Drawable. On device the framework classes resolve and the method verifies.
          .setEnableDex2OatVerification(false)
          .buildR8(OpenSourceBenchmarks::configureWithoutOpenInterfaceSuppression);
    }

    public OmniNotesApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OmniNotesAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OmniNotesAppPartial", "omninotes")
          .setRemoveDontOptimize()
          .setRemoveDontObfuscate()
          // The host dex2oat boot image has no framework classes, so android.* types are
          // unresolved. Glide's DrawableCrossFadeTransition.transition merges Drawable and
          // ColorDrawable, which the verifier then rejects as a hard failure when the merge is
          // used as a Drawable. On device the framework classes resolve and the method verifies.
          .setEnableDex2OatVerification(false)
          .buildR8WithPartialShrinking(
              OpenSourceBenchmarks::configurePartialWithoutOpenInterfaceSuppression);
    }

    public OmniNotesAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OpenTracksApp extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OpenTracksApp", "opentracks")
          .setRemoveDontOptimize()
          .buildR8(OpenSourceBenchmarks::configureWithoutOpenInterfaceSuppression);
    }

    public OpenTracksApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class OpenTracksAppPartial extends OpenSourceBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("OpenTracksAppPartial", "opentracks")
          .setRemoveDontOptimize()
          .buildR8WithPartialShrinking(
              OpenSourceBenchmarks::configurePartialWithoutOpenInterfaceSuppression);
    }

    public OpenTracksAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}
