// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.internal.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.internal.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.internal.collections.MutableBidirectionalOneToOneMap;

/**
 * Maps private interface instance methods to their staticized counterparts, which take the receiver
 * as their first argument. The argument list is unchanged, so there are no prototype changes.
 */
public class PrivateInterfaceMethodGraphLens extends NestedGraphLens {

  private PrivateInterfaceMethodGraphLens(
      AppView<?> appView, BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap) {
    super(appView, EMPTY_FIELD_MAP, methodMap, EMPTY_TYPE_MAP);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean isPrivateInterfaceMethodLens() {
    return true;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    // Leave invalid static invokes of private instance methods unchanged, since the staticized
    // method takes the receiver as an additional argument.
    if (previous.getType().isStatic()) {
      return previous.verify(this, codeLens);
    }
    return internalDescribeLookupMethod(
        previous,
        context,
        codeLens,
        resultBuilder -> {
          // The staticized method is on an interface. Set the interface bit, since invokes in DEX
          // input do not have it. Invokes of unmapped methods are left unchanged.
          if (resultBuilder.getReference().isNotIdenticalTo(previous.getReference())) {
            resultBuilder.setIsInterface(true);
          }
        });
  }

  @Override
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod newReboundMethod, DexMethod previousMethod, InvokeType type) {
    return newMethod.isIdenticalTo(previousMethod) ? type : InvokeType.STATIC;
  }

  public static class Builder {

    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    public void recordMove(DexMethod from, DexMethod to) {
      newMethodSignatures.put(from, to);
    }

    public PrivateInterfaceMethodGraphLens build(AppView<?> appView) {
      return new PrivateInterfaceMethodGraphLens(appView, newMethodSignatures);
    }
  }
}
