// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PerClassFileSyntheticsDeterminismTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    byte[] transformedA = transformer(A.class).setNest(B.class, A.class).transform();
    byte[] transformedB = transformer(B.class).setNest(B.class, A.class).transform();
    String hash = compilePerClassFile(transformedA, transformedB);
    String hash2 =
        compilePerClassFile(
            transformedA, transformedB, ToolHelper.getClassAsBytes(UnrelatedWithLambda.class));
    assertEquals(hash, hash2);
  }

  private String compilePerClassFile(byte[]... programClasses) throws Exception {
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(programClasses)
            .setIntermediate(true)
            .setMinApi(parameters)
            .setOutputMode(OutputMode.DexFilePerClass)
            .compile();
    // Output of A and B should be deterministic.
    Hasher hasher = Hashing.sha256().newHasher();
    try (Closer closer = Closer.create()) {
      for (ProgramResource dex : compileResult.getApp().getDexProgramResourcesForTesting()) {
        if (dex.getClassDescriptors().contains(descriptor(A.class))
            || dex.getClassDescriptors().contains(descriptor(B.class))) {
          hasher.putBytes(ByteStreams.toByteArray(closer.register(dex.getByteStream())));
        }
      }
    }
    return hasher.hash().toString();
  }

  public static class A {}

  public static class B {
    public static Object list() {
      return List.of("a");
    }

    public static Object set() {
      return Set.of("b");
    }

    public static Object map() {
      return Map.of("k", "v");
    }
  }

  public static class UnrelatedWithLambda {
    public static Runnable r() {
      return () -> {};
    }
  }
}
