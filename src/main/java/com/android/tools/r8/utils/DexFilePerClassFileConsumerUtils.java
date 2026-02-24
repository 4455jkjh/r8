// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.DEX_EXTENSION;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.Resource;
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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DexFilePerClassFileConsumerUtils {

  public static String getDexFileName(String classDescriptor) {
    assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
    return DescriptorUtils.getClassBinaryNameFromDescriptor(classDescriptor) + DEX_EXTENSION;
  }

  public static class ArchiveConsumerUtils {

    public static void writeResourcesForTesting(
        Path archive,
        List<ProgramResource> resources,
        Map<Resource, String> primaryClassDescriptors)
        throws IOException, ResourceException {
      OpenOption[] options =
          new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
      try (Closer closer = Closer.create()) {
        try (ZipOutputStream out =
            new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(archive, options)))) {
          for (ProgramResource resource : resources) {
            String primaryClassDescriptor = primaryClassDescriptors.get(resource);
            String entryName = getDexFileName(primaryClassDescriptor);
            byte[] bytes = ByteStreams.toByteArray(closer.register(resource.getByteStream()));
            ZipUtils.writeToZipStream(out, entryName, bytes, ZipEntry.STORED);
          }
        }
      }
    }
  }

  public static class DirectoryConsumerUtils {

    public static void writeResources(
        Path directory,
        List<ProgramResource> resources,
        Map<Resource, String> primaryClassDescriptors)
        throws IOException, ResourceException {
      try (Closer closer = Closer.create()) {
        for (ProgramResource resource : resources) {
          String primaryClassDescriptor = primaryClassDescriptors.get(resource);
          Path target = getTargetDexFile(directory, primaryClassDescriptor);
          writeFile(ByteStreams.toByteArray(closer.register(resource.getByteStream())), target);
        }
      }
    }

    private static Path getTargetDexFile(Path directory, String primaryClassDescriptor) {
      return directory.resolve(getDexFileName(primaryClassDescriptor));
    }

    private static void writeFile(byte[] contents, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      FileUtils.writeToFile(target, null, contents);
    }
  }
}
