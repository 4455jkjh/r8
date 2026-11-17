// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.aapt.Resources;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.ResourcePath;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceFileDeduplicationTest extends TestBase {

  private static final String SELECTOR_XML =
      "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">"
          + "<item android:color=\"#123456\"/></selector>";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  private void enableResourceFileDeduplication(R8TestBuilder<?, ?, ?> builder) {
    if (parameters.getPartialCompilationTestParameters().isSome()) {
      builder.addR8PartialR8OptionsModification(o -> o.enableResourceFileDeduplication = true);
    } else {
      builder.addOptionsModification(o -> o.enableResourceFileDeduplication = true);
    }
  }

  private static void setStoredEntries(Path zipPath, Set<String> storedEntries) throws IOException {
    Path rewritten = zipPath.resolveSibling(zipPath.getFileName() + ".rewritten.ap_");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(rewritten))) {
      ZipUtils.iter(
          zipPath,
          (entry, inputStream) -> {
            String name = entry.getName();
            int method = storedEntries.contains(name) ? ZipEntry.STORED : ZipEntry.DEFLATED;
            ZipUtils.writeToZipStream(out, name, inputStream.readAllBytes(), method);
          });
    }
    Files.move(rewritten, zipPath, StandardCopyOption.REPLACE_EXISTING);
  }

  private static ArchiveProtoAndroidResourceProvider withUnknownMetadataFor(
      AndroidTestResource testResource,
      String unknownSizeLocation,
      Set<String> unknownCompressionLocations) {
    return new ArchiveProtoAndroidResourceProvider(testResource.getResourceZip()) {
      @Override
      public Collection<AndroidResourceInput> getAndroidResources() throws ResourceException {
        List<AndroidResourceInput> wrapped = new ArrayList<>();
        for (AndroidResourceInput input : super.getAndroidResources()) {
          String location = input.getPath().location();
          boolean unknownSize = location.equals(unknownSizeLocation);
          boolean unknownCompression = unknownCompressionLocations.contains(location);
          if (!unknownSize && !unknownCompression) {
            wrapped.add(input);
            continue;
          }
          wrapped.add(
              new AndroidResourceInput() {
                @Override
                public Origin getOrigin() {
                  return input.getOrigin();
                }

                @Override
                public ResourcePath getPath() {
                  return input.getPath();
                }

                @Override
                public Kind getKind() {
                  return input.getKind();
                }

                @Override
                public long getByteSize() throws ResourceException {
                  return unknownSize
                      ? AndroidResourceInput.super.getByteSize()
                      : input.getByteSize();
                }

                @Override
                public Compression getCompression() throws ResourceException {
                  return unknownCompression
                      ? AndroidResourceInput.super.getCompression()
                      : input.getCompression();
                }

                @Override
                public InputStream getByteStream() throws ResourceException {
                  return input.getByteStream();
                }
              });
        }
        return wrapped;
      }
    };
  }

  @Test
  public void testDeduplication() throws Exception {
    AndroidTestResource resources =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(
                R.drawable.class, R.color.class, R.xml.class, R.raw.class, R.styleable.class)
            .addDrawable("jpg_a.jpg", AndroidResourceTestingUtils.TINY_PNG)
            .addDrawable("webp_b.webp", AndroidResourceTestingUtils.TINY_PNG)
            .addDrawable("drawable_xml.xml", SELECTOR_XML.getBytes(StandardCharsets.UTF_8))
            .addColorXml("color_a", SELECTOR_XML)
            .addColorXml("color_b", SELECTOR_XML)
            .addXml("xml_a.xml", SELECTOR_XML)
            .addXml("xml_b.xml", SELECTOR_XML)
            .addXml("xml_c.xml", SELECTOR_XML.replace("123456", "654321"))
            .addXml(
                "xml_d.xml",
                "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>")
            .addRawXml("raw_a.txt", "a")
            .addRawXml("raw_b.txt", "a")
            .build(temp)
            .mangleResourceTable(
                table -> {
                  ResourceTable.Builder builder = table.toBuilder();
                  for (Package.Builder p : builder.getPackageBuilderList()) {
                    for (Resources.Type.Builder type : p.getTypeBuilderList()) {
                      for (Entry.Builder entry : type.getEntryBuilderList()) {
                        if (entry.getName().equals("hdpi")) {
                          entry.getConfigValueBuilder(0).getConfigBuilder().setDensity(240);
                        }
                      }
                    }
                  }
                  return builder.build();
                });
    setStoredEntries(
        resources.getResourceZip(),
        ImmutableSet.of("res/drawable/stored_a.png", "res/drawable/stored_b.png"));

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addProgramClasses(Main.class)
            .addAndroidResources(resources)
            .apply(
                b ->
                    b.getBuilder()
                        .setAndroidResourceProvider(
                            withUnknownMetadataFor(
                                resources,
                                "res/drawable/unknown_size.png",
                                ImmutableSet.of(
                                    "res/drawable/unknown_compression_a.png",
                                    "res/drawable/unknown_compression_b.png"))))
            .enableOptimizedShrinking()
            .apply(this::enableResourceFileDeduplication)
            .addKeepMainRule(Main.class)
            .compile();
    Path resourceShrinkerOutput = compileResult.getResourceShrinkerOutput();
    Map<String, Integer> outputCompressionMethods = new HashMap<>();
    ZipUtils.iter(
        resourceShrinkerOutput,
        entry -> outputCompressionMethods.put(entry.getName(), entry.getMethod()));
    assertEquals(
        Integer.valueOf(ZipEntry.DEFLATED), outputCompressionMethods.get("res/drawable/bar.png"));
    assertEquals(
        Integer.valueOf(ZipEntry.STORED),
        outputCompressionMethods.get("res/drawable/stored_a.png"));
    ProcessResult convertResult =
        ToolHelper.runAapt2(
            "convert",
            "--output-format",
            "binary",
            "-o",
            temp.newFile().toString(),
            resourceShrinkerOutput.toString());
    assertEquals(convertResult.toString(), 0, convertResult.exitCode);
    ResourceTable shrunkenTable =
        ResourceTable.parseFrom(ZipUtils.readSingleEntry(resourceShrinkerOutput, "resources.pb"));
    assertTrue(
        shrunkenTable.getPackage(0).getTypeList().stream()
            .filter(t -> t.getName().equals("attr"))
            .flatMap(t -> t.getEntryList().stream())
            .anyMatch(
                e ->
                    e.getConfigValueCount() > 0
                        && e.getConfigValue(0).getValue().hasCompoundValue()));
    compileResult
        .inspectShrunkenResources(
            inspector -> {
              assertEquals("res/drawable/bar.png", inspector.getSingleFileValue("drawable", "foo"));
              assertEquals("res/drawable/bar.png", inspector.getSingleFileValue("drawable", "bar"));
              // Stored entries deduplicate with each other but never with deflated entries.
              assertEquals(
                  "res/drawable/stored_a.png",
                  inspector.getSingleFileValue("drawable", "stored_a"));
              assertEquals(
                  "res/drawable/stored_a.png",
                  inspector.getSingleFileValue("drawable", "stored_b"));
              assertEquals("res/xml/xml_a.xml", inspector.getSingleFileValue("xml", "xml_b"));
              assertEquals(
                  "res/color/color_a.xml", inspector.getSingleFileValue("color", "color_b"));
              // Different density, different resource type, and different extension.
              assertEquals(
                  "res/drawable/hdpi.png", inspector.getSingleFileValue("drawable", "hdpi"));
              assertEquals(
                  "res/color/color_a.xml", inspector.getSingleFileValue("color", "color_a"));
              assertEquals(
                  "res/drawable/drawable_xml.xml",
                  inspector.getSingleFileValue("drawable", "drawable_xml"));
              assertEquals(
                  "res/drawable/jpg_a.jpg", inspector.getSingleFileValue("drawable", "jpg_a"));
              assertEquals(
                  "res/drawable/webp_b.webp", inspector.getSingleFileValue("drawable", "webp_b"));
              // Same length/key with different content, unique length, and unknown byte size or
              // compression.
              assertEquals("res/xml/xml_c.xml", inspector.getSingleFileValue("xml", "xml_c"));
              assertEquals("res/xml/xml_d.xml", inspector.getSingleFileValue("xml", "xml_d"));
              assertEquals(
                  "res/drawable/unknown_size.png",
                  inspector.getSingleFileValue("drawable", "unknown_size"));
              assertEquals(
                  "res/drawable/unknown_compression_a.png",
                  inspector.getSingleFileValue("drawable", "unknown_compression_a"));
              assertEquals(
                  "res/drawable/unknown_compression_b.png",
                  inspector.getSingleFileValue("drawable", "unknown_compression_b"));
              // Raw resources are never deduplicated.
              assertEquals("res/raw/raw_a.txt", inspector.getSingleFileValue("raw", "raw_a"));
              assertEquals("res/raw/raw_b.txt", inspector.getSingleFileValue("raw", "raw_b"));
            })
        .assertResourceFile("res/drawable/foo.png", /* present= */ false)
        .assertResourceFile("res/drawable/stored_a.png", /* present= */ true)
        .assertResourceFile("res/drawable/stored_b.png", /* present= */ false)
        .assertResourceFile("res/xml/xml_b.xml", /* present= */ false)
        .assertResourceFile("res/color/color_b.xml", /* present= */ false)
        .assertResourceFile("res/drawable/drawable_xml.xml", /* present= */ true)
        .assertResourceFile("res/drawable/webp_b.webp", /* present= */ true)
        .assertResourceFile("res/drawable/unknown_size.png", /* present= */ true)
        .assertResourceFile("res/drawable/unknown_compression_a.png", /* present= */ true)
        .assertResourceFile("res/drawable/unknown_compression_b.png", /* present= */ true)
        .assertResourceFile("res/raw/raw_b.txt", /* present= */ true)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World");
  }

  @Test
  public void testFeatureSplits() throws Exception {
    AndroidTestResource baseResources =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(R.drawable.class)
            .build(temp);
    TemporaryFolder featureTemp = ToolHelper.getTemporaryFolderForTest();
    featureTemp.create();
    AndroidTestResource featureResources =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .setPackageId(0x7e)
            .addRClassInitializeWithDefaultValues(FeatureSplit.R.drawable.class)
            .build(featureTemp);
    String featureName = FeatureSplit.class.getName();

    testForR8(parameters)
        .addProgramClasses(BaseMain.class)
        .addAndroidResources(baseResources)
        .addFeatureSplitAndroidResources(
            featureResources, featureName, FeatureSplit.FeatureMain.class)
        .enableOptimizedShrinking()
        .apply(this::enableResourceFileDeduplication)
        .addKeepMainRule(BaseMain.class)
        .addKeepMainRule(FeatureSplit.FeatureMain.class)
        .compile()
        .inspectShrunkenResources(
            inspector ->
                assertEquals(
                    "res/drawable/in_base_and_feature.png",
                    inspector.getSingleFileValue("drawable", "in_base_and_feature")))
        .assertResourceFile("res/drawable/foo.png", /* present= */ true)
        .assertResourceFile("res/drawable/in_base_and_feature.png", /* present= */ true)
        .inspectShrunkenResourcesForFeature(
            inspector -> {
              assertEquals(
                  "res/drawable/feature_a.png",
                  inspector.getSingleFileValue("drawable", "feature_a"));
              assertEquals(
                  "res/drawable/feature_a.png",
                  inspector.getSingleFileValue("drawable", "feature_b"));
              // Files that exist in multiple splits are not deduplicated.
              assertEquals(
                  "res/drawable/in_base_and_feature.png",
                  inspector.getSingleFileValue("drawable", "in_base_and_feature"));
            },
            featureName)
        .assertFeatureResourceFile("res/drawable/feature_b.png", /* present= */ false, featureName)
        .run(parameters.getRuntime(), BaseMain.class)
        .assertSuccessWithOutputLines("Hello World");
  }

  public static class Main {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foo);
        System.out.println(R.drawable.bar);
        System.out.println(R.drawable.stored_a);
        System.out.println(R.drawable.stored_b);
        System.out.println(R.drawable.hdpi);
        System.out.println(R.drawable.jpg_a);
        System.out.println(R.drawable.webp_b);
        System.out.println(R.drawable.drawable_xml);
        System.out.println(R.drawable.unknown_size);
        System.out.println(R.drawable.unknown_compression_a);
        System.out.println(R.drawable.unknown_compression_b);
        System.out.println(R.color.color_a);
        System.out.println(R.color.color_b);
        System.out.println(R.xml.xml_a);
        System.out.println(R.xml.xml_b);
        System.out.println(R.xml.xml_c);
        System.out.println(R.xml.xml_d);
        System.out.println(R.raw.raw_a);
        System.out.println(R.raw.raw_b);
        System.out.println(R.styleable.our_styleable[0]);
      }
      System.out.println("Hello World");
    }
  }

  public static class BaseMain {
    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foo);
        System.out.println(R.drawable.in_base_and_feature);
      }
      System.out.println("Hello World");
    }
  }

  public static class FeatureSplit {
    public static class FeatureMain {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.drawable.feature_a);
          System.out.println(R.drawable.feature_b);
          System.out.println(R.drawable.in_base_and_feature);
        }
        System.out.println("Hello Feature");
      }
    }

    public static class R {
      public static class drawable {
        public static int feature_a;
        public static int feature_b;
        public static int in_base_and_feature;
      }
    }
  }

  public static class R {
    public static class drawable {
      public static int foo;
      public static int bar;
      public static int stored_a;
      public static int stored_b;
      public static int hdpi;
      public static int jpg_a;
      public static int webp_b;
      public static int drawable_xml;
      public static int unknown_size;
      public static int unknown_compression_a;
      public static int unknown_compression_b;
      public static int in_base_and_feature;
    }

    public static class color {
      public static int color_a;
      public static int color_b;
    }

    public static class xml {
      public static int xml_a;
      public static int xml_b;
      public static int xml_c;
      public static int xml_d;
    }

    public static class raw {
      public static int raw_a;
      public static int raw_b;
    }

    public static class styleable {
      public static int[] our_styleable;
    }
  }
}
