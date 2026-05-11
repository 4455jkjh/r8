// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.CliParser;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CliParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CliParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static class Builder {
    String output;
    boolean help;
    List<String> positionals = new ArrayList<>();
  }

  @Test
  public void testOption1WithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option1("--output", "<file>", "Output file", (builder, arg) -> builder.output = arg);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--output=foo.bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals("foo.bar", builder.output);
  }

  @Test
  public void testOption1WithSpace() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option1("--output", "<file>", "Output file", (builder, arg) -> builder.output = arg);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--output", "foo.bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals("foo.bar", builder.output);
  }

  @Test
  public void testOption0() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help", builder -> builder.help = true);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--help"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertTrue(builder.help);
  }

  @Test
  public void testOption0WithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help", builder -> builder.help = true);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--help=true"}, builder, errors::add);

    assertEquals(1, errors.size());
    assertEquals("Option --help does not take a value.", errors.get(0));
  }

  @Test
  public void testPositional() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.positional((builder, arg) -> builder.positionals.add(arg));

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"foo", "bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals(ImmutableList.of("foo", "bar"), builder.positionals);
  }

  @Test
  public void testUnknownOption() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--unknown"}, builder, errors::add);

    assertEquals(1, errors.size());
    assertEquals("Unknown option: --unknown", errors.get(0));
  }

  @Test
  public void testUnknownOptionWithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--unknown=value"}, builder, errors::add);

    assertEquals(1, errors.size());
    assertEquals("Unknown option: --unknown=value", errors.get(0));
  }
}
