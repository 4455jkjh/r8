// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.FieldResolutionResult.createSingleFieldResolutionResult;

import com.android.tools.r8.utils.internal.SetUtils;
import java.util.Set;

/**
 * Implements resolution of a field descriptor against a type.
 *
 * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.2">
 * Section 5.4.3.2 of the JVM Spec</a>.
 */
public class FieldResolution {

  private final DexDefinitionSupplier definitionFor;

  public FieldResolution(DexDefinitionSupplier definitionFor) {
    this.definitionFor = definitionFor;
  }

  public FieldResolutionResult resolveFieldOn(DexType type, DexField field) {
    ClassResolutionResult classResolutionResult =
        definitionFor.contextIndependentDefinitionForWithResolutionResult(type);
    if (!classResolutionResult.isMultipleClassResolutionResult()) {
      // Fast path: this is called for each field reference, and types almost always resolve to a
      // single class, which avoids allocating a builder and a capturing lambda.
      DexClass clazz = classResolutionResult.toSingleClassWithProgramOverLibrary();
      return clazz != null ? resolveFieldOn(clazz, field) : FieldResolutionResult.failure();
    }
    FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
    classResolutionResult.forEachClassResolutionResult(
        clazz -> resolveFieldOn(clazz, field, clazz, null, builder));
    return builder.buildOrIfEmpty(FieldResolutionResult.failure());
  }

  public FieldResolutionResult resolveFieldOn(DexClass holder, DexField field) {
    assert holder != null;
    // Fast path: most fields are declared on the holder, which avoids allocating a builder.
    DexEncodedField definition = holder.lookupField(field);
    if (definition != null) {
      return createSingleFieldResolutionResult(holder, holder, definition);
    }
    FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
    resolveFieldOnSupertypes(holder, field, holder, null, builder);
    return builder.buildOrIfEmpty(FieldResolutionResult.failure());
  }

  private void resolveFieldOn(
      DexClass holder,
      DexField field,
      DexClass initialResolutionHolder,
      Set<DexType> visitedInterfaces,
      FieldResolutionResult.Builder builder) {
    assert holder != null;
    // Step 1: Class declares the field.
    DexEncodedField definition = holder.lookupField(field);
    if (definition != null) {
      builder.addResolutionResult(
          createSingleFieldResolutionResult(initialResolutionHolder, holder, definition));
      return;
    }
    resolveFieldOnSupertypes(holder, field, initialResolutionHolder, visitedInterfaces, builder);
  }

  private void resolveFieldOnSupertypes(
      DexClass holder,
      DexField field,
      DexClass initialResolutionHolder,
      Set<DexType> visitedInterfaces,
      FieldResolutionResult.Builder builder) {
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    if (!holder.interfaces.isEmpty()) {
      if (visitedInterfaces == null) {
        visitedInterfaces = SetUtils.newIdentityHashSet(8);
      }
      FieldResolutionResult result =
          resolveFieldOnDirectInterfaces(initialResolutionHolder, holder, field, visitedInterfaces);
      if (result != null) {
        builder.addResolutionResult(result);
        return;
      }
    }
    // Step 3: Apply recursively to superclass.
    if (holder.superType != null) {
      ClassResolutionResult superClassResolutionResult =
          definitionFor.contextIndependentDefinitionForWithResolutionResult(holder.superType);
      // Alternative superclass definitions share the set of visited interfaces.
      Set<DexType> finalVisitedInterfaces =
          visitedInterfaces == null && superClassResolutionResult.isMultipleClassResolutionResult()
              ? SetUtils.newIdentityHashSet(8)
              : visitedInterfaces;
      superClassResolutionResult.forEachClassResolutionResult(
          superClass -> {
            // Check if the subtype is a library type and if it is child of a non-library type.
            // If that is the case, do not return any results.
            if (holder.isLibraryClass() && !superClass.isLibraryClass()) {
              return;
            }
            resolveFieldOn(
                superClass, field, initialResolutionHolder, finalVisitedInterfaces, builder);
          });
    } else {
      builder.addResolutionResult(FieldResolutionResult.failure());
    }
  }

  private FieldResolutionResult resolveFieldOnDirectInterfaces(
      DexClass initialResolutionHolder,
      DexClass clazz,
      DexField field,
      Set<DexType> visitedInterfaces) {
    for (DexType interfaceType : clazz.interfaces.values) {
      if (visitedInterfaces.add(interfaceType)) {
        FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
        definitionFor
            .contextIndependentDefinitionForWithResolutionResult(interfaceType)
            .forEachClassResolutionResult(
                ifaceClass -> {
                  // Check if the subtype is a library type and if it is child of a non-library
                  // type. If that is the case, do not return any results.
                  if (clazz.isLibraryClass() && !ifaceClass.isLibraryClass()) {
                    return;
                  }
                  FieldResolutionResult otherResult =
                      resolveFieldOnInterface(
                          initialResolutionHolder, ifaceClass, field, visitedInterfaces);
                  if (otherResult != null) {
                    builder.addResolutionResult(otherResult);
                  }
                });
        FieldResolutionResult fieldResolutionResult = builder.buildOrIfEmpty(null);
        if (fieldResolutionResult != null) {
          return fieldResolutionResult;
        }
      }
    }
    return null;
  }

  private FieldResolutionResult resolveFieldOnInterface(
      DexClass initialResolutionHolder,
      DexClass interfaceClass,
      DexField field,
      Set<DexType> visitedInterfaces) {
    // Step 1: Class declares the field.
    DexEncodedField definition = interfaceClass.lookupField(field);
    if (definition != null) {
      return createSingleFieldResolutionResult(initialResolutionHolder, interfaceClass, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    return resolveFieldOnDirectInterfaces(
        initialResolutionHolder, interfaceClass, field, visitedInterfaces);
  }
}
