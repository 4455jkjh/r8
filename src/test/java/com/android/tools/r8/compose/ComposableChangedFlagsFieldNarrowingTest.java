// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposableChangedFlagsFieldNarrowingTest extends TestBase {

  private static final String COMPOSABLE_DESCRIPTOR = "Landroidx/compose/runtime/Composable;";

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
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "true", "true", "true");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject restartLambdaClass = inspector.clazz(RestartLambda.class);
              assertThat(restartLambdaClass, isPresent());
              assertEquals(1, restartLambdaClass.allInstanceFields().size());
              FieldSubject changed1Field = restartLambdaClass.allInstanceFields().get(0);
              assertTrue(changed1Field.getField().getType().isIntType());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "true", "true", "true");
  }

  private static Collection<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Composable.class)
            .removeInnerClasses()
            .setClassDescriptor(COMPOSABLE_DESCRIPTOR)
            .transform(),
        transformer(Main.class)
            .removeInnerClasses()
            .replaceClassDescriptorInAnnotations(
                descriptor(Composable.class), COMPOSABLE_DESCRIPTOR)
            .transform(),
        transformer(RestartLambda.class).removeInnerClasses().transform());
  }

  public static class Main {

    static RestartLambda restart;

    public static void main(String[] args) {
      feedScreen(0, 0);
      restart.run();
    }

    @Composable
    @NeverInline
    static void feedScreen(int changed0, int changed1) {
      int dirty0 = unknownInt();
      int dirty1 = changed1;
      if ((changed1 & 6) == 0) {
        dirty1 |= changed() ? 4 : 2;
      }
      if ((changed1 & 48) == 0) {
        dirty1 |= changed() ? 32 : 16;
      }
      if ((changed1 & 384) == 0) {
        dirty1 |= changed() ? 256 : 128;
      }
      int childChanged1 =
          (14 & (dirty0 >> 27))
              | (112 & (dirty1 << 3))
              | (896 & (dirty1 << 3))
              | (0x70000 & (dirty1 << 9));
      feedContent(childChanged1);
      if (System.currentTimeMillis() < 0) {
        feedScreen(changed0 | 1, changed1);
      }
    }

    @Composable
    @NeverInline
    static void feedContent(int changed1) {
      System.out.println((changed1 & 0x380) == 0x080);
      System.out.println((changed1 & 0x70000) == 0x10000);
      if (restart == null) {
        restart = new RestartLambda(changed1);
      }
    }

    @NeverInline
    static boolean changed() {
      return System.currentTimeMillis() < 0;
    }

    @NeverInline
    static int unknownInt() {
      return System.currentTimeMillis() < 0 ? (int) System.nanoTime() : 0;
    }
  }

  @NeverClassInline
  static class RestartLambda implements Runnable {

    final int changed1;

    RestartLambda(int changed1) {
      this.changed1 = changed1;
    }

    @Override
    @NeverInline
    public void run() {
      Main.feedContent(changed1);
    }
  }

  @Retention(RetentionPolicy.CLASS)
  public @interface Composable {}
}
