// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.apimodel.ParsedApiClass;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.Trimmer;
import java.util.Collection;
import java.util.function.Function;

public class ApiDatabaseGeneratorTestHelper {
  public static <E extends Throwable> Collection<ParsedApiClass> generateClasses(
      ApiDatabaseGeneratorCommand command, Function<JarTrimmer, Trimmer<E>> jarTrimmerWrapper)
      throws ApiDatabaseGeneratorException, E {
    return ApiDatabaseGenerator.generateClasses(command, jarTrimmerWrapper);
  }
}
