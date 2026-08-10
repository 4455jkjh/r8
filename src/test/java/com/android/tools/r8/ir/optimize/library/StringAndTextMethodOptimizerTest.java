// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringAndTextMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    byte[] uriBytes =
        transformer(UriStub.class).setClassDescriptor("Landroid/net/Uri;").transform();
    byte[] textUtilsBytes =
        transformer(TextUtilsStub.class).setClassDescriptor("Landroid/text/TextUtils;").transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(UriStub.class), "Landroid/net/Uri;")
            .replaceClassDescriptorInMethodInstructions(
                descriptor(TextUtilsStub.class), "Landroid/text/TextUtils;")
            .transform();
    testForD8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(uriBytes, textUtilsBytes)
        .addDefaultRuntimeLibrary(parameters)
        .release()
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    byte[] uriBytes =
        transformer(UriStub.class).setClassDescriptor("Landroid/net/Uri;").transform();
    byte[] textUtilsBytes =
        transformer(TextUtilsStub.class).setClassDescriptor("Landroid/text/TextUtils;").transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(UriStub.class), "Landroid/net/Uri;")
            .replaceClassDescriptorInMethodInstructions(
                descriptor(TextUtilsStub.class), "Landroid/text/TextUtils;")
            .transform();
    testForR8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(uriBytes, textUtilsBytes)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testUriEncode"),
        not(invokesMethodWithHolder("android.net.Uri")));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testPatternQuote"),
        not(invokesMethodWithHolder(Pattern.class)));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testTextUtilsEquals"),
        not(invokesMethodWithHolder("android.text.TextUtils")));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testTextUtilsIsEmpty"),
        not(invokesMethodWithHolder("android.text.TextUtils")));
  }

  public static class UriStub {
    public static String encode(String s) {
      throw new RuntimeException();
    }

    public static String encode(String s, String allow) {
      throw new RuntimeException();
    }
  }

  public static class TextUtilsStub {
    public static boolean equals(CharSequence a, CharSequence b) {
      throw new RuntimeException();
    }

    public static boolean isEmpty(CharSequence a) {
      throw new RuntimeException();
    }
  }

  static class Main {
    public static void main(String[] args) {
      testUriEncode();
      testPatternQuote();
      testTextUtilsEquals();
      testTextUtilsIsEmpty();
    }

    @NeverInline
    public static void testUriEncode() {
      System.out.println(UriStub.encode("hello world"));
      System.out.println(UriStub.encode("hello world", " "));
    }

    @NeverInline
    public static void testPatternQuote() {
      System.out.println(Pattern.quote("abc.def"));
    }

    @NeverInline
    public static void testTextUtilsEquals() {
      System.out.println(TextUtilsStub.equals("foo", "foo"));
      System.out.println(TextUtilsStub.equals("foo", null));
    }

    @NeverInline
    public static void testTextUtilsIsEmpty() {
      System.out.println(TextUtilsStub.isEmpty(""));
      System.out.println(TextUtilsStub.isEmpty(null));
    }
  }
}
