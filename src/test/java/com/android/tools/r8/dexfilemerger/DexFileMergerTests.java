// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFileMergerHelper;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.maindexlist.MainDexListTests;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class DexFileMergerTests extends TestBase {

  private static final String CLASS_DIR = ToolHelper.EXAMPLES_BUILD_DIR + "classes/dexmergesample";
  private static final String CLASS1_CLASS = CLASS_DIR + "/Class1.class";
  private static final String CLASS2_CLASS = CLASS_DIR + "/Class2.class";
  private static final int MAX_METHOD_COUNT = Constants.U16BIT_MAX;

  private Path createMergerInputWithTwoClasses(OutputMode outputMode, boolean addMarker)
      throws CompilationFailedException, IOException {
    // Compile Class1 and Class2
    Path mergerInputZip = temp.newFolder().toPath().resolve("merger-input.zip");
    D8Command command =
        D8Command.builder()
            .setOutput(mergerInputZip, outputMode)
            .addProgramFiles(Paths.get(CLASS1_CLASS))
            .addProgramFiles(Paths.get(CLASS2_CLASS))
            .build();

    DexFileMergerHelper.runD8ForTesting(command, !addMarker);

    return mergerInputZip;
  }

  private void testMarker(boolean addMarkerToInput)
      throws CompilationFailedException, IOException, ResourceException, ExecutionException {
    Path mergerInputZip = createMergerInputWithTwoClasses(OutputMode.DexIndexed, addMarkerToInput);
    int expectedNumberOfMarkers = addMarkerToInput ? 1 : 0;

    Collection<Marker> inputMarkers = ExtractMarker.extractMarkerFromDexFile(mergerInputZip);
    assertEquals(expectedNumberOfMarkers, inputMarkers.size());

    // Test that the DexFileMerger preserves markers.
    Path mergerOutputZip = temp.getRoot().toPath().resolve("merger-out.zip");
    DexFileMerger.main(
        new String[] {
          "--input", mergerInputZip.toString(), "--output", mergerOutputZip.toString()
        });
    Collection<Marker> outputMarkers = ExtractMarker.extractMarkerFromDexFile(mergerOutputZip);
    assertEquals(expectedNumberOfMarkers, outputMarkers.size());

    // Test that D8 when used for merging preserves markers.
    D8.main(new String[] { mergerInputZip.toString(), "--output", mergerOutputZip.toString() });
    Collection<Marker> d8OutputMarkers = ExtractMarker.extractMarkerFromDexFile((mergerOutputZip));
    assertEquals(expectedNumberOfMarkers, d8OutputMarkers.size());
  }

  @Test
  public void testMarkerPreserved()
      throws CompilationFailedException, IOException, ResourceException, ExecutionException {
    testMarker(true);
  }

  @Test
  public void testMarkerNotAdded()
      throws CompilationFailedException, IOException, ResourceException, ExecutionException {
    testMarker(false);
  }

  @Test
  public void mergeTwoFiles() throws CompilationFailedException, IOException {
    Path mergerInputZip = createMergerInputWithTwoClasses(OutputMode.DexFilePerClassFile, false);

    Path mergerOutputZip = temp.getRoot().toPath().resolve("merger-out.zip");
    DexFileMerger.main(
        new String[] {
          "--input", mergerInputZip.toString(), "--output", mergerOutputZip.toString()
        });

    // Test by running methods of Class1 and Class2
    for (String className : new String[] {"Class1", "Class2"}) {
      ArtCommandBuilder builder = new ArtCommandBuilder();
      builder.appendClasspath(mergerOutputZip.toString());
      builder.setMainClass("dexmergesample." + className);
      String out = ToolHelper.runArt(builder);
      assertEquals(out, className + "\n");
    }
  }

  private void generateClassesAndTest(int extraMethodCount, int programResourcesSize)
      throws IOException, ExecutionException, CompilationFailedException {
    AndroidApp generatedApp =
        MainDexListTests.generateApplication(
            ImmutableList.of("A", "B"),
            AndroidApiLevel.N.getLevel(),
            MAX_METHOD_COUNT / 2 + 1 + extraMethodCount);
    Path appDir = temp.newFolder().toPath().resolve("merger-input.zip");
    assertEquals(programResourcesSize, generatedApp.getDexProgramResourcesForTesting().size());
    generatedApp.write(appDir, OutputMode.DexIndexed);

    Path outZip = temp.getRoot().toPath().resolve("out.zip");
    DexFileMerger.run(
        new String[] {
          "--input", appDir.toString(), "--output", outZip.toString(), "--multidex=off"
        });
  }

  @Test
  public void failIfTooBig() throws IOException, ExecutionException {
    // Generates an application with two classes, each with the number of methods just enough not to
    // fit into a single dex file.
    try {
      generateClassesAndTest(1, 2);
      fail("Expect to fail");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("does not fit into a single dex file"));
    }
  }

  @Test
  public void failIfTooBigControl()
      throws IOException, ExecutionException, CompilationFailedException {
    // Control test for failIfTooBig to make sure we don't fail with less methods.
    generateClassesAndTest(0, 1);
  }
}
