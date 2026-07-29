// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Removing all the boilerplace from MetroDILikeInterfaceProviderTest demonstrating the core
// Proxy.newProxyInstance reflection.
@RunWith(Parameterized.class)
public class MetroDILikeInterfaceProviderSimplifiedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean addKeepRule;

  @Parameters(name = "{0}, addKeepRule = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    assumeFalse(addKeepRule);
    testForD8(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Api0", "Api1");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(
            addKeepRule,
            b ->
                b.addKeepRules(
                    "-if interface "
                        + getClass().getTypeName()
                        + "$Api? -keep,allowobfuscation,allowoptimization interface "
                        + getClass().getTypeName()
                        + "$Api<1>"))
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            addKeepRule,
            r -> r.assertSuccessWithOutputLines("Api0", "Api1"),
            r -> r.assertFailureWithErrorThatThrows(ClassCastException.class));
  }

  public interface Api0 {
    String ping0();
  }

  public interface Api1 {
    String ping1();
  }

  public static class Factory {
    @NeverInline
    public static Object create(String id, Class<?> clazz) {
      return Proxy.newProxyInstance(
          clazz.getClassLoader(), new Class<?>[] {clazz}, (proxy, method, args) -> id);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      use((Api0) Factory.create("Api0", Api0.class), (Api1) Factory.create("Api1", Api1.class));
    }

    @NeverInline
    public static void use(Api0 a0, Api1 a1) {
      System.out.println(a0.ping0());
      System.out.println(a1.ping1());
    }
  }
}
