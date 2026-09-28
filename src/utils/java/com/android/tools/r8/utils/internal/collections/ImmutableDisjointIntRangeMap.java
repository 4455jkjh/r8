// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal.collections;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A specialized version of {@link SegmentTree} with tiny size.
 *
 * <p>All ranges are inclusive on both bounds.
 *
 * <p>Use {@link #builder()} to construct.
 */
public class ImmutableDisjointIntRangeMap<V> {

  private static final ImmutableDisjointIntRangeMap<Object> EMPTY =
      new ImmutableDisjointIntRangeMap<>(new int[0], new int[0], new Object[0]);

  private final int[] starts;
  private final int[] ends;
  private final V[] values;

  private ImmutableDisjointIntRangeMap(int[] starts, int[] ends, V[] values) {
    this.starts = starts;
    this.ends = ends;
    this.values = values;
  }

  @SuppressWarnings("unchecked")
  public static <V> ImmutableDisjointIntRangeMap<V> empty() {
    return (ImmutableDisjointIntRangeMap<V>) EMPTY;
  }

  public static <V> Builder<V> builder() {
    return new Builder<>();
  }

  public int size() {
    return starts.length;
  }

  public boolean isEmpty() {
    return starts.length == 0;
  }

  public static class Entry<V> {
    public final int start;
    public final V value;

    private Entry(int start, V value) {
      this.start = start;
      this.value = value;
    }
  }

  public Entry<V> getEntry(int point) {
    int index = findRangeIndex(point);
    return index >= 0 ? new Entry<>(starts[index], values[index]) : null;
  }

  public V get(int point) {
    int index = findRangeIndex(point);
    return index >= 0 ? values[index] : null;
  }

  /** Returns the entry index of the given point, or -1. */
  private int findRangeIndex(int point) {
    int index = Arrays.binarySearch(starts, point);
    if (index >= 0) {
      return index;
    }
    // Arrays.binarySearch returns, index = -(insertion point) - 1.
    // So, insertion point = -(index + 1), simplified to -index - 1
    // The greatest element less than point is one earlier, so:
    int candidate = -index - 2;
    if (candidate >= 0 && point <= ends[candidate]) {
      return candidate;
    }
    return -1;
  }

  public void forEach(Consumer<? super V> consumer) {
    for (V value : values) {
      consumer.accept(value);
    }
  }

  public static class Builder<V> {

    private static class RangeEntry<V> {
      final int end;
      final V value;

      RangeEntry(int end, V value) {
        this.end = end;
        this.value = value;
      }
    }

    private final TreeMap<Integer, RangeEntry<V>> ranges = new TreeMap<>();

    /**
     * @param start start of the range (inclusive)
     * @param end end of the range (inclusive)
     * @param value value of the range (non-null)
     * @throws OverlappingRangeException if this entry overlaps with existing range(s)
     * @throws IllegalArgumentException if the range is invalid
     */
    public Builder<V> add(int start, int end, V value) {
      checkValidRange(start, end);
      checkNonOverlapping(start, end);
      addWithoutRangeChecks(start, end, value);
      return this;
    }

    private void addWithoutRangeChecks(int start, int end, V value) {
      if (value == null) {
        throw new IllegalArgumentException(
            "Cannot insert null values [" + start + ", " + end + "]");
      }
      ranges.put(start, new RangeEntry<>(end, value));
    }

    /** See {@link #addUncoveredSubRanges(int, int, BiFunction)}. */
    public Builder<V> addUncoveredSubRanges(int start, int end, V value) {
      return addUncoveredSubRanges(start, end, (subStart, subEnd) -> value);
    }

    /**
     * Adds the portions of the given range that are not already covered by existing ranges,
     * splitting the range into disjoint sub-intervals if necessary. Portions of the range that
     * overlap with already added ranges are skipped.
     */
    public Builder<V> addUncoveredSubRanges(
        int start, int end, BiFunction<Integer, Integer, V> valueFactory) {
      checkValidRange(start, end);
      int current = start;
      Map.Entry<Integer, RangeEntry<V>> floor = ranges.floorEntry(start);
      if (floor != null && floor.getValue().end >= current) {
        if (floor.getValue().end == Integer.MAX_VALUE) {
          return this;
        }
        current = floor.getValue().end + 1;
      }
      while (current <= end) {
        Map.Entry<Integer, RangeEntry<V>> next = ranges.ceilingEntry(current);
        if (next == null || next.getKey() > end) {
          V value = valueFactory.apply(current, end);
          addWithoutRangeChecks(current, end, value);
          break;
        }
        if (next.getKey() > current) {
          int gapEnd = next.getKey() - 1;
          V value = valueFactory.apply(current, gapEnd);
          addWithoutRangeChecks(current, gapEnd, value);
        }
        if (next.getValue().end == Integer.MAX_VALUE) {
          break;
        }
        var nextCurrent = next.getValue().end + 1;
        assert nextCurrent > current : "stepped from " + current + " to " + nextCurrent;
        current = nextCurrent;
      }
      return this;
    }

    public ImmutableDisjointIntRangeMap<V> build() {
      if (ranges.isEmpty()) {
        return empty();
      }
      int size = ranges.size();
      int[] starts = new int[size];
      int[] ends = new int[size];
      @SuppressWarnings("unchecked")
      V[] values = (V[]) new Object[size];
      int i = 0;
      for (Map.Entry<Integer, RangeEntry<V>> entry : ranges.entrySet()) {
        starts[i] = entry.getKey();
        ends[i] = entry.getValue().end;
        values[i] = entry.getValue().value;
        i++;
      }
      return new ImmutableDisjointIntRangeMap<>(starts, ends, values);
    }

    public static class OverlappingRangeException extends IllegalArgumentException {
      public OverlappingRangeException(String s) {
        super(s);
      }
    }

    /**
     * @throws IllegalArgumentException if invalid.
     */
    private static void checkValidRange(int start, int end) {
      if (start > end) {
        throw new IllegalArgumentException("Invalid range: " + start + " > " + end);
      }
    }

    /**
     * @throws OverlappingRangeException if overlapping.
     */
    private void checkNonOverlapping(int start, int end) {
      Map.Entry<Integer, RangeEntry<V>> floorEntry = ranges.floorEntry(start);
      if (floorEntry != null && floorEntry.getValue().end >= start) {
        throw new OverlappingRangeException(
            "Range ["
                + start
                + ", "
                + end
                + "] overlaps with existing range ["
                + floorEntry.getKey()
                + ", "
                + floorEntry.getValue().end
                + "]");
      }

      Map.Entry<Integer, RangeEntry<V>> ceilingEntry = ranges.ceilingEntry(start);
      if (ceilingEntry != null && end >= ceilingEntry.getKey()) {
        throw new OverlappingRangeException(
            "Range ["
                + start
                + ", "
                + end
                + "] overlaps with existing range ["
                + ceilingEntry.getKey()
                + ", "
                + ceilingEntry.getValue().end
                + "]");
      }
    }
  }
}
