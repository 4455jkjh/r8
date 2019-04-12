// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DirectMappedDexApplication extends DexApplication {

  // Unmodifiable mapping of all types to their definitions.
  private final Map<DexType, DexClass> allClasses;
  // Collections of the three different types for iteration.
  private final ImmutableList<DexProgramClass> programClasses;
  private final ImmutableList<DexClasspathClass> classpathClasses;
  private final ImmutableList<DexLibraryClass> libraryClasses;

  private DirectMappedDexApplication(
      ClassNameMapper proguardMap,
      Map<DexType, DexClass> allClasses,
      ImmutableList<DexProgramClass> programClasses,
      ImmutableList<DexClasspathClass> classpathClasses,
      ImmutableList<DexLibraryClass> libraryClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      ImmutableSet<DexType> mainDexList,
      String deadCode,
      DexItemFactory dexItemFactory,
      DexString highestSortingString,
      Timing timing) {
    super(
        proguardMap,
        dataResourceProviders,
        mainDexList,
        deadCode,
        dexItemFactory,
        highestSortingString,
        timing);
    this.allClasses = Collections.unmodifiableMap(allClasses);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexClass> allClasses() {
    return allClasses.values();
  }

  @Override
  List<DexProgramClass> programClasses() {
    return programClasses;
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses;
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return allClasses.get(type);
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz instanceof DexProgramClass ? clazz.asProgramClass() : null;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  @Override
  public DirectMappedDexApplication toDirect() {
    return this;
  }

  @Override
  public DirectMappedDexApplication asDirect() {
    return this;
  }

  @Override
  public String toString() {
    return "DexApplication (direct)";
  }

  public DirectMappedDexApplication rewrittenWithLense(GraphLense graphLense) {
    // As a side effect, this will rebuild the program classes and library classes maps.
    DirectMappedDexApplication rewrittenApplication = this.builder().build().asDirect();
    assert rewrittenApplication.mappingIsValid(graphLense, allClasses.keySet());
    return rewrittenApplication;
  }

  private boolean mappingIsValid(GraphLense graphLense, Iterable<DexType> types) {
    // The lens might either map to a different type that is already present in the application
    // (e.g. relinking a type) or it might encode a type that was renamed, in which case the
    // original type will point to a definition that was renamed.
    for (DexType type : types) {
      DexType renamed = graphLense.lookupType(type);
      if (renamed != type) {
        if (definitionFor(type) == null && definitionFor(renamed) != null) {
          continue;
        }
        assert definitionFor(type).type == renamed || definitionFor(renamed) != null;
      }
    }
    return true;
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private final ImmutableList<DexLibraryClass> libraryClasses;
    private final ImmutableList<DexClasspathClass> classpathClasses;

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = application.loadAllClasses();
      libraryClasses = allClasses.getLibraryClasses();
      classpathClasses = allClasses.getClasspathClasses();
      replaceProgramClasses(allClasses.getProgramClasses());
    }

    private Builder(DirectMappedDexApplication application) {
      super(application);
      libraryClasses = application.libraryClasses;
      classpathClasses = application.classpathClasses;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public DexApplication build() {
      // Rebuild the map. This will fail if keys are not unique.
      // TODO(zerny): Consider not rebuilding the map if no program classes are added.
      Map<DexType, DexClass> allClasses =
          new IdentityHashMap<>(
              programClasses.size() + classpathClasses.size() + libraryClasses.size());
      // Note: writing classes in reverse priority order, so a duplicate will be correctly ordered.
      // There should never be duplicates and that is asserted in the addAll subroutine.
      addAll(allClasses, libraryClasses);
      addAll(allClasses, classpathClasses);
      addAll(allClasses, programClasses);
      return new DirectMappedDexApplication(
          proguardMap,
          allClasses,
          ImmutableList.copyOf(programClasses),
          classpathClasses,
          libraryClasses,
          ImmutableList.copyOf(dataResourceProviders),
          ImmutableSet.copyOf(mainDexList),
          deadCode,
          dexItemFactory,
          highestSortingString,
          timing);
    }
  }

  private static <T extends DexClass> void addAll(
      Map<DexType, DexClass> allClasses, Iterable<T> toAdd) {
    for (DexClass clazz : toAdd) {
      DexClass old = allClasses.put(clazz.type, clazz);
      assert old == null;
    }
  }
}
