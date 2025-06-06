// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.OptionalBool;

// This lens clears all code rewriting (lookup methods mimics identity lens behavior) but still
// relies on the previous lens for names (getRenamed/Original methods).
public class ClearCodeRewritingGraphLens extends DefaultNonIdentityGraphLens {

  public ClearCodeRewritingGraphLens(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(
        appView,
        appView
            .graphLens()
            .removeLenses(l -> l.isClearCodeRewritingLens() || l.isMemberRebindingIdentityLens()));
  }

  @Override
  public boolean isClearCodeRewritingLens() {
    return true;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    return getIdentityLens().lookupPrototypeChangesForMethodDefinition(method, codeLens);
  }

  @Override
  protected FieldLookupResult internalLookupField(
      DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
    return getIdentityLens().internalLookupField(reference, codeLens, continuation);
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    throw new Unreachable();
  }

  @Override
  protected MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      InvokeType type,
      OptionalBool isInterface,
      GraphLens codeLens,
      LookupMethodContinuation continuation) {
    assert codeLens == null || codeLens == this;
    GraphLens identityLens = getIdentityLens();
    return identityLens.internalLookupMethod(
        reference, context, type, isInterface, identityLens, continuation);
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    throw new Unreachable();
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    return true;
  }
}
