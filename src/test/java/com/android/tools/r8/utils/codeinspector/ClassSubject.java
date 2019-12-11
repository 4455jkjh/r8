// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ClassSubject extends Subject {

  public abstract void forAllMethods(Consumer<FoundMethodSubject> inspection);

  public final List<FoundMethodSubject> allMethods() {
    return allMethods(Predicates.alwaysTrue());
  }

  public final List<FoundMethodSubject> allMethods(Predicate<FoundMethodSubject> predicate) {
    ImmutableList.Builder<FoundMethodSubject> builder = ImmutableList.builder();
    forAllMethods(
        methodSubject -> {
          if (predicate.test(methodSubject)) {
            builder.add(methodSubject);
          }
        });
    return builder.build();
  }

  public abstract void forAllVirtualMethods(Consumer<FoundMethodSubject> inspection);

  public final List<FoundMethodSubject> virtualMethods() {
    return virtualMethods(Predicates.alwaysTrue());
  }

  public final List<FoundMethodSubject> virtualMethods(Predicate<FoundMethodSubject> predicate) {
    ImmutableList.Builder<FoundMethodSubject> builder = ImmutableList.builder();
    forAllVirtualMethods(
        methodSubject -> {
          if (predicate.test(methodSubject)) {
            builder.add(methodSubject);
          }
        });
    return builder.build();
  }

  public MethodSubject method(MethodReference method) {
    return method(
        (method.getReturnType() == null ? "void" : method.getReturnType().getTypeName()),
        method.getMethodName(),
        ListUtils.map(method.getFormalTypes(), TypeReference::getTypeName));
  }

  public MethodSubject method(Method method) {
    List<String> parameters = new ArrayList<>();
    for (Class<?> parameterType : method.getParameterTypes()) {
      parameters.add(parameterType.getTypeName());
    }
    return method(method.getReturnType().getTypeName(), method.getName(), parameters);
  }

  public final MethodSubject method(String returnType, String name) {
    return method(returnType, name, ImmutableList.of());
  }

  public abstract MethodSubject method(String returnType, String name, List<String> parameters);

  public abstract MethodSubject uniqueMethodWithName(String name);

  public MethodSubject mainMethod() {
    return method("void", "main", ImmutableList.of("java.lang.String[]"));
  }

  public MethodSubject clinit() {
    return method("void", "<clinit>", ImmutableList.of());
  }

  public MethodSubject init(List<String> parameters) {
    return method("void", "<init>", parameters);
  }

  public MethodSubject init(String... parameters) {
    return init(Arrays.asList(parameters));
  }

  public MethodSubject method(MethodSignature signature) {
    return method(signature.type, signature.name, ImmutableList.copyOf(signature.parameters));
  }

  public MethodSubject method(SmaliBuilder.MethodSignature signature) {
    return method(
        signature.returnType, signature.name, ImmutableList.copyOf(signature.parameterTypes));
  }

  public abstract void forAllFields(Consumer<FoundFieldSubject> inspection);

  public final List<FoundFieldSubject> allFields() {
    ImmutableList.Builder<FoundFieldSubject> builder = ImmutableList.builder();
    forAllFields(builder::add);
    return builder.build();
  }

  public abstract void forAllInstanceFields(Consumer<FoundFieldSubject> inspection);

  public final List<FoundFieldSubject> allInstanceFields() {
    ImmutableList.Builder<FoundFieldSubject> builder = ImmutableList.builder();
    forAllInstanceFields(builder::add);
    return builder.build();
  }

  public abstract FieldSubject field(String type, String name);

  public abstract FieldSubject uniqueFieldWithName(String name);

  public abstract FieldSubject uniqueFieldWithFinalName(String name);

  public FoundClassSubject asFoundClassSubject() {
    return null;
  }

  public abstract boolean isAbstract();

  public abstract boolean isAnnotation();

  public abstract boolean isPublic();

  public String dumpMethods() {
    StringBuilder dump = new StringBuilder();
    forAllMethods(
        (FoundMethodSubject method) ->
            dump.append(method.getMethod().toString()).append(method.getMethod().codeToString()));
    return dump.toString();
  }

  public abstract DexClass getDexClass();

  public abstract AnnotationSubject annotation(String name);

  public abstract String getOriginalName();

  public abstract String getOriginalDescriptor();

  public abstract String getFinalName();

  public abstract String getFinalDescriptor();

  public abstract boolean isMemberClass();

  public abstract boolean isLocalClass();

  public abstract boolean isAnonymousClass();

  public abstract boolean isSynthesizedJavaLambdaClass();

  public abstract String getOriginalSignatureAttribute();

  public abstract String getFinalSignatureAttribute();

  public abstract KmClassSubject getKmClass();
}
