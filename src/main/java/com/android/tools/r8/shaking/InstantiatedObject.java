// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InstantiatedSubTypeInfo;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import java.util.function.Consumer;

public abstract class InstantiatedObject implements InstantiatedSubTypeInfo {

  public static InstantiatedObject of(DexProgramClass clazz) {
    return new InstantiatedClass(clazz);
  }

  public static InstantiatedObject of(LambdaDescriptor lambda) {
    return new InstantiatedLambda(lambda);
  }

  public void apply(
      Consumer<DexProgramClass> classConsumer, Consumer<LambdaDescriptor> lambdaConsumer) {
    if (isClass()) {
      classConsumer.accept(asClass());
    } else {
      assert isLambda();
      lambdaConsumer.accept(asLambda());
    }
  }

  public boolean isClass() {
    return false;
  }

  public DexProgramClass asClass() {
    return null;
  }

  public boolean isLambda() {
    return false;
  }

  public LambdaDescriptor asLambda() {
    return null;
  }

  private static class InstantiatedClass extends InstantiatedObject {
    final DexProgramClass clazz;

    InstantiatedClass(DexProgramClass clazz) {
      assert !clazz.isInterface() || clazz.isAnnotation();
      this.clazz = clazz;
    }

    @Override
    public void forEachInstantiatedSubType(
        DexType type,
        Consumer<DexProgramClass> subTypeConsumer,
        Consumer<LambdaDescriptor> lambdaConsumer) {
      subTypeConsumer.accept(clazz);
    }

    @Override
    public boolean isClass() {
      return true;
    }

    @Override
    public DexProgramClass asClass() {
      return clazz;
    }
  }

  private static class InstantiatedLambda extends InstantiatedObject {
    final LambdaDescriptor lambdaDescriptor;

    public InstantiatedLambda(LambdaDescriptor lambdaDescriptor) {
      this.lambdaDescriptor = lambdaDescriptor;
    }

    @Override
    public void forEachInstantiatedSubType(
        DexType type,
        Consumer<DexProgramClass> subTypeConsumer,
        Consumer<LambdaDescriptor> lambdaConsumer) {
      lambdaConsumer.accept(lambdaDescriptor);
    }

    @Override
    public boolean isLambda() {
      return true;
    }

    @Override
    public LambdaDescriptor asLambda() {
      return lambdaDescriptor;
    }
  }
}
