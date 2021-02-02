// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.lambda;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingWithSmallInliningBudgetTest extends AbstractR8KotlinTestBase {

  @Parameterized.Parameters(name = "{0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinLambdaMergingWithSmallInliningBudgetTest(
      KotlinTestParameters kotlinParameters, boolean allowAccessModification) {
    super(kotlinParameters, allowAccessModification);
  }

  @Test
  public void testJStyleRunnable() throws Exception {
    final String mainClassName = "lambdas_jstyle_runnable.MainKt";
    runTest(
        "lambdas_jstyle_runnable",
        mainClassName,
        testBuilder ->
            testBuilder
                .addDontWarnJetBrainsAnnotations()
                .addOptionsModification(options -> options.inliningInstructionAllowance = 3));
  }
}
