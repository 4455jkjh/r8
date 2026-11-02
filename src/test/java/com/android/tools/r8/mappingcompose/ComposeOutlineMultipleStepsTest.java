// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.mappingcompose.ComposeTestHelpers.doubleToSingleQuote;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposeOutlineMultipleStepsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines(
          "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
          "outline.Class -> a:",
          "    1:2:int some.inlinee():75:76 -> a",
          "    1:2:int outline():0 -> a",
          "    # { 'id':'com.android.tools.r8.outline' }",
          "outline.Callsite -> x:",
          "    4:4:int outlineCaller(int):23:23 -> s",
          "    5:5:int foo.bar.baz.outlineCaller(int):98:98 -> s",
          "    5:5:int outlineCaller(int):24 -> s",
          "    27:27:int outlineCaller(int):0:0 -> s",
          "    # { 'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions': { '1': 4, '2': 5 },"
              + "'outline':'La;a()I' }");
  private static final String mappingBar =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "a -> b:",
          "    4:5:int a():1:2 -> m",
          "x -> y:",
          "    42:42:int s(int):27:27 -> o");
  private static final String mappingBaz =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "b -> c:",
          "    6:7:int m():4:5 -> n",
          "y -> z:",
          "    10:10:int o(int):42:42 -> p");
  private static final String mappingResult =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "outline.Callsite -> z:",
          "    10:10:int outlineCaller(int):0:0 -> p",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'4':11,'5':12},"
              + "'outline':'Lc;m()I'}",
          "    11:11:int outlineCaller(int):23:23 -> p",
          "    12:12:int foo.bar.baz.outlineCaller(int):98:98 -> p",
          "    12:12:int outlineCaller(int):24 -> p",
          "outline.Class -> c:",
          "    6:7:int some.inlinee():75:76 -> n",
          "    6:7:int outline():0 -> n",
          "    # {'id':'com.android.tools.r8.outline'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromStringWithPreamble(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromStringWithPreamble(mappingBar);
    ClassNameMapper mappingForBaz = ClassNameMapper.mapperFromStringWithPreamble(mappingBaz);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar, mappingForBaz);
    assertEquals(mappingResult, doubleToSingleQuote(composed));
  }
}
