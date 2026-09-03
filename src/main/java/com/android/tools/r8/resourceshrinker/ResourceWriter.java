// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import com.android.tools.r8.AndroidResourceConsumer;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.AndroidResourceProvider;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.ShrinkerResult;
import com.android.tools.r8.resourceshrinker.usages.LegacyResourceShrinker;
import com.android.tools.r8.resourceshrinker.usages.R8ResourceShrinker;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ResourceShrinkerUtils;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class ResourceWriter {

  private static boolean isEnabled(InternalOptions options) {
    return options.androidResourceProvider != null && options.androidResourceConsumer != null;
  }

  public static void writeResources(AppView<AppInfoWithClassHierarchy> appView) {
    if (!isEnabled(appView.options()) || !appView.options().isOptimizedResourceShrinking()) {
      return;
    }
    try {
      ResourceShrinkerState<FeatureSplit> state = appView.unsetResourceShrinkerState();
      state.setFeatureSplitsWithoutCode(getFeatureSplitsWithoutCode(appView));
      ShrinkerResult<FeatureSplit> shrinkerResult = state.shrinkModel();
      writeResourcesToConsumers(appView, shrinkerResult);
    } catch (ResourceException | IOException e) {
      appView.reporter().error(new ExceptionDiagnostic(e));
    }
  }

  public static void legacyWriteResources(
      AppView<AppInfoWithClassHierarchy> appView, Map<String, byte[]> dexFileContent) {
    InternalOptions options = appView.options();
    if (!isEnabled(options) || appView.options().isOptimizedResourceShrinking()) {
      return;
    }
    try {
      LegacyResourceShrinker.Builder<FeatureSplit> resourceShrinkerBuilder =
          LegacyResourceShrinker.builder();
      resourceShrinkerBuilder.setDexAnalyser(new R8ResourceShrinker());
      dexFileContent.forEach(resourceShrinkerBuilder::addDexInput);
      legacyAddResourcesToBuilder(
          appView, resourceShrinkerBuilder, options.androidResourceProvider, FeatureSplit.BASE);
      if (options.hasFeatureSplitConfiguration()) {
        for (FeatureSplit featureSplit :
            options.getFeatureSplitConfiguration().getFeatureSplits()) {
          if (featureSplit.getAndroidResourceProvider() != null) {
            legacyAddResourcesToBuilder(
                appView,
                resourceShrinkerBuilder,
                featureSplit.getAndroidResourceProvider(),
                featureSplit);
          }
        }
      }
      if (options.androidResourceProguardMapStrings != null) {
        resourceShrinkerBuilder.setProguardMapStrings(options.androidResourceProguardMapStrings);
      }
      resourceShrinkerBuilder.setShrinkerDebugReporter(
          ResourceShrinkerUtils.shrinkerDebugReporterFromStringConsumer(
              options.resourceShrinkerConfiguration.getDebugConsumer(), appView.reporter()));
      LegacyResourceShrinker<FeatureSplit> shrinker = resourceShrinkerBuilder.build();
      ShrinkerResult<FeatureSplit> shrinkerResult = shrinker.run();
      writeResourcesToConsumers(appView, shrinkerResult);
    } catch (ParserConfigurationException | SAXException | ResourceException | IOException e) {
      appView.reporter().error(new ExceptionDiagnostic(e));
    }
  }

  private static void legacyAddResourcesToBuilder(
      AppView<AppInfoWithClassHierarchy> appView,
      LegacyResourceShrinker.Builder<FeatureSplit> resourceShrinkerBuilder,
      AndroidResourceProvider androidResourceProvider,
      FeatureSplit featureSplit)
      throws ResourceException {
    for (AndroidResourceInput androidResource : androidResourceProvider.getAndroidResources()) {
      try {
        byte[] bytes = androidResource.getByteStream().readAllBytes();
        String path = androidResource.getPath().location();
        switch (androidResource.getKind()) {
          case MANIFEST:
            resourceShrinkerBuilder.addManifest(path, bytes);
            break;
          case RES_FOLDER_FILE:
            resourceShrinkerBuilder.addResFolderInput(path, bytes);
            break;
          case RESOURCE_TABLE:
            resourceShrinkerBuilder.addResourceTable(path, bytes, featureSplit);
            break;
          case XML_FILE:
            resourceShrinkerBuilder.addXmlInput(path, bytes);
            break;
          case KEEP_RULE_FILE:
            resourceShrinkerBuilder.addKeepRuleInput(bytes);
            break;
          case UNKNOWN:
            break;
        }
      } catch (IOException e) {
        appView.reporter().error(new ExceptionDiagnostic(e, androidResource.getOrigin()));
      }
    }
  }

  private static void writeResourcesToConsumers(
      AppView<AppInfoWithClassHierarchy> appView, ShrinkerResult<FeatureSplit> shrinkerResult)
      throws ResourceException {
    InternalOptions options = appView.options();
    writeResourcesToConsumer(
        appView,
        shrinkerResult,
        options.androidResourceProvider,
        options.androidResourceConsumer,
        FeatureSplit.BASE);
    if (options.hasFeatureSplitConfiguration()) {
      for (FeatureSplit featureSplit : options.getFeatureSplitConfiguration().getFeatureSplits()) {
        if (featureSplit.getAndroidResourceProvider() != null) {
          writeResourcesToConsumer(
              appView,
              shrinkerResult,
              featureSplit.getAndroidResourceProvider(),
              featureSplit.getAndroidResourceConsumer(),
              featureSplit);
        }
      }
    }
  }

  private static void writeResourcesToConsumer(
      AppView<AppInfoWithClassHierarchy> appView,
      ShrinkerResult<FeatureSplit> shrinkerResult,
      AndroidResourceProvider androidResourceProvider,
      AndroidResourceConsumer androidResourceConsumer,
      FeatureSplit featureSplit)
      throws ResourceException {
    Reporter reporter = appView.reporter();
    for (AndroidResourceInput androidResource : androidResourceProvider.getAndroidResources()) {
      switch (androidResource.getKind()) {
        case MANIFEST:
          String manifestLocation = androidResource.getPath().location();
          if (shrinkerResult != null
              && shrinkerResult.hasCustomFileFor(manifestLocation, featureSplit)) {
            androidResourceConsumer.accept(
                new R8AndroidResourceWithData(
                    androidResource,
                    reporter,
                    shrinkerResult.getBytesFor(manifestLocation, featureSplit)),
                reporter);
          } else {
            androidResourceConsumer.accept(
                new R8PassThroughAndroidResource(androidResource, reporter), reporter);
          }
          break;
        case UNKNOWN:
          androidResourceConsumer.accept(
              new R8PassThroughAndroidResource(androidResource, reporter), reporter);
          break;
        case RESOURCE_TABLE:
          androidResourceConsumer.accept(
              new R8AndroidResourceWithData(
                  androidResource,
                  reporter,
                  shrinkerResult.getResourceTableInProtoFormat(featureSplit)),
              reporter);
          break;
        case KEEP_RULE_FILE:
          // Intentionally not written
          break;
        case RES_FOLDER_FILE:
        case XML_FILE:
          String location = androidResource.getPath().location();
          if (shrinkerResult.getResFolderEntriesToKeep().contains(location)) {
            if (shrinkerResult.hasCustomFileFor(location, featureSplit)) {
              androidResourceConsumer.accept(
                  new R8AndroidResourceWithData(
                      androidResource,
                      reporter,
                      shrinkerResult.getBytesFor(location, featureSplit)),
                  reporter);
            } else {
              androidResourceConsumer.accept(
                  new R8PassThroughAndroidResource(androidResource, reporter), reporter);
            }
          }
          break;
      }
    }
    androidResourceConsumer.finished(reporter);
  }

  private static Set<FeatureSplit> getFeatureSplitsWithoutCode(
      AppView<AppInfoWithClassHierarchy> appView) {
    if (!appView.options().hasFeatureSplitConfiguration()) {
      return Collections.emptySet();
    }
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    Set<FeatureSplit> splitsWithCode = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      FeatureSplit split = classToFeatureSplitMap.getFeatureSplit(clazz, appView);
      if (split != null && !split.isBase()) {
        splitsWithCode.add(split);
      }
    }
    Set<FeatureSplit> splitsWithoutCode = Sets.newIdentityHashSet();
    for (FeatureSplit split : appView.options().getFeatureSplitConfiguration().getFeatureSplits()) {
      if (!split.isBase() && !splitsWithCode.contains(split)) {
        splitsWithoutCode.add(split);
      }
    }
    return splitsWithoutCode;
  }
}
