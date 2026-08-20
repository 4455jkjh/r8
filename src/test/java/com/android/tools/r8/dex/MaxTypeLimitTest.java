// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.container.DexContainerFormatTestBase;
import com.android.tools.r8.transformers.ClassTransformer;
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
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class MaxTypeLimitTest extends TestBase {

  private static final int CLASS_COUNT = 33000;
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
    generateApplication(inputApp, CLASS_COUNT);
  }

  private static void generateApplication(Path output, int classCount) {
    List<String> classes = new ArrayList<>(classCount);
    for (int i = 0; i < classCount; ++i) {
      classes.add("package_" + (i % 2 == 0 ? "a" : "b") + ".Class" + i);
    }
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (String typename : classes) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(typename);
      byte[] bytes =
          transformer(ClassStub.class)
              .setClassDescriptor(descriptor)
              .addClassTransformer(
                  new ClassTransformer() {
                    @Override
                    public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                      // Strip methods to only generate class definition without methods.
                      return null;
                    }
                  })
              .transform();
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
    // API level 25 only allows 32767 type-ids, so 33000 classes must split into 2 DEX files.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
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
    // API level 26 allows 65536 type-ids, so 33000 classes fit into 1 DEX file.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
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
  }

  @Test
  public void testD8Api25SingleDexOverflow() throws Exception {
    // The workaround is not applied when compiling to mono-dex (e.g. min-api < 21), so compilation
    // should succeed even with 33000 classes.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
            .setMinApi(AndroidApiLevel.K)
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(1, dexes.size());
    int typeCount = getTypeCount(dexes.get(0));
    assertTrue(
        "Type count " + typeCount + " must be > 32768",
        typeCount > VirtualFile.MAX_ENTRIES_ONLY_32K);
  }

  @Test
  public void testD8LegacyMultidexMainDexRulesOverflow() throws Exception {
    // The workaround is not applied when compiling to legacy multi dex, so compilation should
    // succeed even with 33000 classes in the presence of main dex rules.
    Path output =
        testForD8(Backend.DEX)
            .addProgramFiles(inputApp)
            .setMinApi(AndroidApiLevel.K)
            .addMainDexRules("-keep class ** { *; }")
            .compile()
            .writeToZip();
    List<byte[]> dexes = new ArrayList<>();
    ZipUtils.iter(output, (entry, inputStream) -> dexes.add(ByteStreams.toByteArray(inputStream)));
    assertEquals(1, dexes.size());
    int typeCount = getTypeCount(dexes.get(0));
    assertTrue(
        "Type count " + typeCount + " must be > 32768",
        typeCount > VirtualFile.MAX_ENTRIES_ONLY_32K);
  }

  // Simple stub/template for generating the input classes.
  public static class ClassStub {
    public static void methodStub() {}
  }
}
