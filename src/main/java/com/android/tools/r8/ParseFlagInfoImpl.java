// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ParseFlagInfoImpl implements ParseFlagInfo {

  public static ParseFlagInfoImpl flag0(String flag, String... help) {
    return flag(flag, Collections.emptyList(), Arrays.asList(help));
  }

  public static ParseFlagInfoImpl flag1(String flag, String arg, String... help) {
    return flag(flag, Collections.singletonList(arg), Arrays.asList(help));
  }

  public static ParseFlagInfoImpl flag2(String flag, String arg1, String arg2, String... help) {
    return flag(flag, ImmutableList.of(arg1, arg2), Arrays.asList(help));
  }

  private static String fmt(String flag, List<String> args) {
    StringBuilder builder = new StringBuilder(flag);
    for (String arg : args) {
      builder.append(" ").append(arg);
    }
    return builder.toString();
  }

  public static ParseFlagInfoImpl flag(String flag, List<String> args, List<String> help) {
    return new ParseFlagInfoImpl(flag, fmt(flag, args), Collections.emptyList(), help);
  }

  // Note that the raw flag may be non-representable as in the case of the family of flags for
  // assertions.
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final String rawFlag;

  private final String flagWithArgs;
  private final List<String> alternatives;
  private final List<String> flagHelp;

  public ParseFlagInfoImpl(
      String rawFlag, String flagWithArgs, List<String> alternatives, List<String> flagHelp) {
    // Raw flag may be null if it does not have a unique definition.
    assert flagWithArgs != null;
    assert alternatives != null;
    assert flagHelp != null;
    this.rawFlag = rawFlag;
    this.flagWithArgs = flagWithArgs;
    this.alternatives = alternatives;
    this.flagHelp = flagHelp;
  }

  @Override
  public String getFlagFormat() {
    return flagWithArgs;
  }

  @Override
  public List<String> getFlagFormatAlternatives() {
    return alternatives;
  }

  @Override
  public List<String> getFlagHelp() {
    return flagHelp;
  }
}
