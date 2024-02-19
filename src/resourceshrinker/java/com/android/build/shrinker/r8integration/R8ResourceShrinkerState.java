// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.build.shrinker.r8integration;

import static com.android.build.shrinker.r8integration.LegacyResourceShrinker.getUtfReader;

import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.build.shrinker.NoDebugReporter;
import com.android.build.shrinker.ResourceShrinkerImplKt;
import com.android.build.shrinker.ResourceShrinkerModel;
import com.android.build.shrinker.ResourceTableUtilKt;
import com.android.build.shrinker.ShrinkerDebugReporter;
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder;
import com.android.build.shrinker.r8integration.LegacyResourceShrinker.ShrinkerResult;
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorderKt;
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorderKt;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class R8ResourceShrinkerState {

  private final Function<Exception, RuntimeException> errorHandler;
  private final R8ResourceShrinkerModel r8ResourceShrinkerModel;
  private final Map<String, Supplier<InputStream>> xmlFileProviders = new HashMap<>();

  private Supplier<InputStream> manifestProvider;
  private final Map<String, Supplier<InputStream>> resfileProviders = new HashMap<>();
  private final Map<ResourceTable, FeatureSplit> resourceTables = new HashMap<>();
  private ClassReferenceCallback enqueuerCallback;
  private Map<Integer, String> resourceIdToXmlFiles;
  private Set<String> packageNames;
  private final Set<String> seenNoneClassValues = new HashSet<>();
  private final Set<Integer> seenResourceIds = new HashSet<>();

  @FunctionalInterface
  public interface ClassReferenceCallback {
    boolean tryClass(String possibleClass, Origin xmlFileOrigin);
  }

  public R8ResourceShrinkerState(Function<Exception, RuntimeException> errorHandler) {
    r8ResourceShrinkerModel = new R8ResourceShrinkerModel(NoDebugReporter.INSTANCE, true);
    this.errorHandler = errorHandler;
  }

  public void trace(int id) {
    if (!seenResourceIds.add(id)) {
      return;
    }
    Resource resource = r8ResourceShrinkerModel.getResourceStore().getResource(id);
    if (resource == null) {
      return;
    }
    ResourceUsageModel.markReachable(resource);
    traceXml(id);
    if (resource.references != null) {
      for (Resource reference : resource.references) {
        if (!reference.isReachable()) {
          trace(reference.value);
        }
      }
    }
  }

  public void setEnqueuerCallback(ClassReferenceCallback enqueuerCallback) {
    assert this.enqueuerCallback == null;
    this.enqueuerCallback = enqueuerCallback;
  }

  private synchronized Set<String> getPackageNames() {
    // TODO(b/325888516): Consider only doing this for the package corresponding to the current
    // feature.
    if (packageNames == null) {
      packageNames = new HashSet<>();
      for (ResourceTable resourceTable : resourceTables.keySet()) {
        for (Package aPackage : resourceTable.getPackageList()) {
          packageNames.add(aPackage.getPackageName());
        }
      }
    }
    return packageNames;
  }

  public void setManifestProvider(Supplier<InputStream> manifestProvider) {
    this.manifestProvider = manifestProvider;
  }

  public void addXmlFileProvider(Supplier<InputStream> inputStreamSupplier, String location) {
    this.xmlFileProviders.put(location, inputStreamSupplier);
  }

  public void addResFileProvider(Supplier<InputStream> inputStreamSupplier, String location) {
    this.resfileProviders.put(location, inputStreamSupplier);
  }

  public void addResourceTable(InputStream inputStream, FeatureSplit featureSplit) {
    this.resourceTables.put(
        r8ResourceShrinkerModel.instantiateFromResourceTable(inputStream, true), featureSplit);
  }

  public R8ResourceShrinkerModel getR8ResourceShrinkerModel() {
    return r8ResourceShrinkerModel;
  }

  private byte[] getXmlOrResFileBytes(String path) {
    assert !path.startsWith("res/");
    String pathWithRes = "res/" + path;
    Supplier<InputStream> inputStreamSupplier = xmlFileProviders.get(pathWithRes);
    if (inputStreamSupplier == null) {
      inputStreamSupplier = resfileProviders.get(pathWithRes);
    }
    if (inputStreamSupplier == null) {
      // Ill formed resource table with file references inside res/ that does not exist.
      return null;
    }
    try {
      return inputStreamSupplier.get().readAllBytes();
    } catch (IOException ex) {
      throw errorHandler.apply(ex);
    }
  }

  public void setupReferences() {
    for (ResourceTable resourceTable : resourceTables.keySet()) {
      new ProtoResourcesGraphBuilder(this::getXmlOrResFileBytes, unused -> resourceTable)
          .buildGraph(r8ResourceShrinkerModel);
    }
  }

  public ShrinkerResult shrinkModel() throws IOException {
    updateModelWithManifestReferences();
    updateModelWithKeepXmlReferences();
    ResourceStore resourceStore = r8ResourceShrinkerModel.getResourceStore();
    resourceStore.processToolsAttributes();
    ImmutableSet<String> resEntriesToKeep = getResEntriesToKeep(resourceStore);
    List<Integer> resourceIdsToRemove = getResourcesToRemove();

    Map<FeatureSplit, ResourceTable> shrunkenTables = new IdentityHashMap<>();
    resourceTables.forEach(
        (resourceTable, featureSplit) ->
            shrunkenTables.put(
                featureSplit,
                ResourceTableUtilKt.nullOutEntriesWithIds(resourceTable, resourceIdsToRemove)));

    return new ShrinkerResult(resEntriesToKeep, shrunkenTables);
  }

  private ImmutableSet<String> getResEntriesToKeep(ResourceStore resourceStore) {
    ImmutableSet.Builder<String> resEntriesToKeep = new ImmutableSet.Builder<>();
    for (String path : Iterables.concat(xmlFileProviders.keySet(), resfileProviders.keySet())) {
      if (ResourceShrinkerImplKt.isJarPathReachable(resourceStore, path)) {
        resEntriesToKeep.add(path);
      }
    }
    return resEntriesToKeep.build();
  }

  private void traceXml(int id) {
    String xmlFile = getResourceIdToXmlFiles().get(id);
    if (xmlFile != null) {
      InputStream inputStream = xmlFileProviders.get(xmlFile).get();
      try {
        XmlNode xmlNode = XmlNode.parseFrom(inputStream);
        visitNode(xmlNode, xmlFile);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void tryEnqueuerOnString(String possibleClass, String xmlName) {
    // There are a lot of xml tags and attributes that are evaluated over and over, if it is
    // not a class, ignore it.
    if (seenNoneClassValues.contains(possibleClass)) {
      return;
    }
    if (!enqueuerCallback.tryClass(possibleClass, new PathOrigin(Paths.get(xmlName)))) {
      seenNoneClassValues.add(possibleClass);
    }
  }

  private void visitNode(XmlNode xmlNode, String xmlName) {
    XmlElement element = xmlNode.getElement();
    tryEnqueuerOnString(element.getName(), xmlName);
    for (XmlAttribute xmlAttribute : element.getAttributeList()) {
      String value = xmlAttribute.getValue();
      tryEnqueuerOnString(value, xmlName);
      if (value.startsWith(".")) {
        // package specific names, e.g. context
        getPackageNames().forEach(s -> tryEnqueuerOnString(s + value, xmlName));
      }
    }
    element.getChildList().forEach(e -> visitNode(e, xmlName));
  }

  public Map<Integer, String> getResourceIdToXmlFiles() {
    if (resourceIdToXmlFiles == null) {
      resourceIdToXmlFiles = new HashMap<>();
      for (ResourceTable resourceTable : resourceTables.keySet()) {
        for (Package packageEntry : resourceTable.getPackageList()) {
          for (Resources.Type type : packageEntry.getTypeList()) {
            for (Entry entry : type.getEntryList()) {
              for (ConfigValue configValue : entry.getConfigValueList()) {
                if (configValue.hasValue()) {
                  Value value = configValue.getValue();
                  if (value.hasItem()) {
                    Item item = value.getItem();
                    if (item.hasFile()) {
                      FileReference file = item.getFile();
                      if (file.getType() == FileReference.Type.PROTO_XML) {
                        int id = ResourceTableUtilKt.toIdentifier(packageEntry, type, entry);
                        resourceIdToXmlFiles.put(id, file.getPath());
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return resourceIdToXmlFiles;
  }

  private List<Integer> getResourcesToRemove() {
    return r8ResourceShrinkerModel.getResourceStore().getResources().stream()
        .filter(r -> !r.isReachable() && !r.isPublic())
        .filter(r -> r.type != ResourceType.ID)
        .map(r -> r.value)
        .collect(Collectors.toList());
  }

  // Temporary to support updating the reachable entries from the manifest, we need to instead
  // trace these in the enqueuer.
  public void updateModelWithManifestReferences() throws IOException {
    if (manifestProvider == null) {
      return;
    }
    ProtoAndroidManifestUsageRecorderKt.recordUsagesFromNode(
        XmlNode.parseFrom(manifestProvider.get()), r8ResourceShrinkerModel);
  }

  public void updateModelWithKeepXmlReferences() throws IOException {
    for (Map.Entry<String, Supplier<InputStream>> entry : xmlFileProviders.entrySet()) {
      if (entry.getKey().startsWith("res/raw")) {
        ToolsAttributeUsageRecorderKt.processRawXml(
            getUtfReader(entry.getValue().get().readAllBytes()), r8ResourceShrinkerModel);
      }
    }
  }

  public void enqueuerDone(boolean isFinalTreeshaking) {
    enqueuerCallback = null;
    seenResourceIds.clear();
    if (!isFinalTreeshaking) {
      // After final tree shaking we will need the reachability bits to decide what to write out
      // from the model.
      clearReachableBits();
    }
  }

  public void clearReachableBits() {
    for (Resource resource : r8ResourceShrinkerModel.getResourceStore().getResources()) {
      resource.setReachable(false);
    }
  }

  public static class R8ResourceShrinkerModel extends ResourceShrinkerModel {
    private final Map<Integer, String> stringResourcesWithSingleValue = new HashMap<>();

    public R8ResourceShrinkerModel(
        ShrinkerDebugReporter debugReporter, boolean supportMultipackages) {
      super(debugReporter, supportMultipackages);
    }

    public String getSingleStringValueOrNull(int id) {
      return stringResourcesWithSingleValue.get(id);
    }

    // Similar to instantiation in ProtoResourceTableGatherer, but using an inputstream.
    ResourceTable instantiateFromResourceTable(InputStream inputStream, boolean includeStyleables) {
      try {
        ResourceTable resourceTable = ResourceTable.parseFrom(inputStream);
        instantiateFromResourceTable(resourceTable, includeStyleables);
        return resourceTable;
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    void instantiateFromResourceTable(ResourceTable resourceTable, boolean includeStyleables) {
      ResourceTableUtilKt.entriesSequence(resourceTable)
          .iterator()
          .forEachRemaining(
              entryWrapper -> {
                ResourceType resourceType = ResourceType.fromClassName(entryWrapper.getType());
                Entry entry = entryWrapper.getEntry();
                int entryId = entryWrapper.getId();
                recordSingleValueResources(resourceType, entry, entryId);
                if (resourceType != ResourceType.STYLEABLE || includeStyleables) {
                  this.addResource(
                      resourceType,
                      entryWrapper.getPackageName(),
                      ResourcesUtil.resourceNameToFieldName(entry.getName()),
                      entryId);
                }
              });
    }

    private void recordSingleValueResources(ResourceType resourceType, Entry entry, int entryId) {
      if (!entry.hasOverlayableItem() && entry.getConfigValueList().size() == 1) {
        if (resourceType == ResourceType.STRING) {
          ConfigValue configValue = entry.getConfigValue(0);
          if (configValue.hasValue()) {
            Value value = configValue.getValue();
            if (value.hasItem()) {
              Item item = value.getItem();
              if (item.hasStr()) {
                stringResourcesWithSingleValue.put(entryId, item.getStr().getValue());
              }
            }
          }
        }
      }
    }
  }
}
