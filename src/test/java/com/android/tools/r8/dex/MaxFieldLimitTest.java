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
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MaxFieldLimitTest extends TestBase {

  private static final int CLASS_COUNT = 64;
  private static final int FIELDS_PER_CLASS = 1024;
  private static final int TOTAL_FIELD_COUNT = CLASS_COUNT * FIELDS_PER_CLASS; // 65536
  private static Path inputApp;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void generateTestApplication() {
    inputApp = getStaticTemp().getRoot().toPath().resolve("input_app.jar");
    generateApplication(inputApp, CLASS_COUNT, FIELDS_PER_CLASS);
  }

  private static void generateApplication(Path output, int classCount, int fieldsPerClass) {
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (int c = 0; c < classCount; ++c) {
      String typename = "package_" + (c % 2 == 0 ? "a" : "b") + ".Class" + c;
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
      for (int f = 0; f < fieldsPerClass; ++f) {
        cw.visitField(Opcodes.ACC_PUBLIC, "f" + f, "I", null, null).visitEnd();
      }
      cw.visitEnd();
      byte[] bytes = cw.toByteArray();
      consumer.accept(ByteDataView.of(bytes), descriptor, null);
    }
    consumer.finished(null);
  }

  private static int getFieldCount(byte[] dexBytes) {
    CompatByteBuffer buffer = CompatByteBuffer.wrap(dexBytes);
    DexContainerFormatTestBase.setByteOrder(buffer);
    return DexContainerFormatTestBase.getSizeFromMap(Constants.TYPE_FIELD_ID_ITEM, buffer, 0);
  }

  @Test
  public void testD8Api35MultidexSplitOnFields() throws Exception {
    // API level 35 only allows 65535 field-ids, so 65536 fields must split into 2 DEX files.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
            .setMinApi(AndroidApiLevel.V)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(2, dexes.size());
    for (byte[] dex : dexes) {
      int fieldCount = getFieldCount(dex);
      assertTrue(
          "Field count " + fieldCount + " must be <= 65535",
          fieldCount <= VirtualFile.MAX_ENTRIES_ONLY_65535);
    }
  }

  @Test
  public void testD8Api36SingleDex() throws Exception {
    // API level 36 allows 65536 field-ids, so 65536 fields fit into 1 DEX file.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
            .setMinApi(AndroidApiLevel.BAKLAVA)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(1, dexes.size());
    int fieldCount = getFieldCount(dexes.get(0));
    assertEquals(TOTAL_FIELD_COUNT, fieldCount);
    assertTrue(
        "Field count " + fieldCount + " must be > 65535",
        fieldCount > VirtualFile.MAX_ENTRIES_ONLY_65535);
  }

  @Test
  public void testD8Api35SingleDexOverflow() throws Exception {
    // Compiling with mono-dex (e.g. min-api < 21) with 65536 fields should fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp)
                .setMinApi(AndroidApiLevel.K)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(
                            allOf(
                                diagnosticType(DexFileOverflowDiagnostic.class),
                                diagnosticMessage(
                                    containsString(
                                        "# fields: "
                                            + TOTAL_FIELD_COUNT
                                            + " > "
                                            + VirtualFile.MAX_ENTRIES_ONLY_65535))))));
    // Should not fail when alwaysAllow64KFieldIds is set.
    testForD8(Backend.DEX)
        .addProgramFiles(inputApp)
        .setMinApi(AndroidApiLevel.K)
        .addOptionsModification(options -> options.alwaysAllow64KFieldIds = true)
        .compile();
  }

  @Test
  public void testD8LegacyMultidexMainDexRulesOverflow() throws Exception {
    // Compiling with main-dex rules that keep all 65536 fields in main dex on legacy multidex (API
    // 19) should fail.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(inputApp)
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
                                            + " fields: "
                                            + TOTAL_FIELD_COUNT
                                            + " > "
                                            + VirtualFile.MAX_ENTRIES_ONLY_65535))))));

    // Should not fail when alwaysAllow64KFieldIds is set.
    testForD8(Backend.DEX)
        .addProgramFiles(inputApp)
        .setMinApi(AndroidApiLevel.K)
        .addMainDexRules("-keep class ** { *; }")
        .addOptionsModification(options -> options.alwaysAllow64KFieldIds = true)
        .compile();
  }
}
