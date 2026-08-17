// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinModuleFilenameTest extends KotlinTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilers().withAllTargetVersions().build());
  }

  public KotlinModuleFilenameTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testKotlinModuleFilename() throws Exception {
    Path sourceFolder = temp.newFolder().toPath();
    Path ktSource = sourceFolder.resolve("Lib.kt");
    Files.write(
        ktSource, Arrays.asList("package com.test.lib", "fun foo() {", "  println(\"foo\")", "}"));

    // Compile Kotlin code with a module name containing ':'.
    Path libJar =
        kotlinCompilerTool()
            .addSourceFiles(ktSource)
            .addArguments("-module-name", "com.test.lib:some-lib")
            .setOutputPath(temp.newFolder().toPath().resolve("lib.jar"))
            .compile();

    // Run R8, keeping the facade class to ensure it's not fully shrunk,
    // which should still trigger re-serialization since we drop and recreate.
    Path r8Output =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJar)
            .addClasspathFiles(kotlinc.getKotlinStdlibJar())
            .addKeepRules("-keep class com.test.lib.LibKt { void foo(); }")
            .addKeepAttributes("RuntimeVisibleAnnotations") // Metadata is an annotation.
            .compile()
            .writeToZip();

    // Inspect the output zip to see if the .kotlin_module file has a colon in its name.
    List<String> kotlinModuleFiles = new ArrayList<>();
    ZipUtils.iter(
        r8Output,
        (entry, stream) -> {
          if (entry.getName().endsWith(".kotlin_module")) {
            kotlinModuleFiles.add(entry.getName());
          }
        });

    // We expect the filename to be filesystem-safe. R8 should not produce files with ':' in their
    // name.
    assertFalse("Expected some kotlin module files", kotlinModuleFiles.isEmpty());
    List<String> filesWithColon =
        kotlinModuleFiles.stream().filter(name -> name.contains(":")).collect(Collectors.toList());
    assertTrue(
        "Expected no .kotlin_module file with ':' in its name, but found: " + filesWithColon,
        filesWithColon.isEmpty());
  }
}
