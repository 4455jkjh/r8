// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposeSourceFileTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final String mappingFoo =
      StringUtils.unixLines("com.foo -> a:", "    # {'id':'sourceFile','fileName':'Foo.kt'}");
  private static final String mappingBar =
      StringUtils.unixLines(
          "com.bar -> c:", "    # {'id':'sourceFile','fileName':'Bar.kt'}", "a -> b:");
  private static final String mappingResult =
      StringUtils.unixLines(
          "com.bar -> c:",
          "    # {'id':'sourceFile','fileName':'Bar.kt'}",
          "com.foo -> b:",
          "    # {'id':'sourceFile','fileName':'Foo.kt'}");

  @Test
  public void testCompose() throws Exception {
    ClassNameMapper mappingForFoo = ClassNameMapper.mapperFromString(mappingFoo);
    ClassNameMapper mappingForBar = ClassNameMapper.mapperFromString(mappingBar);
    String composed = MappingComposer.compose(mappingForFoo, mappingForBar);
    // TODO(b/241763080): Support mapping information.
    assertNotEquals(mappingResult, composed);
  }
}
