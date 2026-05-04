// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.jumbostring;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class JumboStringTestRunner extends ExamplesTestBase {

  private static final String PACKAGE_PATH =
      JumboStringTestRunner.class.getPackage().getName().replace('.', '/');

  private Path poolJar;

  public JumboStringTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return JumboString.class;
  }

  @Override
  public List<Class<?>> getTestClasses() {
    return ImmutableList.of(getMainClass());
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("zzzz - jumbo string");
  }

  @Test
  @Override
  public void testR8() throws Exception {
    // Disable shrinking and obfuscation so that the fields and their names are retained.
    runTestR8(b -> b.addDontShrink().addDontObfuscate());
  }

  @Override
  protected List<Path> getExtraProgramFiles() throws IOException {
    if (poolJar == null) {
      Path tempFolder = temp.newFolder().toPath();
      poolJar = tempFolder.resolve("pool.jar");

      // We only need to generate two files to get jumbo strings. Each file has 16k static final
      // fields with values, and both the field name and the value will be in the string pool.
      int stringsPerFile = (1 << 14);

      writeToJar(
          poolJar,
          PACKAGE_PATH + "/StringPool0.class",
          generateStringPool(0, stringsPerFile),
          PACKAGE_PATH + "/StringPool1.class",
          generateStringPool(1, stringsPerFile));
    }
    return ImmutableList.of(poolJar);
  }

  private static void writeToJar(
      Path jarPath, String name0, byte[] bytes0, String name1, byte[] bytes1) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(new ZipEntry(name0));
      out.write(bytes0);
      out.closeEntry();
      out.putNextEntry(new ZipEntry(name1));
      out.write(bytes1);
      out.closeEntry();
    }
  }

  /**
   * fileNumber * stringsPerFile is the starting value of the field name and string content, which
   * is incremented stringsPerFile times to make the other fields.
   */
  private static byte[] generateStringPool(int fileNumber, int stringsPerFile) {
    ClassWriter cw = new ClassWriter(0);
    String className = PACKAGE_PATH + "/StringPool" + fileNumber;
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        className,
        null,
        "java/lang/Object",
        null);

    int offset = fileNumber * stringsPerFile;
    for (int i = offset; i < offset + stringsPerFile; i++) {
      cw.visitField(
              Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
              "s" + i,
              "Ljava/lang/String;",
              null,
              String.valueOf(i))
          .visitEnd();
    }
    cw.visitEnd();
    return cw.toByteArray();
  }
}
