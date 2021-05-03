// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignature.getEmptyTypeArguments;

import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.StarFieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.GenericSignature.WildcardIndicator;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class GenericSignaturePartialTypeArgumentApplier implements GenericSignatureVisitor {

  private final Map<String, DexType> substitutions;
  private final Set<String> liveTypeVariables;
  private final DexType objectType;
  private final BiPredicate<DexType, DexType> enclosingPruned;
  private final Predicate<DexType> hasGenericTypeParameters;

  private GenericSignaturePartialTypeArgumentApplier(
      Map<String, DexType> substitutions,
      Set<String> liveTypeVariables,
      DexType objectType,
      BiPredicate<DexType, DexType> enclosingPruned,
      Predicate<DexType> hasGenericTypeParameters) {
    this.substitutions = substitutions;
    this.liveTypeVariables = liveTypeVariables;
    this.objectType = objectType;
    this.enclosingPruned = enclosingPruned;
    this.hasGenericTypeParameters = hasGenericTypeParameters;
  }

  public static GenericSignaturePartialTypeArgumentApplier build(
      DexType objectType,
      BiPredicate<DexType, DexType> enclosingPruned,
      Predicate<DexType> hasGenericTypeParameters) {
    return new GenericSignaturePartialTypeArgumentApplier(
        Collections.emptyMap(),
        Collections.emptySet(),
        objectType,
        enclosingPruned,
        hasGenericTypeParameters);
  }

  public GenericSignaturePartialTypeArgumentApplier addSubstitutionsAndVariables(
      Map<String, DexType> substitutions, Set<String> liveTypeVariables) {
    return new GenericSignaturePartialTypeArgumentApplier(
        substitutions, liveTypeVariables, objectType, enclosingPruned, hasGenericTypeParameters);
  }

  public GenericSignaturePartialTypeArgumentApplier buildForMethod(
      List<FormalTypeParameter> formals) {
    if (formals.isEmpty()) {
      return this;
    }
    ImmutableSet.Builder<String> liveVariablesBuilder = ImmutableSet.builder();
    liveVariablesBuilder.addAll(liveTypeVariables);
    formals.forEach(
        formal -> {
          liveVariablesBuilder.add(formal.name);
        });
    return new GenericSignaturePartialTypeArgumentApplier(
        substitutions, liveTypeVariables, objectType, enclosingPruned, hasGenericTypeParameters);
  }

  @Override
  public ClassSignature visitClassSignature(ClassSignature classSignature) {
    return classSignature.visit(this);
  }

  @Override
  public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
    return methodSignature.visit(this);
  }

  @Override
  public DexType visitType(DexType type) {
    return type;
  }

  @Override
  public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
    if (typeSignature.isBaseTypeSignature()) {
      return typeSignature;
    }
    return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
  }

  @Override
  public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
    FormalTypeParameter rewritten = formalTypeParameter.visit(this);
    // Guard against no information being present in bounds.
    assert (rewritten.getClassBound() != null && rewritten.getClassBound().hasSignature())
        || !rewritten.getInterfaceBounds().isEmpty();
    return rewritten;
  }

  @Override
  public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
    if (fieldSignatures == null || fieldSignatures.isEmpty()) {
      return fieldSignatures;
    }
    return ListUtils.mapOrElse(fieldSignatures, this::visitFieldTypeSignature);
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
  public List<FieldTypeSignature> visitTypeArguments(
      DexType type, List<FieldTypeSignature> typeArguments) {
    if (typeArguments.isEmpty() || !hasGenericTypeParameters.test(type)) {
      return getEmptyTypeArguments();
    }
    // Wildcards can only be called be used in certain positions:
    // https://docs.oracle.com/javase/tutorial/java/generics/wildcards.html
    return ListUtils.mapOrElse(typeArguments, arg -> visitFieldTypeSignature(arg, true));
  }

  @Override
  public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
    return classTypeSignature.visit(this);
  }

  @Override
  public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public ClassTypeSignature visitEnclosing(
      ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
    if (enclosingPruned.test(enclosingSignature.type(), enclosedSignature.type())) {
      return null;
    } else {
      return enclosingSignature.visit(this);
    }
  }

  @Override
  public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    if (typeSignatures.isEmpty()) {
      return typeSignatures;
    }
    return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
  }

  @Override
  public ReturnType visitReturnType(ReturnType returnType) {
    if (returnType.isVoidDescriptor()) {
      return returnType;
    }
    TypeSignature originalSignature = returnType.typeSignature;
    TypeSignature rewrittenSignature = visitTypeSignature(originalSignature);
    if (originalSignature == rewrittenSignature) {
      return returnType;
    }
    return new ReturnType(rewrittenSignature);
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
  public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    if (typeSignatures.isEmpty()) {
      return typeSignatures;
    }
    return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
  }

  @Override
  public ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignature) {
    return classTypeSignature.visit(this);
  }

  @Override
  public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature, false);
  }

  private FieldTypeSignature visitFieldTypeSignature(
      FieldTypeSignature fieldSignature, boolean canUseWildcardInArguments) {
    if (fieldSignature.isStar()) {
      return fieldSignature;
    } else if (fieldSignature.isClassTypeSignature()) {
      return fieldSignature.asClassTypeSignature().visit(this);
    } else if (fieldSignature.isArrayTypeSignature()) {
      return fieldSignature.asArrayTypeSignature().visit(this);
    } else {
      assert fieldSignature.isTypeVariableSignature();
      String typeVariableName = fieldSignature.asTypeVariableSignature().typeVariable();
      if (substitutions.containsKey(typeVariableName)
          && !liveTypeVariables.contains(typeVariableName)) {
        DexType substitution = substitutions.get(typeVariableName);
        if (substitution == null) {
          substitution = objectType;
        }
        return substitution == objectType && canUseWildcardInArguments
            ? StarFieldTypeSignature.getStarFieldTypeSignature()
            : new ClassTypeSignature(substitution).asArgument(WildcardIndicator.NONE);
      }
      return fieldSignature;
    }
  }
}
