// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import java.util.function.Consumer;

/**
 * Definition of a single synthetic method item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticMethodDefinition
    extends SyntheticDefinition<
        SyntheticMethodReference, SyntheticMethodDefinition, DexProgramClass>
    implements SyntheticProgramDefinition {

  private final ProgramMethod method;

  SyntheticMethodDefinition(SyntheticKind kind, SynthesizingContext context, ProgramMethod method) {
    super(kind, context);
    assert kind.isSingleSyntheticMethod();
    this.method = method;
  }

  @Override
  public void apply(
      Consumer<SyntheticMethodDefinition> onMethod,
      Consumer<SyntheticProgramClassDefinition> onClass) {
    onMethod.accept(this);
  }

  public ProgramMethod getMethod() {
    return method;
  }

  @Override
  public boolean isMethodDefinition() {
    return true;
  }

  @Override
  public SyntheticMethodDefinition asMethodDefinition() {
    return this;
  }

  @Override
  public boolean isProgramDefinition() {
    return true;
  }

  @Override
  public SyntheticProgramDefinition asProgramDefinition() {
    return this;
  }

  @Override
  SyntheticMethodReference toReference() {
    return new SyntheticMethodReference(getKind(), getContext(), method.getReference());
  }

  @Override
  public DexProgramClass getHolder() {
    return method.getHolder();
  }

  @Override
  void internalComputeHash(HasherWrapper hasher, RepresentativeMap<DexType> map) {
    method.getDefinition().hashWithTypeEquivalence(hasher, map);
  }

  @Override
  int internalCompareTo(
      SyntheticMethodDefinition other,
      RepresentativeMap<DexType> typeMap,
      RepresentativeMap<DexMethod> methodMap) {
    return method
        .getDefinition()
        .compareWithSyntheticEquivalenceTo(other.method.getDefinition(), typeMap, methodMap);
  }

  @Override
  public boolean isValid() {
    return SyntheticMethodBuilder.isValidSingleSyntheticMethod(method.getDefinition(), getKind());
  }

  @Override
  public String toString() {
    return "SyntheticMethodDefinition{" + method + '}';
  }
}
