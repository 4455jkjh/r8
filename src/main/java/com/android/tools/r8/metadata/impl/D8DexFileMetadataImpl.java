// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.metadata.D8DexFileMetadata;

public class D8DexFileMetadataImpl extends D8R8DexFileMetadataImpl implements D8DexFileMetadata {

  private D8DexFileMetadataImpl(String checksum, int sizeInBytes, boolean startup) {
    super(checksum, sizeInBytes, startup);
  }

  public static D8DexFileMetadata create(VirtualFile virtualFile) {
    assert !virtualFile.isEmpty();
    String checksum = virtualFile.getChecksumForBuildMetadata().toString();
    int sizeInBytes = virtualFile.getSizeInBytesForBuildMetadata();
    boolean startup = virtualFile.isStartup();
    return new D8DexFileMetadataImpl(checksum, sizeInBytes, startup);
  }
}
