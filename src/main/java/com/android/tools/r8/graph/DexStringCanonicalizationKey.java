// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.Arrays;

/**
 * Key wrapping MUTF-8 bytes and their precomputed hash code, used for canonicalization of pending
 * {@link DexString} instances in {@link DexItemFactory} without constructing a {@link DexString} on
 * lookup hits.
 */
final class DexStringCanonicalizationKey {

  private final byte[] content;
  private final int hash;

  DexStringCanonicalizationKey(byte[] content, int hash) {
    this.content = content;
    this.hash = hash;
  }

  int getHash() {
    return hash;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof DexStringCanonicalizationKey)) {
      return false;
    }
    DexStringCanonicalizationKey o = (DexStringCanonicalizationKey) other;
    return hash == o.hash && Arrays.equals(content, o.content);
  }
}
