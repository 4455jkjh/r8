// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ReservedFieldNamingState.InternalState;
import java.util.IdentityHashMap;
import java.util.Map;

class ReservedFieldNamingState extends FieldNamingStateBase<InternalState> {

  private ReservedFieldNamingState interfaceMinificationState = null;

  ReservedFieldNamingState(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView, new IdentityHashMap<>());
  }

  boolean isReserved(DexString name, DexType type) {
    return getReservedByName(name, type) != null
        || getReservedByNameInInterfaces(name, type) != null;
  }

  private DexString getReservedByName(DexString name, DexType type) {
    DexString reservedByNameInState = getReservedByNameInState(getInternalState(type), name);
    if (reservedByNameInState != null) {
      return reservedByNameInState;
    }
    return getReservedByNameInInterfaces(name, type);
  }

  private DexString getReservedByNameInInterfaces(DexString name, DexType type) {
    return interfaceMinificationState == null
        ? null
        : getReservedByNameInState(interfaceMinificationState.getInternalState(type), name);
  }

  private static DexString getReservedByNameInState(InternalState internalState, DexString name) {
    return internalState == null ? null : internalState.getReservedByName(name);
  }

  void markReserved(DexString name, DexClassAndField field) {
    getOrCreateInternalState(field.getType()).markReserved(name, field.getName());
  }

  void includeReservations(ReservedFieldNamingState reservedNames) {
    for (Map.Entry<DexType, InternalState> entry : reservedNames.internalStates.entrySet()) {
      getOrCreateInternalState(entry.getKey()).includeReservations(entry.getValue());
    }
    includeInterfaceReservationState(reservedNames);
  }

  private void includeInterfaceReservationState(ReservedFieldNamingState reservedNames) {
    if (reservedNames.interfaceMinificationState != null) {
      assert interfaceMinificationState == null
          || interfaceMinificationState == reservedNames.interfaceMinificationState;
      interfaceMinificationState = reservedNames.interfaceMinificationState;
    }
  }

  void setInterfaceMinificationState(ReservedFieldNamingState namingState) {
    assert namingState != null;
    assert interfaceMinificationState == null || interfaceMinificationState == namingState;
    this.interfaceMinificationState = namingState;
  }

  @Override
  InternalState createInternalState() {
    return new InternalState();
  }

  static class InternalState {

    private final Map<DexString, DexString> reservedNames = new IdentityHashMap<>();

    DexString getReservedByName(DexString name) {
      DexString reservedBy = reservedNames.get(name);
      return reservedBy != null ? reservedBy : reservedNames.get(name);
    }

    void markReserved(DexString name, DexString originalName) {
      reservedNames.put(name, originalName);
    }

    void includeReservations(InternalState state) {
      reservedNames.putAll(state.reservedNames);
    }
  }
}
