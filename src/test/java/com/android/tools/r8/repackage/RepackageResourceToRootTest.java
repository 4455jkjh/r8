// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V1_8;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;

@RunWith(Parameterized.class)
public class RepackageResourceToRootTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    DataResourceConsumerForTesting dataResourceConsumer = new DataResourceConsumerForTesting();
    testForR8(parameters.getBackend())
        // Code is required in a.b to register the package.
        .addProgramClassFileData(makeEmptyClass("a/b/Main"))
        .addDataResources(DataEntryResource.fromString("a/b/C.txt", Origin.unknown(), "content"))
        .addKeepRules(
            "-adaptresourcefilenames",
            "-repackageclasses ''",
            "-keep,allowobfuscation class a.b.Main")
        .setMinApi(parameters)
        .addOptionsModification(options -> options.dataResourceConsumer = dataResourceConsumer)
        .compile();

    assertThat(
        dataResourceConsumer.getAll().keySet(),
        UnorderedCollectionMatcher.matchesItemsOneToOne(ImmutableList.of("C.txt")));
  }

  private byte[] makeEmptyClass(String className) {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);
    cw.visitEnd();
    return cw.toByteArray();
  }
}
