// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StoreStoreFenceBeforeOtherwiseRedundantStoreTest extends TestBase {

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
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject dataClassSubject = inspector.clazz(Data.class);
    assertThat(dataClassSubject, isPresent());

    FieldSubject xFieldSubject = dataClassSubject.uniqueFieldWithOriginalName("x");
    assertThat(xFieldSubject, isPresent());

    ClassSubject holderClassSubject = inspector.clazz(Holder.class);
    assertThat(holderClassSubject, isPresent());

    MethodSubject holderInitSubject =
        holderClassSubject.init(Wrapper.class.getTypeName(), Data.class.getTypeName());
    assertThat(
        holderInitSubject, isAbsentIf(parameters.canUseJavaLangInvokeVarHandleStoreStoreFence()));

    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject publishMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("publish");
    assertThat(publishMethodSubject, isPresent());

    if (parameters.canUseJavaLangInvokeVarHandleStoreStoreFence()) {
      assertThat(
          publishMethodSubject,
          invokesMethod(
              Reference.methodFromDescriptor(
                  "Ljava/lang/invoke/VarHandle;", "storeStoreFence", "()V")));
    }

    assertEquals(
        parameters.canUseJavaLangInvokeVarHandleStoreStoreFence() ? 2 : 1,
        countInstancePutInstructions(publishMethodSubject, xFieldSubject));
  }

  private long countInstancePutInstructions(
      MethodSubject methodSubject, FieldSubject fieldSubject) {
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isInstancePut)
        .filter(instruction -> instruction.getField().isIdenticalTo(fieldSubject.getDexField()))
        .count();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Data {
    int x;
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Wrapper {
    Data data = new Data();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Holder {
    final Wrapper wrapper;

    // `d` (`w.data`) is already referenced by `w` before `Holder.<init>` runs, so `d` is reachable
    // through the `final` field `this.wrapper` (`this.wrapper.data`). Per JLS §17.5.1, the
    // final-field freeze (`StoreStoreFence(h)` when inlined) must order `d.x = 1` before `h` is
    // published, even though `d` is neither the operand of `StoreStoreFence(h)` nor a
    // `NewInstance` flowing into `w` or `h` inside `publish`.
    @AlwaysInline
    Holder(Wrapper w, Data d) {
      d.x = 1;
      this.wrapper = w;
    }
  }

  static class Main {

    static Holder shared;
    static byte[] sink;
    static volatile boolean done;

    @NeverInline
    static void publish(Wrapper w) {
      // Inlining `Holder.<init>` emits `d.x = 1; h.wrapper = w; StoreStoreFence(h);`.
      Holder h = new Holder(w, w.data);
      // Publishes `h` to the reader thread; a `StaticPut` on the enclosing class `Main` cannot
      // throw or trigger `<clinit>`, so RLSE does not clear `mostRecentFieldWrites`.
      shared = h;
      // Keeps `shared` published for long enough that the reader thread reliably observes
      // `h.wrapper.data.x == 0` if `d.x = 1` before `shared = h` was eliminated; `NewArrayEmpty`
      // with a non-negative constant size has `instructionInstanceCanThrow() == false`, so RLSE
      // does not clear `mostRecentFieldWrites`.
      sink = new byte[1_000_000];
      // Redundant load elimination rewrites `w.data` to `d`, so this second write targets
      // `(Data.x, d)`. Without clearing `mostRecentFieldWrites` at `StoreStoreFence(h)`,
      // store-after-store elimination deletes the initial `d.x = 1` before `StoreStoreFence(h)`,
      // causing `shared` to be published while `h.wrapper.data.x` is still `0`.
      w.data.x = 1;
    }

    public static void main(String[] args) throws Exception {
      Thread writer =
          new Thread(
              () -> {
                for (int i = 0; i < 50 && !done; i++) {
                  shared = null;
                  publish(new Wrapper());
                }
                done = true;
              });
      writer.start();
      while (!done) {
        Holder h = shared;
        if (h != null && h.wrapper.data.x == 0) {
          done = true;
          writer.join();
          System.out.println("0");
          return;
        }
      }
      writer.join();
      // Reading `sink` prevents R8 from pruning `Main.sink` as a write-only static field and
      // dead-code eliminating the `new byte[1_000_000]` delay allocation in `publish`.
      System.out.println(sink != null ? "1" : "0");
    }
  }
}
