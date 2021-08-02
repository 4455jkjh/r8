// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

public abstract class DexClassAndMethod extends DexClassAndMember<DexEncodedMethod, DexMethod>
    implements LookupTarget {

  DexClassAndMethod(DexClass holder, DexEncodedMethod method) {
    super(holder, method);
    assert holder.isClasspathClass() == (this instanceof ClasspathMethod);
    assert holder.isLibraryClass() == (this instanceof LibraryMethod);
    assert holder.isProgramClass() == (this instanceof ProgramMethod);
  }

  public static ProgramMethod asProgramMethodOrNull(DexClassAndMethod method) {
    return method != null ? method.asProgramMethod() : null;
  }

  public static DexClassAndMethod create(DexClass holder, DexEncodedMethod method) {
    if (holder.isProgramClass()) {
      return new ProgramMethod(holder.asProgramClass(), method);
    }
    if (holder.isLibraryClass()) {
      return new LibraryMethod(holder.asLibraryClass(), method);
    }
    assert holder.isClasspathClass();
    return new ClasspathMethod(holder.asClasspathClass(), method);
  }

  public boolean isDefaultMethod() {
    return getHolder().isInterface() && getDefinition().isDefaultMethod();
  }

  public boolean isStructurallyEqualTo(DexClassAndMethod other) {
    return getDefinition() == other.getDefinition() && getHolder() == other.getHolder();
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    return getDefinition().getAccessFlags();
  }

  public MethodReference getMethodReference() {
    return getReference().asMethodReference();
  }

  public DexMethodSignature getMethodSignature() {
    return getReference().getSignature();
  }

  @Override
  public MethodOptimizationInfo getOptimizationInfo() {
    return getDefinition().getOptimizationInfo();
  }

  public DexType getParameter(int index) {
    return getReference().getParameter(index);
  }

  public DexTypeList getParameters() {
    return getReference().getParameters();
  }

  public DexAnnotationSet getParameterAnnotation(int index) {
    return getParameterAnnotations().get(index);
  }

  public final ParameterAnnotationsList getParameterAnnotations() {
    return getDefinition().getParameterAnnotations();
  }

  public DexProto getProto() {
    return getReference().getProto();
  }

  public DexType getReturnType() {
    return getReference().getReturnType();
  }

  @Override
  public boolean isMethodTarget() {
    return true;
  }

  @Override
  public DexClassAndMethod asMethodTarget() {
    return this;
  }

  @Override
  public boolean isMethod() {
    return true;
  }

  @Override
  public DexClassAndMethod asMember() {
    return this;
  }

  @Override
  public DexClassAndMethod asMethod() {
    return this;
  }

  @Override
  public void accept(
      Consumer<DexClassAndMethod> methodConsumer, Consumer<LookupLambdaTarget> lambdaConsumer) {
    methodConsumer.accept(this);
  }
}
