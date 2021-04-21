// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignature.EMPTY_TYPE_ARGUMENTS;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.StarFieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class GenericSignatureTypeRewriter {

  private final DexItemFactory factory;
  private final Predicate<DexType> wasPruned;
  private final Function<DexType, DexType> lookupType;
  private final DexType context;

  private final FieldTypeSignature objectTypeSignature;

  public GenericSignatureTypeRewriter(AppView<?> appView, DexProgramClass context) {
    this(
        appView.dexItemFactory(),
        appView.appInfo().hasLiveness()
            ? appView.appInfo().withLiveness()::wasPruned
            : alwaysFalse(),
        appView.graphLens()::lookupType,
        context.getType());
  }

  public GenericSignatureTypeRewriter(
      DexItemFactory factory,
      Predicate<DexType> wasPruned,
      Function<DexType, DexType> lookupType,
      DexType context) {
    this.factory = factory;
    this.wasPruned = wasPruned;
    this.lookupType = lookupType;
    this.context = context;
    objectTypeSignature = new ClassTypeSignature(factory.objectType, EMPTY_TYPE_ARGUMENTS);
  }

  public ClassSignature rewrite(ClassSignature classSignature) {
    if (classSignature.hasNoSignature() || classSignature.isInvalid()) {
      return classSignature;
    }
    return new GenericSignatureRewriter().visitClassSignature(classSignature);
  }

  public FieldTypeSignature rewrite(FieldTypeSignature fieldTypeSignature) {
    if (fieldTypeSignature.hasNoSignature() || fieldTypeSignature.isInvalid()) {
      return fieldTypeSignature;
    }
    FieldTypeSignature rewrittenSignature =
        new GenericSignatureRewriter().visitFieldTypeSignature(fieldTypeSignature);
    return rewrittenSignature == null ? FieldTypeSignature.noSignature() : rewrittenSignature;
  }

  public MethodTypeSignature rewrite(MethodTypeSignature methodTypeSignature) {
    if (methodTypeSignature.hasNoSignature() || methodTypeSignature.isInvalid()) {
      return methodTypeSignature;
    }
    return new GenericSignatureRewriter().visitMethodSignature(methodTypeSignature);
  }

  private class GenericSignatureRewriter implements GenericSignatureVisitor {

    @Override
    public ClassSignature visitClassSignature(ClassSignature classSignature) {
      ClassSignature rewritten = classSignature.visit(this);
      if (rewritten.getFormalTypeParameters().isEmpty()
          && rewritten.superInterfaceSignatures.isEmpty()
          && rewritten.superClassSignature.type == factory.objectType) {
        return ClassSignature.noSignature();
      }
      return rewritten;
    }

    @Override
    public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
      return methodSignature.visit(this);
    }

    @Override
    public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
      if (fieldSignature.isStar() || fieldSignature.isTypeVariableSignature()) {
        return fieldSignature;
      } else if (fieldSignature.isArrayTypeSignature()) {
        return fieldSignature.asArrayTypeSignature().visit(this);
      } else {
        assert fieldSignature.isClassTypeSignature();
        return fieldSignature.asClassTypeSignature().visit(this);
      }
    }

    @Override
    public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        return typeSignature;
      } else {
        return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
      }
    }

    @Override
    public List<FormalTypeParameter> visitFormalTypeParameters(
        List<FormalTypeParameter> formalTypeParameters) {
      if (formalTypeParameters.isEmpty()) {
        return formalTypeParameters;
      }
      return ListUtils.mapOrElse(formalTypeParameters, this::visitFormalTypeParameter);
    }

    @Override
    public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
      return formalTypeParameter.visit(this);
    }

    @Override
    public ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignature) {
      ClassTypeSignature rewritten = classTypeSignature.visit(this);
      return rewritten == null || rewritten.type() == context
          ? new ClassTypeSignature(factory.objectType, EMPTY_TYPE_ARGUMENTS)
          : rewritten;
    }

    @Override
    public List<ClassTypeSignature> visitSuperInterfaces(
        List<ClassTypeSignature> interfaceSignatures) {
      if (interfaceSignatures.isEmpty()) {
        return interfaceSignatures;
      }
      return ListUtils.mapOrElse(interfaceSignatures, this::visitSuperInterface);
    }

    @Override
    public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
      ClassTypeSignature rewritten = classTypeSignature.visit(this);
      return rewritten == null || rewritten.type() == context ? null : rewritten;
    }

    @Override
    public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
      if (typeSignatures.isEmpty()) {
        return typeSignatures;
      }
      return ListUtils.mapOrElse(
          typeSignatures,
          typeSignature -> {
            TypeSignature rewrittenSignature = visitTypeSignature(typeSignature);
            return rewrittenSignature == null ? objectTypeSignature : rewrittenSignature;
          });
    }

    @Override
    public ReturnType visitReturnType(ReturnType returnType) {
      if (returnType.isVoidDescriptor()) {
        return ReturnType.VOID;
      } else {
        TypeSignature originalType = returnType.typeSignature();
        TypeSignature rewrittenType = visitTypeSignature(originalType);
        if (rewrittenType == null) {
          return ReturnType.VOID;
        } else if (rewrittenType == originalType) {
          return returnType;
        } else {
          return new ReturnType(rewrittenType);
        }
      }
    }

    @Override
    public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
      if (typeSignatures.isEmpty()) {
        return typeSignatures;
      }
      // If a throwing type is no longer found we remove it from the signature.
      return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
    }

    @Override
    public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
      return visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
      if (fieldSignatures == null || fieldSignatures.isEmpty()) {
        return fieldSignatures;
      }
      return ListUtils.mapOrElse(fieldSignatures, this::visitFieldTypeSignature);
    }

    @Override
    public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
      return visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public ClassTypeSignature visitSimpleClass(ClassTypeSignature classTypeSignature) {
      return classTypeSignature.visit(this);
    }

    @Override
    public List<FieldTypeSignature> visitTypeArguments(List<FieldTypeSignature> typeArguments) {
      if (typeArguments.isEmpty()) {
        return typeArguments;
      }
      return ListUtils.mapOrElse(
          typeArguments,
          fieldTypeSignature -> {
            FieldTypeSignature rewrittenSignature = visitFieldTypeSignature(fieldTypeSignature);
            return rewrittenSignature == null
                ? StarFieldTypeSignature.getStarFieldTypeSignature()
                : rewrittenSignature;
          });
    }

    @Override
    public DexType visitType(DexType type) {
      DexType rewrittenType = lookupType.apply(type);
      return wasPruned.test(rewrittenType) ? null : rewrittenType;
    }
  }
}
