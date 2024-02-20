// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.AppView;
import java.io.InputStream;
import java.util.Collection;

public class ResourceShrinkerUtils {

  public static R8ResourceShrinkerState createResourceShrinkerState(AppView<?> appView) {
    R8ResourceShrinkerState state =
        new R8ResourceShrinkerState(
            exception -> appView.reporter().fatalError(new ExceptionDiagnostic(exception)));
    InternalOptions options = appView.options();
    if (options.resourceShrinkerConfiguration.isOptimizedShrinking()
        && options.androidResourceProvider != null) {
      try {
        addResources(
            appView,
            state,
            options.androidResourceProvider.getAndroidResources(),
            FeatureSplit.BASE);
        if (options.hasFeatureSplitConfiguration()) {
          for (FeatureSplit featureSplit :
              options.getFeatureSplitConfiguration().getFeatureSplits()) {
            if (featureSplit.getAndroidResourceProvider() != null) {
              addResources(
                  appView,
                  state,
                  featureSplit.getAndroidResourceProvider().getAndroidResources(),
                  featureSplit);
            }
          }
        }
      } catch (ResourceException e) {
        throw appView.reporter().fatalError("Failed initializing resource table");
      }
      state.setupReferences();
    }
    return state;
  }

  private static void addResources(
      AppView<?> appView,
      R8ResourceShrinkerState state,
      Collection<AndroidResourceInput> androidResources,
      FeatureSplit featureSplit)
      throws ResourceException {
    for (AndroidResourceInput androidResource : androidResources) {
      switch (androidResource.getKind()) {
        case MANIFEST:
          state.addManifestProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource));
          break;
        case RESOURCE_TABLE:
          state.addResourceTable(androidResource.getByteStream(), featureSplit);
          break;
        case XML_FILE:
          state.addXmlFileProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource),
              androidResource.getPath().location());
          break;
        case RES_FOLDER_FILE:
          state.addResFileProvider(
              () -> wrapThrowingInputStreamResource(appView, androidResource),
              androidResource.getPath().location());
          break;
        case UNKNOWN:
          break;
      }
    }
  }

  private static InputStream wrapThrowingInputStreamResource(
      AppView<?> appView, AndroidResourceInput androidResource) {
    try {
      return androidResource.getByteStream();
    } catch (ResourceException ex) {
      throw appView.reporter().fatalError("Failed reading " + androidResource.getPath().location());
    }
  }
}
