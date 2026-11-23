// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.VersionProperties;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProguardMapMarkerTest extends TestBase {

  static class TestClass {

    public static void main(String[] args) {}
  }

  private static final Set<String> EXPECTED_KEYS_DEX =
      ImmutableSet.of(
          "compiler",
          "compiler_version",
          "min_api",
          "min_api_major",
          "min_api_minor",
          "compiler_hash",
          "pg_map_id",
          "pg_map_hash");
  private static final Set<String> EXPECTED_KEYS_CF =
      ImmutableSet.of("compiler", "compiler_version", "compiler_hash", "pg_map_id", "pg_map_hash");
  private static final Path CLASS_FILE = ToolHelper.getClassFileForTestClass(TestClass.class);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProguardMapMarkerTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void proguardMapMarkerTest24() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.N);
  }

  @Test
  public void proguardMapMarkerTest26() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.O);
  }

  @Test
  public void proguardMapMarkerTest36_1() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.BAKLAVA_1);
  }

  private static class ProguardMapIds {
    String fromProgram = null;
    String fromMap = null;
  }

  private void proguardMapMarkerTestDex(AndroidApiLevel minApiLevel)
      throws CompilationFailedException {
    ProguardMapIds proguardMapIds = new ProguardMapIds();
    R8.run(
        R8Command.builder()
            .addProgramFiles(CLASS_FILE)
            .setDisableTreeShaking(true)
            .setProgramConsumer(
                new DexIndexedConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      int fileIndex,
                      ByteDataView data,
                      Set<String> descriptors,
                      DiagnosticsHandler handler) {
                    Marker marker;
                    try {
                      Collection<Marker> markers =
                          ExtractMarkerUtils.extractMarkerFromDexProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    assertEquals(minApiLevel.asUnchecked(), marker.getMinApi());
                    proguardMapIds.fromProgram = marker.getPgMapId();
                  }
                })
            .addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(minApiLevel))
            .setMinApiLevel(minApiLevel.getMajor(), minApiLevel.getMinor())
            .setProguardMapConsumer(
                ToolHelper.consumeString(
                    proguardMap ->
                        proguardMapIds.fromMap =
                            verifyMarkersGetPgMapId(proguardMap, minApiLevel, EXPECTED_KEYS_DEX)))
            .build());
    verifyProguardMapIds(proguardMapIds);
  }

  private void verifyProguardMapIds(ProguardMapIds proguardMapIds) {
    assertTrue(
        proguardMapIds.fromProgram != null
            && proguardMapIds.fromProgram.length() == ProguardMapSupplier.PG_MAP_ID_LENGTH);
    assertTrue(proguardMapIds.fromMap != null);
    assertEquals(proguardMapIds.fromMap, proguardMapIds.fromProgram);
  }

  @Test
  public void proguardMapMarkerTestCf() throws CompilationFailedException {
    ProguardMapIds buildIds = new ProguardMapIds();
    R8.run(
        R8Command.builder()
            .addProgramFiles(CLASS_FILE)
            .setDisableTreeShaking(true)
            .setProgramConsumer(
                new ClassFileConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                    Marker marker;
                    try {
                      Collection<Marker> markers =
                          ExtractMarkerUtils.extractMarkerFromClassProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    buildIds.fromProgram = marker.getPgMapId();
                  }
                })
            .addLibraryFiles(TestDeps.getJava8RuntimeJar())
            .setProguardMapConsumer(
                ToolHelper.consumeString(
                    proguardMap -> {
                      buildIds.fromMap =
                          verifyMarkersGetPgMapId(proguardMap, null, EXPECTED_KEYS_CF);
                    }))
            .build());
    verifyProguardMapIds(buildIds);
  }

  private static String verifyMarkersGetPgMapId(
      String proguardMap, AndroidApiLevel minApiLevel, Set<String> expectedKeys) {
    String[] lines = proguardMap.split("\n");
    Set<String> keysFound = new HashSet<>();
    String proguardMapId = null;
    String proguardMapHash = null;
    for (String line : lines) {
      if (!line.startsWith("#")) {
        continue;
      }
      String comment = line.substring(1).trim();
      int colonIndex = comment.indexOf(":");
      if (colonIndex < 0) {
        continue;
      }
      String key = comment.substring(0, colonIndex).trim();
      String value = comment.substring(colonIndex + 1).trim();
      switch (key) {
        case "compiler":
          assertEquals("R8", value);
          break;
        case "compiler_version":
          assertEquals(Version.LABEL, value);
          break;
        case "min_api":
        case "min_api_major":
          assertNotNull(minApiLevel);
          assertEquals(minApiLevel.getMajor(), Integer.parseInt(value));
          break;
        case "min_api_minor":
          assertNotNull(minApiLevel);
          assertEquals(minApiLevel.getMinor(), Integer.parseInt(value));
          break;
        case "compiler_hash":
          assertEquals(VersionProperties.INSTANCE.getSha(), value);
          break;
        case "pg_map_id":
          proguardMapId = value;
          break;
        case "pg_map_hash":
          proguardMapHash = value;
          break;
        default:
          continue;
      }
      assertFalse(keysFound.contains(key));
      keysFound.add(key);
    }
    assertEquals(expectedKeys, keysFound);
    assertNotNull(proguardMapHash);
    return proguardMapId;
  }
}
