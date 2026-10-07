// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.ArrayList;
import java.util.Collection;

public class ParsedApiClassMapping {

  public interface Mapper<T, R, E extends Throwable> {
    R mapClass(ClassReference clazz, T range) throws E;

    R mapExtends(ClassReference clazz, ClassReference supertype, T range) throws E;

    R mapImplements(ClassReference clazz, ClassReference supertype, T range) throws E;

    R mapMethod(ClassReference clazz, MethodReference method, T range) throws E;

    R mapField(ClassReference clazz, FieldTypelessReference field, T range) throws E;

    void done() throws E;
  }

  public static <T, R, E extends Throwable> Collection<ParsedApiClass<R>> map(
      Collection<ParsedApiClass<T>> classes, Mapper<T, R, E> mapper) throws E {
    var result = new ArrayList<ParsedApiClass<R>>(classes.size());
    for (var clazz : classes) {
      result.add(map(clazz, mapper));
    }
    mapper.done();
    return result;
  }

  private static <T, R, E extends Throwable> ParsedApiClass<R> map(
      ParsedApiClass<T> clazz, Mapper<T, R, E> mapper) throws E {
    var holdingClass = clazz.getClassReference();
    var mappedClass =
        new ParsedApiClass<>(holdingClass, mapper.mapClass(holdingClass, clazz.getData()));
    clazz.forEachSupertypeThrowing(
        (classReference, range) ->
            mappedClass.registerSupertype(
                classReference, mapper.mapExtends(holdingClass, classReference, range)));
    clazz.forEachInterfaceThrowing(
        (classReference, range) ->
            mappedClass.registerInterface(
                classReference, mapper.mapImplements(holdingClass, classReference, range)));
    clazz.forEachMethodThrowing(
        (methodReference, range) ->
            mappedClass.registerMethod(
                methodReference, mapper.mapMethod(holdingClass, methodReference, range)));
    clazz.forEachFieldThrowing(
        (fieldReference, range) ->
            mappedClass.registerField(
                fieldReference, mapper.mapField(holdingClass, fieldReference, range)));
    return mappedClass;
  }

  public static class GreatestEndingRangeMapper
      implements Mapper<ApiVersionSet, ApiRange, RuntimeException> {

    @Override
    public ApiRange mapClass(ClassReference clazz, ApiVersionSet range) {
      return range.getLargestEndRange();
    }

    @Override
    public ApiRange mapExtends(
        ClassReference clazz, ClassReference supertype, ApiVersionSet range) {
      return range.getLargestEndRange();
    }

    @Override
    public ApiRange mapImplements(
        ClassReference clazz, ClassReference supertype, ApiVersionSet range) {
      return range.getLargestEndRange();
    }

    @Override
    public ApiRange mapMethod(ClassReference clazz, MethodReference method, ApiVersionSet range) {
      return range.getLargestEndRange();
    }

    @Override
    public ApiRange mapField(
        ClassReference clazz, FieldTypelessReference field, ApiVersionSet range) {
      return range.getLargestEndRange();
    }

    @Override
    public void done() {
      // Do nothing.
    }
  }
}
