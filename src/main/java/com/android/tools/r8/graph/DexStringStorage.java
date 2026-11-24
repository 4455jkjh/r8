// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.Map;

/**
 * Flat open-addressed hash table for committed {@link DexString} instances.
 *
 * <p>Compared to storing committed strings in a {@code Map<DexStringCanonicalizationKey,
 * DexString>}, this avoids retaining a {@link DexStringCanonicalizationKey} wrapper and map entry
 * node per committed string, and allows probing directly with a raw MUTF-8 {@code byte[]} or an
 * ASCII {@link String} ({@link #getAscii}) without allocating a key object or encoding an ASCII
 * {@link String} to {@code byte[]} on lookups.
 */
final class DexStringStorage {

  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  private DexString[] items = new DexString[INITIAL_CAPACITY];
  private int[] hashes = new int[INITIAL_CAPACITY];
  // The table capacity is always a power of two, so capacity - 1 is a bitmask of all ones in the
  // low bits and masking with it keeps indices within bounds.
  private int mask = INITIAL_CAPACITY - 1;
  private int count = 0;
  private int maxFill = computeMaxFill(INITIAL_CAPACITY);

  private static int computeMaxFill(int capacity) {
    return Math.min((int) (capacity * LOAD_FACTOR), capacity - 1);
  }

  private static int firstIndex(int hash, int mask) {
    // HashCommon#mix scrambles the 32-bit hash (multiplying by the Fibonacci hashing constant
    // 0x9E3779B9 and XOR-folding the upper 16 bits into the lower 16 bits) so poorly distributed
    // low bits do not cluster when masked into a power-of-two table.
    return HashCommon.mix(hash) & mask;
  }

  DexString get(byte[] content, int contentHash) {
    DexString[] tableItems = this.items;
    int[] tableHashes = this.hashes;
    int tableMask = this.mask;
    int pos = firstIndex(contentHash, tableMask);
    while (true) {
      DexString existing = tableItems[pos];
      if (existing == null) {
        return null;
      }
      if (tableHashes[pos] == contentHash && Arrays.equals(existing.content, content)) {
        return existing;
      }
      pos = (pos + 1) & tableMask;
    }
  }

  DexString getAscii(String source, int contentHash) {
    DexString[] tableItems = this.items;
    int[] tableHashes = this.hashes;
    int tableMask = this.mask;
    int pos = firstIndex(contentHash, tableMask);
    while (true) {
      DexString existing = tableItems[pos];
      if (existing == null) {
        return null;
      }
      if (tableHashes[pos] == contentHash && existing.isEqualToAscii(source)) {
        return existing;
      }
      pos = (pos + 1) & tableMask;
    }
  }

  void commit(Map<DexStringCanonicalizationKey, DexString> pending) {
    int pendingSize = pending.size();
    if (pendingSize == 0) {
      return;
    }
    int requiredCount = count + pendingSize;
    if (requiredCount > maxFill) {
      int newCapacity =
          Math.max(INITIAL_CAPACITY, HashCommon.arraySize(requiredCount, LOAD_FACTOR));
      while (computeMaxFill(newCapacity) < requiredCount) {
        newCapacity <<= 1;
      }
      rehash(newCapacity);
    }
    for (Map.Entry<DexStringCanonicalizationKey, DexString> entry : pending.entrySet()) {
      put(entry.getValue(), entry.getKey().getHash());
    }
    pending.clear();
  }

  private void put(DexString value, int valueHash) {
    if (count >= maxFill) {
      rehash(items.length << 1);
    }
    DexString[] tableItems = this.items;
    int[] tableHashes = this.hashes;
    int tableMask = this.mask;
    int pos = firstIndex(valueHash, tableMask);
    while (true) {
      DexString existing = tableItems[pos];
      if (existing == null) {
        tableItems[pos] = value;
        tableHashes[pos] = valueHash;
        count++;
        return;
      }
      if (existing.isIdenticalTo(value)
          || (tableHashes[pos] == valueHash && Arrays.equals(existing.content, value.content))) {
        return;
      }
      pos = (pos + 1) & tableMask;
    }
  }

  private void rehash(int newCapacity) {
    assert Integer.bitCount(newCapacity) == 1;
    DexString[] newItems = new DexString[newCapacity];
    int[] newHashes = new int[newCapacity];
    int newMask = newCapacity - 1;
    DexString[] oldItems = this.items;
    int[] oldHashes = this.hashes;
    for (int i = 0; i < oldItems.length; i++) {
      DexString item = oldItems[i];
      if (item == null) {
        continue;
      }
      int itemHash = oldHashes[i];
      int pos = firstIndex(itemHash, newMask);
      while (newItems[pos] != null) {
        pos = (pos + 1) & newMask;
      }
      newItems[pos] = item;
      newHashes[pos] = itemHash;
    }
    this.items = newItems;
    this.hashes = newHashes;
    this.mask = newMask;
    this.maxFill = computeMaxFill(newCapacity);
  }
}
