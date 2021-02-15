// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProcessKotlinStdlibTest extends KotlinTestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public ProcessKotlinStdlibTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private void test(Collection<String> rules) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addKeepRules(rules)
        .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
        .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
        .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));
  }

  @Test
  public void testAsIs() throws Exception {
    test(ImmutableList.of("-dontshrink", "-dontoptimize", "-dontobfuscate"));
  }

  @Test
  public void testDontShrinkAndDontOptimize() throws Exception {
    test(ImmutableList.of("-dontshrink", "-dontoptimize"));
  }

  @Test
  public void testDontShrinkAndDontObfuscate() throws Exception {
    test(ImmutableList.of("-dontshrink", "-dontobfuscate"));
  }

  @Test
  public void testDontShrink() throws Exception {
    test(ImmutableList.of("-dontshrink"));
  }

  @Test
  public void testDontOptimize() throws Exception {
    test(ImmutableList.of("-dontoptimize"));
  }

  @Test
  public void testDontObfuscate() throws Exception {
    test(ImmutableList.of("-dontobfuscate"));
  }
}
