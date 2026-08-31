// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.utils.internal.ArrayUtils;
import java.util.Set;

public class MethodBoxingStatus {

  public static final MethodBoxingStatus NONE_UNBOXABLE = new MethodBoxingStatus(null, null);
  public static final MethodBoxingStatus UNPROCESSED_CANDIDATE = new MethodBoxingStatus(null, null);

  private final ValueBoxingStatus returnStatus;
  private final ValueBoxingStatus[] argStatuses;

  public static MethodBoxingStatus create(
      ValueBoxingStatus returnStatus, ValueBoxingStatus[] argStatuses) {
    assert !ArrayUtils.contains(argStatuses, null);
    if (returnStatus.isNotUnboxable()
        && ArrayUtils.all(argStatuses, ValueBoxingStatus.NOT_UNBOXABLE)) {
      return NONE_UNBOXABLE;
    }
    return new MethodBoxingStatus(returnStatus, argStatuses);
  }

  private MethodBoxingStatus(ValueBoxingStatus returnStatus, ValueBoxingStatus[] argStatuses) {
    this.returnStatus = returnStatus;
    this.argStatuses = argStatuses;
  }

  public MethodBoxingStatus merge(
      MethodBoxingStatus other, NumberUnboxerOptions numberUnboxerOptions) {
    if (isNoneUnboxable() || other.isNoneUnboxable()) {
      return NONE_UNBOXABLE;
    }
    if (isUnprocessedCandidate()) {
      return other;
    }
    if (other.isUnprocessedCandidate()) {
      return this;
    }
    assert argStatuses.length == other.argStatuses.length;
    ValueBoxingStatus[] newArgStatuses = new ValueBoxingStatus[argStatuses.length];
    for (int i = 0; i < other.argStatuses.length; i++) {
      newArgStatuses[i] = other.argStatuses[i].merge(argStatuses[i], numberUnboxerOptions);
    }
    return create(returnStatus.merge(other.returnStatus, numberUnboxerOptions), newArgStatuses);
  }

  public boolean isNoneUnboxable() {
    return this == NONE_UNBOXABLE;
  }

  public boolean isUnprocessedCandidate() {
    return this == UNPROCESSED_CANDIDATE;
  }

  public ValueBoxingStatus getReturnStatus() {
    assert !isNoneUnboxable();
    return returnStatus;
  }

  public ValueBoxingStatus getArgStatus(int i) {
    assert !isNoneUnboxable();
    assert argStatuses[i] != null;
    return argStatuses[i];
  }

  public ValueBoxingStatus[] getArgStatuses() {
    assert !isNoneUnboxable();
    return argStatuses;
  }

  public MethodBoxingStatus rewrittenWithLens(
      AppView<?> appView,
      GraphLens graphLens,
      GraphLens codeLens,
      DexMethod newMethod,
      Set<DexMethod> prunedMethods) {
    ValueBoxingStatus newReturnStatus =
        returnStatus.rewrittenWithLens(appView, graphLens, codeLens, prunedMethods);
    boolean diff = newReturnStatus != returnStatus;
    RewrittenPrototypeDescription rewrittenPrototypeDescription =
        graphLens.lookupPrototypeChangesForMethodDefinition(newMethod, codeLens);
    // The boxing status contains the argument values, but not the receiver value.
    // The rewritten prototype description works with inValues.
    DexClassAndMethod dexClassAndMethod = appView.definitionFor(newMethod);
    // TODO(b/307872552): When a method is removed, we move everything to NONE_UNBOXABLE, check and
    //  understand the consequences (for example for initializers) (?).
    if (dexClassAndMethod == null) {
      return MethodBoxingStatus.NONE_UNBOXABLE;
    }
    int shift =
        rewrittenPrototypeDescription.getArgumentInfoCollection().isConvertedToStaticMethod()
            ? 1
            : (dexClassAndMethod.getDefinition().isStatic() ? 0 : 1);
    ValueBoxingStatus[] newArgStatuses = new ValueBoxingStatus[newMethod.getArity()];
    int j = 0;
    for (int i = 0; i < argStatuses.length; i++) {
      ArgumentInfo argumentInfo =
          rewrittenPrototypeDescription.getArgumentInfoCollection().getArgumentInfo(i + shift);
      if (argumentInfo.isRemovedArgumentInfo()) {
        diff = true;
        continue;
      }
      newArgStatuses[j] =
          argStatuses[i].rewrittenWithLens(appView, graphLens, codeLens, prunedMethods);
      diff |= newArgStatuses[j] != argStatuses[i];
      assert newArgStatuses[j].isNotUnboxable() || !argumentInfo.isRewrittenTypeInfo()
          : "Rewriting an argument type of an argument being unboxed.";
      j++;
    }
    assert newArgStatuses.length == 0 || newArgStatuses[newMethod.getArity() - 1] != null;
    return diff ? new MethodBoxingStatus(newReturnStatus, newArgStatuses) : this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MethodBoxingStatus[");
    if (isUnprocessedCandidate()) {
      sb.append("UNPROCESSED_CANDIDATE");
    } else if (isNoneUnboxable()) {
      sb.append("NONE_UNBOXABLE");
    } else {
      for (int i = 0; i < argStatuses.length; i++) {
        if (argStatuses[i].mayBeUnboxable()) {
          sb.append(i).append(":").append(argStatuses[i]).append(";");
        }
      }
      if (returnStatus.mayBeUnboxable()) {
        sb.append("ret").append(":").append(returnStatus).append(";");
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
