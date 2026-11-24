// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexApplication.classesWithDeterministicOrder;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ImmediateAppSubtypingInfo extends ImmediateSubtypingInfo<DexClass, DexClass> {

  private ImmediateAppSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexClass, Object> immediateSubtypes) {
    super(appView, immediateSubtypes);
  }

  public static ImmediateAppSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    DirectMappedDexApplication app = appView.app().asDirect();
    Iterable<DexClass> classes =
        Iterables.concat(app.programClasses(), app.classpathClasses(), app.libraryClasses());
    Map<DexClass, Object> immediateSubtypes = new IdentityHashMap<>();
    for (DexClass clazz : classes) {
      addImmediateSupertypes(immediateSubtypes, clazz, appView);
    }
    return new ImmediateAppSubtypingInfo(appView, immediateSubtypes);
  }

  private static void addImmediateSupertypes(
      Map<DexClass, Object> immediateSubtypes, DexClass clazz, AppView<?> appView) {
    DexType superType = clazz.getSuperType();
    if (superType != null) {
      addImmediateSuperclass(immediateSubtypes, appView.definitionFor(superType), clazz);
    }
    for (DexType iface : clazz.getInterfaces().getBacking()) {
      addImmediateSuperclass(immediateSubtypes, appView.definitionFor(iface), clazz);
    }
  }

  @Override
  DexClass toS(DexClass clazz) {
    return clazz;
  }

  // TODO(b/188395655): This is equivalent to just sorting all interfaces?
  public List<DexClass> computeReachableInterfacesWithDeterministicOrder() {
    List<DexClass> interfaces = new ArrayList<>();
    forEachInterface(interfaces::add);
    return classesWithDeterministicOrder(interfaces);
  }

  private void forEachInterface(Consumer<DexClass> consumer) {
    DexClass objectClass = appView.definitionFor(appView.dexItemFactory().objectType);
    forEachImmediateSubClassMatching(objectClass, DexClass::isInterface, consumer);
  }

  public void update(AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      return;
    }
    for (DexClass clazz : appView.getSyntheticItems().getAllPendingSyntheticClasses()) {
      addImmediateSupertypes(immediateSubtypes, clazz, appView);
    }
  }

  public boolean verifyUpToDate(AppView<AppInfoWithClassHierarchy> appView) {
    DirectMappedDexApplication app = appView.app().asDirect();
    Iterable<DexClass> classes =
        Iterables.concat(
            app.programClasses(),
            app.classpathClasses(),
            app.libraryClasses(),
            appView.getSyntheticItems().getAllPendingSyntheticClasses());
    for (DexClass clazz : classes) {
      assert verifyUpToDate(appView, clazz);
    }
    return true;
  }

  private boolean verifyUpToDate(AppView<AppInfoWithClassHierarchy> appView, DexClass clazz) {
    for (DexType supertype : clazz.allImmediateSupertypes()) {
      DexClass superclass = appView.definitionFor(supertype);
      if (superclass == null) {
        continue;
      }
      assert getSubclasses(superclass).contains(clazz)
              || (clazz.isLibraryClass()
                  && !appView.definitionFor(clazz.getType()).isLibraryClass())
          : "Expected subtypes(" + supertype.getTypeName() + ") to include " + clazz.getTypeName();
    }
    return true;
  }
}
