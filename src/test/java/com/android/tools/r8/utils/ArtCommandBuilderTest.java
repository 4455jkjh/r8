// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtCommandBuilderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  private static final String SCRIPT =
      System.getProperty("os.name").startsWith("Linux") ? "/bin/bash " : "tools/docker/run.sh ";
  private static final String FORK_JOIN_PARALLELISM =
      " -Djava.util.concurrent.ForkJoinPool.common.parallelism="
          + Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

  @Before
  public void setUp() {
    Assume.assumeTrue(ToolHelper.artSupported());
    Assume.assumeTrue(getVm().getKind() == Kind.HOST);
  }

  private DexVm getVm() {
    return parameters.getDexVm();
  }

  @Test
  public void noArguments() {
    ArtCommandBuilder builder = new ArtCommandBuilder(getVm());
    Assert.assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + FORK_JOIN_PARALLELISM, builder.build());
  }

  @Test
  public void simple() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder.appendClasspath("xxx.dex").setMainClass("Test");
    assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + FORK_JOIN_PARALLELISM + " -cp xxx.dex Test",
        builder.build());
  }

  @Test
  public void classpath() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder.appendClasspath("xxx.dex").appendClasspath("yyy.jar");
    assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + FORK_JOIN_PARALLELISM + " -cp xxx.dex:yyy.jar",
        builder.build());
  }

  @Test
  public void artOptions() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder.appendArtOption("-d").appendArtOption("--test");
    assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + " -d --test" + FORK_JOIN_PARALLELISM,
        builder.build());
  }

  @Test
  public void artSystemProperties() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder.appendArtSystemProperty("a.b.c", "1").appendArtSystemProperty("x.y.z", "2");
    assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + " -Da.b.c=1 -Dx.y.z=2" + FORK_JOIN_PARALLELISM,
        builder.build());
  }

  @Test
  public void programOptions() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder.setMainClass("Test").appendProgramArgument("hello").appendProgramArgument("world");
    assertEquals(
        SCRIPT + ToolHelper.getArtBinary(getVm()) + FORK_JOIN_PARALLELISM + " Test hello world",
        builder.build());
  }

  @Test
  public void allOfTheAbove() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder
        .appendArtOption("-d")
        .appendArtOption("--test")
        .appendArtSystemProperty("a.b.c", "1")
        .appendArtSystemProperty("x.y.z", "2")
        .appendClasspath("xxx.dex")
        .appendClasspath("yyy.jar")
        .setMainClass("Test")
        .appendProgramArgument("hello")
        .appendProgramArgument("world");
    assertEquals(
        SCRIPT
            + ToolHelper.getArtBinary(getVm())
            + " -d --test -Da.b.c=1 -Dx.y.z=2"
            + FORK_JOIN_PARALLELISM
            + " -cp xxx.dex:yyy.jar Test hello world",
        builder.build());
  }

  @Test
  public void allOfTheAboveWithClasspathAndSystemPropertiesBeforeOptions() {
    ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(getVm());
    builder
        .appendClasspath("xxx.dex")
        .appendClasspath("yyy.jar")
        .appendArtSystemProperty("a.b.c", "1")
        .appendArtSystemProperty("x.y.z", "2")
        .appendArtOption("-d")
        .appendArtOption("--test")
        .setMainClass("Test")
        .appendProgramArgument("hello")
        .appendProgramArgument("world");
    assertEquals(
        SCRIPT
            + ToolHelper.getArtBinary(getVm())
            + " -d --test -Da.b.c=1 -Dx.y.z=2"
            + FORK_JOIN_PARALLELISM
            + " -cp xxx.dex:yyy.jar Test hello world",
        builder.build());
  }

  @Test
  public void testVersion() {
    for (DexVm version : ToolHelper.getArtVersions()) {
      ToolHelper.ArtCommandBuilder builder = new ToolHelper.ArtCommandBuilder(version);
      builder.setMainClass("Test").appendProgramArgument("hello").appendProgramArgument("world");
      assertEquals(
          SCRIPT + ToolHelper.getArtBinary(version) + FORK_JOIN_PARALLELISM + " Test hello world",
          builder.build());
    }
  }

  @Test
  public void testProcessTimeout() {
    Assume.assumeTrue(ToolHelper.isLinux());
    ProcessBuilder wrapperBuilder =
        new ProcessBuilder("/bin/bash", "-c", "echo out-msg; echo err-msg >&2; sleep 60 & wait");
    RuntimeException wrapperException =
        assertThrows(
            RuntimeException.class,
            () -> ToolHelper.runProcess(wrapperBuilder, System.out, 200, TimeUnit.MILLISECONDS));
    assertTrue(
        wrapperException.getMessage(),
        wrapperException.getMessage().contains("Process timed out after 200 milliseconds"));
    assertTrue(
        wrapperException.getMessage(), wrapperException.getMessage().contains("STDOUT:\nout-msg"));
    assertTrue(
        wrapperException.getMessage(), wrapperException.getMessage().contains("STDERR:\nerr-msg"));

    ProcessBuilder directBuilder =
        new ProcessBuilder(
            "/bin/bash", "-c", "echo direct-out; echo direct-err >&2; exec sleep 60");
    RuntimeException directException =
        assertThrows(
            RuntimeException.class,
            () -> ToolHelper.runProcess(directBuilder, System.out, 200, TimeUnit.MILLISECONDS));
    assertTrue(
        directException.getMessage(),
        directException.getMessage().contains("Process timed out after 200 milliseconds"));
    assertTrue(
        directException.getMessage(), directException.getMessage().contains("STDOUT:\ndirect-out"));
    assertTrue(
        directException.getMessage(), directException.getMessage().contains("STDERR:\ndirect-err"));
  }
}
