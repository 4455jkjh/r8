// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Tests that the class to DEX distribution leaves the requested leg room for refinement. */
@RunWith(Parameterized.class)
public class DexDistributionLegRoomTest extends TestBase {

  private static final int CLASS_COUNT = 64;
  private static final int FIELDS_PER_CLASS = 1024;
  private static Path inputApp;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public int legRoomPercentage;

  @Parameters(name = "{0}, leg room: {1}%")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), ImmutableList.of(0, 50));
  }

  @BeforeClass
  public static void generateTestApplication() {
    // 64 classes with 1024 fields each, exactly the 65536 field-ids allowed in one DEX file.
    inputApp = getStaticTemp().getRoot().toPath().resolve("input_app.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(inputApp);
    for (int c = 0; c < CLASS_COUNT; ++c) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor("package_a.Class" + c);
      String internalName = descriptor.substring(1, descriptor.length() - 1);
      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
          internalName,
          null,
          "java/lang/Object",
          null);
      for (int f = 0; f < FIELDS_PER_CLASS; ++f) {
        cw.visitField(Opcodes.ACC_PUBLIC, "f" + f, "I", null, null).visitEnd();
      }
      cw.visitEnd();
      consumer.accept(ByteDataView.of(cw.toByteArray()), descriptor, null);
    }
    consumer.finished(null);
  }

  @Test
  public void test() throws Exception {
    // API level 36 allows 65536 field-ids, so without leg room all classes fit into one DEX file.
    testForD8(Backend.DEX)
        .addProgramFiles(inputApp)
        .setMinApi(AndroidApiLevel.BAKLAVA)
        .release()
        .addOptionsModification(
            options ->
                options.testing.classToDexDistributionRefinementLegRoomPercentage =
                    legRoomPercentage)
        .compile()
        .inspectMultiDex(
            primaryDexInspector -> assertFalse(primaryDexInspector.allClasses().isEmpty()),
            secondaryDexInspector ->
                assertEquals(legRoomPercentage == 0, secondaryDexInspector.allClasses().isEmpty()));
  }
}
