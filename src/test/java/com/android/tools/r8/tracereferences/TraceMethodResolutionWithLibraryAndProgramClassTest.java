// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceMethodResolutionWithLibraryAndProgramClassTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  static class SeenReferencesConsumer implements TraceReferencesConsumer {

    private final Set<MethodReference> seenMethods = new HashSet<>();
    private final Set<MethodReference> seenMissingMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      if (tracedMethod.isMissingDefinition()) {
        seenMissingMethods.add(tracedMethod.getReference());
      } else {
        seenMethods.add(tracedMethod.getReference());
      }
    }
  }

  @Test
  public void testValidResolution() throws Exception {
    Path dir = temp.newFolder().toPath();
    Path libJar =
        ZipBuilder.builder(dir.resolve("lib.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(A.class),
                ToolHelper.getClassFileForTestClass(B.class))
            .build();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(A.class),
                ToolHelper.getClassFileForTestClass(B.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(Main.class))
            .build();
    SeenReferencesConsumer consumer = new SeenReferencesConsumer();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addLibraryFiles(libJar)
            .addTargetFiles(targetJar)
            .addSourceFiles(sourceJar)
            .setConsumer(consumer)
            .build());
    ImmutableSet<MethodReference> foundSet =
        ImmutableSet.of(
            Reference.methodFromMethod(A.class.getMethod("foo")),
            Reference.methodFromMethod(A.class.getMethod("bar")),
            Reference.methodFromMethod(B.class.getMethod("baz")),
            Reference.methodFromMethod(B.class.getMethod("qux")));
    assertEquals(foundSet, consumer.seenMethods);
    assertEquals(Collections.emptySet(), consumer.seenMissingMethods);
  }

  // A is added to both library and program, but the program one is missing the methods {foo,bar}
  public static class A {

    public static boolean foo() {
      return true;
    }

    public int bar() {
      return 42;
    }
  }

  // B is added to both library and program, but the library one is missing the methods {baz,qux}
  public static class B {

    public static boolean baz() {
      return false;
    }

    public int qux() {
      return 42;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = getAInstance(null);
      B b = getBInstance(null);
      int value = (A.foo() && B.baz()) ? a.bar() : b.qux();
    }

    private static A getAInstance(Object o) {
      return (A) o;
    }

    private static B getBInstance(Object o) {
      return (B) o;
    }
  }
}
