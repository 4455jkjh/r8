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
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    ProcessResult result = ToolHelper.runR8Assistant(inputApk, outputApk);
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
