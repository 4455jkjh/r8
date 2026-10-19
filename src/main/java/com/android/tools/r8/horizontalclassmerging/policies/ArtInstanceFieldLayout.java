// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Iterables;

/**
 * Estimates the size of instances in ART, following the instance field layout of ART's
 * ClassLinker::LinkFields:
 *
 * <ul>
 *   <li>The fields of a class are laid out after the fields of its superclass. The fields of
 *       java.lang.Object form an 8-byte object header.
 *   <li>Reference fields (4 bytes) come first, followed by the primitive fields in decreasing order
 *       of size.
 *   <li>Each field is aligned to its size. The gaps introduced by alignment are filled with smaller
 *       primitive fields.
 *   <li>The instance size is rounded up to a multiple of 8 bytes.
 * </ul>
 *
 * <p>ART used a different memory representation before ART 7 (Android API level 24, officially
 * released on August 22, 2016), so horizontal class merging based on this layout can increase
 * runtime instance sizes on older runtimes.
 */
class ArtInstanceFieldLayout {

  static final int OBJECT_HEADER_SIZE = 8;
  private static final int OBJECT_ALIGNMENT = 8;
  private static final int REFERENCE_SIZE = 4;
  private static final int LARGEST_PRIMITIVE_SIZE = 8;

  static final int UNKNOWN_OFFSET = -1;

  /**
   * Returns the offset right after the last instance field of {@param type}, or its supertypes.
   *
   * <p>Returns {@link #UNKNOWN_OFFSET} if a supertype other than java.lang.Object is not a program
   * class, since the private fields of library classes are not known.
   */
  static int getInstanceFieldsEndOffset(AppView<?> appView, DexType type) {
    if (type.isIdenticalTo(appView.dexItemFactory().objectType)) {
      return OBJECT_HEADER_SIZE;
    }
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
    if (clazz == null) {
      return UNKNOWN_OFFSET;
    }
    int superFieldsEndOffset = getInstanceFieldsEndOffset(appView, clazz.getSuperType());
    if (superFieldsEndOffset == UNKNOWN_OFFSET) {
      return UNKNOWN_OFFSET;
    }
    return getInstanceFieldsEndOffset(
        superFieldsEndOffset,
        Iterables.transform(clazz.instanceFields(), DexEncodedField::getType));
  }

  /**
   * Returns the offset right after the last field, when laying out fields of the given types
   * starting at {@param startOffset}.
   */
  static int getInstanceFieldsEndOffset(int startOffset, Iterable<DexType> fieldTypes) {
    int referenceFieldCount = 0;
    // Indexed by the size of the primitive field in bytes (1, 2, 4 or 8).
    int[] primitiveFieldCountBySize = new int[LARGEST_PRIMITIVE_SIZE + 1];
    for (DexType fieldType : fieldTypes) {
      if (fieldType.isReferenceType()) {
        referenceFieldCount++;
      } else {
        primitiveFieldCountBySize[fieldType.getPrimitiveTypeSizeInBytes()]++;
      }
    }
    int offset = startOffset;
    if (referenceFieldCount > 0) {
      offset = alignAndFillGaps(offset, REFERENCE_SIZE, primitiveFieldCountBySize);
      offset += referenceFieldCount * REFERENCE_SIZE;
    }
    for (int size = LARGEST_PRIMITIVE_SIZE; size >= 1; size /= 2) {
      if (primitiveFieldCountBySize[size] > 0) {
        offset = alignAndFillGaps(offset, size, primitiveFieldCountBySize);
        offset += primitiveFieldCountBySize[size] * size;
      }
    }
    return offset;
  }

  /** Returns the instance size given the offset right after the last instance field. */
  static int getInstanceSize(int instanceFieldsEndOffset) {
    assert instanceFieldsEndOffset >= OBJECT_HEADER_SIZE;
    return (instanceFieldsEndOffset + OBJECT_ALIGNMENT - 1) & ~(OBJECT_ALIGNMENT - 1);
  }

  /**
   * Aligns {@param offset} to {@param alignment}, and fills the gaps skipped by the alignment with
   * the remaining primitive fields that fit.
   */
  private static int alignAndFillGaps(int offset, int alignment, int[] primitiveFieldCountBySize) {
    for (int gapSize = 1; gapSize < alignment; gapSize *= 2) {
      if ((offset & gapSize) != 0) {
        fillGap(gapSize, primitiveFieldCountBySize);
        offset += gapSize;
      }
    }
    return offset;
  }

  /** Removes the primitive fields that are placed in a gap of {@param gapSize} bytes. */
  private static void fillGap(int gapSize, int[] primitiveFieldCountBySize) {
    int remainingGapSize = gapSize;
    for (int size = gapSize; size >= 1; size /= 2) {
      int fieldsInGap = Math.min(primitiveFieldCountBySize[size], remainingGapSize / size);
      primitiveFieldCountBySize[size] -= fieldsInGap;
      remainingGapSize -= fieldsInGap * size;
    }
  }
}
