// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

public interface AmendmentListener {

  void onAmendApiClass(ApiAmendments.ClassAmendment amendment);

  void onAlreadyPresentApiClass(ApiAmendments.ClassAmendment amendment);

  void onAmendApiMethod(ApiAmendments.MethodAmendment amendment);

  void onAlreadyPresentApiMethod(ApiAmendments.MethodAmendment amendment);

  void onAmendApiField(ApiAmendments.FieldAmendment amendment);

  void onAlreadyPresentApiField(ApiAmendments.FieldAmendment amendment);

  void onAmendJarClass(JarAmendments.ClassAmendment amendment);

  void onAlreadyPresentJarClass(JarAmendments.ClassAmendment amendment);

  void onAmendJarInterface(JarAmendments.InterfaceAmendment amendment);

  void onAlreadyPresentJarInterface(JarAmendments.InterfaceAmendment amendment);

  void onAmendJarMethod(JarAmendments.MethodAmendment amendment);

  void onAlreadyPresentJarMethod(JarAmendments.MethodAmendment amendment);

  void onAmendJarField(JarAmendments.FieldAmendment amendment);

  void onAlreadyPresentJarField(JarAmendments.FieldAmendment amendment);

  void done();

  static AmendmentListener empty() {
    return new AmendmentListener() {
      @Override
      public void onAmendApiClass(ApiAmendments.ClassAmendment amendment) {}

      @Override
      public void onAlreadyPresentApiClass(ApiAmendments.ClassAmendment amendment) {}

      @Override
      public void onAmendApiMethod(ApiAmendments.MethodAmendment amendment) {}

      @Override
      public void onAlreadyPresentApiMethod(ApiAmendments.MethodAmendment amendment) {}

      @Override
      public void onAmendApiField(ApiAmendments.FieldAmendment amendment) {}

      @Override
      public void onAlreadyPresentApiField(ApiAmendments.FieldAmendment amendment) {}

      @Override
      public void onAmendJarClass(JarAmendments.ClassAmendment amendment) {}

      @Override
      public void onAlreadyPresentJarClass(JarAmendments.ClassAmendment amendment) {}

      @Override
      public void onAmendJarInterface(JarAmendments.InterfaceAmendment amendment) {}

      @Override
      public void onAlreadyPresentJarInterface(JarAmendments.InterfaceAmendment amendment) {}

      @Override
      public void onAmendJarMethod(JarAmendments.MethodAmendment amendment) {}

      @Override
      public void onAlreadyPresentJarMethod(JarAmendments.MethodAmendment amendment) {}

      @Override
      public void onAmendJarField(JarAmendments.FieldAmendment amendment) {}

      @Override
      public void onAlreadyPresentJarField(JarAmendments.FieldAmendment amendment) {}

      @Override
      public void done() {}
    };
  }
}
