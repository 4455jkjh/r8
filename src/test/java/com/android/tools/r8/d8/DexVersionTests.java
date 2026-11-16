// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexVersionTests extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableDexVersion35UpToApi30;

  @Parameters(name = "{0}, enableDexVersion35UpToApi30={1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  @Rule public TemporaryFolder defaultApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder defaultApiFolder2 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidOApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidOApiFolder2 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidNApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidNApiFolder2 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidSApiFolder1 = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder androidSApiFolder2 = ToolHelper.getTemporaryFolderForTest();

  static class Input1 {}

  static class Input2 {}

  private void runD8(D8Command.Builder builder) throws CompilationFailedException {
    ToolHelper.runD8(
        builder,
        options ->
            options.getTestingOptions().enableDexVersion35UpToApi30 = enableDexVersion35UpToApi30);
  }

  @Before
  public void compileVersions() throws Exception {
    Path inputJar1 = writeClassesToJar(Input1.class);
    D8Command.Builder arrayAccessBuilder = D8Command.builder().addProgramFiles(inputJar1);
    runD8(
        arrayAccessBuilder.setOutput(defaultApiFolder1.getRoot().toPath(), OutputMode.DexIndexed));
    runD8(
        arrayAccessBuilder
            .setOutput(androidOApiFolder1.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.O.getMajor()));
    runD8(
        arrayAccessBuilder
            .setOutput(androidNApiFolder1.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.N.getMajor()));
    runD8(
        arrayAccessBuilder
            .setOutput(androidSApiFolder1.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.S.getMajor()));

    Path inputJar2 = writeClassesToJar(Input2.class);
    D8Command.Builder arithmeticBuilder = D8Command.builder().addProgramFiles(inputJar2);
    runD8(arithmeticBuilder.setOutput(defaultApiFolder2.getRoot().toPath(), OutputMode.DexIndexed));
    runD8(
        arithmeticBuilder
            .setOutput(androidOApiFolder2.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.O.getMajor()));
    runD8(
        arithmeticBuilder
            .setOutput(androidNApiFolder2.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.N.getMajor()));
    runD8(
        arithmeticBuilder
            .setOutput(androidSApiFolder2.getRoot().toPath(), OutputMode.DexIndexed)
            .setMinApiLevel(AndroidApiLevel.S.getMajor()));
  }

  private class EnsureOutputConsumer implements DexIndexedConsumer {

    boolean hasOutput = false;

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      hasOutput = true;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      assertTrue(hasOutput);
    }
  }

  private Path default1() {
    return defaultApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path default2() {
    return defaultApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidO1() {
    return androidOApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidO2() {
    return androidOApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidN1() {
    return androidNApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidN2() {
    return androidNApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidS1() {
    return androidSApiFolder1.getRoot().toPath().resolve("classes.dex");
  }

  private Path androidS2() {
    return androidSApiFolder2.getRoot().toPath().resolve("classes.dex");
  }

  @Test
  public void mergeCompatibleVersions() throws Exception {
    // Verify that we can merge between all versions when no explicit min sdk version is set.
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(default1())
            .addProgramFiles(default2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(default1())
            .addProgramFiles(androidO2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidO2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(androidN1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(default1())
            .addProgramFiles(androidS2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .addProgramFiles(androidS1())
            .addProgramFiles(androidS2()));
    // Verify that we can merge between all version when api version is explicitly
    // set to Android O.
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(default2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(androidO2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(androidO1())
            .addProgramFiles(androidO2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.O.getMajor())
            .addProgramFiles(androidN1())
            .addProgramFiles(androidN2()));
    // Verify that we can merge up to version N when api version is explicitly set to
    // Android N.
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.N.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(default2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.N.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(androidN2()));
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.N.getMajor())
            .addProgramFiles(androidN1())
            .addProgramFiles(androidN2()));
    // Verify that we can merge default api version when api version is explicitly set to
    // Android K.
    runD8(
        D8Command.builder()
            .setProgramConsumer(new EnsureOutputConsumer())
            .setMinApiLevel(AndroidApiLevel.K.getMajor())
            .addProgramFiles(default1())
            .addProgramFiles(default2()));
  }

  private void runMergeWithHigherApiInput(AndroidApiLevel minApiLevel, Path input1, Path input2)
      throws Exception {
    if (enableDexVersion35UpToApi30) {
      runD8(
          D8Command.builder()
              .setProgramConsumer(new EnsureOutputConsumer())
              .setMinApiLevel(minApiLevel.getMajor())
              .addProgramFiles(input1)
              .addProgramFiles(input2));
    } else {
      runMergeWithIncompatibleInput(minApiLevel, input1, input2);
    }
  }

  private void runMergeWithIncompatibleInput(
      AndroidApiLevel minApiLevel, Path input1, Path input2) {
    assertThrows(
        CompilationFailedException.class,
        () ->
            runD8(
                D8Command.builder()
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .setMinApiLevel(minApiLevel.getMajor())
                    .addProgramFiles(input1)
                    .addProgramFiles(input2)));
  }

  @Test
  public void mergeErrorVersionNWithVersionOInput() throws Exception {
    runMergeWithHigherApiInput(AndroidApiLevel.N, default1(), androidO2());
  }

  @Test
  public void mergeErrorVersionKWithVersionOInput() throws Exception {
    runMergeWithHigherApiInput(AndroidApiLevel.K, default1(), androidO2());
  }

  @Test
  public void mergeErrorVersionKWithVersionNInput() throws Exception {
    runMergeWithHigherApiInput(AndroidApiLevel.K, default1(), androidN2());
  }

  // DEX 039 inputs (API 31+) are rejected below API P regardless of enableDexVersion35UpToApi30.
  @Test
  public void mergeErrorVersionOWithVersionSInput() {
    runMergeWithIncompatibleInput(AndroidApiLevel.O, default1(), androidS2());
  }

  @Test
  public void mergeErrorVersionNWithVersionSInput() {
    runMergeWithIncompatibleInput(AndroidApiLevel.N, default1(), androidS2());
  }

  @Test
  public void mergeErrorVersionKWithVersionSInput() {
    runMergeWithIncompatibleInput(AndroidApiLevel.K, default1(), androidS2());
  }
}
