// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DumpFeatureSplitInputsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    Path dumpFile = temp.newFolder().toPath().resolve("dump.zip");
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class)
        .addFeatureSplit(Feature1.class)
        .addFeatureSplit(Feature2.class)
        .addKeepAllClassesRule()
        .setMinApi(parameters)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Dumped compilation inputs to:"))));

    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    assertEquals(
        ImmutableList.of(binaryName(Base.class) + ".class"), getEntries(dump.getProgramArchive()));
    List<Path> featureArchives = new ArrayList<>();
    dump.forEachFeatureArchive(
        (featureArchive, unusedResources) -> featureArchives.add(featureArchive));
    List<List<String>> featureEntries = new ArrayList<>();
    for (Path featureArchive : featureArchives) {
      featureEntries.add(getEntries(featureArchive));
    }
    assertEquals(
        ImmutableList.of(
            ImmutableList.of(binaryName(Feature1.class) + ".class"),
            ImmutableList.of(binaryName(Feature2.class) + ".class")),
        featureEntries);
  }

  private static List<String> getEntries(Path archive) throws Exception {
    List<String> entries = new ArrayList<>();
    ZipUtils.iter(archive, entry -> entries.add(entry.getName()));
    return entries;
  }

  static class Base {}

  static class Feature1 {}

  static class Feature2 {}
}
