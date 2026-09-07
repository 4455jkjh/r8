// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.timing.Timing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JoinInterfaceCollectionWithMissingClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    // Intentionally not adding I.
    AppView<? extends AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            AndroidApp.builder()
                .addProgramFiles(
                    ToolHelper.getClassFileForTestClass(J.class),
                    ToolHelper.getClassFileForTestClass(A.class))
                .build(),
            Timing.empty());
    ClassTypeElement typeA =
        appView.dexItemFactory().createType(descriptor(A.class)).toClassTypeElement(appView);
    assertEquals(A.class.getTypeName(), typeA.getClassType().getTypeName());
    assertTrue(typeA.getInterfaces().hasSingleKnownInterface());
    assertEquals(
        J.class.getTypeName(), typeA.getInterfaces().getSingleKnownInterface().getTypeName());
  }

  interface I {}

  interface J extends I {}

  static class A implements J, I {}
}
