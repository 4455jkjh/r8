// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.UnorderedCollectionMatcher.matchesItemsOneToOne;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceOverloadRenamingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep interface " + TargetI.class.getTypeName() + " { void b(); }",
            "-keep,allowobfuscation interface * { <methods>; }")
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // CF will reuse names, DEX should not.
    List<String> expectedNames =
        parameters.isCfRuntime()
            ? ImmutableList.of("a", "b", "b")
            : ImmutableList.of("a", "b", "c");
    List<String> interfaceMethods =
        inspector.clazz(TargetI.class).allMethods().stream()
            .map(MethodSubject::getFinalName)
            .collect(Collectors.toList());
    assertThat(interfaceMethods, matchesItemsOneToOne(expectedNames));
  }

  public interface TargetI {
    void a(float x);

    void b();

    void c(float x);
  }

  @NeverClassInline
  public static class Main implements TargetI {
    @Override
    @NeverInline
    public void a(float x) {
      System.out.println("a");
    }

    @Override
    @NeverInline
    public void b() {
      System.out.println("b");
    }

    @Override
    @NeverInline
    public void c(float x) {
      System.out.println("c");
    }

    public static void main(String[] args) {
      Main main = new Main();
      main.a(0);
      main.b();
      main.c(0);
    }
  }
}
