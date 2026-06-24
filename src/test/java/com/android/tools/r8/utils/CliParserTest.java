// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
    parser.option1("--output", "<file>", "Output file.", (builder, arg) -> builder.output = arg);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--output=foo.bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals("foo.bar", builder.output);
  }

  @Test
  public void testOption1WithSpace() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option1("--output", "<file>", "Output file.", (builder, arg) -> builder.output = arg);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--output", "foo.bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals("foo.bar", builder.output);
  }

  @Test
  public void testOption0() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help.", builder -> builder.help = true);

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--help"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertTrue(builder.help);
  }

  @Test
  public void testOption0WithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help.", builder -> builder.help = true);

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

  @Test
  public void testOption0WithAlternative() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help.", builder -> builder.help = true, "-h");

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"-h"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertTrue(builder.help);
  }

  @Test
  public void testOption1WithAlternative() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option1(
        "--output", "<file>", "Output file.", (builder, arg) -> builder.output = arg, "-o");

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"-o", "foo.bar"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals("foo.bar", builder.output);
  }

  @Test
  public void testUsageMessageWithAlternative() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--help", "Help.", builder -> builder.help = true, "-h");

    String usage = CliParserUtils.getUsageMessage(parser);
    assertTrue(usage.contains("--help"));
    assertTrue(usage.contains("-h"));
  }

  @Test
  public void testUsageMessageWithPrefix() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.prefix0("--flag", "[prop]", "Flag.", (b, s) -> {});
    parser.prefix1("--mapping", "[key]", "<value>", "Mapping.", (b, s, v) -> {});

    String usage = CliParserUtils.getUsageMessage(parser);
    assertTrue(usage.contains("--flag[prop]"));
    assertTrue(usage.contains("--mapping[key] <value>"));
  }

  @Test
  public void testInvalidOptionName() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    assertThrows(
        AssertionError.class,
        () -> parser.option0("-invalid", "Help.", builder -> builder.help = true));
  }

  @Test
  public void testInvalidShorthand() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    assertThrows(
        AssertionError.class,
        () -> parser.option0("--help", "Help.", builder -> builder.help = true, "---h"));
  }

  @Test
  public void testPrefix1WithSpace() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    List<String> mappings = new ArrayList<>();
    parser.prefix1(
        "--mapping",
        "[key]",
        "<value>",
        "Mapping.",
        (builder, suffix, value) -> {
          mappings.add(suffix);
          mappings.add(value);
        });

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--mapping:fun", "a"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals(2, mappings.size());
    assertEquals(":fun", mappings.get(0));
    assertEquals("a", mappings.get(1));
  }

  @Test
  public void testPrefix1WithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    List<String> mappings = new ArrayList<>();
    parser.prefix1(
        "--mapping",
        "[key]",
        "<value>",
        "Mapping.",
        (builder, suffix, value) -> {
          mappings.add(suffix);
          mappings.add(value);
        });

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--mapping:fun=a"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals(2, mappings.size());
    assertEquals(":fun", mappings.get(0));
    assertEquals("a", mappings.get(1));
  }

  @Test
  public void testOverlapPrefixAndOption() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.prefix1("--test", "[key]", "<val>", "Prefix.", (b, s, v) -> {});
    assertThrows(AssertionError.class, () -> parser.option0("--test-now", "Option.", b -> {}));
  }

  @Test
  public void testOverlapOptionAndPrefix() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--test-now", "Option.", b -> {});
    assertThrows(
        AssertionError.class,
        () -> parser.prefix1("--test", "[key]", "<val>", "Prefix.", (b, s, v) -> {}));
  }

  @Test
  public void testOverlapPrefixAndPrefix() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.prefix1("--test", "[key]", "<val>", "Prefix.", (b, s, v) -> {});
    assertThrows(
        AssertionError.class,
        () -> parser.prefix1("--test-now", "[key]", "<val>", "Prefix.", (b, s, v) -> {}));
  }

  @Test
  public void testNoOverlapOptionAndPrefix() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.option0("--test", "Option.", b -> {});
    parser.prefix1("--test-now", "[key]", "<val>", "Prefix.", (b, s, v) -> {});
  }

  @Test
  public void testPrefix0() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    List<String> suffixes = new ArrayList<>();
    parser.prefix0(
        "--flag", "[prop]", "Flag with suffix.", (builder, suffix) -> suffixes.add(suffix));

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--flag:foo"}, builder, errors::add);

    assertTrue(errors.isEmpty());
    assertEquals(1, suffixes.size());
    assertEquals(":foo", suffixes.get(0));
  }

  @Test
  public void testPrefix0WithEquals() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    List<String> suffixes = new ArrayList<>();
    parser.prefix0(
        "--flag", "[prop]", "Flag with suffix.", (builder, suffix) -> suffixes.add(suffix));

    Builder builder = new Builder();
    List<String> errors = new ArrayList<>();
    parser.parse(new String[] {"--flag:foo=bar"}, builder, errors::add);

    assertEquals(1, errors.size());
    assertEquals("Option --flag:foo does not take a value.", errors.get(0));
    assertTrue(suffixes.isEmpty());
  }

  @Test
  public void testOverlapPrefix0AndPrefix1() {
    CliParser<Builder> parser = new CliParser<>("Usage: test");
    parser.prefix0("--test", "[prop]", "Prefix 0.", (b, s) -> {});
    assertThrows(
        AssertionError.class,
        () -> parser.prefix1("--test-now", "[key]", "<val>", "Prefix 1.", (b, s, v) -> {}));
  }
}
