// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.partition;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Path;

public class RetracePartitionTestUtils {

  public static PartitionMapConsumer createPartitionZipConsumer(Path pgMapFile) throws IOException {
    ZipBuilder zipBuilder = ZipBuilder.builder(pgMapFile);
    return new PartitionMapConsumer() {
      @Override
      public void acceptMappingPartition(MappingPartition mappingPartition) {
        try {
          zipBuilder.addBytes(mappingPartition.getKey(), mappingPartition.getPayload());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void acceptMappingPartitionMetadata(
          MappingPartitionMetadata mappingPartitionMetadata) {
        try {
          zipBuilder.addBytes("METADATA", mappingPartitionMetadata.getBytes());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        try {
          zipBuilder.build();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
