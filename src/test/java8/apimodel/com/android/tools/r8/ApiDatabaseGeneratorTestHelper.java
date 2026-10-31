// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.apimodel.AmendmentListener;
import com.android.tools.r8.apimodel.ParsedApiClass;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.Trimmer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.UncheckedApiLevel;
import com.android.tools.r8.utils.internal.SemiInfiniteRange;
import java.util.Collection;
import java.util.function.Function;

public class ApiDatabaseGeneratorTestHelper {
  public static void run(
      ApiDatabaseGeneratorCommand command,
      AmendmentListener amendmentListener,
      Function<
              JarTrimmer<SemiInfiniteRange<UncheckedApiLevel>>,
              Trimmer<SemiInfiniteRange<UncheckedApiLevel>, ApiDatabaseGeneratorException>>
          jarTrimmerWrapper)
      throws ApiDatabaseGeneratorException {
    ApiDatabaseGenerator.run(command, amendmentListener, jarTrimmerWrapper);
  }

  public static <E extends Throwable>
      Collection<ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> generateClasses(
          ApiDatabaseGeneratorCommand command,
          AmendmentListener amendmentListener,
          Function<
                  JarTrimmer<SemiInfiniteRange<UncheckedApiLevel>>,
                  Trimmer<SemiInfiniteRange<UncheckedApiLevel>, E>>
              jarTrimmerWrapper)
          throws ApiDatabaseGeneratorException, E {
    return ApiDatabaseGenerator.generateClasses(command, amendmentListener, jarTrimmerWrapper);
  }
}
