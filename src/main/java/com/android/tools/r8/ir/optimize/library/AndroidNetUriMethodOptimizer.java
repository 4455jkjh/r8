// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class AndroidNetUriMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public AndroidNetUriMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.androidNetUriType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    DexItemFactory.AndroidNetUriMembers members = factory.androidNetUriMembers;
    if (singleTargetReference.isIdenticalTo(members.encode)) {
      optimizeEncode(code, instructionIterator, invoke, affectedValues, null);
    } else if (singleTargetReference.isIdenticalTo(members.encodeWithAllow)) {
      Value allowArg = invoke.getSecondArgument().getAliasedValue();
      DexString allowStr = allowArg.getConstStringOrNull(appView, code);
      if (allowStr != null
          || allowArg.getAbstractValue(appView, code.context()).isNull()
          || allowArg.isAlwaysNull(appView)) {
        optimizeEncode(
            code,
            instructionIterator,
            invoke,
            affectedValues,
            allowStr != null ? allowStr.toString() : null);
      }
    }
    return instructionIterator;
  }

  private void optimizeEncode(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      String allow) {
    Value sArg = invoke.getFirstArgument().getAliasedValue();
    if (sArg.getAbstractValue(appView, code.context()).isNull() || sArg.isAlwaysNull(appView)) {
      instructionIterator.replaceCurrentInstructionWithConstNull(code);
    } else {
      optimizeStringToStringFunction(
          code,
          instructionIterator,
          invoke,
          affectedValues,
          s -> {
            String encoded = encode(s.toString(), allow);
            return encoded != null ? factory.createString(encoded) : null;
          });
    }
  }

  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
  private static final String ALLOWED_CHARS = "_-!.~'()*";

  private static String encode(String s, String allow) {
    if (s == null) {
      return null;
    }
    StringBuilder encoded = new StringBuilder();
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      char c = (char) (b & 0xff);
      if (isAllowed(c, allow)) {
        encoded.append(c);
      } else {
        encoded.append('%');
        encoded.append(HEX_DIGITS[(b & 0xf0) >> 4]);
        encoded.append(HEX_DIGITS[b & 0xf]);
      }
    }
    return encoded.toString();
  }

  private static boolean isAllowed(char c, String allow) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || ALLOWED_CHARS.indexOf(c) != -1
        || (allow != null && allow.indexOf(c) != -1);
  }
}
