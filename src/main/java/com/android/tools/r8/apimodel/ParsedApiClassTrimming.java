// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.apimodel.jar.ApiClassInfo;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
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

  public interface Trimmer<E extends Throwable> {
    SkipAnswer skipClass(ClassReference clazz, ApiRange range) throws E;

    SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, ApiRange range) throws E;

    SkipAnswer skipImplements(ClassReference clazz, ClassReference supertype, ApiRange range)
        throws E;

    SkipAnswer skipMethod(ClassReference clazz, MethodReference method, ApiRange range) throws E;

    SkipAnswer skipField(ClassReference clazz, FieldTypelessReference field, ApiRange range)
        throws E;
  }

  public static <E extends Throwable> Collection<ParsedApiClass> trim(
      Collection<ParsedApiClass> classes, Trimmer<E> trimmer) throws E {
    var result = new ArrayList<ParsedApiClass>();
    for (var clazz : classes) {
      if (trimmer.skipClass(clazz.getClassReference(), clazz.getRange()) == SkipAnswer.KEEP) {
        result.add(trim(clazz, trimmer));
      }
    }
    return result;
  }

  private static <E extends Throwable> ParsedApiClass trim(ParsedApiClass clazz, Trimmer<E> trimmer)
      throws E {
    var holdingClass = clazz.getClassReference();
    var trimmedClass = new ParsedApiClass(holdingClass, clazz.getRange());
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

  public static class RemovedTrimmer implements Trimmer<RuntimeException> {

    @Override
    public SkipAnswer skipClass(ClassReference clazz, ApiRange range) {
      return range.isRemoved() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, ApiRange range) {
      return range.isRemoved() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipImplements(
        ClassReference clazz, ClassReference supertype, ApiRange range) {
      return range.isRemoved() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipMethod(ClassReference clazz, MethodReference method, ApiRange range) {
      return range.isRemoved() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }

    @Override
    public SkipAnswer skipField(
        ClassReference clazz, FieldTypelessReference field, ApiRange range) {
      return range.isRemoved() ? SkipAnswer.SKIP : SkipAnswer.KEEP;
    }
  }

  public static class JarTrimmer
      implements ParsedApiClassTrimming.Trimmer<ApiDatabaseGeneratorException> {
    private final ApiJarInfo jarInfo;

    public JarTrimmer(ApiJarInfo jarInfo) {
      this.jarInfo = jarInfo;
    }

    @Override
    public SkipAnswer skipClass(ClassReference clazz, ApiRange range) {
      return SkipAnswer.skipIf(!jarInfo.hasClass(clazz));
    }

    @Override
    public SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, ApiRange range) {
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
        ClassReference clazz, ClassReference supertype, ApiRange range) {
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
    public SkipAnswer skipMethod(ClassReference clazz, MethodReference method, ApiRange range)
        throws ApiDatabaseGeneratorException {
      return SkipAnswer.skipIf(
          !jarInfo.hasMethod(
              clazz.getBinaryName(), method.getMethodName(), method.getMethodDescriptor()));
    }

    @Override
    public SkipAnswer skipField(ClassReference clazz, FieldTypelessReference field, ApiRange range)
        throws ApiDatabaseGeneratorException {
      return SkipAnswer.skipIf(!jarInfo.hasField(clazz.getBinaryName(), field.getFieldName()));
    }
  }

  public interface TrimmerListener {
    void skipClass(ClassReference clazz, ApiRange range, SkipAnswer answer);

    void skipExtends(
        ClassReference clazz, ClassReference supertype, ApiRange range, SkipAnswer answer);

    void skipImplements(
        ClassReference clazz, ClassReference supertype, ApiRange range, SkipAnswer answer);

    void skipMethod(
        ClassReference clazz, MethodReference method, ApiRange range, SkipAnswer answer);

    void skipField(
        ClassReference clazz, FieldTypelessReference field, ApiRange range, SkipAnswer answer);
  }

  public static class ListeningDecorator<E extends Throwable> implements Trimmer<E> {

    private final Trimmer<E> trimmer;
    private final TrimmerListener listener;

    public ListeningDecorator(Trimmer<E> trimmer, TrimmerListener listener) {
      this.trimmer = trimmer;
      this.listener = listener;
    }

    @Override
    public SkipAnswer skipClass(ClassReference clazz, ApiRange range) throws E {
      var result = trimmer.skipClass(clazz, range);
      listener.skipClass(clazz, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipExtends(ClassReference clazz, ClassReference supertype, ApiRange range)
        throws E {
      var result = trimmer.skipExtends(clazz, supertype, range);
      listener.skipExtends(clazz, supertype, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipImplements(ClassReference clazz, ClassReference supertype, ApiRange range)
        throws E {
      var result = trimmer.skipImplements(clazz, supertype, range);
      listener.skipImplements(clazz, supertype, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipMethod(ClassReference clazz, MethodReference method, ApiRange range)
        throws E {
      var result = trimmer.skipMethod(clazz, method, range);
      listener.skipMethod(clazz, method, range, result);
      return result;
    }

    @Override
    public SkipAnswer skipField(ClassReference clazz, FieldTypelessReference field, ApiRange range)
        throws E {
      var result = trimmer.skipField(clazz, field, range);
      listener.skipField(clazz, field, range, result);
      return result;
    }
  }
}
