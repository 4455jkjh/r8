// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;

public class LibraryValidator {

  // Estimates if the library passed is at the expected minimum level, if it is not, raise
  // a warning.
  public static void validate(DexApplication app, AndroidApiLevel requiredCompilationAPILevel) {
    DexType levelType;
    if (requiredCompilationAPILevel.isEqualTo(AndroidApiLevel.O)) {
      levelType = app.dexItemFactory.createType("Ljava/time/LocalTime;");
    } else if (requiredCompilationAPILevel.isEqualTo(AndroidApiLevel.R)) {
      levelType = app.dexItemFactory.createType("Ljava/util/concurrent/Flow;");
    } else {
      app.options.reporter.warning(
          "Unsupported requiredCompilationAPILevel: " + requiredCompilationAPILevel);
      return;
    }
    DexClass dexClass = app.definitionFor(levelType);
    if (dexClass == null) {
      app.options.reporter.warning(
          "Desugared library requires to be compiled with a library file of API greater or equal to"
              + " "
              + requiredCompilationAPILevel
              + ", but it seems the library file passed is of a lower API.");
    }
  }
}
