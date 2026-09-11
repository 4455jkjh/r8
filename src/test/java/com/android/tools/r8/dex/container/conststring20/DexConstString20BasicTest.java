// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container.conststring20;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.DexInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.ListUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexConstString20BasicTest extends DexConstString20TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testEnabled() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());
    byte[] dex =
        build(
            AndroidApiLevel.MAIN,
            options -> {
              options.testing.enableDexConstString20 = true;
              options.testing.forceDexContainerFormat = true;
            },
            constString -> assertTrue(constString.isConstString20()));

    // Check dex version.
    validate(dex, DexVersion.V42);

    // Check string index.
    List<DexString> strings = parseStringPool(dex);
    int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
    assertTrue(zIndex > Constants.U16BIT_MAX);
  }

  @Test
  public void testContainerFormatDisabled() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());
    byte[] dex =
        build(
            AndroidApiLevel.MAIN,
            options -> {
              options.testing.enableDexConstString20 = true;
              options.testing.forceDexContainerFormat = false;
            },
            constString -> assertTrue(constString.isJumboString()));

    // Check dex version.
    validate(dex, DexVersion.V39);

    // Check string index.
    List<DexString> strings = parseStringPool(dex);
    int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
    assertTrue(zIndex > Constants.U16BIT_MAX);
  }

  @Test
  public void testConstString20Disabled() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());

    byte[] dex =
        build(
            AndroidApiLevel.MAIN,
            options -> {
              options.testing.enableDexConstString20 = false;
              options.testing.forceDexContainerFormat = true;
            },
            constString -> assertTrue(constString.isJumboString()));

    // Check dex version.
    validate(dex, DexVersion.V41);

    // Check string index.
    List<DexString> strings = parseStringPool(dex);
    int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
    assertTrue(zIndex > Constants.U16BIT_MAX);
  }

  @Test
  public void testUnsupportedMinApi() throws Exception {
    byte[] dex =
        build(
            AndroidApiLevel.CINNAMON_BUN,
            options -> {
              options.testing.enableDexConstString20 = true;
              options.testing.forceDexContainerFormat = true;
            },
            constString -> assertTrue(constString.isJumboString()));

    // Check dex version.
    validate(dex, DexVersion.V41);

    // Check string index.
    List<DexString> strings = parseStringPool(dex);
    int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
    assertTrue(zIndex > Constants.U16BIT_MAX);
  }

  private byte[] build(
      AndroidApiLevel minApiLevel,
      Consumer<InternalOptions> optionsModification,
      Consumer<DexInstructionSubject> constStringConsumer)
      throws Exception {
    Path output =
        testForD8()
            .addInnerClasses(getClass())
            // Add 64K strings to the string pool that sort lexicographically before "z".
            // This ensures that "z" cannot be referenced using the const-string[/16] instruction,
            // though it will not be exactly at the 2^16 index.
            .addProgramClassFileData(get32KClassesWith64KStrings())
            .addOptionsModification(optionsModification)
            .setMinApi(minApiLevel)
            .release()
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  mainMethod
                      .streamInstructions()
                      .filter(InstructionSubject::isConstString)
                      .map(InstructionSubject::asDexInstruction)
                      .forEach(constStringConsumer);
                })
            .writeSingleDexOutputToFile();
    return Files.readAllBytes(output);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("z");
    }
  }
}
