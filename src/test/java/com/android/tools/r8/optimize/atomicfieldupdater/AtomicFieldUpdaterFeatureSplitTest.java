// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.ConsumerUtils;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterFeatureSplitTest extends SplitterTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testUnsafeSyntheticInBase() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(BBaseClass.class)
        .addFeatureSplit(AFeatureClass.class)
        .addKeepMainRule(BBaseClass.class)
        .addKeepClassRules(AFeatureClass.class)
        .setMinApi(parameters)
        .compile()
        // TODO(b/537973315): Unsafe synthetic class is incorrectly placed in feature split.
        .inspect(
            ConsumerUtils.emptyThrowingConsumer(),
            featureInspector ->
                assertThat(
                    featureInspector.clazz(
                        SyntheticItemsTestUtils.syntheticUnsafeClass(AFeatureClass.class)),
                    isPresent()))
        .run(parameters.getRuntime(), BBaseClass.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
  }

  public static class AFeatureClass {}

  public static class BBaseClass {
    public volatile Object myString;

    public static final AtomicReferenceFieldUpdater<BBaseClass, Object> myString$FU =
        AtomicReferenceFieldUpdater.newUpdater(BBaseClass.class, Object.class, "myString");

    public BBaseClass() {
      myString = "Hello";
    }

    public static void main(String[] args) {
      BBaseClass instance = new BBaseClass();
      myString$FU.getAndSet(instance, "World!");
      System.out.println(myString$FU.get(instance));
    }
  }
}
