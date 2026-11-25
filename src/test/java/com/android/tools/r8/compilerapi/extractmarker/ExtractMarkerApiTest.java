// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.extractmarker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.ExtractMarkerCommand;
import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.MarkerInfoConsumer;
import com.android.tools.r8.MarkerInfoConsumerData;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.IntBox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.Test;

public class ExtractMarkerApiTest extends CompilerApiTestRunner {

  public ExtractMarkerApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class SomeClass {}

  public static class SomeOrigin extends Origin {
    private final String name;

    public SomeOrigin(String name) {
      super(Origin.root());
      this.name = name;
    }

    @Override
    public String part() {
      return name;
    }
  }

  private byte[] compileDex(AndroidApiLevel minApi) throws Exception {
    Path dexFile =
        testForD8(Backend.DEX)
            .addProgramClasses(SomeClass.class)
            .setMinApi(minApi)
            .compile()
            .writeSingleDexOutputToFile();
    return Files.readAllBytes(dexFile);
  }

  @Test
  public void test() throws Exception {
    Path inputFile = ToolHelper.getClassFileForTestClass(SomeClass.class);
    byte[] inputCf = ToolHelper.getClassAsBytes(SomeClass.class);
    byte[] inputDex = compileDex(AndroidApiLevel.B);

    Origin originDex = new SomeOrigin("dex bytes");
    Origin originCf = new SomeOrigin("cf bytes");
    IntBox calls = new IntBox(0);
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            inputFile,
            inputDex,
            originDex,
            inputCf,
            originCf,
            (origin, marker) -> {
              calls.increment();
              if (origin == originDex) {
                assertEquals("D8", marker.getTool());
                assertTrue(marker.isD8());
                assertFalse(marker.isR8());
                assertFalse(marker.isL8());
                assertEquals(1, marker.getMinApi());
                assertEquals(1, marker.getMinApiMajor());
                assertEquals(0, marker.getMinApiMinor());
                assertTrue(marker.hasBackend());
                assertTrue(marker.isBackendDexFiles());
                assertFalse(marker.isBackendClassFiles());
                assertTrue(marker.hasCompilationMode());
                assertTrue(marker.isCompilationModeDebug());
                assertFalse(marker.isCompilationModeRelease());
                assertFalse(marker.isR8ModeCompatibility());
                assertFalse(marker.isR8ModeFull());
                assertThat(marker.getRawEncoding(), startsWith("~~D8{"));
              } else {
                assertTrue(origin == originCf || origin.equals(new PathOrigin(inputFile)));
                // The CF input file and bytes have no marker as they are javac generated.
                assertNull(marker);
              }
            });
    assertEquals(4, calls.get());
  }

  @Test
  public void testMinorApiLevel() throws Exception {
    byte[] inputDex = compileDex(AndroidApiLevel.BAKLAVA_1);
    Origin originDex = new SomeOrigin("dex minor bytes");
    IntBox calls = new IntBox(0);
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            null,
            inputDex,
            originDex,
            null,
            null,
            (origin, marker) -> {
              calls.increment();
              assertEquals(originDex, origin);
              assertNotNull(marker);
              assertEquals(36, marker.getMinApi());
              assertEquals(36, marker.getMinApiMajor());
              assertEquals(1, marker.getMinApiMinor());
            });
    assertEquals(1, calls.get());
  }

  @Test
  public void testNoMinApiLevel() throws Exception {
    Path cfZip =
        testForR8(Backend.CF)
            .addProgramClasses(SomeClass.class)
            .addKeepAllClassesRule()
            .compile()
            .writeToZip();
    IntBox calls = new IntBox(0);
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            cfZip,
            null,
            null,
            null,
            null,
            (origin, marker) -> {
              calls.increment();
              assertEquals(new PathOrigin(cfZip), origin);
              assertNotNull(marker);
              assertTrue(marker.isR8());
              assertTrue(marker.isBackendClassFiles());
              assertEquals(-1, marker.getMinApi());
              assertEquals(-1, marker.getMinApiMajor());
              assertEquals(-1, marker.getMinApiMinor());
            });
    assertEquals(2, calls.get());
  }

  @Test
  public void testLegacyMinApiOnlyMarker() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder("LegacyMarker");
    smaliBuilder.addStaticField(
        "MARKER",
        "Ljava/lang/String;",
        "~~D8{\\\"compilation-mode\\\":\\\"debug\\\",\\\"has-checksums\\\":false,"
            + "\\\"min-api\\\":21,\\\"sha-1\\\":\\\"engineering\\\",\\\"version\\\":\\\"main\\\"}");
    byte[] legacyMarkerDex = smaliBuilder.compile();

    Origin originDex = new SomeOrigin("legacy marker dex bytes");
    IntBox calls = new IntBox(0);
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            null,
            legacyMarkerDex,
            originDex,
            null,
            null,
            (origin, marker) -> {
              calls.increment();
              assertEquals(originDex, origin);
              assertNotNull(marker);
              assertTrue(marker.isD8());
              assertTrue(marker.isBackendDexFiles());
              assertEquals(21, marker.getMinApi());
              assertEquals(21, marker.getMinApiMajor());
              assertEquals(0, marker.getMinApiMinor());
            });
    assertEquals(1, calls.get());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(
        Path inputFile,
        byte[] inputDex,
        Origin dexOrigin,
        byte[] inputCf,
        Origin cfOrigin,
        BiConsumer<Origin, MarkerInfo> consumer)
        throws Exception {
      ExtractMarkerCommand.Builder builder = ExtractMarkerCommand.builder();
      if (inputFile != null) {
        builder.addProgramFiles(inputFile).addProgramFiles(Collections.singleton(inputFile));
      }
      if (inputDex != null) {
        builder.addDexProgramData(inputDex, dexOrigin);
      }
      if (inputCf != null) {
        builder.addClassProgramData(inputCf, cfOrigin);
      }
      builder.setMarkerInfoConsumer(
          new MarkerInfoConsumer() {
            @Override
            public void acceptMarkerInfo(MarkerInfoConsumerData data) {
              if (data.hasMarkers()) {
                data.getMarkers()
                    .forEach(
                        marker -> {
                          consumer.accept(data.getInputOrigin(), marker);
                        });
              } else {
                consumer.accept(data.getInputOrigin(), null);
              }
            }

            @Override
            public void finished() {}
          });
      ExtractMarker.run(builder.build());
    }

    @Test
    public void test() throws Exception {
      byte[] inputCf = getBytesForClass(getMockClass());
      List<byte[]> dexOutputs = new ArrayList<>();
      D8.run(
          D8Command.builder()
              .addClassProgramData(inputCf, Origin.unknown())
              .addLibraryFiles(getAndroidJar())
              .setMinApiLevel(1)
              .setProgramConsumer(
                  new DexIndexedConsumer.ForwardingConsumer(null) {
                    @Override
                    public void accept(
                        int fileIndex,
                        ByteDataView data,
                        Set<String> descriptors,
                        DiagnosticsHandler handler) {
                      dexOutputs.add(data.copyByteData());
                    }
                  })
              .build());
      boolean[] sawNonNullMarker = new boolean[] {false};
      run(
          null,
          dexOutputs.get(0),
          Origin.root(),
          inputCf,
          Origin.root(),
          (origin, marker) -> {
            if (marker != null) {
              sawNonNullMarker[0] = true;
              String tool = marker.getTool();
              String version = marker.getVersion();
              boolean d8 = marker.isD8();
              boolean r8 = marker.isR8();
              boolean l8 = marker.isL8();
              int minApi = marker.getMinApi();
              int minApiMajor = marker.getMinApiMajor();
              int minApiMinor = marker.getMinApiMinor();
              boolean hasBackend = marker.hasBackend();
              boolean backendDexFiles = marker.isBackendDexFiles();
              boolean backendClassFiles = marker.isBackendClassFiles();
              boolean hasCompilationMode = marker.hasCompilationMode();
              boolean compilationModeDebug = marker.isCompilationModeDebug();
              boolean compilationModeRelease = marker.isCompilationModeRelease();
              boolean r8ModeCompatibility = marker.isR8ModeCompatibility();
              boolean r8ModeFull = marker.isR8ModeFull();
              String raw = marker.getRawEncoding();
            }
          });
      assertTrue(sawNonNullMarker[0]);
    }
  }
}
