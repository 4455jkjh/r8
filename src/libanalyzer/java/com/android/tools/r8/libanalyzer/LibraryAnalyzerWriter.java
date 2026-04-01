// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.libanalyzer.proto.BlockedConsumerKeepRule;
import com.android.tools.r8.libanalyzer.proto.ConfigurationSummary;
import com.android.tools.r8.libanalyzer.proto.D8CompileResult;
import com.android.tools.r8.libanalyzer.proto.ItemCollectionSummary;
import com.android.tools.r8.libanalyzer.proto.KeepRuleBlastRadiusSummary;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.libanalyzer.proto.R8CompileResult;
import com.android.tools.r8.libanalyzer.proto.ValidateConsumerKeepRulesResult;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;

public class LibraryAnalyzerWriter {

  static void writeAnalysisResult(
      D8CompileResult d8CompileResult,
      R8CompileResult r8CompileResult,
      ValidateConsumerKeepRulesResult validateConsumerKeepRulesResult,
      LibraryAnalyzerOptions options) {
    LibraryAnalyzerResult.Builder resultBuilder = LibraryAnalyzerResult.newBuilder();
    if (d8CompileResult != null) {
      resultBuilder.setD8CompileResult(d8CompileResult);
    }
    if (r8CompileResult != null) {
      resultBuilder.setR8CompileResult(r8CompileResult);
    }
    if (validateConsumerKeepRulesResult != null) {
      resultBuilder.setValidateConsumerKeepRulesResult(validateConsumerKeepRulesResult);
    }
    LibraryAnalyzerResult result = resultBuilder.build();
    if (options.outputConsumer != null) {
      options.outputConsumer.accept(result);
    } else {
      writeAnalysisResultToStdout(result);
    }
  }

  private static void writeAnalysisResultToStdout(LibraryAnalyzerResult result) {
    // D8CompileResult.
    D8CompileResult d8CompileResult = result.getD8CompileResult();
    writeKeyValuePairToStdout("D8CompileResult.dex_size_bytes", d8CompileResult.getDexSizeBytes());

    // R8CompileResult.
    R8CompileResult r8CompileResult = result.getR8CompileResult();
    writeKeyValuePairToStdout("R8CompileResult.dex_size_bytes", r8CompileResult.getDexSizeBytes());
    writeConfigurationSummaryToStdout(
        r8CompileResult.getConfiguration(), "R8CompileResult.configuration");
    writeItemCollectionSummaryToStdout(r8CompileResult.getClasses(), "R8CompileResult.classes");
    writeItemCollectionSummaryToStdout(r8CompileResult.getFields(), "R8CompileResult.fields");
    writeItemCollectionSummaryToStdout(r8CompileResult.getMethods(), "R8CompileResult.methods");

    // ValidateConsumerKeepRulesResult.
    ValidateConsumerKeepRulesResult validateConsumerKeepRulesResult =
        result.getValidateConsumerKeepRulesResult();
    for (var element : validateConsumerKeepRulesResult.getBlockedKeepRulesList()) {
      writeBlockedConsumerKeepRuleToStdout(
          element, "ValidateConsumerKeepRulesResult.blocked_keep_rules");
    }
  }

  private static void writeBlockedConsumerKeepRuleToStdout(
      BlockedConsumerKeepRule blockedConsumerKeepRule, String path) {
    writeKeyValuePairToStdout(path + ".source", blockedConsumerKeepRule.getSource());
  }

  private static void writeConfigurationSummaryToStdout(
      ConfigurationSummary configurationSummary, String path) {
    for (var element : configurationSummary.getKeepRulesList()) {
      writeKeepRuleBlastRadiusSummaryToStdout(element, path + ".keep_rules");
    }
    for (var element : configurationSummary.getUsedPackageWideKeepRulesList()) {
      writeKeepRuleBlastRadiusSummaryToStdout(element, path + ".used_package_wide_keep_rules");
    }
    for (var element : configurationSummary.getUnusedPackageWideKeepRulesList()) {
      writeKeepRuleBlastRadiusSummaryToStdout(element, path + ".unused_package_wide_keep_rules");
    }
  }

  private static void writeKeepRuleBlastRadiusSummaryToStdout(
      KeepRuleBlastRadiusSummary keepRuleBlastRadiusSummary, String path) {
    writeKeyValuePairToStdout(path + ".source", keepRuleBlastRadiusSummary.getSource());
    writeKeyValuePairToStdout(
        path + ".kept_item_count", keepRuleBlastRadiusSummary.getKeptItemCount());
    writeKeyValuePairToStdout(
        path + ".no_obfuscation", keepRuleBlastRadiusSummary.getNoObfuscation());
    writeKeyValuePairToStdout(
        path + ".no_optimization", keepRuleBlastRadiusSummary.getNoOptimization());
    writeKeyValuePairToStdout(path + ".no_shrinking", keepRuleBlastRadiusSummary.getNoShrinking());
  }

  private static void writeItemCollectionSummaryToStdout(
      ItemCollectionSummary itemCollectionSummary, String path) {
    writeKeyValuePairToStdout(path + ".item_count", itemCollectionSummary.getItemCount());
    writeKeyValuePairToStdout(path + ".kept_item_count", itemCollectionSummary.getKeptItemCount());
    writeKeyValuePairToStdout(
        path + ".no_obfuscation_count", itemCollectionSummary.getNoObfuscationCount());
    writeKeyValuePairToStdout(
        path + ".no_optimization_count", itemCollectionSummary.getNoOptimizationCount());
    writeKeyValuePairToStdout(
        path + ".no_shrinking_count", itemCollectionSummary.getNoShrinkingCount());
  }

  private static void writeKeyValuePairToStdout(String key, Object value) {
    System.out.println(key + ": " + value);
  }
}
