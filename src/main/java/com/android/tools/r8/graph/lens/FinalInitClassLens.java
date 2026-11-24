// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.utils.timing.Timing;
import java.util.IdentityHashMap;
import java.util.Map;

public class FinalInitClassLens extends InitClassLens {

  private final Map<DexType, DexField> mapping;

  FinalInitClassLens(Map<DexType, DexField> mapping) {
    this.mapping = mapping;
  }

  @Override
  public DexField getInitClassField(DexType type) {
    DexField field = mapping.get(type);
    if (field != null) {
      return field;
    }
    throw new Unreachable("Unexpected InitClass instruction for `" + type.toSourceString() + "`");
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public InitClassLens rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens, Timing timing) {
    return timing.time("Rewrite FinalInitClassLens", () -> rewrittenWithLens(definitions, lens));
  }

  private InitClassLens rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
    Map<DexType, DexField> rewrittenMapping = new IdentityHashMap<>(mapping.size());
    mapping.forEach(
        (type, field) -> {
          DexType rewrittenType = lens.lookupType(type);
          DexField rewrittenField = lens.lookupField(field);
          assert rewrittenField.getHolderType().isIdenticalTo(rewrittenType);
          rewrittenMapping.compute(
              rewrittenType,
              (unused, existingField) ->
                  selectInitClassField(definitions, rewrittenType, existingField, rewrittenField));
        });
    return new FinalInitClassLens(rewrittenMapping);
  }

  private static DexField selectInitClassField(
      DexDefinitionSupplier definitions,
      DexType rewrittenType,
      DexField existingField,
      DexField candidateField) {
    if (existingField == null || existingField.isIdenticalTo(candidateField)) {
      return candidateField;
    }
    DexClass holder = definitions.contextIndependentDefinitionFor(rewrittenType);
    if (holder != null) {
      int visibilityComparison =
          Integer.compare(
              getVisibilityOrdinal(holder, candidateField),
              getVisibilityOrdinal(holder, existingField));
      if (visibilityComparison != 0) {
        return visibilityComparison > 0 ? candidateField : existingField;
      }
    }
    return candidateField.compareTo(existingField) < 0 ? candidateField : existingField;
  }

  private static int getVisibilityOrdinal(DexClass holder, DexField field) {
    DexEncodedField definition = holder.lookupField(field);
    return definition != null ? definition.getAccessFlags().getVisibilityOrdinal() : -1;
  }
}
