// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression test for Mockito.spy(Factory.getInstance()) where the factory method is inlined in the
 * first optimization pass. In the second round of tree shaking the spied value is then a phi, which
 * must still be recognized so that the spied class is not made final.
 */
@RunWith(Parameterized.class)
public class MockitoSpyInlinedFactoryTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static class MockitoStub {
    public static <T> T spy(T classToMock) {
      return classToMock;
    }
  }

  public static class Manager {
    private static final Manager INSTANCE = new Manager();

    static Manager getInstance() {
      return INSTANCE;
    }

    public void doThing() {
      System.out.println("did thing");
    }
  }

  public static class ManagerFactory {
    private static Manager sInstanceForTesting;

    @AlwaysInline
    public static Manager getInstance() {
      if (sInstanceForTesting != null) {
        return sInstanceForTesting;
      }
      return Manager.getInstance();
    }

    public static void setInstanceForTesting(Manager instance) {
      sInstanceForTesting = instance;
    }
  }

  public static class TestMain {

    @NeverInline
    private static void setUp() {
      Manager manager = MockitoStub.spy(ManagerFactory.getInstance());
      ManagerFactory.setInstanceForTesting(manager);
    }

    public static void main(String[] args) {
      setUp();
      ManagerFactory.getInstance().doThing();
    }
  }

  private static final String MOCKITO_DESCRIPTOR = "Lorg/mockito/Mockito;";

  private static byte[] rewriteTestMain() throws IOException {
    return transformer(TestMain.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MockitoStub.class), MOCKITO_DESCRIPTOR)
        .transform();
  }

  private static byte[] rewriteMockito() throws IOException {
    return transformer(MockitoStub.class).setClassDescriptor(MOCKITO_DESCRIPTOR).transform();
  }

  private static boolean isInvokeGetInstance(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && instruction.getMethod().getName().toString().equals("getInstance");
  }

  @Test
  public void testR8() throws Exception {
    byte[] mockitoClassBytes = rewriteMockito();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Manager.class, ManagerFactory.class)
        .addProgramClassFileData(rewriteTestMain())
        .addClasspathClassFileData(mockitoClassBytes)
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .addKeepMainRule(TestMain.class)
        .compile()
        .inspect(
            inspector -> {
              // Verify the factory method was inlined so that the spied value is a phi.
              assertTrue(
                  inspector
                      .clazz(TestMain.class)
                      .uniqueMethodWithOriginalName("setUp")
                      .streamInstructions()
                      .noneMatch(MockitoSpyInlinedFactoryTest::isInvokeGetInstance));
              ClassSubject manager = inspector.clazz(Manager.class);
              assertThat(manager, isPresent());
              // The spied class and its virtual methods must not be final.
              assertThat(manager, not(isFinal()));
              manager.forAllMethods(
                  method -> {
                    if (method.isVirtual()) {
                      assertThat(method, not(isFinal()));
                    }
                  });
            })
        .addRunClasspathClassFileData(mockitoClassBytes)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutputLines("did thing");
  }
}
