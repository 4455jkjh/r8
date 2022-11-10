// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.RetracedFieldReference.KnownRetracedFieldReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedMethodReference.KnownRetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.MappingSupplierInternalImpl;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import java.util.OptionalInt;
import java.util.function.Function;

public class RetracerForCodePrinting {

  private static final RetracerForCodePrinting EMPTY = new RetracerForCodePrinting(null);

  public static RetracerForCodePrinting empty() {
    return EMPTY;
  }

  private final Retracer retracer;

  private RetracerForCodePrinting(Retracer retracer) {
    this.retracer = retracer;
  }

  public static RetracerForCodePrinting create(
      ClassNameMapper classNameMapper, DiagnosticsHandler handler) {
    return classNameMapper == null
        ? empty()
        : new RetracerForCodePrinting(
            RetracerImpl.createInternal(
                MappingSupplierInternalImpl.createInternal(classNameMapper), handler));
  }

  public <T extends RetraceElement<?>> String joinAmbiguousResults(
      RetraceResult<T> retraceResult, Function<T, String> nameToString) {
    return StringUtils.join(" <OR> ", retraceResult.stream(), nameToString);
  }

  private String typeToString(
      DexType type,
      Function<DexType, String> noRetraceString,
      Function<RetraceClassElement, String> retraceResult) {
    return retracer == null
        ? noRetraceString.apply(type)
        : joinAmbiguousResults(retracer.retraceClass(type.asClassReference()), retraceResult);
  }

  public String toSourceString(DexType type) {
    return typeToString(
        type, DexType::toSourceString, element -> element.getRetracedClass().getTypeName());
  }

  public String toDescriptor(DexType type) {
    return typeToString(
        type, DexType::toDescriptorString, element -> element.getRetracedClass().getDescriptor());
  }

  private String retraceMethodToString(
      DexMethod method,
      Function<DexMethod, String> noRetraceString,
      Function<KnownRetracedMethodReference, String> knownToString,
      Function<RetracedMethodReference, String> unknownToString) {
    if (retracer == null) {
      return noRetraceString.apply(method);
    }
    // TODO(b/169953605): Use retracer.retraceMethod() when we have enough information.
    MethodReference methodReference = method.asMethodReference();
    RetraceFrameResult retraceFrameResult =
        retracer
            .retraceClass(methodReference.getHolderClass())
            .lookupMethod(methodReference.getMethodName())
            .narrowByPosition(RetraceStackTraceContext.empty(), OptionalInt.of(1));
    return joinAmbiguousResults(
        retraceFrameResult,
        element -> {
          if (element.isUnknown()) {
            return unknownToString.apply(element.getTopFrame());
          } else {
            return knownToString.apply(element.getTopFrame().asKnown());
          }
        });
  }

  public String toSourceString(DexMethod method) {
    return retraceMethodToString(
        method,
        DexMethod::toSourceString,
        knownRetracedMethodReference ->
            knownRetracedMethodReference.getMethodReference().toSourceString(),
        unknown -> unknown.getHolderClass().getDescriptor() + " " + unknown.getMethodName());
  }

  public String toDescriptor(DexMethod method) {
    return retraceMethodToString(
        method,
        m -> m.asMethodReference().toString(),
        knownRetracedMethodReference ->
            knownRetracedMethodReference.getMethodReference().toString(),
        unknown -> unknown.getHolderClass().getDescriptor() + unknown.getMethodName());
  }

  private String retraceFieldToString(
      DexField field,
      Function<DexField, String> noRetraceString,
      Function<KnownRetracedFieldReference, String> knownToString,
      Function<RetracedFieldReference, String> unknownToString) {
    if (retracer == null) {
      return noRetraceString.apply(field);
    }
    // TODO(b/169953605): Use retracer.retraceField() when we have enough information.
    FieldReference fieldReference = field.asFieldReference();
    return joinAmbiguousResults(
        retracer
            .retraceClass(fieldReference.getHolderClass())
            .lookupField(fieldReference.getFieldName()),
        element -> {
          if (element.isUnknown()) {
            return unknownToString.apply(element.getField());
          } else {
            return knownToString.apply(element.getField().asKnown());
          }
        });
  }

  public String toSourceString(DexField field) {
    return retraceFieldToString(
        field,
        f -> f.asFieldReference().toSourceString(),
        known -> known.getFieldReference().toSourceString(),
        unknown -> unknown.getHolderClass().getDescriptor() + " " + unknown.getFieldName());
  }

  public String toDescriptor(DexField field) {
    return retraceFieldToString(
        field,
        f -> f.asFieldReference().toString(),
        known -> known.getFieldReference().toString(),
        unknown -> unknown.getHolderClass().getDescriptor() + unknown.getFieldName());
  }

  public String toDescriptor(IndexedDexItem item) {
    if (!(item instanceof DexReference)) {
      return item.toString();
    }
    return ((DexReference) item).apply(this::toDescriptor, this::toDescriptor, this::toDescriptor);
  }

  public String toSourceString(IndexedDexItem item) {
    if (!(item instanceof DexReference)) {
      return item.toSourceString();
    }
    return ((DexReference) item)
        .apply(this::toSourceString, this::toSourceString, this::toSourceString);
  }
}
