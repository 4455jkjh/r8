// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.initializer.readbeforewrite;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.ConcreteMutableFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.UnknownFieldSet;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.FailedTransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** The (immutable) abstract state used by the read-before-write instance initializer analysis. */
public class ReadBeforeWriteAnalysisState extends AbstractState<ReadBeforeWriteAnalysisState> {

  private static final ReadBeforeWriteAnalysisState BOTTOM =
      new ReadBeforeWriteAnalysisState(
          false, false, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

  private static final int MAX_SET_SIZE = Integer.MAX_VALUE;

  // Whether at the given program point, is `this` maybe escaped?
  final boolean isThisEscaped;

  // Whether at the given program point, is `this` definitely initialized (i.e., has the parent or
  // forwarding constructor call inside the initialized been seen)?
  final boolean isThisInitialized;

  // At the given program point, what are the fields that may have been read before they are
  // written?
  //
  // This may be set to null, which should be interpreted as: All instance fields may be read
  // before they are written, except those in the `writeBeforeReadSet`.
  final Set<DexEncodedField> readBeforeWriteSet;

  // At the given program point, what are the fields that are definitely written before read?
  // Mutually exclusive with the `readBeforeWriteSet`.
  //
  // Unlike the `readBeforeWriteSet`, this can never be null.
  final Set<DexEncodedField> writtenBeforeReadSet;

  // At the given program point, what are the SSA aliases of `this`? This includes values derived
  // from `this` through Assume instructions, CheckCast instructions and phis.
  //
  // Note that, when `this` has escaped, the out-value of any instruction may in principle return
  // `this`. We don't include these SSA values in this set, but instead conservatively treat these
  // values as possible `this` aliases when `isThisEscaped` is set.
  final Set<Value> thisAliases;

  private ReadBeforeWriteAnalysisState(
      boolean isThisEscaped,
      boolean isThisInitialized,
      Set<DexEncodedField> readBeforeWriteSet,
      Set<DexEncodedField> writtenBeforeReadSet,
      Set<Value> thisAliases) {
    this.isThisEscaped = isThisEscaped;
    this.isThisInitialized = isThisInitialized;
    this.readBeforeWriteSet = readBeforeWriteSet;
    this.writtenBeforeReadSet = writtenBeforeReadSet;
    this.thisAliases = thisAliases;
    assert verifyFieldSetsDisjoint();
  }

  public static ReadBeforeWriteAnalysisState bottom() {
    return BOTTOM;
  }

  /** Creates a ReadBeforeWriteAnalysisState if the state is not too big. */
  public static TransferFunctionResult<ReadBeforeWriteAnalysisState> create(
      boolean isThisEscaped,
      boolean isThisInitialized,
      Set<DexEncodedField> readBeforeWriteSet,
      Set<DexEncodedField> writtenBeforeReadSet,
      Set<Value> thisAliases) {
    if (readBeforeWriteSet != null && readBeforeWriteSet.size() > MAX_SET_SIZE) {
      return new FailedTransferFunctionResult<>();
    }
    if (writtenBeforeReadSet.size() > MAX_SET_SIZE) {
      return new FailedTransferFunctionResult<>();
    }
    if (thisAliases.size() > MAX_SET_SIZE) {
      return new FailedTransferFunctionResult<>();
    }
    return new ReadBeforeWriteAnalysisState(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }

  public AbstractFieldSet getAbstractReadBeforeWriteSet() {
    if (readBeforeWriteSet != null) {
      return readBeforeWriteSet.isEmpty()
          ? EmptyFieldSet.getInstance()
          : new ConcreteMutableFieldSet(readBeforeWriteSet);
    }
    return UnknownFieldSet.getInstance();
  }

  public AbstractFieldSet getAbstractWrittenBeforeReadSet() {
    return writtenBeforeReadSet.isEmpty()
        ? EmptyFieldSet.getInstance()
        : new ConcreteMutableFieldSet(writtenBeforeReadSet);
  }

  boolean isDefinitelyThis(Value thisValue, Value value) {
    return value.getAliasedValue() == thisValue;
  }

  boolean isMaybeThis(Value thisValue, Value value) {
    return value == thisValue || thisAliases.contains(value);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> joinWrittenBeforeRead(
      DexEncodedField field) {
    if (writtenBeforeReadSet.contains(field)) {
      return this;
    }
    Set<DexEncodedField> newWrittenBeforeReadSet =
        SetUtils.newIdentityHashSet(writtenBeforeReadSet.size() + 1);
    newWrittenBeforeReadSet.addAll(writtenBeforeReadSet);
    newWrittenBeforeReadSet.add(field);
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, newWrittenBeforeReadSet, thisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> joinReadBeforeWrite(DexEncodedField field) {
    if (readBeforeWriteSet == null) {
      // The transfer function explicitly handles the `readBeforeWriteSet == null` case,
      // so this should never happen.
      assert false;
      return this;
    }
    ;
    if (readBeforeWriteSet.contains(field)) {
      return this;
    }
    Set<DexEncodedField> newReadBeforeWriteSet =
        SetUtils.newIdentityHashSet(readBeforeWriteSet.size() + 1);
    newReadBeforeWriteSet.addAll(readBeforeWriteSet);
    newReadBeforeWriteSet.add(field);
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, isThisInitialized, newReadBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> setReadBeforeWriteSetToUnknown() {
    if (readBeforeWriteSet == null) {
      return this;
    }
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, isThisInitialized, null, writtenBeforeReadSet, thisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> joinThisAlias(Value thisAlias) {
    if (thisAliases.contains(thisAlias)) {
      return this;
    }
    Set<Value> newThisAliases = SetUtils.newIdentityHashSet(thisAliases.size() + 1);
    newThisAliases.addAll(thisAliases);
    newThisAliases.add(thisAlias);
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, newThisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> joinThisAliases(
      Collection<Value> otherThisAliases) {
    if (thisAliases.containsAll(otherThisAliases)) {
      return this;
    }
    Set<Value> newThisAliases = Sets.newIdentityHashSet();
    newThisAliases.addAll(thisAliases);
    newThisAliases.addAll(otherThisAliases);
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, newThisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> withEscapedThis() {
    // The `this` cannot escape before it is initialized.
    assert isThisInitialized;
    return ReadBeforeWriteAnalysisState.create(
        true, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> withEscapedThis(boolean newIsThisEscaped) {
    if (newIsThisEscaped) {
      return withEscapedThis();
    } else {
      assert !isThisEscaped;
      return this;
    }
  }

  TransferFunctionResult<ReadBeforeWriteAnalysisState> withInitializedThis() {
    assert !isThisInitialized;
    return ReadBeforeWriteAnalysisState.create(
        isThisEscaped, true, readBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }

  @Override
  public ReadBeforeWriteAnalysisState asAbstractState() {
    return this;
  }

  @Override
  public ReadBeforeWriteAnalysisState join(AppView<?> appView, ReadBeforeWriteAnalysisState state) {
    boolean newIsThisEscaped = isThisEscaped || state.isThisEscaped;
    boolean newIsThisInitialized = isThisInitialized || state.isThisInitialized;
    Set<DexEncodedField> newReadBeforeWriteSet =
        joinIdentitySets(readBeforeWriteSet, state.readBeforeWriteSet);
    Set<DexEncodedField> newWrittenBeforeReadSet =
        meetIdentitySets(writtenBeforeReadSet, state.writtenBeforeReadSet);
    Set<Value> newThisAliases = joinIdentitySets(thisAliases, state.thisAliases);
    if (isIdentical(
        newIsThisEscaped,
        newIsThisInitialized,
        newReadBeforeWriteSet,
        newWrittenBeforeReadSet,
        newThisAliases)) {
      return this;
    }
    if (state.isIdentical(
        newIsThisEscaped,
        newIsThisInitialized,
        newReadBeforeWriteSet,
        newWrittenBeforeReadSet,
        newThisAliases)) {
      return state;
    }
    return new ReadBeforeWriteAnalysisState(
        newIsThisEscaped,
        newIsThisInitialized,
        newReadBeforeWriteSet,
        newWrittenBeforeReadSet,
        newThisAliases);
  }

  static <T> Set<T> joinIdentitySets(Set<T> set, Set<T> otherSet) {
    if (set == null || otherSet == null) {
      // Unknown.
      return null;
    }
    if (set.isEmpty()) {
      return otherSet;
    }
    if (otherSet.isEmpty() || set.containsAll(otherSet)) {
      return set;
    }
    if (otherSet.size() > set.size() && otherSet.containsAll(set)) {
      return otherSet;
    }
    Set<T> union = Sets.newIdentityHashSet();
    union.addAll(set);
    union.addAll(otherSet);
    return union;
  }

  static <T> Set<T> meetIdentitySets(Set<T> set, Set<T> otherSet) {
    if (set.isEmpty() || otherSet.isEmpty()) {
      return Collections.emptySet();
    }
    if (set.size() <= otherSet.size() && otherSet.containsAll(set)) {
      return set;
    }
    if (otherSet.size() < set.size() && set.containsAll(otherSet)) {
      return otherSet;
    }
    Set<T> intersection = Sets.newIdentityHashSet();
    for (T element : set) {
      if (otherSet.contains(element)) {
        intersection.add(element);
      }
    }
    return intersection.isEmpty() ? Collections.emptySet() : intersection;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ReadBeforeWriteAnalysisState)) {
      return false;
    }
    ReadBeforeWriteAnalysisState state = (ReadBeforeWriteAnalysisState) other;
    return isThisEscaped == state.isThisEscaped
        && isThisInitialized == state.isThisInitialized
        && Objects.equals(readBeforeWriteSet, state.readBeforeWriteSet)
        && writtenBeforeReadSet.equals(state.writtenBeforeReadSet)
        && thisAliases.equals(state.thisAliases);
  }

  private boolean isIdentical(
      boolean otherIsThisEscaped,
      boolean otherIsThisInitialized,
      Set<DexEncodedField> otherReadBeforeWriteSet,
      Set<DexEncodedField> otherWrittenBeforeReadSet,
      Set<Value> otherThisAliases) {
    return isThisEscaped == otherIsThisEscaped
        && isThisInitialized == otherIsThisInitialized
        && readBeforeWriteSet == otherReadBeforeWriteSet
        && writtenBeforeReadSet == otherWrittenBeforeReadSet
        && thisAliases == otherThisAliases;
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashZZLLL(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }

  private boolean verifyFieldSetsDisjoint() {
    if (readBeforeWriteSet != null) {
      for (DexEncodedField field : readBeforeWriteSet) {
        assert !writtenBeforeReadSet.contains(field);
      }
      for (DexEncodedField field : writtenBeforeReadSet) {
        assert !readBeforeWriteSet.contains(field)
            : "Field marked as both read-before-write and written-before-read: " + field;
      }
    }
    return true;
  }
}
