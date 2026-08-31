// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;

// Transitive dependencies are computed only one way, the other way is always pessimistic.
// This means that invoke arguments, method return value and field write have information such as
// "A value flowing into it is a method argument, a field read or an invoke return value".
// However, method argument, field reads and invoke return value are analyzed pessimistically,
// i.e., if they are used as an invoke argument, a method return value or a field write, then
// the delta is pessimistically increased.
public interface TransitiveDependency {

  default boolean isMethodDependency() {
    return false;
  }

  default MethodDependency asMethodDependency() {
    return null;
  }

  default boolean isMethodArg() {
    return false;
  }

  default MethodArg asMethodArg() {
    return null;
  }

  default boolean isMethodRet() {
    return false;
  }

  default MethodRet asMethodRet() {
    return null;
  }

  TransitiveDependency rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens codeLens);

  @Override
  int hashCode();

  @Override
  boolean equals(Object o);

  abstract class MethodDependency implements TransitiveDependency {

    final DexMethod method;

    public MethodDependency(DexMethod method) {
      this.method = method;
    }

    @Override
    public boolean isMethodDependency() {
      return true;
    }

    @Override
    public MethodDependency asMethodDependency() {
      return this;
    }

    public DexMethod getMethod() {
      return method;
    }
  }

  class MethodRet extends MethodDependency {

    public MethodRet(DexMethod method) {
      super(method);
    }

    @Override
    public boolean isMethodRet() {
      return true;
    }

    @Override
    public MethodRet asMethodRet() {
      return this;
    }

    @Override
    public TransitiveDependency rewrittenWithLens(
        AppView<?> appView, GraphLens graphLens, GraphLens codeLens) {
      DexMethod newMethod = graphLens.getRenamedMethodSignature(method, codeLens);
      if (newMethod.isIdenticalTo(method)) {
        return this;
      }
      assert !graphLens
          .lookupPrototypeChangesForMethodDefinition(newMethod, codeLens)
          .hasRewrittenReturnInfo();
      return new MethodRet(newMethod);
    }

    @Override
    public int hashCode() {
      return getMethod().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MethodRet)) {
        return false;
      }
      return getMethod().isIdenticalTo(((MethodRet) obj).getMethod());
    }

    @Override
    public String toString() {
      return "MethodRet("
          + getMethod().getHolderType().getSimpleName()
          + "#"
          + getMethod().name
          + ')';
    }
  }

  class MethodArg extends MethodDependency {

    private final int parameterIndex;

    public MethodArg(int parameterIndex, DexMethod method) {
      super(method);
      assert parameterIndex >= 0;
      assert parameterIndex < method.getArity();
      this.parameterIndex = parameterIndex;
    }

    public int getParameterIndex() {
      return parameterIndex;
    }

    @Override
    public boolean isMethodArg() {
      return true;
    }

    @Override
    public MethodArg asMethodArg() {
      return this;
    }

    @Override
    public TransitiveDependency rewrittenWithLens(
        AppView<?> appView, GraphLens graphLens, GraphLens codeLens) {
      DexMethod newMethod = graphLens.getRenamedMethodSignature(method, codeLens);
      if (newMethod.isIdenticalTo(method)) {
        return this;
      }
      RewrittenPrototypeDescription rewrittenPrototypeDescription =
          graphLens.lookupPrototypeChangesForMethodDefinition(newMethod, codeLens);
      DexClassAndMethod dexClassAndMethod = appView.definitionFor(newMethod);
      if (dexClassAndMethod == null) {
        return this;
      }
      int newShift = dexClassAndMethod.getDefinition().isStatic() ? 0 : 1;
      int prevShift =
          rewrittenPrototypeDescription.getArgumentInfoCollection().isConvertedToStaticMethod()
              ? 1
              : newShift;
      int newParameterIndex =
          rewrittenPrototypeDescription
                  .getArgumentInfoCollection()
                  .getNewArgumentIndex(parameterIndex + prevShift)
              - newShift;
      assert !rewrittenPrototypeDescription
          .getArgumentInfoCollection()
          .getArgumentInfo(parameterIndex + prevShift)
          .isRewrittenTypeInfo();
      assert appView
          .dexItemFactory()
          .primitiveToBoxed
          .containsValue(newMethod.getParameter(newParameterIndex));
      return new MethodArg(newParameterIndex, newMethod);
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(parameterIndex) + 7 * getMethod().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MethodArg)) {
        return false;
      }
      MethodArg other = (MethodArg) obj;
      return getMethod().isIdenticalTo(other.getMethod())
          && getParameterIndex() == other.getParameterIndex();
    }

    @Override
    public String toString() {
      return "MethodArg("
          + getMethod().getHolderType().getSimpleName()
          + "#"
          + getMethod().name
          + "#"
          + parameterIndex
          + ')';
    }
  }
}
