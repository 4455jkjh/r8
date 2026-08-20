// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import java.util.Objects;

public class ApiJarMerging {

  /** Merges {@code additions} into {@code jarInfo} and returns {@code jarInfo}. */
  public static ApiJarInfo mergeJarInfo(ApiJarInfo jarInfo, ApiJarInfo additions)
      throws ApiDatabaseGeneratorException {
    for (ApiClassInfo additionClass : additions.getClasses()) {
      mergeJarInfo(jarInfo, additionClass);
    }
    return jarInfo;
  }

  /** Returns a new info, which has all the merged info of {@param infos}. */
  public static ApiJarInfo mergeJarInfos(Iterable<ApiJarInfo> infos)
      throws ApiDatabaseGeneratorException {
    ApiJarInfo info = ApiJarInfo.empty();
    for (var i : infos) {
      mergeJarInfo(info, i);
    }
    return info;
  }

  /** Merges {@code additionClass} into {@code jarInfo} and returns {@code jarInfo}. */
  private static void mergeJarInfo(ApiJarInfo jarInfo, ApiClassInfo additionClass)
      throws ApiDatabaseGeneratorException {
    String binaryName = additionClass.getBinaryName();
    ApiClassInfo originalClass = jarInfo.getClassInfo(binaryName);
    if (originalClass == null) {
      jarInfo.addClass(additionClass.copy());
    } else {
      jarInfo.overwriteClass(mergeClass(originalClass, additionClass));
    }
  }

  /** Merged {@code addition} into {@code classInfo} and returns {@code classInfo}. */
  private static ApiClassInfo mergeClass(ApiClassInfo classInfo, ApiClassInfo addition)
      throws ApiDatabaseGeneratorException {
    checkClassHeaderConsistency(classInfo, addition);
    for (ApiMethodInfo method : addition.getMethods()) {
      ApiMethodInfo existing = classInfo.getMethod(method.name, method.descriptor);
      if (existing == null) {
        classInfo.addMethod(method.name, method.descriptor, method.isStatic);
      } else {
        if (!existing.equals(method)) {
          throw new ApiDatabaseGeneratorException(
              "Overlapping inconsistent method "
                  + method.name
                  + method.descriptor
                  + " in class "
                  + classInfo.getBinaryName());
        }
      }
    }
    for (String field : addition.getFields()) {
      if (!classInfo.hasField(field)) {
        classInfo.addField(field);
      }
    }
    return classInfo;
  }

  private static void checkClassHeaderConsistency(ApiClassInfo classInfo, ApiClassInfo addition)
      throws ApiDatabaseGeneratorException {
    if (classInfo.isInterface() != addition.isInterface()) {
      throw new ApiDatabaseGeneratorException(
          "Inconsistent isInterface for class " + classInfo.getBinaryName());
    }
    if (!Objects.equals(classInfo.getSuperClass(), addition.getSuperClass())) {
      throw new ApiDatabaseGeneratorException(
          "Inconsistent superClass for class "
              + classInfo.getBinaryName()
              + ": "
              + classInfo.getSuperClass()
              + " vs "
              + addition.getSuperClass());
    }
    if (!classInfo.getInterfaces().equals(addition.getInterfaces())) {
      throw new ApiDatabaseGeneratorException(
          "Inconsistent interfaces for class " + classInfo.getBinaryName());
    }
  }
}
