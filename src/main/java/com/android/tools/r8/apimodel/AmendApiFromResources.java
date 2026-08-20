// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.utils.internal.ThrowingFunction;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class AmendApiFromResources {

  public static void applyAmendments(Collection<ParsedApiClass> classes, ApiJarInfo jarInfo)
      throws ApiDatabaseGeneratorException, IOException {
    ApiAmendments missingApi =
        readResource("/resources/missing.api.txt", ApiAmendmentsParser::parseApiAmendments);
    ApiAmendments hiddenApi =
        readResource("/resources/hidden.api.txt", ApiAmendmentsParser::parseApiAmendments);
    JarAmendments jarHidden =
        readResource("/resources/hidden.jar.txt", ApiAmendmentsParser::parseJarAmendments);

    ParsedApiClassAmending.amendApi(classes, missingApi);
    ParsedApiClassAmending.amendApi(classes, hiddenApi);
    ApiJarInfoAmending.amendJar(jarInfo, jarHidden);
  }

  private static <T, E extends Throwable> T readResource(
      String resourceName, ThrowingFunction<InputStream, T, E> consumer)
      throws IOException, ApiDatabaseGeneratorException, E {
    try (InputStream stream = AmendApiFromResources.class.getResourceAsStream(resourceName)) {
      if (stream == null) {
        throw new ApiDatabaseGeneratorException("Resource " + resourceName + " not found");
      }
      return consumer.apply(stream);
    }
  }
}
