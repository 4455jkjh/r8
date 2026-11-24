// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.utils.internal.TraversalContinuation;
import com.android.tools.r8.utils.internal.collections.WorkList;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class ImmediateSubtypingInfo<S extends DexClass, T extends DexClass> {

  final AppView<? extends AppInfoWithClassHierarchy> appView;
  // Most supertypes have a single immediate subtype; store T directly until a second distinct
  // subtype upgrades the bucket to a LinkedHashSet<T>.
  final Map<S, Object> immediateSubtypes;

  ImmediateSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView, Map<S, Object> immediateSubtypes) {
    this.appView = appView;
    this.immediateSubtypes = immediateSubtypes;
  }

  @SuppressWarnings({"ReferenceEquality", "unchecked"})
  static <S extends DexClass, T extends DexClass> void addImmediateSuperclass(
      Map<S, Object> immediateSubtypes, S superclass, T subclass) {
    if (superclass == null) {
      return;
    }
    Object existing = immediateSubtypes.get(superclass);
    if (existing == null) {
      immediateSubtypes.put(superclass, subclass);
    } else if (existing instanceof Set) {
      ((Set<T>) existing).add(subclass);
    } else if (existing != subclass) {
      Set<T> subtypes = Sets.newLinkedHashSetWithExpectedSize(2);
      subtypes.add((T) existing);
      subtypes.add(subclass);
      immediateSubtypes.put(superclass, subtypes);
    }
  }

  @SuppressWarnings("unchecked")
  private void addSubclassesToWorkList(S clazz, WorkList<? super T> worklist) {
    Object existing = immediateSubtypes.get(clazz);
    if (existing == null) {
      return;
    }
    if (existing instanceof Set) {
      worklist.addIfNotSeen((Set<T>) existing);
    } else {
      worklist.addIfNotSeen((T) existing);
    }
  }

  abstract S toS(DexClass clazz);

  public void forEachImmediateSuperClass(DexClass clazz, Consumer<? super DexClass> consumer) {
    forEachImmediateSuperClassMatching(
        clazz,
        (supertype, superclass) -> superclass != null,
        (supertype, superclass) -> consumer.accept(superclass));
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz,
      BiPredicate<? super DexType, ? super DexClass> predicate,
      BiConsumer<? super DexType, ? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (predicate.test(supertype, superclass)) {
            consumer.accept(supertype, superclass);
          }
        });
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz, Predicate<? super DexClass> predicate, Consumer<? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  public void forEachImmediateProgramSuperClass(
      DexProgramClass clazz, Consumer<? super DexProgramClass> consumer) {
    forEachImmediateProgramSuperClassMatching(clazz, alwaysTrue(), consumer);
  }

  public void forEachImmediateProgramSuperClassMatching(
      DexProgramClass clazz,
      Predicate<? super DexProgramClass> predicate,
      Consumer<? super DexProgramClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(supertype));
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  @SuppressWarnings("unchecked")
  public void forEachImmediateSubClassMatching(
      S clazz, Predicate<? super T> predicate, Consumer<? super T> consumer) {
    Object existing = immediateSubtypes.get(clazz);
    if (existing == null) {
      return;
    }
    if (existing instanceof Set) {
      for (T subclass : (Set<T>) existing) {
        if (predicate.test(subclass)) {
          consumer.accept(subclass);
        }
      }
    } else {
      T subclass = (T) existing;
      if (predicate.test(subclass)) {
        consumer.accept(subclass);
      }
    }
  }

  public void forEachTransitiveSubclass(S clazz, Consumer<? super T> consumer) {
    forEachTransitiveSubclassMatching(clazz, Predicates.alwaysTrue(), consumer);
  }

  @SuppressWarnings("unchecked")
  public <U extends DexClass> void forEachTransitiveSubclassMatching(
      S clazz, Predicate<? super T> predicate, Consumer<? super U> consumer) {
    WorkList<T> worklist = WorkList.newIdentityWorkList();
    addSubclassesToWorkList(clazz, worklist);
    worklist.process(
        subclass -> {
          if (predicate.test(subclass)) {
            consumer.accept((U) subclass);
          }
          S subclassOrNull = toS(subclass);
          if (subclassOrNull != null) {
            addSubclassesToWorkList(subclassOrNull, worklist);
          }
        });
  }

  public void forEachTransitiveProgramSubclass(
      S clazz, Consumer<? super DexProgramClass> consumer) {
    forEachTransitiveProgramSubclassMatching(clazz, Predicates.alwaysTrue(), consumer);
  }

  @SuppressWarnings("unchecked")
  public void forEachTransitiveProgramSubclassMatching(
      S clazz,
      Predicate<? super DexProgramClass> predicate,
      Consumer<? super DexProgramClass> consumer) {
    forEachTransitiveSubclassMatching(
        clazz,
        subclass -> subclass.isProgramClass() && predicate.test(subclass.asProgramClass()),
        consumer);
  }

  @SuppressWarnings("unchecked")
  public Set<T> getSubclasses(S clazz) {
    Object existing = immediateSubtypes.get(clazz);
    if (existing == null) {
      return Collections.emptySet();
    }
    if (existing instanceof Set) {
      return (Set<T>) existing;
    }
    return Collections.singleton((T) existing);
  }

  public Iterable<T> getSubinterfaces(S clazz) {
    assert clazz.isInterface();
    return Iterables.filter(getSubclasses(clazz), DexClass::isInterface);
  }

  public Iterable<DexProgramClass> getSuperProgramInterfaces(
      DexProgramClass clazz, DexDefinitionSupplier definitions) {
    return Iterables.filter(
        Iterables.transform(
            clazz.getInterfaces(), i -> asProgramClassOrNull(definitions.definitionFor(i, clazz))),
        Objects::nonNull);
  }

  public boolean hasSubclasses(S clazz) {
    return immediateSubtypes.containsKey(clazz);
  }

  public <TB, TC> TraversalContinuation<TB, TC> traverseTransitiveSubclasses(
      S clazz, Function<? super DexClass, TraversalContinuation<TB, TC>> fn) {
    TraversalContinuation<TB, TC> traversalContinuation = TraversalContinuation.doContinue();
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList();
    addSubclassesToWorkList(clazz, worklist);
    while (worklist.hasNext()) {
      DexClass subclass = worklist.next();
      traversalContinuation = fn.apply(subclass);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
      S subclassOrNull = toS(subclass);
      if (subclassOrNull != null) {
        addSubclassesToWorkList(subclassOrNull, worklist);
      }
    }
    return traversalContinuation;
  }
}
