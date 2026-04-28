// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.withMainProgramHandler;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.retrace.internal.MappingPartitionImpl;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/** A tool for creating a partition-map from a proguard map. */
@KeepForApi
public class Partition {

  public static void run(String[] args) throws CompilationFailedException {
    PartitionCommand command = PartitionCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    run(command);
  }

  /**
   * The main entry point for partitioning a map.
   *
   * @param command The command that describes the desired behavior of this partition invocation.
   */
  public static void run(PartitionCommand command) throws CompilationFailedException {
    if (command.isPrintHelp()) {
      System.out.println(PartitionCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("Partition " + Version.getVersionString());
      return;
    }
    ExceptionUtils.withCompilationHandler(
        command.getReporter(),
        () -> {
          if (command.getPartitionMapSuppliers().isEmpty()) {
            runConvert(command);
          } else {
            runMerge(command);
          }
        });
  }

  private static void runConvert(PartitionCommand command) throws IOException {
    command
        .getPartitionMapConsumer()
        .acceptMappingPartitionMetadata(
            ProguardMapPartitioner.builder(command.getReporter())
                .setProguardMapProducer(command.getProguardMapProducer())
                .setPartitionConsumer(command.getPartitionMapConsumer()::acceptMappingPartition)
                .setAllowEmptyMappedRanges(true)
                .setAllowExperimentalMapping(false)
                .build()
                .run());
    command.getPartitionMapConsumer().finished(command.getReporter());
  }

  private static void runMerge(PartitionCommand command) {
    assert command.getProguardMapProducer() == null;

    Reporter reporter = command.getReporter();
    Map<String, byte[]> partitions = new LinkedHashMap<>();

    // Process PartitionMapSuppliers
    MappingPartitionMetadataInternal mergedMetadata = null;
    for (PartitionMappingSupplier supplier : command.getPartitionMapSuppliers()) {
      MappingPartitionMetadataInternal metadata = supplier.getMetadata(reporter);
      if (mergedMetadata == null) {
        mergedMetadata = metadata;
      } else {
        mergedMetadata = mergedMetadata.combineMetadata(metadata, command.getPartitionMapId());
      }
      for (String key : metadata.getPartitionKeys()) {
        byte[] payload = supplier.getMappingPartitionFromKeySupplier().get(key);
        byte[] previousPayload = partitions.put(key, payload);
        if (previousPayload != null) {
          throw new RetracePartitionException(
              "Composition not supported, multiple payloads for same key (" + key + ") found");
        }
      }
    }

    // Output merged partitions
    PartitionMapConsumer consumer = command.getPartitionMapConsumer();
    for (Entry<String, byte[]> entry : partitions.entrySet()) {
      consumer.acceptMappingPartition(new MappingPartitionImpl(entry.getKey(), entry.getValue()));
    }
    consumer.acceptMappingPartitionMetadata(mergedMetadata);
    consumer.finished(reporter);
  }

  /**
   * The main entry point for running a legacy proguard map to partition map from command line.
   *
   * @param args The argument that describes this command.
   */
  public static void main(String... args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines("Invalid invocation.", PartitionCommandParser.getUsageMessage()));
    }
    withMainProgramHandler(() -> run(args));
  }
}
