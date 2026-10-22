// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/123730537. */
@RunWith(Parameterized.class)
public class InterfaceMethodNameMinifierTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InterfaceMethodNameMinifierTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a", "b", "c");

    testForR8(Backend.DEX)
        .addInnerClasses(InterfaceMethodNameMinifierTest.class)
        .addKeepRules(
            "-keep,allowobfuscation interface * { <methods>; }",
            "-keep,allowobfuscation class * { <methods>; }",
            "-keep interface " + L.class.getTypeName() + " { <methods>; }",
            "-obfuscationdictionary " + dictionary.toString())
        // Minify the interface methods in alphabetic order.
        .addOptionsModification(
            options -> options.testing.minifier.interfaceMethodOrdering = DexMethod::compareTo)
        .compile();
  }

  interface I {}

  interface J extends I {

    // Will be renamed first, to a().
    void a();

    // Will be renamed secondly. Should be renamed to c(), and not b(), because `void K.b()` will
    // be renamed to b() because `void L.b()` is reserved.
    void c();
  }

  interface K extends I {

    // Will be renamed thirdly, to b(), because `void L.b()` is reserved.
    void b();
  }

  interface L {

    // Reserved. Will be renamed together with `void K.b()`.
    void b();
  }

  static class Implementation implements J, K {

    @Override
    public void a() {}

    @Override
    public void b() {}

    @Override
    public void c() {}
  }
}
