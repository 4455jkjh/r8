// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.ClassResolutionResult.NoResolutionResult.noResult;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DirectMappedDexApplication extends DexApplication {

  // Mapping from code objects to their encoded-method owner. Used for asserting unique ownership
  // and debugging purposes.
  private final Map<Code, DexEncodedMethod> codeOwners = new IdentityHashMap<>();
  private List<KeepDeclaration> keepDeclarations;

  // Unmodifiable mapping of all types to their definitions.
  private final IdentityHashMap<DexType, ProgramOrClasspathClass> programOrClasspathClasses;
  private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;
  // Identity-based copy of libraryClasses for lookup. libraryClasses is kept for iteration order.
  private final IdentityHashMap<DexType, DexLibraryClass> libraryClassesLookup;

  // Collections of different types for iteration.
  //
  // Program classes are kept sorted by DexType once the initial TreePruner has run, so that
  // classesWithDeterministicOrder() (called many times per compilation) is O(1) instead of copying
  // and sorting all program classes on every call.
  //
  // Comparing classes (by DexType) is relatively expensive, and there are tens of thousands of
  // program classes, so this design avoids full sorts and full order checks where possible:
  // * Classes are not sorted when read. Doing so would sort many classes that the initial
  //   TreePruner then removes, and would change the order in which the Enqueuer visits classes.
  //   TreePruner instead removes dead classes first and then sorts only the live ones.
  // * After that, Builder maintains sorted order incrementally: added classes are sorted on their
  //   own and merged in (see commitPendingProgramClasses()). Re-checking and re-sorting the whole
  //   list in build() was measured to cost a large part of what keeping the list sorted saves.
  private volatile ImmutableList<DexProgramClass> programClasses;
  private volatile boolean programClassesSorted;
  // Lazily populated only if classesWithDeterministicOrder() is called before programClasses is
  // sorted (e.g., in D8 or testing-only checks).
  private volatile ImmutableList<DexProgramClass> cachedClassesWithDeterministicOrder;
  // Tracks whether deterministic ordering was already computed on an ancestor application (via
  // programClassesSorted or classesWithDeterministicOrder()), so R8 can assert that sorting does
  // not happen prior to initial tree pruning.
  private final boolean ancestorComputedDeterministicOrder;
  private final ImmutableCollection<DexClasspathClass> classpathClasses;

  private DirectMappedDexApplication(
      ClassNameMapper proguardMap,
      DexApplicationReadFlags flags,
      IdentityHashMap<DexType, ProgramOrClasspathClass> programOrClasspathClasses,
      ImmutableMap<DexType, DexLibraryClass> libraryClasses,
      ImmutableList<DexProgramClass> programClasses,
      boolean programClassesSorted,
      boolean ancestorComputedDeterministicOrder,
      ImmutableCollection<DexClasspathClass> classpathClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      List<KeepDeclaration> keepDeclarations,
      InternalOptions options) {
    super(proguardMap, flags, dataResourceProviders, options);
    assert !programClassesSorted || Comparators.isInOrder(programClasses, CLASS_COMPARATOR);
    this.programOrClasspathClasses = programOrClasspathClasses;
    this.libraryClasses = libraryClasses;
    this.libraryClassesLookup = new IdentityHashMap<>(libraryClasses);
    this.programClasses = programClasses;
    this.programClassesSorted = programClassesSorted;
    this.ancestorComputedDeterministicOrder = ancestorComputedDeterministicOrder;
    this.classpathClasses = classpathClasses;
    this.keepDeclarations = keepDeclarations;
  }

  public static Builder directBuilder(InternalOptions options) {
    return new Builder(options);
  }

  public Collection<DexClasspathClass> classpathClasses() {
    return classpathClasses;
  }

  @Override
  List<DexProgramClass> programClasses() {
    return programClasses;
  }

  public boolean isProgramClassesSorted() {
    return programClassesSorted;
  }

  public boolean hasComputedDeterministicOrder() {
    return programClassesSorted
        || cachedClassesWithDeterministicOrder != null
        || ancestorComputedDeterministicOrder;
  }

  // Called single-threaded by TreePruner when no program classes were removed, keeping this
  // DirectMappedDexApplication instance identical for AppView.pruneItems when prunedItems is empty.
  public void setSortedProgramClasses(List<DexProgramClass> sortedProgramClasses) {
    assert !programClassesSorted;
    assert sortedProgramClasses.size() == programClasses.size();
    assert SetUtils.newIdentityHashSet(sortedProgramClasses)
        .equals(SetUtils.newIdentityHashSet(programClasses));
    assert Comparators.isInOrder(sortedProgramClasses, CLASS_COMPARATOR);
    this.programClasses = ImmutableList.copyOf(sortedProgramClasses);
    this.programClassesSorted = true;
    this.cachedClassesWithDeterministicOrder = null;
  }

  @Override
  public Collection<DexProgramClass> classesWithDeterministicOrder() {
    if (programClassesSorted) {
      return programClasses;
    }
    ImmutableList<DexProgramClass> sorted = cachedClassesWithDeterministicOrder;
    if (sorted == null) {
      sorted = ImmutableList.sortedCopyOf(CLASS_COMPARATOR, programClasses);
      cachedClassesWithDeterministicOrder = sorted;
    }
    return sorted;
  }

  @Override
  public void forEachProgramType(Consumer<DexType> consumer) {
    programClasses.forEach(clazz -> consumer.accept(clazz.type));
  }

  @Override
  public void forEachLibraryType(Consumer<DexType> consumer) {
    libraryClasses.forEach((type, clazz) -> consumer.accept(type));
  }

  public List<KeepDeclaration> getKeepDeclarations() {
    return keepDeclarations;
  }

  public void setKeepDeclarations(List<KeepDeclaration> keepDeclarations) {
    this.keepDeclarations = keepDeclarations;
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses.values();
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    DexLibraryClass libraryClass = libraryClassesLookup.get(type);
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    if (libraryClass == null && programOrClasspathClass == null) {
      return noResult();
    } else if (libraryClass != null && programOrClasspathClass == null) {
      return libraryClass;
    } else if (libraryClass == null) {
      return programOrClasspathClass.asDexClass();
    } else {
      return ClassResolutionResult.builder().add(libraryClass).add(programOrClasspathClass).build();
    }
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    if (programOrClasspathClass != null && programOrClasspathClass.isProgramClass()) {
      return programOrClasspathClass.asDexClass();
    }
    DexLibraryClass libraryClass = libraryClassesLookup.get(type);
    return libraryClass != null
        ? libraryClass
        : (programOrClasspathClass == null ? null : programOrClasspathClass.asDexClass());
  }

  @Override
  public ProgramOrClasspathClass definitionForProgramOrClasspathClassNotOnLibrary(DexType type) {
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    if (programOrClasspathClass != null && !libraryClassesLookup.containsKey(type)) {
      return programOrClasspathClass;
    }
    return null;
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    return programOrClasspathClass == null ? null : programOrClasspathClass.asProgramClass();
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  @Override
  public boolean isDirect() {
    return true;
  }

  @Override
  public DirectMappedDexApplication asDirect() {
    return this;
  }

  @Override
  public String toString() {
    return "DexApplication (direct)";
  }

  public boolean verifyWithLens(DirectMappedDexApplication beforeLensApplication, GraphLens lens) {
    assert mappingIsValid(beforeLensApplication.programClasses(), lens);
    assert verifyCodeObjectsOwners();
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean mappingIsValid(
      Collection<DexProgramClass> classesBeforeLensApplication, GraphLens lens) {
    // The lens might either map to a different type that is already present in the application
    // (e.g. relinking a type) or it might encode a type that was renamed, in which case the
    // original type will point to a definition that was renamed.
    for (DexProgramClass clazz : classesBeforeLensApplication) {
      DexType type = clazz.getType();
      DexType renamed = lens.lookupType(type);
      if (renamed.isIntType()) {
        continue;
      }
      if (renamed != type) {
        if (definitionFor(type) == null && definitionFor(renamed) != null) {
          continue;
        }
        assert definitionFor(type).type == renamed || definitionFor(renamed) != null
            : "The lens and app is inconsistent";
      }
    }
    return true;
  }

  // Debugging helper to compute the code-object owner map.
  public Map<Code, DexEncodedMethod> computeCodeObjectOwnersForDebugging() {
    // Call the verification method without assert to ensure owners are computed.
    verifyCodeObjectsOwners();
    return codeOwners;
  }

  // Debugging helper to find the method a code object belongs to.
  public DexEncodedMethod getCodeOwnerForDebugging(Code code) {
    return computeCodeObjectOwnersForDebugging().get(code);
  }

  public boolean verifyCodeObjectsOwners() {
    codeOwners.clear();
    for (DexProgramClass clazz : programClasses) {
      for (DexEncodedMethod method :
          clazz.methods(DexEncodedMethod::isNonAbstractNonNativeMethod)) {
        Code code = method.getCode();
        assert code != null;
        // If code is (lazy) CF code, then use the CF code object rather than the lazy wrapper.
        if (code.isCfCode()) {
          code = code.asCfCode();
        } else if (code.isSharedCodeObject()) {
          continue;
        }
        DexEncodedMethod otherMethod = codeOwners.put(code, method);
        assert otherMethod == null;
      }
    }
    return true;
  }

  public static class Builder extends DexApplication.Builder<DirectMappedDexApplication, Builder> {

    private final DirectMappedDexApplication previousApplication;
    private boolean programClassesSorted;
    private ImmutableCollection<DexClasspathClass> classpathClasses;
    private Map<DexType, DexLibraryClass> libraryClasses;
    private List<KeepDeclaration> keepDeclarations = Collections.emptyList();

    private List<DexClasspathClass> pendingClasspathClasses = new ArrayList<>();
    private final Set<DexType> pendingClasspathRemovalIfPresent = Sets.newIdentityHashSet();
    private boolean hasReplacedOrRemovedClasspathClasses = false;

    Builder(LazyLoadedDexApplication application, AllClasses allClasses) {
      super(application, ImmutableList.of());
      previousApplication = null;
      programClassesSorted = false;
      classpathClasses = ImmutableList.copyOf(allClasses.getClasspathClasses());
      libraryClasses = allClasses.getLibraryClasses();
      replaceProgramClasses(allClasses.getProgramClasses());
    }

    private Builder(DirectMappedDexApplication application) {
      super(application, application.programClasses);
      previousApplication = application;
      programClassesSorted = application.programClassesSorted;
      classpathClasses = application.classpathClasses;
      libraryClasses = application.libraryClasses;
      keepDeclarations = application.keepDeclarations;
    }

    private Builder(InternalOptions options) {
      super(options);
      previousApplication = null;
      programClassesSorted = false;
    }

    @Override
    public boolean isDirect() {
      return true;
    }

    @Override
    public Builder asDirect() {
      return this;
    }

    @Override
    public void addProgramClassPotentiallyOverridingNonProgramClass(DexProgramClass clazz) {
      addProgramClass(clazz);
      if (mayHaveClasspathClass(clazz.type)) {
        pendingClasspathRemovalIfPresent.add(clazz.type);
      }
      // When java.lang.Record is added remove the library class, as this program class will
      // eventually be renamed to com.android.tools.r8.RecordTag so there should not be a
      // multi-resolution result when looking up java.lang.Record.
      if (clazz.type.isIdenticalTo(options.dexItemFactory().recordType)
          && libraryClasses.containsKey(clazz.type)) {
        ensureMutableLibraryClassesMap();
        libraryClasses.remove(clazz.type);
      }
    }

    private boolean mayHaveClasspathClass(DexType type) {
      if (previousApplication != null
          && !hasReplacedOrRemovedClasspathClasses
          && pendingClasspathClasses.isEmpty()) {
        ProgramOrClasspathClass clazz = previousApplication.programOrClasspathClasses.get(type);
        return clazz != null && clazz.isClasspathClass();
      }
      return true;
    }

    private void ensureMutableLibraryClassesMap() {
      if (!(libraryClasses instanceof IdentityHashMap)) {
        libraryClasses = new IdentityHashMap<>(libraryClasses);
      }
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    List<DexProgramClass> commitPendingProgramClasses() {
      if (pendingProgramClasses.isEmpty()) {
        return Collections.emptyList();
      }
      if (!programClassesSorted) {
        return super.commitPendingProgramClasses();
      }
      List<DexProgramClass> committedProgramClasses = pendingProgramClasses;
      pendingProgramClasses = new ArrayList<>();
      classesWithDeterministicOrder(committedProgramClasses);
      programClasses = mergeSortedProgramClasses(programClasses, committedProgramClasses);
      return committedProgramClasses;
    }

    // Merges two sorted lists. Comparisons stop once either list is exhausted, so merging a few
    // added classes into a large sorted list is usually much cheaper than re-sorting the result.
    private static ImmutableList<DexProgramClass> mergeSortedProgramClasses(
        List<DexProgramClass> first, List<DexProgramClass> second) {
      int firstSize = first.size();
      int secondSize = second.size();
      ImmutableList.Builder<DexProgramClass> builder =
          ImmutableList.builderWithExpectedSize(firstSize + secondSize);
      int i = 0;
      int j = 0;
      while (i < firstSize && j < secondSize) {
        DexProgramClass a = first.get(i);
        DexProgramClass b = second.get(j);
        if (a.getType().compareTo(b.getType()) <= 0) {
          builder.add(a);
          i++;
        } else {
          builder.add(b);
          j++;
        }
      }
      if (i < firstSize) {
        builder.addAll(first.subList(i, firstSize));
      } else if (j < secondSize) {
        builder.addAll(second.subList(j, secondSize));
      }
      return builder.build();
    }

    @Override
    public Builder removeProgramClasses(Predicate<DexProgramClass> predicate) {
      if (programClassesSorted) {
        commitPendingProgramClasses();
      }
      super.removeProgramClasses(predicate);
      assert !programClassesSorted || Comparators.isInOrder(programClasses, CLASS_COMPARATOR);
      return this;
    }

    @Override
    public Builder replaceProgramClasses(Collection<DexProgramClass> newProgramClasses) {
      assert newProgramClasses != null;
      return super.replaceProgramClasses(
          programClassesSorted
              ? ImmutableList.sortedCopyOf(CLASS_COMPARATOR, newProgramClasses)
              : newProgramClasses);
    }

    public Builder replaceProgramClassesWithDeterministicOrder(
        Collection<DexProgramClass> newProgramClasses) {
      assert newProgramClasses != null;
      assert Comparators.isInOrder(newProgramClasses, CLASS_COMPARATOR);
      programClassesSorted = true;
      return super.replaceProgramClasses(newProgramClasses);
    }

    @Override
    public Builder addClasspathClass(DexClasspathClass clazz) {
      pendingClasspathClasses.add(clazz);
      return self();
    }

    public Builder addClasspathClasses(Collection<DexClasspathClass> classes) {
      pendingClasspathClasses.addAll(classes);
      return self();
    }

    private List<DexClasspathClass> commitPendingClasspathClasses() {
      if (pendingClasspathClasses.isEmpty()) {
        return Collections.emptyList();
      }
      classpathClasses =
          ImmutableList.<DexClasspathClass>builderWithExpectedSize(
                  classpathClasses.size() + pendingClasspathClasses.size())
              .addAll(classpathClasses)
              .addAll(pendingClasspathClasses)
              .build();
      List<DexClasspathClass> committedClasspathClasses = pendingClasspathClasses;
      pendingClasspathClasses = new ArrayList<>();
      return committedClasspathClasses;
    }

    public Builder replaceClasspathClasses(Collection<DexClasspathClass> newClasspathClasses) {
      return replaceClasspathClasses(ImmutableList.copyOf(newClasspathClasses));
    }

    public Builder replaceClasspathClasses(ImmutableList<DexClasspathClass> newClasspathClasses) {
      assert newClasspathClasses != null;
      classpathClasses = newClasspathClasses;
      pendingClasspathClasses.clear();
      hasReplacedOrRemovedClasspathClasses = true;
      return self();
    }

    public Builder removeClasspathClasses(Predicate<DexClasspathClass> predicate) {
      ImmutableList.Builder<DexClasspathClass> newClasspathClasses = ImmutableList.builder();
      for (DexClasspathClass clazz : classpathClasses) {
        if (!predicate.test(clazz)) {
          newClasspathClasses.add(clazz);
        }
      }
      for (DexClasspathClass clazz : pendingClasspathClasses) {
        if (!predicate.test(clazz)) {
          newClasspathClasses.add(clazz);
        }
      }
      return replaceClasspathClasses(newClasspathClasses.build());
    }

    public Builder replaceLibraryClasses(Collection<DexLibraryClass> libraryClasses) {
      ImmutableMap.Builder<DexType, DexLibraryClass> builder = ImmutableMap.builder();
      libraryClasses.forEach(clazz -> builder.put(clazz.getType(), clazz));
      return replaceLibraryClasses(builder.build());
    }

    public Builder replaceLibraryClasses(Map<DexType, DexLibraryClass> libraryClasses) {
      this.libraryClasses = libraryClasses;
      return self();
    }

    public Builder setKeepDeclarations(List<KeepDeclaration> declarations) {
      this.keepDeclarations = declarations;
      return this;
    }

    @Override
    public DirectMappedDexApplication build(Timing timing) {
      try (Timing t0 = timing.begin("Build")) {
        // Rebuild the map. This will fail if keys are not unique.
        return new DirectMappedDexApplication(
            proguardMap,
            flags,
            buildProgramAndClasspathClasses(),
            getLibraryClassesAsImmutableMap(),
            programClasses,
            programClassesSorted,
            previousApplication != null && previousApplication.hasComputedDeterministicOrder(),
            classpathClasses,
            ImmutableList.copyOf(dataResourceProviders),
            keepDeclarations,
            options);
      }
    }

    /**
     * Builds the mapping from {@link DexType} to {@link ProgramOrClasspathClass}, committing any
     * pending program and classpath additions or removals.
     *
     * <p>If this builder was created from an existing {@link DirectMappedDexApplication} and no
     * existing program or classpath classes have been replaced or removed, reuses the previous
     * application's map and inserts only the newly added classes. Otherwise, rebuilds the map from
     * scratch.
     */
    private IdentityHashMap<DexType, ProgramOrClasspathClass> buildProgramAndClasspathClasses() {
      List<DexProgramClass> committedProgramClasses = commitPendingProgramClasses();
      List<DexClasspathClass> committedClasspathClasses = commitPendingClasspathClasses();
      if (previousApplication != null
          && !hasReplacedOrRemovedProgramClasses
          && !hasReplacedOrRemovedClasspathClasses
          && pendingClasspathRemovalIfPresent.isEmpty()) {
        IdentityHashMap<DexType, ProgramOrClasspathClass> programAndClasspathClasses =
            previousApplication.programOrClasspathClasses;
        addAll(programAndClasspathClasses, committedClasspathClasses);
        addAll(programAndClasspathClasses, committedProgramClasses);
        return programAndClasspathClasses;
      }
      IdentityHashMap<DexType, ProgramOrClasspathClass> programAndClasspathClasses =
          new IdentityHashMap<>(programClasses.size() + classpathClasses.size());
      // Note: writing classes in reverse priority order, so a duplicate will be correctly
      // ordered.
      // There should not be duplicates between program and classpath and that is asserted in
      // the addAll subroutine.
      if (addAll(programAndClasspathClasses, classpathClasses)) {
        ImmutableList.Builder<DexClasspathClass> builder = ImmutableList.builder();
        for (DexClasspathClass classpathClass : classpathClasses) {
          if (!pendingClasspathRemovalIfPresent.contains(classpathClass.getType())) {
            builder.add(classpathClass);
          }
        }
        classpathClasses = builder.build();
      }
      addAll(programAndClasspathClasses, programClasses);
      return programAndClasspathClasses;
    }

    private <T extends ProgramOrClasspathClass> boolean addAll(
        Map<DexType, ProgramOrClasspathClass> allClasses, Iterable<T> toAdd) {
      boolean seenRemoved = false;
      for (T clazz : toAdd) {
        if (clazz.isProgramClass() || !pendingClasspathRemovalIfPresent.contains(clazz.getType())) {
          ProgramOrClasspathClass old = allClasses.put(clazz.getType(), clazz);
          assert old == null : "Class " + old.getType().toString() + " was already present.";
        } else {
          seenRemoved = true;
        }
      }
      return seenRemoved;
    }

    private ImmutableMap<DexType, DexLibraryClass> getLibraryClassesAsImmutableMap() {
      if (libraryClasses instanceof ImmutableMap) {
        return (ImmutableMap<DexType, DexLibraryClass>) libraryClasses;
      } else {
        return ImmutableMap.copyOf(libraryClasses);
      }
    }
  }
}
