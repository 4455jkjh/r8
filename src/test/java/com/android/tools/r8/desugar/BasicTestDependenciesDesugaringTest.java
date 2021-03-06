// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.ToolHelper.CLASSPATH_SEPARATOR;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicTestDependenciesDesugaringTest {

  private static final String[] allLibs;
  static {
    try {
      allLibs =
          Files.readAllLines(Paths.get(ToolHelper.BUILD_DIR, "generated", "supportlibraries.txt"))
          .toArray(StringUtils.EMPTY_ARRAY);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static Set<String> knownIssues = Sets.newHashSet(StringUtils.EMPTY_ARRAY);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Parameters(name = "{0}")
  public static Collection<String[]> data() {
    int libCount = allLibs.length;
    Collection<String[]> datas = new ArrayList<String[]>(libCount);
    for (int i = 0; i < libCount; i++) {
      StringBuilder classpath = new StringBuilder();
      for (int j = 0; j < libCount; j++) {
        if (j != i) {
          classpath.append(allLibs[j]).append(CLASSPATH_SEPARATOR);
        }
      }
      datas.add(new String[] {new File(allLibs[i]).getName(), allLibs[i], classpath.toString()});
    }
    return datas;
  }

  private String name;
  private Path toCompile;
  private List<Path> classpath;

  public BasicTestDependenciesDesugaringTest(String name, String toCompile, String classpath) {
    this.name = name;
    this.toCompile = Paths.get(toCompile);
    this.classpath = Arrays.asList(classpath.split(CLASSPATH_SEPARATOR)).stream()
        .map(string -> Paths.get(string)).collect(Collectors.toList());
  }

  @Test
  public void testCompile() throws Exception {
    if (knownIssues.contains(name)) {
      thrown.expect(CompilationError.class);
    }
    ToolHelper.runD8(
        D8Command.builder()
            .addClasspathFiles(classpath)
            .addProgramFiles(toCompile)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
            .setMinApiLevel(AndroidApiLevel.K.getLevel()),
        options -> {
          options.interfaceMethodDesugaring = OffOrAuto.Auto;
          options.testing.disableStackMapVerification = name.equals("espresso-core-3.0.0.jar");
        });
  }

  @Test
  public void testCompileDontDesugarDefault() throws Exception {
    if (knownIssues.contains(name)) {
      thrown.expect(CompilationError.class);
    }
    ToolHelper.runD8(
        D8Command.builder()
            .addClasspathFiles(classpath)
            .addProgramFiles(toCompile)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
            .setMinApiLevel(AndroidApiLevel.K.getLevel()),
        options -> {
          options.interfaceMethodDesugaring = OffOrAuto.Off;
          options.testing.disableStackMapVerification = name.equals("espresso-core-3.0.0.jar");
        });
  }
}
