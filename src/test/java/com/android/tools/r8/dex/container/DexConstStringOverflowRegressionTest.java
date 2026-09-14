// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexConstStringOverflowRegressionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  @Test
  public void test() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addOptionsModification(
                    options ->
                        options.getTestingOptions().collectIndexedItemsCallback =
                            DexConstStringOverflowRegressionTest::fillStringPool)
                .allowDiagnosticWarningMessages()
                .setMinApi(AndroidApiLevel.CINNAMON_BUN)
                .compile());
  }

  private static void fillStringPool(
      AppView<?> appView, DexProgramClass clazz, IndexedItemCollection indexedItemCollection) {
    if (clazz.getTypeName().equals(Main.class.getTypeName())) {
      // Add strings that sort before "~~~" so that "~~~" has offset 65535 before lazy strings.
      for (int i = 0; i < Constants.U16BIT_MAX - 12; i++) {
        indexedItemCollection.addString(appView.dexItemFactory().createString("A" + i));
      }
    }
  }

  static class Main {

    public static void main(String[] args) {
      foo("~~~");
    }

    public static void foo(String s) {
      System.out.println(s);
    }
  }
}
