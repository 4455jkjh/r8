// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.repackaging.RepackagingLens;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class VirtualMethodHoisterInspector {
  private final Set<DexMethod> hoisted;
  private final Set<DexMethod> candidatesNothoisted;
  private final NonIdentityGraphLens repackagingLens;
  private final DexItemFactory factory;

  public VirtualMethodHoisterInspector(
      AppView<?> appView, Set<DexMethod> hoisted, Set<DexMethod> candidatesNothoisted) {
    this.hoisted = hoisted;
    this.candidatesNothoisted = candidatesNothoisted;
    this.factory = appView.dexItemFactory();
    this.repackagingLens =
        appView.graphLens().isNonIdentityLens()
            ? appView
                .graphLens()
                .asNonIdentityLens()
                .find(lens -> lens.isRepackagingLens() && !((RepackagingLens) lens).isEmpty())
            : null;
  }

  public VirtualMethodHoisterInspector assertHoisted(Method method) {
    if (hoisted.contains(toRepackagedDexMethod(method))) {
      return this;
    }
    fail("Method " + method + "(...) WAS NOT hoisted");
    return this;
  }

  public VirtualMethodHoisterInspector assertMethodCheckedButNotHoisted(Method method) {
    DexMethod dexMethod = toRepackagedDexMethod(method);
    if (hoisted.contains(dexMethod)) {
      fail("Method " + method + "(...) WAS hoisted");
    }
    if (candidatesNothoisted.contains(dexMethod)) {
      return this;
    }
    fail("Method " + method + "(...) WAS NOT checked");
    return this;
  }

  private DexMethod toRepackagedDexMethod(Method method) {
    return factory.createMethod(
        toRepackagedDexType(method.getDeclaringClass()),
        factory.createProto(
            toRepackagedDexType(method.getReturnType()),
            Arrays.stream(method.getParameterTypes())
                .map(this::toRepackagedDexType)
                .collect(Collectors.toList())),
        factory.createString(method.getName()));
  }

  private DexType toRepackagedDexType(Class<?> clazz) {
    return getRepackagedType(TestBase.toDexType(clazz, factory));
  }

  private DexType getRepackagedType(DexType type) {
    return repackagingLens != null ? repackagingLens.getNextType(type) : type;
  }
}
