// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.container.DexContainerFormatTestBase;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MaxTypeLimitTest extends TestBase {

  private static final int CLASS_COUNT_32K = 33000;
  private static final int CLASS_COUNT_64K = 65533;
  private static final int TOTAL_TYPES_64K = 65536;
  private static Path inputApp32K;
  private static Path inputApp64K;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void generateTestApplication() {
    inputApp32K = getStaticTemp().getRoot().toPath().resolve("input_app_32k.jar");
    generateApplication(inputApp32K, CLASS_COUNT_32K, false);
    inputApp64K = getStaticTemp().getRoot().toPath().resolve("input_app_64k.jar");
    generateApplication(inputApp64K, CLASS_COUNT_64K, true);
  }

  private static void generateApplication(Path output, int classCount, boolean includeMain) {
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (int i = 0; i < classCount; ++i) {
      String typename = "package_" + (i % 2 == 0 ? "a" : "b") + ".Class" + i;
      String descriptor = DescriptorUtils.javaTypeToDescriptor(typename);
      String internalName = descriptor.substring(1, descriptor.length() - 1);
      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
          internalName,
          null,
          "java/lang/Object",
          null);
      if (includeMain && i == 0) {
        MethodVisitor mv =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      byte[] bytes = cw.toByteArray();
      consumer.accept(ByteDataView.of(bytes), descriptor, null);
    }
    consumer.finished(null);
  }

  private static int getTypeCount(byte[] dexBytes) {
    CompatByteBuffer buffer = CompatByteBuffer.wrap(dexBytes);
    DexContainerFormatTestBase.setByteOrder(buffer);
    return DexContainerFormatTestBase.getSizeFromMap(Constants.TYPE_TYPE_ID_ITEM, buffer, 0);
  }

  @Test
  public void testD8Api25MultidexSplitOnTypes() throws Exception {
    // API level 25 only allows 32768 type-ids, so 33000 classes must split into 2 DEX files.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp32K)
            .setMinApi(AndroidApiLevel.N_MR1)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(2, dexes.size());
    for (byte[] dex : dexes) {
      int typeCount = getTypeCount(dex);
      assertTrue(
          "Type count " + typeCount + " must be <= 32768",
          typeCount <= VirtualFile.MAX_ENTRIES_ONLY_32K);
    }
  }

  @Test
  public void testD8Api26SingleDex() throws Exception {
    // API level 26 allows 65535 type-ids, so 33000 classes fit into 1 DEX file.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp32K)
            .setMinApi(AndroidApiLevel.O)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(1, dexes.size());
    int typeCount = getTypeCount(dexes.get(0));
    assertTrue(
        "Type count " + typeCount + " must be > 32768",
        typeCount > VirtualFile.MAX_ENTRIES_ONLY_32K);
    assertTrue(
        "Type count " + typeCount + " must be <= 65535",
        typeCount <= VirtualFile.MAX_ENTRIES_ONLY_65535);
  }

  @Test
  public void testD8Api25SingleDexOverflow() throws Exception {
    // Compiling with mono-dex (e.g. min-api < 21) with 33000 classes should fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp32K)
                .setMinApi(AndroidApiLevel.K)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticType(DexFileOverflowDiagnostic.class),
                                diagnosticMessage(
                                    containsString(
                                        "# types: " + (CLASS_COUNT_32K + 1) + " > 32768"))))));
    // Should not fail when alwaysAllow64KTypeIds is set.
    testForD8(Backend.DEX)
        .addProgramFiles(inputApp32K)
        .setMinApi(AndroidApiLevel.K)
        .addOptionsModification(options -> options.alwaysAllow64KTypeIds = true)
        .compile();
  }

  @Test
  public void testD8LegacyMultidexMainDexRulesOverflow() throws Exception {
    // Compiling with main-dex rules that keep all 33000 classes in main dex on legacy multidex (API
    // 19) should fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp32K)
                .setMinApi(AndroidApiLevel.K)
                .addMainDexRules("-keep class ** { *; }")
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticType(DexFileOverflowDiagnostic.class),
                                diagnosticMessage(
                                    containsString(
                                        "Cannot fit requested classes in the main-dex file (#"
                                            + " types: "
                                            + (CLASS_COUNT_32K + 1)
                                            + " > 32768)"))))));

    // Should not fail when alwaysAllow64KTypeIds is set.
    testForD8(Backend.DEX)
        .addProgramFiles(inputApp32K)
        .setMinApi(AndroidApiLevel.K)
        .addMainDexRules("-keep class ** { *; }")
        .addOptionsModification(options -> options.alwaysAllow64KTypeIds = true)
        .compile();
  }

  @Test
  public void testD8Api26MultidexSplitOn65KTypes() throws Exception {
    // API level 26 allows at most 65535 type-ids, so 65536 types must split into 2 DEX files.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp64K)
            .setMinApi(AndroidApiLevel.O)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(2, dexes.size());
    for (byte[] dex : dexes) {
      int typeCount = getTypeCount(dex);
      assertTrue(
          "Type count " + typeCount + " must be <= 65535",
          typeCount <= VirtualFile.MAX_ENTRIES_ONLY_65535);
    }
  }

  @Test
  public void testD8Api26SingleDexOverflow() throws Exception {
    // Compiling with mono-dex with 65536 types on API 19 should fail because 65536 > 32768.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp64K)
                .setMinApi(AndroidApiLevel.K)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticType(DexFileOverflowDiagnostic.class),
                                diagnosticMessage(
                                    containsString("# types: " + TOTAL_TYPES_64K + " > 32768"))))));

    // Compiling with alwaysAllow64KTypeIds allows at most 65535 types, so 65536 types still fails.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp64K)
                .setMinApi(AndroidApiLevel.K)
                .addOptionsModification(options -> options.alwaysAllow64KTypeIds = true)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticType(DexFileOverflowDiagnostic.class),
                                diagnosticMessage(
                                    containsString("# types: " + TOTAL_TYPES_64K + " > 65535"))))));
  }
}
