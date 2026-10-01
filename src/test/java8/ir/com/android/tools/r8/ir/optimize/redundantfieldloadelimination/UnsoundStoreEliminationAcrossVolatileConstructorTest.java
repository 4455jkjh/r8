// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnsoundStoreEliminationAcrossVolatileConstructorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class, "all")
        .assertSuccessWithOutputLines(
            "Saw zero: false", "Saw zero read: false", "Saw zero read volatile: false");
  }

  private void runTest(String mode, String methodName, String fieldName, String expectedOutput)
      throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject channelClass = inspector.clazz(Channel.class);
              MethodSubject method = channelClass.uniqueMethodWithOriginalName(methodName);
              FieldSubject seqField = channelClass.uniqueFieldWithOriginalName(fieldName);
              long staticPuts =
                  method
                      .streamInstructions()
                      .filter(InstructionSubject::isStaticPut)
                      .filter(i -> i.getField().isIdenticalTo(seqField.getDexField()))
                      .count();
              assertEquals(2, staticPuts);
            })
        .run(parameters.getRuntime(), Main.class, mode)
        .assertSuccessWithOutputLines(expectedOutput);
  }

  @Test
  public void testVolatileWriteInConstructor() throws Exception {
    runTest("write", "publish", "seq", "Saw zero: false");
  }

  @Test
  public void testVolatileReadInConstructor() throws Exception {
    runTest("read_volatile", "publishReadsVolatile", "seq5", "Saw zero read volatile: false");
  }

  @Test
  public void testNormalReadInConstructor() throws Exception {
    runTest("read", "publishReadsField", "seq3", "Saw zero read: false");
  }

  @NeverClassInline
  public static class State {
    volatile boolean ready;

    @NeverInline
    public State() {
      ready = true;
    }
  }

  @NeverClassInline
  public static class StateReadsField {
    @NeverPropagateValue boolean ready;

    @NeverInline
    public StateReadsField() {
      int x = Channel.seq3;
      if (x != 0) {
        ready = true;
      } else {
        ready = true;
      }
    }
  }

  @NeverClassInline
  public static class StateReadsVolatile {
    @NeverPropagateValue boolean ready;

    @NeverInline
    public StateReadsVolatile() {
      boolean unused = Channel.volatileFlag;
      ready = true;
    }
  }

  public static class Channel {
    static final Object lock = new Object();
    static State SHARED;
    static StateReadsField SHARED3;
    static StateReadsVolatile SHARED5;
    @NeverPropagateValue public static volatile boolean volatileFlag;
    @NeverPropagateValue public static int seq;
    @NeverPropagateValue public static int seq3;
    @NeverPropagateValue public static int seq5;

    @NeverInline
    public static void publish() {
      seq = 1;
      SHARED = new State();
      seq = 2;
    }

    @NeverInline
    public static void publishReadsField() {
      seq3 = 1;
      SHARED3 = new StateReadsField();
      seq3 = 2;
    }

    @NeverInline
    public static void publishReadsVolatile() {
      seq5 = 1;
      SHARED5 = new StateReadsVolatile();
      seq5 = 2;
    }
  }

  public static class Main {
    static volatile boolean running = true;
    static volatile boolean sawZero = false;
    static volatile boolean sawZeroRead = false;
    static volatile boolean sawZeroReadVolatile = false;

    public static void main(String[] args) throws Exception {
      String test = args.length > 0 ? args[0] : "all";
      CountDownLatch startLatch = new CountDownLatch(1);
      Thread reader =
          new Thread(
              () -> {
                startLatch.countDown();
                while (running) {
                  synchronized (Channel.lock) {
                    if (test.equals("all") || test.equals("write")) {
                      State state = Channel.SHARED;
                      if (state != null && state.ready) {
                        int s = Channel.seq;
                        if (s == 0) {
                          sawZero = true;
                          running = false;
                        }
                      }
                    }

                    if (test.equals("all") || test.equals("read")) {
                      StateReadsField state3 = Channel.SHARED3;
                      if (state3 != null && state3.ready) {
                        int s = Channel.seq3;
                        if (s == 0) {
                          sawZeroRead = true;
                          running = false;
                        }
                      }
                    }

                    if (test.equals("all") || test.equals("read_volatile")) {
                      StateReadsVolatile state5 = Channel.SHARED5;
                      if (state5 != null && state5.ready) {
                        int s = Channel.seq5;
                        if (s == 0) {
                          sawZeroReadVolatile = true;
                          running = false;
                        }
                      }
                    }
                  }
                }
              });
      reader.setDaemon(true);
      reader.start();
      startLatch.await();

      try {
        for (int i = 0; i < 10_000 && running; i++) {
          synchronized (Channel.lock) {
            Channel.SHARED = null;
            Channel.seq = 0;
            Channel.SHARED3 = null;
            Channel.seq3 = 0;
            Channel.SHARED5 = null;
            Channel.seq5 = 0;
          }
          Channel.publishReadsField();
          Channel.publishReadsVolatile();
          Channel.publish();
        }
      } finally {
        running = false;
        reader.join(5000);
      }
      if (test.equals("all") || test.equals("write")) {
        System.out.println("Saw zero: " + sawZero);
      }
      if (test.equals("all") || test.equals("read")) {
        System.out.println("Saw zero read: " + sawZeroRead);
      }
      if (test.equals("all") || test.equals("read_volatile")) {
        System.out.println("Saw zero read volatile: " + sawZeroReadVolatile);
      }
    }
  }
}
