// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;

import static com.android.tools.r8.utils.internal.ConsumerUtils.emptyThrowingConsumer;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
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

// Metro DI (https://zacsweers.github.io/metro/) inspired code, see b/534628768.
@RunWith(Parameterized.class)
public class MetroDILikeInterfaceProviderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean disableHorizontalClassMerging;

  @Parameter(2)
  public boolean addKeepRule;

  @Parameters(name = "{0}, disableHorizontalClassMerging = {1}, addKeepRule = {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    assumeFalse(disableHorizontalClassMerging);
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
        .enableNeverClassInliningAnnotations()
        .applyIf(disableHorizontalClassMerging, R8TestBuilder::noHorizontalClassMerging)
        .applyIf(
            !parameters.isRandomPartialCompilation() && !disableHorizontalClassMerging,
            b ->
                b.addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertMergedInto(Api1Provider.class, Api0Provider.class)
                            .assertMergedInto(Companion1.class, Companion0.class)))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            addKeepRule,
            r -> r.assertSuccessWithOutputLines("Api0", "Api1"),
            !parameters.isRandomPartialCompilation(),
            r -> r.assertFailureWithErrorThatThrows(ClassCastException.class),
            // Result from random partial compilation is not checked.
            emptyThrowingConsumer());
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

  public interface Provider<T> {
    T get();
  }

  public interface ApiBindings {
    default Api0 provideApi0() {
      return (Api0) Factory.create("Api0", Api0.class);
    }

    default Api1 provideApi1() {
      return (Api1) Factory.create("Api1", Api1.class);
    }
  }

  public static class ApiBindingsImpl implements ApiBindings {}

  public static class Companion0 {
    @NeverInline
    public static Api0 provideApi0(ApiBindings instance) {
      return instance.provideApi0();
    }
  }

  public static class Companion1 {
    @NeverInline
    public static Api1 provideApi1(ApiBindings instance) {
      return instance.provideApi1();
    }
  }

  @NeverClassInline
  public static class Api0Provider implements Provider<Api0> {
    private final ApiBindings instance;

    public Api0Provider(ApiBindings instance) {
      this.instance = instance;
    }

    @NeverInline
    @Override
    public Api0 get() {
      return Companion0.provideApi0(instance);
    }
  }

  @NeverClassInline
  public static class Api1Provider implements Provider<Api1> {
    private final ApiBindings instance;

    public Api1Provider(ApiBindings instance) {
      this.instance = instance;
    }

    @NeverInline
    @Override
    public Api1 get() {
      return Companion1.provideApi1(instance);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ApiBindings bindings = new ApiBindingsImpl();
      Api0Provider p0 = new Api0Provider(bindings);
      Api1Provider p1 = new Api1Provider(bindings);
      use(p0.get(), p1.get());
    }

    @NeverInline
    public static void use(Api0 a0, Api1 a1) {
      System.out.println(a0.ping0());
      System.out.println(a1.ping1());
    }
  }
}
