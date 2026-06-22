// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.partition;

import static com.android.tools.r8.TestRuntime.getDefaultCfRuntime;
import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.retrace.partition.RetracePartitionTestUtils.createPartitionZipConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.retrace.Partition;
import com.android.tools.r8.retrace.PartitionCommand;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.google.common.collect.Sets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartitionMergeTest extends TestBase {

  private static StackTrace expectedStackTrace1;
  private static StackTrace expectedStackTrace2;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack traces by running on the JVM.
    expectedStackTrace1 =
        testForJvm(getStaticTemp())
            .addProgramClasses(A.class)
            .run(getDefaultCfRuntime(), A.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class)
            .getStackTrace();
    expectedStackTrace2 =
        testForJvm(getStaticTemp())
            .addProgramClasses(B.class)
            .run(getDefaultCfRuntime(), B.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class)
            .getStackTrace();
  }

  @Test
  public void testMergeDisjoint() throws Exception {
    // Build two disjoint partitioned mapping files.
    Path part1 = temp.newFile("part1.zip").toPath();
    R8TestRunResult runResult1 =
        testForR8(parameters)
            .addProgramClasses(A.class)
            .addKeepRules(
                "-keep,allowobfuscation class " + A.class.getTypeName(),
                "-keepclassmembers class * { public static void main(...); }",
                "-repackageclasses a")
            .setPartitionMapConsumer(createPartitionZipConsumer(part1))
            .compile()
            .inspect(
                inspector ->
                    assertEquals(
                        "a",
                        inspector.clazz(A.class).getDexProgramClass().getType().getPackageName()))
            .run(parameters.getRuntime(), A.class);

    Path part2 = temp.newFile("part2.zip").toPath();
    R8TestRunResult runResult2 =
        testForR8(parameters)
            .addProgramClasses(B.class)
            .addKeepRules(
                "-keep,allowobfuscation class " + B.class.getTypeName(),
                "-keepclassmembers class * { public static void main(...); }",
                "-repackageclasses b")
            .setPartitionMapConsumer(createPartitionZipConsumer(part2))
            .compile()
            .inspect(
                inspector ->
                    assertEquals(
                        "b",
                        inspector.clazz(B.class).getDexProgramClass().getType().getPackageName()))
            .run(parameters.getRuntime(), B.class);

    // Merge them using Partition.
    Path merged = temp.newFile("merged.zip").toPath();
    Partition.run(
        PartitionCommand.builder()
            .addPartitionMapSupplier(PartitionMappingSupplier.fromPath(part1))
            .addPartitionMapSupplier(PartitionMappingSupplier.fromPath(part2))
            .setPartitionMapId("0123456789012345678901234567890123456789012345678901234567890123")
            .setPartitionMapConsumer(
                PartitionMapZipContainer.createPartitionMapZipContainerConsumer(merged))
            .build());
    assertTrue(Files.exists(merged));

    // Extract partition keys from metadata.
    PartitionMappingSupplier supplier = PartitionMappingSupplier.fromPath(merged);
    TestDiagnosticMessages diagnostics = new TestDiagnosticMessagesImpl();
    MappingPartitionMetadataInternal metadata = supplier.getMetadata(diagnostics);
    assertEquals(Sets.newHashSet("a.a", "b.a"), new HashSet<>(metadata.getPartitionKeys()));
    diagnostics.assertNoMessages();

    // Retrace with the merged mapping file.
    assertThat(
        runResult1.getOriginalStackTrace().retrace(PartitionMappingSupplier.fromPath(merged)),
        isSame(expectedStackTrace1));
    assertThat(
        runResult2.getOriginalStackTrace().retrace(PartitionMappingSupplier.fromPath(merged)),
        isSame(expectedStackTrace2));
  }

  static class A {

    public static void main(String[] args) {
      f();
    }

    static void f() {
      throw new RuntimeException();
    }
  }

  static class B {

    public static void main(String[] args) {
      g();
    }

    static void g() {
      throw new RuntimeException();
    }
  }
}
