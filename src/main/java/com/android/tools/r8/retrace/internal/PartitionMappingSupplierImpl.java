// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * IntelliJ highlights the class as being invalid because it cannot see getClassNameMapper is
 * defined on the class for some reason.
 */
public class PartitionMappingSupplierImpl extends PartitionMappingSupplier {

  private final byte[] metadata;
  private final Consumer<String> partitionToFetchConsumer;
  private final Runnable prepare;
  private final Function<String, byte[]> partitionSupplier;
  private final boolean allowExperimental;

  private ClassNameMapper classNameMapper;
  private final Set<String> pendingKeys = new LinkedHashSet<>();
  private final Set<String> builtKeys = new HashSet<>();

  private MappingPartitionMetadataInternal mappingPartitionMetadataCache;

  PartitionMappingSupplierImpl(
      byte[] metadata,
      Consumer<String> partitionToFetchConsumer,
      Runnable prepare,
      Function<String, byte[]> partitionSupplier,
      boolean allowExperimental) {
    this.metadata = metadata;
    this.partitionToFetchConsumer = partitionToFetchConsumer;
    this.prepare = prepare;
    this.partitionSupplier = partitionSupplier;
    this.allowExperimental = allowExperimental;
  }

  private MappingPartitionMetadataInternal getMetadata(DiagnosticsHandler diagnosticsHandler) {
    return mappingPartitionMetadataCache =
        MappingPartitionMetadataInternal.createFromBytes(
            metadata, MapVersion.MAP_VERSION_NONE, diagnosticsHandler);
  }

  @Override
  Set<MapVersionMappingInformation> getMapVersions(DiagnosticsHandler diagnosticsHandler) {
    return Collections.singleton(
        new MapVersionMappingInformation(getMetadata(diagnosticsHandler).getMapVersion(), ""));
  }

  @Override
  ClassNamingForNameMapper getClassNaming(DiagnosticsHandler diagnosticsHandler, String typeName) {
    registerClassUse(diagnosticsHandler, Reference.classFromTypeName(typeName));
    return getClassNameMapper(diagnosticsHandler).getClassNaming(typeName);
  }

  @Override
  String getSourceFileForClass(DiagnosticsHandler diagnosticsHandler, String typeName) {
    // Getting source file should not trigger new fetches of partitions so we are not calling
    // register here.
    return getClassNameMapper(diagnosticsHandler).getSourceFile(typeName);
  }

  private ClassNameMapper getClassNameMapper(DiagnosticsHandler diagnosticsHandler) {
    MappingPartitionMetadataInternal metadata = getMetadata(diagnosticsHandler);
    if (!pendingKeys.isEmpty()) {
      prepare.run();
    }
    for (String pendingKey : pendingKeys) {
      try {
        LineReader reader =
            new ProguardMapReaderWithFilteringInputBuffer(
                new ByteArrayInputStream(partitionSupplier.apply(pendingKey)), alwaysTrue(), true);
        classNameMapper =
            ClassNameMapper.mapperFromLineReaderWithFiltering(
                    reader, metadata.getMapVersion(), diagnosticsHandler, true, allowExperimental)
                .combine(this.classNameMapper);
      } catch (IOException e) {
        throw new InvalidMappingFileException(e);
      }
    }
    builtKeys.addAll(pendingKeys);
    pendingKeys.clear();
    if (classNameMapper == null) {
      classNameMapper = ClassNameMapper.builder().build();
    }
    return classNameMapper;
  }

  @Override
  public PartitionMappingSupplier registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    registerKeyUse(getMetadata(diagnosticsHandler).getKey(classReference));
    return this;
  }

  private void registerKeyUse(String key) {
    if (!builtKeys.contains(key) && pendingKeys.add(key)) {
      partitionToFetchConsumer.accept(key);
    }
  }

  @Override
  public void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler) {
    String errorMessage = "Cannot verify map file hash for partitions";
    diagnosticsHandler.error(new StringDiagnostic(errorMessage));
    throw new RuntimeException(errorMessage);
  }
}
