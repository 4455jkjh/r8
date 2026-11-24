// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ThreadingModuleLoader {

  @UsedByReflection(
      description = "Prevent analysis any inlining and assumptions of the provider class names",
      constraints = {KeepConstraint.NEVER_INLINE})
  private static List<String> getProviderNames() {
    return ImmutableList.of(
        "com.android.tools.r8.threading.providers.blocking.ThreadingModuleBlockingProvider",
        "com.android.tools.r8.threading.providers.singlethreaded.ThreadingModuleSingleThreadedProvider");
  }

  @UsesReflection({
    @KeepTarget(
        kind = KeepItemKind.CLASS_AND_METHODS,
        className =
            "com.android.tools.r8.threading.providers."
                + "blocking.ThreadingModuleBlockingProvider",
        methodName = "<init>",
        methodParameters = {}),
    @KeepTarget(
        kind = KeepItemKind.CLASS_AND_METHODS,
        className =
            "com.android.tools.r8.threading.providers."
                + "singlethreaded.ThreadingModuleSingleThreadedProvider",
        methodName = "<init>",
        methodParameters = {})
  })
  public static ThreadingModuleProvider load() {
    for (String name : getProviderNames()) {
      try {
        Class<?> providerClass = Class.forName(name);
        return (ThreadingModuleProvider) providerClass.getDeclaredConstructor().newInstance();
      } catch (ClassNotFoundException ignored) {
        // Try the next provider.
      } catch (ReflectiveOperationException e) {
        throw new CompilationError("Failure creating provider for the threading module", e);
      }
    }
    throw new CompilationError("Failure to find a provider for the threading module");
  }
}
