// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker.usages;

public interface AnalysisCallback {
  boolean shouldProcess(String internalName);

  void referencedInt(int value);

  void referencedString(String value);

  void referencedStaticField(String internalName, String fieldName);

  void referencedMethod(String internalName, String methodName, String methodDescriptor);

  default void startMethodVisit(String methodName) {}

  default void endMethodVisit(String methodName) {}

  class MethodVisitingStatus {
    public boolean isVisiting = false;
    public String methodName = null;
  }
}
