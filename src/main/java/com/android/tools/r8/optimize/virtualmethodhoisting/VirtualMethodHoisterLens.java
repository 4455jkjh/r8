// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.virtualmethodhoisting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualMethodHoisterLens extends DefaultNonIdentityGraphLens {

  private final Map<DexMethod, DexMethod> methodMap;

  public VirtualMethodHoisterLens(AppView<?> appView, Map<DexMethod, DexMethod> methodMap) {
    super(appView);
    this.methodMap = methodMap;
  }

  // Methods.

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return methodMap.getOrDefault(method, method);
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    DexMethod newReference = methodMap.get(previous.getReference());
    if (newReference != null) {
      return MethodLookupResult.builder(this, codeLens)
          .setReboundReference(newReference)
          .setReference(newReference)
          .setPrototypeChanges(previous.getPrototypeChanges())
          .setType(previous.getType())
          .build();
    }
    return previous.verify(this, codeLens);
  }

  public static class Builder {

    private final Map<DexMethod, DexMethod> methodMap = new ConcurrentHashMap<>();

    public void map(ProgramMethod from, ProgramMethod to) {
      methodMap.put(from.getReference(), to.getReference());
    }

    public boolean isEmpty() {
      return methodMap.isEmpty();
    }

    public VirtualMethodHoisterLens build(AppView<?> appView) {
      return new VirtualMethodHoisterLens(appView, new IdentityHashMap<>(methodMap));
    }
  }
}
