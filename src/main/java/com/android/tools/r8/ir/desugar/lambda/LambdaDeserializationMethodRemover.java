// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;

public class LambdaDeserializationMethodRemover {

  /** Remove lambda deserialization methods. */
  public static void run(AppView<AppInfo> appView) {
    if (appView.options().desugarState.isOn()) {
      run(appView, appView.appInfo().classes());
    }
  }

  /** Remove lambda deserialization methods. */
  public static void run(AppView<?> appView, Collection<DexProgramClass> classes) {
    assert appView.options().desugarState.isOn() || classes.isEmpty();
    DexItemFactory factory = appView.dexItemFactory();
    DexMethod reference = factory.deserializeLambdaMethod;
    for (DexProgramClass clazz : classes) {
      DexEncodedMethod method = clazz.lookupDirectMethod(reference);
      if (method != null
          && method.getAccessFlags().isPrivate()
          && method.getAccessFlags().isStatic()
          && method.hasCfCode()) {
        // From JDK 27 the synthetic $deserializeLambda$ method is split into multiple methods.
        // These are all outlined from $deserializeLambda$, and prefixed by the same name.
        // See https://bugs.openjdk.org/browse/JDK-8381812. This happens independent of the
        // --release argument passed to javac from JDK-27.
        for (CfInstruction instruction : method.getCfCode().getInstructions()) {
          if (instruction.isInvokeStatic()) {
            DexMethod invokedMethod = instruction.asInvoke().getMethod();
            if (invokedMethod.getHolderType().isIdenticalTo(clazz.getType())
                && invokedMethod.isLambdaDeserializeMethod(factory)) {
              DexEncodedMethod target = clazz.lookupDirectMethod(invokedMethod);
              if (target != null
                  && target.getAccessFlags().isPrivate()
                  && target.getAccessFlags().isStatic()) {
                clazz.removeMethod(invokedMethod);
              }
            }
          }
        }
        clazz.removeMethod(method.getReference());
      }
    }
  }
}
