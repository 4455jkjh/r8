// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;

public class BaseCompilerCommandUtils {

  public static InternalProgramOutputPathConsumer createProgramOutputConsumer(
      Path path, OutputMode mode, boolean consumeDataResources) {
    if (mode == OutputMode.DexIndexed) {
      return FileUtils.isArchive(path)
          ? new DexIndexedConsumer.ArchiveConsumer(path, consumeDataResources)
          : new DexIndexedConsumer.DirectoryConsumer(path, consumeDataResources);
    }
    if (mode == OutputMode.DexFilePerClass) {
      if (FileUtils.isArchive(path)) {
        return new DexFilePerClassFileConsumer.ArchiveConsumer(path, consumeDataResources) {
          @Override
          public boolean combineSyntheticClassesWithPrimaryClass() {
            return false;
          }
        };
      } else {
        return new DexFilePerClassFileConsumer.DirectoryConsumer(path, consumeDataResources) {
          @Override
          public boolean combineSyntheticClassesWithPrimaryClass() {
            return false;
          }
        };
      }
    }
    if (mode == OutputMode.DexFilePerClassFile) {
      return FileUtils.isArchive(path)
          ? new DexFilePerClassFileConsumer.ArchiveConsumer(path, consumeDataResources)
          : new DexFilePerClassFileConsumer.DirectoryConsumer(path, consumeDataResources);
    }
    if (mode == OutputMode.ClassFile) {
      return (InternalProgramOutputPathConsumer)
          createClassFileProgramOutputConsumer(path, consumeDataResources);
    }
    throw new Unreachable("Unexpected output mode: " + mode);
  }

  public static ClassFileConsumer createClassFileProgramOutputConsumer(
      Path path, boolean consumeDataResources) {
    return FileUtils.isArchive(path)
        ? new ClassFileConsumer.ArchiveConsumer(path, consumeDataResources)
        : new ClassFileConsumer.DirectoryConsumer(path, consumeDataResources);
  }
}
