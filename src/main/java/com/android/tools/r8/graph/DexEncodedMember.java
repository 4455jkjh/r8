// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.optimize.info.MemberOptimizationInfo;
import com.android.tools.r8.kotlin.KotlinMemberLevelInfo;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class DexEncodedMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    extends DexDefinition {

  // This flag indicates if this member has been synthesized by D8/R8. Such members do not require
  // a proguard mapping file entry. This flag is different from the synthesized access flag. When a
  // non synthesized member is inlined into a synthesized member, the member no longer has the
  // synthesized access flag, but the d8R8Synthesized flag is still there. Members can also have
  // the synthesized access flag prior to D8/R8 compilation, in which case d8R8Synthesized is not
  // set.
  private final boolean d8R8Synthesized;

  private final R reference;

  public DexEncodedMember(R reference, DexAnnotationSet annotations, boolean d8R8Synthesized) {
    super(annotations);
    this.reference = reference;
    this.d8R8Synthesized = d8R8Synthesized;
  }

  public abstract KotlinMemberLevelInfo getKotlinInfo();

  public abstract void clearKotlinInfo();

  public abstract void clearGenericSignature();

  public DexType getHolderType() {
    return getReference().getHolderType();
  }

  public DexString getName() {
    return getReference().getName();
  }

  @Override
  public R getReference() {
    return reference;
  }

  public boolean isD8R8Synthesized() {
    return d8R8Synthesized;
  }

  @Override
  public boolean isDexEncodedMember() {
    return true;
  }

  @Override
  public DexEncodedMember<D, R> asDexEncodedMember() {
    return this;
  }

  public final boolean isPrivate() {
    return getAccessFlags().isPrivate();
  }

  public abstract ProgramMember<D, R> asProgramMember(DexDefinitionSupplier definitions);

  public abstract <T> T apply(
      Function<DexEncodedField, T> fieldConsumer, Function<DexEncodedMethod, T> methodConsumer);

  public void apply(
      Consumer<DexEncodedField> fieldConsumer, Consumer<DexEncodedMethod> methodConsumer) {
    apply(
        field -> {
          fieldConsumer.accept(field);
          return null;
        },
        method -> {
          methodConsumer.accept(method);
          return null;
        });
  }

  public abstract MemberOptimizationInfo<?> getOptimizationInfo();

  @Override
  public final boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    return other.getClass() == getClass()
        && ((DexEncodedMember<?, ?>) other).getReference().equals(getReference());
  }

  @Override
  public final int hashCode() {
    return getReference().hashCode();
  }
}
