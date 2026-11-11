// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dagger;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class DaggerTestBase extends TestBase {

  public static Path compileExample(boolean fastInit, String... relativeResourcePaths)
      throws Exception {
    List<Path> sources =
        Arrays.stream(relativeResourcePaths)
            .map(path -> ToolHelper.getAbsoluteResourceAsReadOnlyFile("examplesDagger/" + path))
            .collect(ImmutableList.toImmutableList());
    List<String> options =
        fastInit ? ImmutableList.of("-Adagger.fastInit=enabled") : ImmutableList.of();
    return DaggerUtils.compileWithAnnotationProcessing("1.8", sources, ImmutableList.of(), options);
  }

  public static Map<Boolean, Path> compileExampleWithAndWithoutFastInit(
      String... relativeResourcePaths) throws Exception {
    ImmutableMap.Builder<Boolean, Path> jars = ImmutableMap.builder();
    for (boolean fastInit : BooleanUtils.values()) {
      jars.put(fastInit, compileExample(fastInit, relativeResourcePaths));
    }
    return jars.build();
  }

  public static Collection<Path> getProgramFiles(Path compiledExampleJar) {
    return ImmutableList.<Path>builder()
        .add(compiledExampleJar)
        .addAll(DaggerUtils.getDaggerRuntime())
        .build();
  }

  public static void configureAssertions(R8TestBuilder<?, ?, ?> builder, boolean enableAssertions) {
    builder.applyIf(
        enableAssertions,
        b ->
            b.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        b ->
            b.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions));
  }

  public static Set<String> presentConstStrings(
      CodeInspector inspector, Collection<String> candidates) {
    return candidates.stream()
        .filter(
            candidate ->
                inspector.allClasses().stream()
                    .flatMap(c -> c.allMethods().stream())
                    .filter(m -> m.getMethod().hasCode())
                    .anyMatch(
                        m -> m.streamInstructions().anyMatch(i -> i.isConstString(candidate))))
        .collect(Collectors.toSet());
  }
}
