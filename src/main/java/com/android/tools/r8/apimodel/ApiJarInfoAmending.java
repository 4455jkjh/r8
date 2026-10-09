// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.apimodel.JarAmendments.ClassAmendment;
import com.android.tools.r8.apimodel.JarAmendments.FieldAmendment;
import com.android.tools.r8.apimodel.JarAmendments.InterfaceAmendment;
import com.android.tools.r8.apimodel.JarAmendments.MethodAmendment;
import com.android.tools.r8.apimodel.jar.ApiClassInfo;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.references.ClassReference;

public class ApiJarInfoAmending {

  public static void amendJar(
      ApiJarInfo jarInfo, JarAmendments jarAmendments, AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    for (ClassAmendment classAmendment : jarAmendments.getClasses()) {
      amendJarClass(jarInfo, classAmendment, listener);
    }
    for (JarAmendments.InterfaceAmendment interfaceAmendment : jarAmendments.getInterfaces()) {
      amendJarInterface(jarInfo, interfaceAmendment, listener);
    }
    for (MethodAmendment methodAmendment : jarAmendments.getMethods()) {
      amendJarMethod(jarInfo, methodAmendment, listener);
    }
    for (FieldAmendment fieldAmendment : jarAmendments.getFields()) {
      amendJarField(jarInfo, fieldAmendment, listener);
    }
  }

  private static void amendJarClass(
      ApiJarInfo jarInfo, ClassAmendment classAmendment, AmendmentListener listener) {
    ClassReference classRef = classAmendment.getClassReference();
    if (jarInfo.hasClass(classRef)) {
      listener.onAlreadyPresentJarClass(classAmendment);
    } else {
      String superClassBin =
          classAmendment.getSuperClass() != null
              ? classAmendment.getSuperClass().getBinaryName()
              : null;

      ApiClassInfo classInfo = new ApiClassInfo(classRef.getBinaryName(), superClassBin, false);
      classInfo.addInterfaces(classAmendment.getInterfaces(), ClassReference::getBinaryName);
      jarInfo.addClass(classInfo);
      listener.onAmendJarClass(classAmendment);
    }
  }

  private static void amendJarInterface(
      ApiJarInfo jarInfo, InterfaceAmendment interfaceAmendment, AmendmentListener listener) {
    ClassReference ifaceRef = interfaceAmendment.getInterfaceReference();
    if (jarInfo.hasClass(ifaceRef)) {
      listener.onAlreadyPresentJarInterface(interfaceAmendment);
    } else {
      ApiClassInfo classInfo = new ApiClassInfo(ifaceRef.getBinaryName(), null, true);
      classInfo.addInterfaces(interfaceAmendment.getInterfaces(), ClassReference::getBinaryName);
      jarInfo.addClass(classInfo);
      listener.onAmendJarInterface(interfaceAmendment);
    }
  }

  private static void amendJarMethod(
      ApiJarInfo jarInfo, MethodAmendment methodAmendment, AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    ClassReference holderRef = methodAmendment.getHolder();
    ApiClassInfo holder = jarInfo.getClassInfo(holderRef);
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + holderRef
              + " for hidden jar method "
              + methodAmendment.getName()
              + " does not exist");
    }
    if (holder.hasMethod(methodAmendment.getName(), methodAmendment.getDescriptor())) {
      listener.onAlreadyPresentJarMethod(methodAmendment);
    } else {
      holder.addMethod(
          methodAmendment.getName(), methodAmendment.getDescriptor(), methodAmendment.isStatic());
      listener.onAmendJarMethod(methodAmendment);
    }
  }

  private static void amendJarField(
      ApiJarInfo jarInfo, FieldAmendment fieldAmendment, AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    ClassReference holderRef = fieldAmendment.getHolder();
    ApiClassInfo holder = jarInfo.getClassInfo(holderRef);
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + holderRef
              + " for hidden jar field "
              + fieldAmendment.getName()
              + " does not exist");
    }
    if (holder.hasField(fieldAmendment.getName())) {
      listener.onAlreadyPresentJarField(fieldAmendment);
    } else {
      holder.addField(fieldAmendment.getName());
      listener.onAmendJarField(fieldAmendment);
    }
  }
}
