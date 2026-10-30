// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.assistant.cli.FakeAndroidSdk;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8AssistantCliTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  private Path buildTestApk() throws Exception {
    Path tempDex1 = testForD8(parameters).addProgramClasses(TestClass.class).compile().writeToZip();
    Path tempDex2 =
        testForD8(parameters).addProgramClasses(Foo.class, Bar.class).compile().writeToZip();

    Path apk = temp.newFile("test.apk").toPath();
    try (ZipOutputStream out =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(apk)))) {
      ZipEntry resEntry = new ZipEntry("assets/sample.txt");
      out.putNextEntry(resEntry);
      out.write("Sample Asset Content".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();

      ZipEntry sigEntry = new ZipEntry("META-INF/CERT.RSA");
      out.putNextEntry(sigEntry);
      out.write("MOCK_CERT".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();

      byte[] dex1Bytes = ZipUtils.readSingleEntry(tempDex1, "classes.dex");
      out.putNextEntry(new ZipEntry("classes.dex"));
      out.write(dex1Bytes);
      out.closeEntry();

      byte[] dex2Bytes = ZipUtils.readSingleEntry(tempDex2, "classes.dex");
      out.putNextEntry(new ZipEntry("classes2.dex"));
      out.write(dex2Bytes);
      out.closeEntry();
    }
    return apk;
  }

  @Test
  public void testAssistantJarOnApk() throws Exception {
    Path inputApk = buildTestApk();
    Path outputApk = temp.newFile("output.apk").toPath();
    FakeAndroidSdk sdk = FakeAndroidSdk.create(temp.newFolder().toPath());

    ProcessResult result =
        ToolHelper.runR8Assistant(
            inputApk,
            outputApk,
            "--sdk",
            sdk.getDirectory().toString(),
            "--ks",
            sdk.getKeystore().toString());
    assertEquals(result.toString(), 0, result.exitCode);

    List<String> entryNames = new ArrayList<>();
    ZipUtils.iter(outputApk, entry -> entryNames.add(entry.getName()));

    assertTrue(entryNames.contains("classes.dex"));
    assertTrue(entryNames.contains("assets/sample.txt"));
    assertFalse(entryNames.contains("META-INF/CERT.RSA"));

    byte[] assetBytes = ZipUtils.readSingleEntry(outputApk, "assets/sample.txt");
    assertEquals("Sample Asset Content", new String(assetBytes, StandardCharsets.UTF_8));

    Path jsonLog = temp.newFile().toPath();
    ProcessResult runResult =
        ToolHelper.runArtRaw(
            Collections.singletonList(outputApk.toString()),
            TestClass.class.getName(),
            builder ->
                builder.appendArtOption("-Dcom.android.tools.r8.reflectiveJsonLogger=" + jsonLog),
            parameters.getRuntime().asDex().getVm(),
            false);
    assertEquals(runResult.toString(), 0, runResult.exitCode);
    assertThat(runResult.stdout, containsString("Hello from Foo"));

    String logContent = FileUtils.readTextFile(jsonLog, StandardCharsets.UTF_8);
    assertThat(logContent, containsString(Bar.class.getName()));
    assertThat(logContent, containsString("callMe"));
    assertThat(logContent, containsString("CLASS_FOR_NAME"));
  }

  @Test
  public void testHelpVersionAndUsageOnJar() throws Exception {
    ProcessResult helpResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."), ToolHelper.R8_ASSISTANT_LIB_JAR, Collections.singletonList("--help"));
    assertEquals(helpResult.toString(), 0, helpResult.exitCode);
    assertThat(
        helpResult.stdout, containsString("Usage: java -jar r8-assistant.jar <command> [options]"));

    ProcessResult instrumentHelpResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."), ToolHelper.R8_ASSISTANT_LIB_JAR, Arrays.asList("instrument", "--help"));
    assertEquals(instrumentHelpResult.toString(), 0, instrumentHelpResult.exitCode);
    assertThat(
        instrumentHelpResult.stdout,
        containsString("Usage: java -jar r8-assistant.jar instrument"));

    ProcessResult versionResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Collections.singletonList("--version"));
    assertEquals(versionResult.toString(), 0, versionResult.exitCode);
    assertThat(versionResult.stdout, startsWith("R8 Assistant "));

    ProcessResult invalidResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Collections.singletonList("--unknown"));
    assertEquals(invalidResult.toString(), 2, invalidResult.exitCode);
    assertThat(invalidResult.stderr, containsString("Unknown option: --unknown"));
  }

  @Test
  public void testSuggestDiffAndQueryOnJar() throws Exception {
    Path log = temp.newFile("log.jsonl").toPath();
    FileUtils.writeTextFile(
        log,
        "{\"event\":\"CLASS_FOR_NAME\",\"id\":1,"
            + "\"stack\":[\"com.example.App.run(App.java:10)\"],"
            + "\"args\":[\"com.example.Plugin\",\"true\",\"dalvik.system.PathClassLoader\"]}",
        "{\"returned\":1,\"result\":\"com.example.Plugin\"}",
        "{\"event\":\"CLASS_GET_FIELD\",\"id\":2,"
            + "\"stack\":[\"com.example.App.run(App.java:11)\"],"
            + "\"args\":[\"java.lang.String\",\"com.example.User\",\"name\"]}",
        "{\"returned\":2,\"result\":\"java.lang.String name\"}");

    ProcessResult suggestResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Arrays.asList("suggest", "--log", log.toString()));
    assertEquals(suggestResult.toString(), 0, suggestResult.exitCode);
    assertEquals("", suggestResult.stderr);
    assertThat(suggestResult.stdout, containsString("-keep class com.example.Plugin"));
    assertThat(suggestResult.stdout, containsString("-keepclassmembers class com.example.User"));

    Path rules = temp.newFile("rules.pro").toPath();
    FileUtils.writeTextFile(
        rules,
        "-keep class com.example.**",
        "-keepclassmembers class com.example.** {",
        "    !private java.lang.String name;",
        "}");
    Path proposed = temp.newFile("proposed.pro").toPath();
    ProcessResult diffResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Arrays.asList(
                "diff",
                "--log",
                log.toString(),
                "--rules",
                rules.toString(),
                "--output",
                proposed.toString()));
    assertEquals(diffResult.toString(), 0, diffResult.exitCode);
    assertEquals("", diffResult.stderr);
    String proposedContent = FileUtils.readTextFile(proposed, StandardCharsets.UTF_8);
    assertThat(proposedContent, containsString("-keep class com.example.Plugin"));
    assertThat(
        proposedContent,
        containsString(
            "-keepclassmembers class com.example.User {\n    !private java.lang.String name;\n}"));

    ProcessResult rerunDiffResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Arrays.asList("diff", "--log", log.toString(), "--rules", proposed.toString()));
    assertEquals(rerunDiffResult.toString(), 0, rerunDiffResult.exitCode);
    assertEquals("", rerunDiffResult.stderr);
    assertThat(rerunDiffResult.stdout, containsString("Rules: 2 (2 justified)"));

    ProcessResult queryResult =
        ToolHelper.forkJavaWithJar(
            Paths.get("."),
            ToolHelper.R8_ASSISTANT_LIB_JAR,
            Arrays.asList("query", "--log", log.toString(), "--class", "com.example.User"));
    assertEquals(queryResult.toString(), 0, queryResult.exitCode);
    assertEquals("", queryResult.stderr);
    assertThat(
        queryResult.stdout,
        containsString("[returned] Class.getField com.example.User.name (CLASS_GET_FIELD)"));
    assertThat(queryResult.stdout, containsString("1 of 2 events match."));
  }

  @Test
  public void testRuntimeDexOnlyContainsRuntimeClasses() throws Exception {
    // The runtime is injected into instrumented apps, and ReflectiveInstrumentation only skips
    // classes in the runtime package. A class outside it could collide with app classes and would
    // itself be instrumented, recursing into the receiver at runtime.
    String runtimePackage = ReflectiveOperationReceiver.class.getPackage().getName() + ".";
    List<FoundClassSubject> classes =
        new CodeInspector(ToolHelper.ASSISTANT_RUNTIME_DEX).allClasses();
    assertFalse(classes.isEmpty());
    for (FoundClassSubject clazz : classes) {
      assertThat(clazz.getFinalName(), startsWith(runtimePackage));
    }
  }

  static class TestClass {
    public static void main(String[] args) throws Exception {
      Bar.reflectOn();
    }
  }

  static class Foo {
    public static void callMe() {
      System.out.println("Hello from Foo");
    }
  }

  static class Bar {
    public static void reflectOn() throws Exception {
      Class<?> fooClass = Class.forName("com.android.tools.r8.assistant.R8AssistantCliTest$Foo");
      fooClass.getMethod("callMe").invoke(null);
    }
  }
}
