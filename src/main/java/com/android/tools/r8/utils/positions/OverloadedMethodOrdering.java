// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.internal.AssertionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

public class OverloadedMethodOrdering {

  private static int compareMethods(ProgramMethod m1, ProgramMethod m2) {
    int m1StartLine = getMethodStartLine(m1, 0);
    int m2StartLine = getMethodStartLine(m2, 0);
    int startLineDiff = m1StartLine - m2StartLine;
    if (startLineDiff != 0) return startLineDiff;
    return DexEncodedMethod.slowCompare(m1.getDefinition(), m2.getDefinition());
  }

  public static void sortOverloadedMethods(List<ProgramMethod> methods) {
    if (methods.size() <= 1) {
      return;
    }

    methods.sort(OverloadedMethodOrdering::compareMethods);
    // Only the first method can use pc-encoding, bias the choice by code size.
    moveLargestCodeToFront(methods);
  }

  public static ProgramMethod getFirstOverload(List<ProgramMethod> methods) {
    if (methods.size() <= 1) {
      return methods.get(0);
    }
    int largestCode = -1;
    ProgramMethod minMethod = null;
    for (ProgramMethod method : methods) {
      var codeSize = getCodeSize(method);
      if (codeSize == -1) {
        // Do nothing.
      } else if (codeSize > largestCode) {
        largestCode = codeSize;
        minMethod = method;
      } else if (codeSize == largestCode) {
        if (compareMethods(method, minMethod) < 0) {
          minMethod = method;
        }
      }
      if (minMethod == null) {
        minMethod = method;
      } else if (largestCode == -1) {
        // We are still based on comparator.
        if (compareMethods(method, minMethod) < 0) {
          minMethod = method;
        }
      }
    }
    if (AssertionUtils.assertionsEnabled()) {
      var dup = new ArrayList<>(methods);
      sortOverloadedMethods(dup);
      assert dup.get(0) == minMethod;
    }
    return minMethod;
  }

  @SuppressWarnings("SameParameterValue")
  private static int getMethodStartLine(ProgramMethod method, int defaultValue) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return defaultValue;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      if (dexDebugInfo != null) {
        return dexDebugInfo.getStartLine();
      }
    } else if (code.isCfCode()) {
      for (CfInstruction instruction : code.asCfCode().getInstructions()) {
        if (instruction instanceof CfPosition) {
          return ((CfPosition) instruction).getPosition().getLine();
        }
      }
    }
    return defaultValue;
  }

  private static void moveLargestCodeToFront(List<ProgramMethod> methods) {
    int largestIndex = getIndexOfLargest(methods);
    if (largestIndex > 0) {
      Collections.swap(methods, 0, largestIndex);
    }
  }

  /** If multiple are tied, returns the first largest method. */
  private static int getIndexOfLargest(List<ProgramMethod> methods) {
    int largestIndex = -1;
    int largestCode = -1;
    for (int i = 0; i < methods.size(); i++) {
      ProgramMethod method = methods.get(i);
      int codeSize = getCodeSize(method);
      if (codeSize > largestCode) {
        largestIndex = i;
        largestCode = codeSize;
      }
    }
    return largestIndex;
  }

  private static int getCodeSize(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return -1;
    } else if (code.isDexCode()) {
      return code.asDexCode().codeSizeInBytes();
    } else if (code.isCfCode()) {
      // CF code is ignored for size.
      return -1;
    } else {
      return -1;
    }
  }

  /**
   * Returns a map from renamed names, to the methods that were renamed to it (or stayed with that
   * name).
   */
  public static IdentityHashMap<DexString, List<ProgramMethod>> groupMethodsByRenamedName(
      AppView<?> appView, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (ProgramMethod programMethod : clazz.programMethods()) {
      DexMethod method = programMethod.getReference();
      DexString renamedName = appView.getNamingLens().lookupName(method);
      methodsByRenamedName
          .computeIfAbsent(renamedName, key -> new ArrayList<>())
          .add(programMethod);
    }
    return methodsByRenamedName;
  }
}
