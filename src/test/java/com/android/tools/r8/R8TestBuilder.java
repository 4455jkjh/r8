// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestBase.R8Mode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class R8TestBuilder
    extends TestShrinkerBuilder<
        R8Command, Builder, R8TestCompileResult, R8TestRunResult, R8TestBuilder> {

  private R8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8TestBuilder create(TestState state, Backend backend, R8Mode mode) {
    R8Command.Builder builder =
        mode == R8Mode.Full ? R8Command.builder() : new CompatProguardCommandBuilder();
    return new R8TestBuilder(state, builder, backend);
  }

  private boolean enableInliningAnnotations = false;
  private boolean enableClassInliningAnnotations = false;
  private boolean enableMergeAnnotations = false;
  private List<String> keepRules = new ArrayList<>();

  @Override
  R8TestBuilder self() {
    return this;
  }

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableInliningAnnotations || enableClassInliningAnnotations || enableMergeAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    if (!keepRules.isEmpty()) {
      builder.addProguardConfiguration(keepRules, Origin.unknown());
    }
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setDisableTreeShaking(!enableTreeShaking);
    builder.setDisableMinification(!enableMinification);
    builder.setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string));

    class Box {
      private List<ProguardConfigurationRule> syntheticProguardRules;
      private ProguardConfiguration proguardConfiguration;
    }
    Box box = new Box();
    ToolHelper.addSyntheticProguardRulesConsumerForTesting(
        builder, rules -> box.syntheticProguardRules = rules);
    ToolHelper.runR8WithoutResult(
        builder.build(),
        optionsConsumer.andThen(
            options -> box.proguardConfiguration = options.getProguardConfiguration()));
    return new R8TestCompileResult(
        getState(),
        backend,
        app.get(),
        box.proguardConfiguration,
        box.syntheticProguardRules,
        proguardMapBuilder.toString());
  }

  public R8TestBuilder addDataResources(List<DataEntryResource> resources) {
    resources.forEach(builder.getAppBuilder()::addDataResource);
    return self();
  }

  @Override
  public R8TestBuilder addKeepRuleFiles(List<Path> files) {
    builder.addProguardConfigurationFiles(files);
    return self();
  }

  @Override
  public R8TestBuilder addKeepRules(Collection<String> rules) {
    // Delay adding the actual rules so that we only associate a single origin and unique lines to
    // each actual rule.
    keepRules.addAll(rules);
    return self();
  }

  public R8TestBuilder enableInliningAnnotations() {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addKeepRules(
          "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
          "-neverinline class * { @com.android.tools.r8.NeverInline *; }");
    }
    return self();
  }

  public R8TestBuilder enableClassInliningAnnotations() {
    if (!enableClassInliningAnnotations) {
      enableClassInliningAnnotations = true;
      addKeepRules("-neverclassinline @com.android.tools.r8.NeverClassInline class *");
    }
    return self();
  }

  public R8TestBuilder enableMergeAnnotations() {
    if (!enableMergeAnnotations) {
      enableMergeAnnotations = true;
      addKeepRules(
          "-nevermerge @com.android.tools.r8.NeverMerge class *");
    }
    return self();
  }

  public R8TestBuilder enableProguardTestOptions() {
    builder.allowTestProguardOptions();
    return self();
  }
}
