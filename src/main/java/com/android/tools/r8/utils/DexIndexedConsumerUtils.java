// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DexIndexedConsumerUtils {

  public static class ArchiveConsumerUtils {

    public static void writeResourcesForTesting(
        Path archive,
        List<ProgramResource> resources,
        Set<DataDirectoryResource> dataDirectoryResources,
        Set<DataEntryResource> dataEntryResources)
        throws IOException, ResourceException {
      OpenOption[] options =
          new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
      try (Closer closer = Closer.create()) {
        try (ZipOutputStream out =
            new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(archive, options)))) {
          for (int i = 0; i < resources.size(); i++) {
            ProgramResource resource = resources.get(i);
            String entryName = DexUtils.getDefaultDexFileName(i);
            byte[] bytes = ByteStreams.toByteArray(closer.register(resource.getByteStream()));
            ZipUtils.writeToZipStream(out, entryName, bytes, ZipEntry.STORED);
          }
          for (DataDirectoryResource dataDirectoryResource : dataDirectoryResources) {
            ZipUtils.writeToZipStream(
                out, dataDirectoryResource.getName(), new byte[0], ZipEntry.STORED);
          }
          for (DataEntryResource dataEntryResource : dataEntryResources) {
            String entryName = dataEntryResource.getName();
            byte[] bytes =
                ByteStreams.toByteArray(closer.register(dataEntryResource.getByteStream()));
            ZipUtils.writeToZipStream(out, entryName, bytes, ZipEntry.STORED);
          }
        }
      }
    }
  }

  public static class DirectoryConsumerUtils {

    public static void deleteClassesDexFiles(Path directory) throws IOException {
      try (Stream<Path> filesInDir = Files.list(directory)) {
        for (Path path : filesInDir.collect(Collectors.toList())) {
          if (FileUtils.isClassesDexFile(path)) {
            Files.delete(path);
          }
        }
      }
    }

    public static void writeResources(Path directory, List<ProgramResource> resources)
        throws IOException, ResourceException {
      deleteClassesDexFiles(directory);
      try (Closer closer = Closer.create()) {
        for (int i = 0; i < resources.size(); i++) {
          ProgramResource resource = resources.get(i);
          Path target = getTargetDexFile(directory, i);
          writeFile(ByteStreams.toByteArray(closer.register(resource.getByteStream())), target);
        }
      }
    }

    private static Path getTargetDexFile(Path directory, int fileIndex) {
      return directory.resolve(DexUtils.getDefaultDexFileName(fileIndex));
    }

    private static void writeFile(byte[] contents, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      FileUtils.writeToFile(target, null, contents);
    }
  }
}
