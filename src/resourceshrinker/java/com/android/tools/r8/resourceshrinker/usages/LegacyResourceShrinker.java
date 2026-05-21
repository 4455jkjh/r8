// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker.usages;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.XmlNode;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.R8ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.ShrinkerResult;
import com.android.tools.r8.resourceshrinker.ResourceStoreUtilsKt;
import com.android.tools.r8.resourceshrinker.ResourceTableUtilKt;
import com.android.tools.r8.resourceshrinker.ShrinkerDebugReporter;
import com.android.tools.r8.resourceshrinker.graph.ProtoResourcesGraphBuilder;
import com.android.tools.r8.resourceshrinker.obfuscation.ProguardMappingsRecorder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class LegacyResourceShrinker<T> {
  private final Map<String, byte[]> dexInputs;
  private final Collection<PathAndBytes> resFolderInputs;
  private final Collection<PathAndBytes> xmlInputs;
  private final List<byte[]> keepRuleInput;
  private List<String> proguardMapStrings;
  private final ShrinkerDebugReporter debugReporter;
  private final List<PathAndBytes> manifest;
  private final Map<PathAndBytes, T> resourceTables;
  private final DexAnalyser dexAnalyser;

  public static class Builder<T> {

    private final Map<String, byte[]> dexInputs = new HashMap<>();
    private final Map<String, PathAndBytes> resFolderInputs = new HashMap<>();
    private final Map<String, PathAndBytes> xmlInputs = new HashMap<>();
    private final List<byte[]> keepRuleInput = new ArrayList<>();

    private final List<PathAndBytes> manifests = new ArrayList<>();
    private final Map<PathAndBytes, T> resourceTables = new HashMap<>();
    private List<String> proguardMapStrings;
    private ShrinkerDebugReporter debugReporter;
    private DexAnalyser dexAnalyser;

    private Builder() {}

    public Builder<T> addManifest(String path, byte[] bytes) {
      manifests.add(new PathAndBytes(bytes, path));
      return this;
    }

    public Builder<T> addResourceTable(String path, byte[] bytes, T featureSplit) {
      resourceTables.put(new PathAndBytes(bytes, path), featureSplit);
      return this;
    }

    public Builder<T> addDexInput(String classesLocation, byte[] bytes) {
      dexInputs.put(classesLocation, bytes);
      return this;
    }

    public Builder<T> addKeepRuleInput(byte[] bytes) {
      keepRuleInput.add(bytes);
      return this;
    }

    public Builder<T> addResFolderInput(String path, byte[] bytes) {
      PathAndBytes existing = resFolderInputs.get(path);
      if (existing != null) {
        existing.setDuplicated(true);
      } else {
        resFolderInputs.put(path, new PathAndBytes(bytes, path));
      }
      return this;
    }

    public Builder<T> addXmlInput(String path, byte[] bytes) {
      PathAndBytes existing = xmlInputs.get(path);
      if (existing != null) {
        existing.setDuplicated(true);
      } else {
        xmlInputs.put(path, new PathAndBytes(bytes, path));
      }
      return this;
    }

    public Builder<T> setDexAnalyser(DexAnalyser dexAnalyser) {
      this.dexAnalyser = dexAnalyser;
      return this;
    }

    public LegacyResourceShrinker<T> build() {
      assert manifests != null && resourceTables != null && dexAnalyser != null;
      return new LegacyResourceShrinker<T>(
          dexInputs,
          resFolderInputs.values(),
          manifests,
          resourceTables,
          xmlInputs.values(),
          keepRuleInput,
          proguardMapStrings,
          debugReporter,
          dexAnalyser);
    }

    public void setProguardMapStrings(List<String> proguardMapStrings) {
      this.proguardMapStrings = proguardMapStrings;
    }

    public Builder<T> setShrinkerDebugReporter(ShrinkerDebugReporter debugReporter) {
      this.debugReporter = debugReporter;
      return this;
    }
  }

  private LegacyResourceShrinker(
      Map<String, byte[]> dexInputs,
      Collection<PathAndBytes> resFolderInputs,
      List<PathAndBytes> manifests,
      Map<PathAndBytes, T> resourceTables,
      Collection<PathAndBytes> xmlInputs,
      List<byte[]> additionalRawXmlInputs,
      List<String> proguardMapStrings,
      ShrinkerDebugReporter debugReporter,
      DexAnalyser dexAnalyser) {
    this.dexInputs = dexInputs;
    this.resFolderInputs = resFolderInputs;
    this.manifest = manifests;
    this.resourceTables = resourceTables;
    this.xmlInputs = xmlInputs;
    this.keepRuleInput = additionalRawXmlInputs;
    this.proguardMapStrings = proguardMapStrings;
    this.debugReporter = debugReporter;
    this.dexAnalyser = dexAnalyser;
  }

  public static <T> Builder<T> builder() {
    return new Builder<T>();
  }

  public static Predicate<String> classNamesNeededForResourceShrinkingPredicate() {
    return AppCompat.INSTANCE
        .getRequiredClassNamesPredicate()
        .or(DexUsageRecorder.getRequiredClassNamesPredicate());
  }

  public ShrinkerResult<T> run() throws IOException, ParserConfigurationException, SAXException {
    R8ResourceShrinkerModel model = new R8ResourceShrinkerModel(debugReporter, true);
    for (PathAndBytes pathAndBytes : resourceTables.keySet()) {
      ResourceTable loadedResourceTable = ResourceTable.parseFrom(pathAndBytes.bytes);
      model.instantiateFromResourceTable(loadedResourceTable, false);
    }
    if (proguardMapStrings != null) {
      new ProguardMappingsRecorder(proguardMapStrings).recordObfuscationMappings(model);
      proguardMapStrings = null;
    }
    for (Entry<String, byte[]> entry : dexInputs.entrySet()) {
      // The analysis needs an origin for the dex files, synthesize an easy recognizable one.
      Path inMemoryR8 = Paths.get("in_memory_r8_" + entry.getKey());
      dexAnalyser.analyse(
          entry.getValue(),
          inMemoryR8.toString(),
          new DexUsageRecorder.DexFileAnalysisCallback(inMemoryR8, model));
    }
    for (PathAndBytes pathAndBytes : manifest) {
      ProtoAndroidManifestUsageRecorderKt.recordUsagesFromNode(
          XmlNode.parseFrom(pathAndBytes.bytes), model);
    }
    for (byte[] keepBytes : keepRuleInput) {
      ToolsAttributeUsageRecorderKt.processRawXml(getUtfReader(keepBytes), model);
    }

    ImmutableMap<String, PathAndBytes> resFolderMappings =
        new ImmutableMap.Builder<String, PathAndBytes>()
            .putAll(
                xmlInputs.stream()
                    .collect(Collectors.toMap(PathAndBytes::getPathWithoutRes, a -> a)))
            .putAll(
                resFolderInputs.stream()
                    .collect(Collectors.toMap(PathAndBytes::getPathWithoutRes, a -> a)))
            .build();
    for (PathAndBytes pathAndBytes : resourceTables.keySet()) {
      ResourceTable resourceTable = ResourceTable.parseFrom(pathAndBytes.bytes);
      new ProtoResourcesGraphBuilder(
              pathInRes -> resFolderMappings.get(pathInRes).getBytes(), unused -> resourceTable)
          .buildGraph(model);
    }
    ResourceStore resourceStore = model.getResourceStore();
    resourceStore.processToolsAttributes();
    model.keepPossiblyReferencedResources();
    debugReporter.debug(model.getResourceStore()::dumpResourceModel);
    // Transitively mark the reachable resources in the model.
    // Finds unused resources in provided resources collection.
    // Marks all used resources as 'reachable' in original collection.
    List<Resource> unusedResources =
        new ArrayList<>(
            ResourcesUtil.findUnusedResources(
                model.getResourceStore().getResources(),
                roots -> {
                  if (debugReporter.isDebugEnabled()) {
                    debugReporter.debug(() -> "The root reachable resources are:");
                    List<Resource> sortedRoots = new ArrayList<>(roots);
                    sortedRoots.sort(ResourceShrinkerState.RESOURCE_COMPARATOR);
                    sortedRoots.forEach(root -> debugReporter.debug(() -> " " + root));
                  }
                }));
    ImmutableSet.Builder<String> resEntriesToKeepBuilder = new ImmutableSet.Builder<>();
    for (PathAndBytes xmlInput : Iterables.concat(xmlInputs, resFolderInputs)) {
      if (ResourceStoreUtilsKt.isJarPathReachable(resourceStore, xmlInput.path.toString())) {
        resEntriesToKeepBuilder.add(xmlInput.path.toString());
        if (xmlInput.duplicated) {
          // Ensure that we don't remove references to duplicated res folder entries.
          List<Resource> duplicatedResources =
              ResourceStoreUtilsKt.getResourcesFor(resourceStore, xmlInput.path.toString());
          unusedResources.removeAll(duplicatedResources);
        }
      }
    }
    if (debugReporter.isDebugEnabled()) {
      debugReporter.debug(() -> "Unused resources are: ");
      List<Resource> sortedUnused = new ArrayList<>(unusedResources);
      sortedUnused.sort(ResourceShrinkerState.RESOURCE_COMPARATOR);
      sortedUnused.forEach(unused -> debugReporter.debug(() -> " " + unused));
    }
    List<Integer> resourceIdsToRemove = getResourceIdsToRemove(unusedResources);
    Map<T, byte[]> shrunkenTables = new HashMap<>();
    for (Entry<PathAndBytes, T> entry : resourceTables.entrySet()) {
      byte[] shrunkenTableBytes =
          ResourceTableUtilKt.nullOutEntriesWithIds(entry.getKey().bytes, resourceIdsToRemove);
      shrunkenTables.put(entry.getValue(), shrunkenTableBytes);
    }
    return new ShrinkerResult<T>(
        resEntriesToKeepBuilder.build(), shrunkenTables, Collections.emptyMap());
  }

  private static List<Integer> getResourceIdsToRemove(List<Resource> unusedResources) {
    return unusedResources.stream()
        .filter(s -> s.type != ResourceType.ID)
        .map(resource -> resource.value)
        .collect(Collectors.toList());
  }

  // Lifted from com/android/utils/XmlUtils.java which we can't easily update internal dependency
  // for.
  /**
   * Returns a character reader for the given bytes, which must be a UTF encoded file.
   *
   * <p>The reader does not need to be closed by the caller (because the file is read in full in one
   * shot and the resulting array is then wrapped in a byte array input stream, which does not need
   * to be closed.)
   */
  public static Reader getUtfReader(byte[] bytes) throws IOException {
    int length = bytes.length;
    if (length == 0) {
      return new StringReader("");
    }

    switch (bytes[0]) {
      case (byte) 0xEF:
        {
          if (length >= 3 && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            // UTF-8 BOM: EF BB BF: Skip it
            return new InputStreamReader(new ByteArrayInputStream(bytes, 3, length - 3), UTF_8);
          }
          break;
        }
      case (byte) 0xFE:
        {
          if (length >= 2 && bytes[1] == (byte) 0xFF) {
            // UTF-16 Big Endian BOM: FE FF
            return new InputStreamReader(new ByteArrayInputStream(bytes, 2, length - 2), UTF_16BE);
          }
          break;
        }
      case (byte) 0xFF:
        {
          if (length >= 2 && bytes[1] == (byte) 0xFE) {
            if (length >= 4 && bytes[2] == (byte) 0x00 && bytes[3] == (byte) 0x00) {
              // UTF-32 Little Endian BOM: FF FE 00 00
              return new InputStreamReader(
                  new ByteArrayInputStream(bytes, 4, length - 4), "UTF-32LE");
            }

            // UTF-16 Little Endian BOM: FF FE
            return new InputStreamReader(new ByteArrayInputStream(bytes, 2, length - 2), UTF_16LE);
          }
          break;
        }
      case (byte) 0x00:
        {
          if (length >= 4
              && bytes[0] == (byte) 0x00
              && bytes[1] == (byte) 0x00
              && bytes[2] == (byte) 0xFE
              && bytes[3] == (byte) 0xFF) {
            // UTF-32 Big Endian BOM: 00 00 FE FF
            return new InputStreamReader(
                new ByteArrayInputStream(bytes, 4, length - 4), "UTF-32BE");
          }
          break;
        }
    }

    // No byte order mark: Assume UTF-8 (where the BOM is optional).
    return new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8);
  }

  private static class PathAndBytes {
    private final byte[] bytes;
    private final String path;
    private boolean duplicated;

    private PathAndBytes(byte[] bytes, String path) {
      this.bytes = bytes;
      this.path = path;
    }

    public void setDuplicated(boolean duplicated) {
      this.duplicated = duplicated;
    }

    public String getPathWithoutRes() {
      assert path.startsWith("res/");
      return path.substring(4);
    }

    public byte[] getBytes() {
      return bytes;
    }
  }
}
