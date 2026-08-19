// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/**
 * Diagnostic information about errors when classes cannot fit in a DEX file.
 *
 * <p>This can happen when compiling to a single DEX file but not all classes can fit in it; or when
 * compiling for legacy multidex but there are too many classes that need to fit in the main DEX
 * file, e.g., classes.dex.
 */
@KeepForApi
public class DexFileOverflowDiagnostic implements Diagnostic {
  private final boolean hasMainDexSpecification;
  private final long numOfMethods;
  private final long numOfFields;
  private final long numOfTypes;
  private final long maxNumOfFields;
  private final long maxNumOfTypes;

  public DexFileOverflowDiagnostic(
      boolean hasMainDexSpecification, long numOfMethods, long numOfFields) {
    this(
        hasMainDexSpecification,
        numOfMethods,
        numOfFields,
        0,
        VirtualFile.MAX_ENTRIES,
        VirtualFile.MAX_ENTRIES);
  }

  public DexFileOverflowDiagnostic(
      boolean hasMainDexSpecification,
      long numOfMethods,
      long numOfFields,
      long numOfTypes,
      long maxNumOfTypes) {
    this(
        hasMainDexSpecification,
        numOfMethods,
        numOfFields,
        numOfTypes,
        VirtualFile.MAX_ENTRIES,
        maxNumOfTypes);
  }

  public DexFileOverflowDiagnostic(
      boolean hasMainDexSpecification,
      long numOfMethods,
      long numOfFields,
      long numOfTypes,
      long maxNumOfTypes,
      long maxNumOfFields) {
    this.hasMainDexSpecification = hasMainDexSpecification;
    this.numOfMethods = numOfMethods;
    this.numOfFields = numOfFields;
    this.numOfTypes = numOfTypes;
    this.maxNumOfTypes = maxNumOfTypes;
    this.maxNumOfFields = maxNumOfFields;
  }

  /** The number of fields that the application needs to include in the main DEX file. */
  public long getNumberOfFields() {
    return numOfFields;
  }

  /** The number of methods that the application needs to include in the main DEX file. */
  public long getNumberOfMethods() {
    return numOfMethods;
  }

  /** The number of types that the application needs to include in the main DEX file. */
  public long getNumberOfTypes() {
    return numOfTypes;
  }

  /** The maximum number of fields that can be included in a DEX file. */
  public long getMaximumNumberOfFields() {
    return maxNumOfFields;
  }

  /** The maximum number of methods that can be included in a DEX file. */
  public long getMaximumNumberOfMethods() {
    return VirtualFile.MAX_ENTRIES;
  }

  /** The maximum number of types that can be included in a DEX file. */
  public long getMaximumNumberOfTypes() {
    return maxNumOfTypes;
  }

  /** True if the application has specified lists and/or rules for computing the main DEX file. */
  public boolean hasMainDexSpecification() {
    return hasMainDexSpecification;
  }

  /** The origin of a main DEX file overflow is not unique. (The whole app is to blame.) */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** The position of the main DEX error is not specified. */
  @Override
  public Position getPosition() {
    return null;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    // General message: Cannot fit.
    builder
        .append("Cannot fit requested classes in ")
        .append(hasMainDexSpecification() ? "the main-" : "a single ")
        .append("dex file")
        .append(" (");
    // Show the numbers of methods and/or fields and/or types that exceed the limit.
    boolean hasItem = false;
    if (getNumberOfMethods() > getMaximumNumberOfMethods()) {
      builder
          .append("# methods: ")
          .append(getNumberOfMethods())
          .append(" > ")
          .append(getMaximumNumberOfMethods());
      hasItem = true;
    }
    if (getNumberOfFields() > getMaximumNumberOfFields()) {
      if (hasItem) {
        builder.append(" ; ");
      }
      builder
          .append("# fields: ")
          .append(getNumberOfFields())
          .append(" > ")
          .append(getMaximumNumberOfFields());
      hasItem = true;
    }
    if (getNumberOfTypes() > getMaximumNumberOfTypes()) {
      if (hasItem) {
        builder.append(" ; ");
      }
      builder
          .append("# types: ")
          .append(getNumberOfTypes())
          .append(" > ")
          .append(getMaximumNumberOfTypes());
    }
    return builder.append(")").toString();
  }
}
