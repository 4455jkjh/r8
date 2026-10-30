// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.apimodel.jar.ApiClassInfo;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.SemiInfiniteRange;
import com.android.tools.r8.utils.internal.structural.Ordered;
import java.util.ArrayList;
import java.util.Collection;

public class ParsedApiClassTrimming {

  public enum SkipAnswer {
    SKIP,
    KEEP;

    public static SkipAnswer skipIf(boolean conditionToSkip) {
      return conditionToSkip ? SKIP : KEEP;
    }
  }

  public interface Trimmer<D, E extends Throwable> {
    SkipAnswer skipClass(ClassReference clazz, D range) throws E;

    SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, D range) throws E;

    SkipAnswer skipImplements(ClassReference clazz, ClassReference supertype, D range) throws E;

    SkipAnswer skipMethod(ClassReference clazz, MethodReference method, D range) throws E;

    SkipAnswer skipField(ClassReference clazz, FieldTypelessReference field, D range) throws E;

    void done() throws E;
  }

  public static <D, E extends Throwable> Collection<ParsedApiClass<D>> trim(
      Collection<ParsedApiClass<D>> classes, Trimmer<D, E> trimmer) throws E {
    var result = new ArrayList<ParsedApiClass<D>>();
    for (var clazz : classes) {
      if (trimmer.skipClass(clazz.getClassReference(), clazz.getData()) == SkipAnswer.KEEP) {
        result.add(trim(clazz, trimmer));
      }
    }
    trimmer.done();
    return result;
  }

  private static <D, E extends Throwable> ParsedApiClass<D> trim(
      ParsedApiClass<D> clazz, Trimmer<D, E> trimmer) throws E {
    var holdingClass = clazz.getClassReference();
    var trimmedClass = new ParsedApiClass<>(holdingClass, clazz.getData());
    clazz.forEachSupertypeThrowing(
        (classReference, apiRange) -> {
          if (trimmer.skipExtends(holdingClass, classReference, apiRange) == SkipAnswer.KEEP) {
            trimmedClass.registerSupertype(classReference, apiRange);
          }
        });
    clazz.forEachInterfaceThrowing(
        (classReference, apiRange) -> {
          if (trimmer.skipImplements(holdingClass, classReference, apiRange) == SkipAnswer.KEEP) {
            trimmedClass.registerInterface(classReference, apiRange);
          }
        });
    clazz.forEachMethodThrowing(
        (methodReference, apiRange) -> {
          if (trimmer.skipMethod(holdingClass, methodReference, apiRange) == SkipAnswer.KEEP) {
            trimmedClass.registerMethod(methodReference, apiRange);
          }
        });
    clazz.forEachFieldThrowing(
        (fieldReference, apiRange) -> {
          if (trimmer.skipField(holdingClass, fieldReference, apiRange) == SkipAnswer.KEEP) {
            trimmedClass.registerField(fieldReference, apiRange);
          }
        });
    return trimmedClass;
  }

  public static class RemovedTrimmer<T extends Ordered<T>>
      implements Trimmer<SemiInfiniteRange<T>, RuntimeException> {

    @Override
    public SkipAnswer skipClass(ClassReference clazz, SemiInfiniteRange<T> range) {
      return range.hasEnd() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipExtends(
        ClassReference clazz, ClassReference supertype, SemiInfiniteRange<T> range) {
      return range.hasEnd() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipImplements(
        ClassReference clazz, ClassReference supertype, SemiInfiniteRange<T> range) {
      return range.hasEnd() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipMethod(
        ClassReference clazz, MethodReference method, SemiInfiniteRange<T> range) {
      return range.hasEnd() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipField(
        ClassReference clazz, FieldTypelessReference field, SemiInfiniteRange<T> range) {
      return range.hasEnd() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public void done() throws RuntimeException {
      // Do nothing.
    }
  }

  public static class JarTrimmer
      implements Trimmer<SemiInfiniteRange<AndroidApiLevel>, ApiDatabaseGeneratorException> {
    private final ApiJarInfo jarInfo;

    public JarTrimmer(ApiJarInfo jarInfo) {
      this.jarInfo = jarInfo;
    }

    public ApiJarInfo getJarInfo() {
      return jarInfo;
    }

    @Override
    public SkipAnswer skipClass(ClassReference clazz, SemiInfiniteRange<AndroidApiLevel> range) {
      return SkipAnswer.skipIf(!jarInfo.hasClass(clazz));
    }

    @Override
    public SkipAnswer skipExtends(
        ClassReference clazz, ClassReference supertype, SemiInfiniteRange<AndroidApiLevel> range) {
      ApiClassInfo info = jarInfo.getClassInfo(clazz);
      if (info == null) {
        return SkipAnswer.SKIP;
      }
      if (info.getSuperClass() != null && info.getSuperClass().equals(supertype.getBinaryName())) {
        return SkipAnswer.KEEP;
      }
      return SkipAnswer.SKIP;
    }

    @Override
    public SkipAnswer skipImplements(
        ClassReference clazz, ClassReference supertype, SemiInfiniteRange<AndroidApiLevel> range) {
      ApiClassInfo info = jarInfo.getClassInfo(clazz);
      if (info == null) {
        return SkipAnswer.SKIP;
      }
      if (info.implementsInterface(supertype.getBinaryName())) {
        return SkipAnswer.KEEP;
      }
      return SkipAnswer.SKIP;
    }

    @Override
    public SkipAnswer skipMethod(
        ClassReference clazz, MethodReference method, SemiInfiniteRange<AndroidApiLevel> range)
        throws ApiDatabaseGeneratorException {
      return SkipAnswer.skipIf(
          !jarInfo.hasMethod(
              clazz.getBinaryName(), method.getMethodName(), method.getMethodDescriptor()));
    }

    @Override
    public SkipAnswer skipField(
        ClassReference clazz,
        FieldTypelessReference field,
        SemiInfiniteRange<AndroidApiLevel> range)
        throws ApiDatabaseGeneratorException {
      return SkipAnswer.skipIf(!jarInfo.hasField(clazz.getBinaryName(), field.getFieldName()));
    }

    @Override
    public void done() throws ApiDatabaseGeneratorException {
      // Do nothing.
    }
  }

  public interface TrimmerListener<D> {
    void skipClass(ClassReference clazz, D range, SkipAnswer answer);

    void skipExtends(ClassReference clazz, ClassReference supertype, D range, SkipAnswer answer);

    void skipImplements(ClassReference clazz, ClassReference supertype, D range, SkipAnswer answer);

    void skipMethod(ClassReference clazz, MethodReference method, D range, SkipAnswer answer);

    void skipField(ClassReference clazz, FieldTypelessReference field, D range, SkipAnswer answer);

    void done();
  }

  public static class ListeningDecorator<D, E extends Throwable> implements Trimmer<D, E> {

    private final Trimmer<D, E> trimmer;
    private final TrimmerListener<D> listener;

    public ListeningDecorator(Trimmer<D, E> trimmer, TrimmerListener<D> listener) {
      this.trimmer = trimmer;
      this.listener = listener;
    }

    @Override
    public SkipAnswer skipClass(ClassReference clazz, D range) throws E {
      var result = trimmer.skipClass(clazz, range);
      listener.skipClass(clazz, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, D range)
        throws E {
      var result = trimmer.skipExtends(clazz, supertype, range);
      listener.skipExtends(clazz, supertype, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipImplements(ClassReference clazz, ClassReference supertype, D range)
        throws E {
      var result = trimmer.skipImplements(clazz, supertype, range);
      listener.skipImplements(clazz, supertype, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipMethod(ClassReference clazz, MethodReference method, D range) throws E {
      var result = trimmer.skipMethod(clazz, method, range);
      listener.skipMethod(clazz, method, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipField(ClassReference clazz, FieldTypelessReference field, D range)
        throws E {
      var result = trimmer.skipField(clazz, field, range);
      listener.skipField(clazz, field, range, result);
      return result;
    }

    @Override
    public void done() throws E {
      trimmer.done();
      listener.done();
    }
  }
}
