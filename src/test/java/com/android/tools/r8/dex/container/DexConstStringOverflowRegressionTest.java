// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexReader;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options ->
                    options.getTestingOptions().collectIndexedItemsCallback =
                        DexConstStringOverflowRegressionTest::fillStringPool)
            .setMinApi(AndroidApiLevel.CINNAMON_BUN)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod.streamInstructions().anyMatch(InstructionSubject::isJumboString));
                });

    List<DexString> strings = parseStringPool(compileResult.writeSingleDexOutputToFile());
    assertEquals(65537, strings.size());
    assertEquals("~~~", strings.get(65536).toString());
  }

  private static void fillStringPool(
      AppView<?> appView, DexProgramClass clazz, IndexedItemCollection indexedItemCollection) {
    assertEquals(Main.class.getTypeName(), clazz.getTypeName());
    // Add strings that sort before "~~~" so that "~~~" has offset 65535 before lazy strings.
    for (int i = 0; i < Constants.U16BIT_MAX - 12; i++) {
      indexedItemCollection.addString(appView.dexItemFactory().createString("A" + i));
    }
  }

  protected static List<DexString> parseStringPool(Path dexFile) throws Exception {
    byte[] dex = Files.readAllBytes(dexFile);
    DexParser<DexProgramClass> parser =
        new DexParser<>(
            new DexReader(ProgramResource.fromBytes(Origin.unknown(), Kind.DEX, dex, null)),
            ClassKind.PROGRAM,
            new InternalOptions());
    parser.populateStrings();
    return Arrays.asList(parser.getIndexedItems().getStringMap());
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
