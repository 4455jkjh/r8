// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container.conststring20;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.code.DexConstString20;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.ListUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexConstString20D8MergeTest extends DexConstString20TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testConstString20ToConstString16() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());

    byte[] dex = buildConstString20();
    validate(dex, DexVersion.V42);

    {
      // Check string index.
      List<DexString> strings = parseStringPool(dex);
      int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
      assertTrue(zIndex > Constants.U16BIT_MAX);
    }

    // Recompile to DEX. Since all the unused strings added above will be removed, the output should
    // not have any const-string/20 or const-string/jumbo instructions.
    Path mergeOutput =
        testForD8()
            .addProgramDexFileData(dex)
            .addOptionsModification(
                options -> {
                  options.getTestingOptions().enableDexConstString20 = true;
                  options.getTestingOptions().forceDexContainerFormat = true;
                })
            .release()
            .setMinApi(AndroidApiLevel.MAIN)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod
                          .streamInstructions()
                          .noneMatch(InstructionSubject::isConstString20));
                  assertTrue(
                      mainMethod.streamInstructions().noneMatch(InstructionSubject::isJumboString));
                })
            .writeSingleDexOutputToFile();

    // Check dex version.
    byte[] mergeDex = Files.readAllBytes(mergeOutput);
    validate(mergeDex, DexVersion.V42);

    {
      // Check string index.
      List<DexString> strings = parseStringPool(mergeDex);
      int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
      assertTrue(zIndex < DexConstString20.MIN_REFERENCE_INCLUSIVE);
    }
  }

  @Test
  public void testConstString20ToConstStringJumbo() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());

    byte[] dex = buildConstString20();
    validate(dex, DexVersion.V42);

    {
      // Check string index.
      List<DexString> strings = parseStringPool(dex);
      int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
      assertTrue(zIndex > Constants.U16BIT_MAX);
    }

    // Recompile to DEX.
    Path mergeOutput =
        testForD8()
            .addProgramDexFileData(dex)
            .addOptionsModification(
                options -> {
                  options.getTestingOptions().enableDexConstString20 = true;
                  options.getTestingOptions().forceDexContainerFormat = true;
                  options.getTestingOptions().collectIndexedItemsCallback =
                      DexConstString20D8MergeTest::fillStringPoolJumbo;
                })
            .release()
            .setMinApi(AndroidApiLevel.MAIN)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod.streamInstructions().anyMatch(InstructionSubject::isJumboString));
                })
            .writeSingleDexOutputToFile();

    // Check dex version.
    byte[] mergeDex = Files.readAllBytes(mergeOutput);
    validate(mergeDex, DexVersion.V42);

    {
      // Check string index.
      List<DexString> strings = parseStringPool(mergeDex);
      int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
      assertTrue(zIndex > DexConstString20.MAX_REFERENCE_INCLUSIVE);
    }
  }

  private byte[] buildConstString20() throws Exception {
    Path output =
        testForD8()
            .addInnerClasses(getClass())
            .addOptionsModification(
                options -> {
                  options.getTestingOptions().enableDexConstString20 = true;
                  options.getTestingOptions().forceDexContainerFormat = true;
                  options.getTestingOptions().collectIndexedItemsCallback =
                      DexConstString20D8MergeTest::fillStringPool64K;
                })
            .release()
            .setMinApi(AndroidApiLevel.MAIN)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod
                          .streamInstructions()
                          .anyMatch(InstructionSubject::isConstString20));
                })
            .writeSingleDexOutputToFile();
    return Files.readAllBytes(output);
  }

  private static void fillStringPool64K(
      AppView<?> appView, DexProgramClass clazz, IndexedItemCollection indexedItemCollection) {
    fillStringPool(appView, clazz, indexedItemCollection, Constants.U16BIT_SIZE);
  }

  private static void fillStringPoolJumbo(
      AppView<?> appView, DexProgramClass clazz, IndexedItemCollection indexedItemCollection) {
    fillStringPool(
        appView, clazz, indexedItemCollection, Constants.U16BIT_SIZE + Constants.U20BIT_SIZE);
  }

  private static void fillStringPool(
      AppView<?> appView,
      DexProgramClass clazz,
      IndexedItemCollection indexedItemCollection,
      int stringsToAdd) {
    assertEquals(Main.class.getTypeName(), clazz.getTypeName());
    for (int i = 0; i < stringsToAdd; i++) {
      indexedItemCollection.addString(appView.dexItemFactory().createString("A" + i));
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("z");
    }
  }
}
