// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.VirtualFile.VirtualFileIndexedItemCollection;
import com.android.tools.r8.dex.distribution.ExpandedClassItems.ExpandedItemCollector;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.ArrayUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Predicate;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Refines an initial class-to-{@link VirtualFile} assignment by moving classes across DEX files to
 * reduce duplicated indexed items (strings, types, fields, methods, protos, call sites, and method
 * handles).
 *
 * <p>Startup and non-startup DEX partitions are refined independently:
 *
 * <ol>
 *   <li>Initialization sorts each file's classes in deterministic DexType order, expands each
 *       class's transitive DEX references once into {@link ExpandedClassItems}, and computes
 *       per-file item reference counts in parallel.
 *   <li>Each refinement pass iterates over active files: evaluates beneficial target files per
 *       candidate class in parallel ({@code refCount == 1} savings in source vs {@code refCount ==
 *       0} cost in target), commits moves sequentially in deterministic class order using lazy
 *       section-capacity upper bounds, and updates reference counts in parallel across {@code
 *       (file, item-kind)} maps.
 * </ol>
 */
public class DexDistributionRefinement {

  // Number of classes processed per parallel expansion task during initialization.
  private static final int EXPAND_BUCKET_SIZE = 512;
  // Number of candidate classes evaluated per parallel target-search task when refining a file.
  private static final int TARGET_FILE_BUCKET_SIZE = 32;

  private final AppView<?> appView;
  private final InternalOptions options;
  private final VirtualFileCycler cycler;
  private final boolean enableContainerDex;

  // Per-file state across refinement passes for each VirtualFile in the partition: cached 64K
  // section limits, whether the file remains non-empty (active), its remaining movable candidate
  // classes and expanded items in deterministic DexType order, and temporary per-step move staging
  // plus lazy capacity-materialization bounds.
  private final FileRefinementState[] fileStates;

  private DexDistributionRefinement(
      AppView<?> appView,
      VirtualFileCycler cycler,
      List<VirtualFile> filesSubjectToRefinement,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    assert filesSubjectToRefinement.size() > 1;
    this.appView = appView;
    this.options = appView.options();
    this.cycler = cycler;
    this.enableContainerDex = options.enableContainerDex();
    this.fileStates =
        ArrayUtils.initialize(
            new FileRefinementState[filesSubjectToRefinement.size()],
            i -> new FileRefinementState(filesSubjectToRefinement.get(i)));
    initialize(executorService, timing);
  }

  public static void run(
      AppView<?> appView, VirtualFileCycler cycler, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (appView.testing().enableClassToDexDistributionRefinement(appView.options())) {
      runOnPartition(appView, cycler, VirtualFile::isStartup, executorService, timing);
      runOnPartition(appView, cycler, not(VirtualFile::isStartup), executorService, timing);
    }
  }

  private static void runOnPartition(
      AppView<?> appView,
      VirtualFileCycler cycler,
      Predicate<VirtualFile> predicate,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    List<VirtualFile> filesSubjectToRefinement =
        ListUtils.filter(
            cycler.getFilesForDistribution(),
            file ->
                !file.isEmpty()
                    && predicate.test(file)
                    && appView
                        .testing()
                        .enableClassToDexDistributionRefinement(appView.options(), file));
    if (filesSubjectToRefinement.size() > 1) {
      timing.begin("Dex distribution refinement");
      new DexDistributionRefinement(
              appView, cycler, filesSubjectToRefinement, executorService, timing)
          .internalRun(executorService, timing);
      timing.end();
    }
  }

  private void initialize(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Initialize");

    // Sort classes in each virtual file by DexType for deterministic candidate evaluation and
    // moves.
    int totalExpandTasks = 0;
    for (FileRefinementState state : fileStates) {
      DexProgramClass[] sorted = state.file.classes().toArray(DexProgramClass.EMPTY_ARRAY);
      Arrays.sort(sorted, Comparator.comparing(DexClass::getType));
      state.initCandidates(sorted);
      totalExpandTasks += (sorted.length + EXPAND_BUCKET_SIZE - 1) / EXPAND_BUCKET_SIZE;
    }

    // Expand each class's transitive DEX references into compact ExpandedClassItems.
    expandItemsConcurrently(executorService, totalExpandTasks);

    // Concurrently compute initial reference counters for each virtual file. Each (file, item-kind)
    // pair owns a separate Reference2IntOpenHashMap, so all tasks update disjoint maps without
    // synchronization.
    computeReferenceCountersConcurrently(executorService);
    timing.end();
  }

  private void expandItemsConcurrently(ExecutorService executorService, int totalExpandTasks)
      throws ExecutionException {
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(appView, true);
    TaskCollection<Void> taskCollection =
        new TaskCollection<>(options, executorService, totalExpandTasks);
    for (FileRefinementState state : fileStates) {
      int count = state.size;
      for (int start = 0; start < count; start += EXPAND_BUCKET_SIZE) {
        int bucketStart = start;
        int bucketEnd = Math.min(count, start + EXPAND_BUCKET_SIZE);
        taskCollection.submit(
            () -> {
              ExpandedItemCollector collector = new ExpandedItemCollector(appView);
              for (int i = bucketStart; i < bucketEnd; i++) {
                state.classItems[i] = collector.expand(state.classes[i], rewriter);
              }
            });
      }
    }
    taskCollection.await();
  }

  private void computeReferenceCountersConcurrently(ExecutorService executorService)
      throws ExecutionException {
    TaskCollection<Void> taskCollection =
        new TaskCollection<>(options, executorService, fileStates.length * 7);
    for (FileRefinementState state : fileStates) {
      VirtualFileIndexedItemCollection items = state.file.indexedItems;
      submitCountReferencesTask(taskCollection, items.callSites, state, e -> e.callSites);
      submitCountReferencesTask(taskCollection, items.fields, state, e -> e.fields);
      submitCountReferencesTask(taskCollection, items.methods, state, e -> e.methods);
      submitCountReferencesTask(taskCollection, items.methodHandles, state, e -> e.methodHandles);
      submitCountReferencesTask(taskCollection, items.protos, state, e -> e.protos);
      submitCountReferencesTask(taskCollection, items.strings, state, e -> e.strings);
      submitCountReferencesTask(taskCollection, items.types, state, e -> e.types);
    }
    taskCollection.await();
  }

  private <T> void submitCountReferencesTask(
      TaskCollection<Void> tasks,
      Reference2IntOpenHashMap<T> map,
      FileRefinementState state,
      Function<ExpandedClassItems, T[]> itemsGetter)
      throws ExecutionException {
    tasks.submit(
        () -> {
          for (ExpandedClassItems items : state.classItems) {
            incrementReferenceCounters(map, itemsGetter.apply(items));
          }
        });
  }

  private static <T> void incrementReferenceCounters(Reference2IntOpenHashMap<T> map, T[] array) {
    for (T item : array) {
      map.addTo(item, 1);
    }
  }

  private static <T> void decrementReferenceCounters(Reference2IntOpenHashMap<T> map, T[] array) {
    for (T item : array) {
      int prev = map.addTo(item, -1);
      assert prev >= 1;
      if (prev == 1) {
        map.removeInt(item);
      }
    }
  }

  private void internalRun(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Run refinement.
    boolean hasEmptyFiles = false;
    for (int i = 0; i < appView.testing().classToDexDistributionRefinementPasses; i++) {
      boolean changed = false;
      timing.begin(i, pass -> "Pass " + pass);
      for (FileRefinementState state : fileStates) {
        if (!state.active) {
          continue;
        }
        if (refineFile(state, executorService, timing)) {
          changed = true;
        }
        // If the file became empty, then don't consider it for any further refinement.
        if (state.file.isEmpty()) {
          state.active = false;
          hasEmptyFiles = true;
        }
      }
      timing.end();
      if (!changed) {
        break;
      }
    }

    // Fixup empty files.
    if (hasEmptyFiles) {
      cycler.removeEmptyFilesAndRenumber();
    }
  }

  @SuppressWarnings("unchecked")
  private boolean refineFile(
      FileRefinementState sourceState, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin(sourceState, ss -> "Refine file " + ss.file.getId())) {
      int classCount = sourceState.size;
      if (classCount == 0) {
        return false;
      }

      DexProgramClass[] classes = sourceState.classes;
      ExpandedClassItems[] classItems = sourceState.classItems;

      // Phase 1: Concurrently evaluate beneficial target files for each remaining candidate class.
      PriorityQueue<TargetCandidate>[] targetQueues = new PriorityQueue[classCount];
      try (Timing t1 = timing.begin("Compute target files")) {
        int numBuckets = (classCount + TARGET_FILE_BUCKET_SIZE - 1) / TARGET_FILE_BUCKET_SIZE;
        TaskCollection<Void> taskCollection =
            new TaskCollection<>(options, executorService, numBuckets);
        for (int start = 0; start < classCount; start += TARGET_FILE_BUCKET_SIZE) {
          int bucketStart = start;
          int bucketEnd = Math.min(classCount, start + TARGET_FILE_BUCKET_SIZE);
          taskCollection.submit(
              () -> {
                Timing threadTiming = timing.createThreadTiming("Compute target files", options);
                for (int i = bucketStart; i < bucketEnd; i++) {
                  ExpandedClassItems items = classItems[i];
                  targetQueues[i] = findTargetFiles(sourceState, items);
                }
                threadTiming.end().notifyThreadTimingFinished();
              });
        }
        taskCollection.await();
      }

      // Phase 2: Commit moves sequentially in deterministic DexType order against target 64K
      // section limits, compacting unmoved candidates in place. Moved classes are intentionally not
      // added to target.classes so they are not reconsidered in subsequent passes.
      try (Timing t2 = timing.begin("Move classes to target files")) {
        for (FileRefinementState state : fileStates) {
          if (state.active && state != sourceState) {
            state.initializeMoveState();
          }
        }

        List<ExpandedClassItems> movedItems = new ArrayList<>();
        int newClassCount = 0;
        for (int i = 0; i < classCount; i++) {
          DexProgramClass clazz = classes[i];
          ExpandedClassItems items = classItems[i];
          FileRefinementState target = pollFittingTarget(targetQueues[i], items);
          if (target != null) {
            target.acceptMove(clazz, items);
            movedItems.add(items);
          } else {
            // Retain unmoved classes in the compacted candidate list for the next refinement pass.
            classes[newClassCount] = clazz;
            classItems[newClassCount] = items;
            newClassCount++;
          }
        }
        for (int i = newClassCount; i < classCount; i++) {
          classes[i] = null;
          classItems[i] = null;
        }
        sourceState.size = newClassCount;
        return applyCommittedMoves(sourceState, movedItems, executorService);
      }
    }
  }

  private static FileRefinementState pollFittingTarget(
      PriorityQueue<TargetCandidate> queue, ExpandedClassItems items) {
    if (queue == null) {
      return null;
    }
    while (!queue.isEmpty()) {
      FileRefinementState target = queue.poll().getTarget();
      if (target.canFitDuringMove(items)) {
        return target;
      }
    }
    return null;
  }

  private boolean applyCommittedMoves(
      FileRefinementState sourceState,
      List<ExpandedClassItems> movedItems,
      ExecutorService executorService)
      throws ExecutionException {
    if (movedItems.isEmpty()) {
      return false;
    }

    for (ExpandedClassItems items : movedItems) {
      sourceState.removeClass(items.owner);
    }

    // Concurrently update item reference counts across the source file (decrements) and each
    // receiving target file (increments). Each task mutates a single (file, item-kind) map. For
    // fields, methods, and types, skip the prefix already flushed during cannotFitDuringMove().
    TaskCollection<Void> tasks =
        new TaskCollection<>(options, executorService, fileStates.length * 7);
    submitDecrementReferenceCountersTasks(movedItems, tasks, sourceState.file.indexedItems);
    submitIncrementReferenceCountersTasks(sourceState, tasks);
    tasks.await();

    for (FileRefinementState target : fileStates) {
      target.clearAcceptedItems();
    }
    return true;
  }

  private static void submitDecrementReferenceCountersTasks(
      List<ExpandedClassItems> movedFromSourceItems,
      TaskCollection<Void> tasks,
      VirtualFileIndexedItemCollection sourceItems)
      throws ExecutionException {
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.callSites, movedFromSourceItems, e -> e.callSites);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.fields, movedFromSourceItems, e -> e.fields);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.methods, movedFromSourceItems, e -> e.methods);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.methodHandles, movedFromSourceItems, e -> e.methodHandles);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.protos, movedFromSourceItems, e -> e.protos);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.strings, movedFromSourceItems, e -> e.strings);
    submitDecrementReferenceCountersTask(
        tasks, sourceItems.types, movedFromSourceItems, e -> e.types);
  }

  private static <T> void submitDecrementReferenceCountersTask(
      TaskCollection<Void> tasks,
      Reference2IntOpenHashMap<T> map,
      List<ExpandedClassItems> movedItems,
      Function<ExpandedClassItems, T[]> itemsGetter)
      throws ExecutionException {
    tasks.submit(
        () -> {
          for (ExpandedClassItems movedItem : movedItems) {
            decrementReferenceCounters(map, itemsGetter.apply(movedItem));
          }
        });
  }

  private void submitIncrementReferenceCountersTasks(
      FileRefinementState sourceState, TaskCollection<Void> tasks) throws ExecutionException {
    for (FileRefinementState target : fileStates) {
      if (!target.active || target == sourceState || target.acceptedItems.isEmpty()) {
        continue;
      }
      VirtualFileIndexedItemCollection targetItems = target.file.indexedItems;
      List<ExpandedClassItems> accepted = target.acceptedItems;
      submitIncrementReferenceCountersTask(
          tasks, targetItems.callSites, accepted, 0, e -> e.callSites);
      submitIncrementReferenceCountersTask(
          tasks, targetItems.fields, accepted, target.getFieldsMaterialized(), e -> e.fields);
      submitIncrementReferenceCountersTask(
          tasks, targetItems.methods, accepted, target.getMethodsMaterialized(), e -> e.methods);
      submitIncrementReferenceCountersTask(
          tasks, targetItems.methodHandles, accepted, 0, e -> e.methodHandles);
      submitIncrementReferenceCountersTask(tasks, targetItems.protos, accepted, 0, e -> e.protos);
      submitIncrementReferenceCountersTask(tasks, targetItems.strings, accepted, 0, e -> e.strings);
      submitIncrementReferenceCountersTask(
          tasks, targetItems.types, accepted, target.getTypesMaterialized(), e -> e.types);
    }
  }

  private static <T> void submitIncrementReferenceCountersTask(
      TaskCollection<Void> tasks,
      Reference2IntOpenHashMap<T> map,
      List<ExpandedClassItems> acceptedItems,
      int startIndex,
      Function<ExpandedClassItems, T[]> itemsGetter)
      throws ExecutionException {
    int size = acceptedItems.size();
    if (startIndex < size) {
      tasks.submit(
          () -> {
            for (int i = startIndex; i < size; i++) {
              incrementReferenceCounters(map, itemsGetter.apply(acceptedItems.get(i)));
            }
          });
    }
  }

  // Finds all active target files into which a class with `items` fits and where moving it yields a
  // strict net reduction in indexed items (new items added to target < items uniquely referenced in
  // source), ordered by ascending move cost.
  private PriorityQueue<TargetCandidate> findTargetFiles(
      FileRefinementState sourceState, ExpandedClassItems items) {
    int savings = countItemsWithReferenceCountOne(items, sourceState);
    if (savings == 0) {
      return null;
    }
    // TODO(b/473427453): To improve build speed, consider if we can return null here if the
    //  estimated savings are small compared to the number of items in the class (i.e., the class
    //  already fits well in the current dex file).
    int maxAllowedCost = savings - 1;
    PriorityQueue<TargetCandidate> targetFiles = null;
    for (FileRefinementState targetState : fileStates) {
      if (!targetState.active || targetState == sourceState || targetState.cannotFit(items)) {
        continue;
      }
      int cost = countNewItemsBounded(items, targetState.file, maxAllowedCost);
      if (cost <= maxAllowedCost) {
        if (targetFiles == null) {
          targetFiles = new PriorityQueue<>();
        }
        targetFiles.add(new TargetCandidate(targetState, cost));
      }
    }
    return targetFiles;
  }

  private int countItemsWithReferenceCountOne(
      ExpandedClassItems items, FileRefinementState sourceState) {
    VirtualFileIndexedItemCollection fileItems = sourceState.file.indexedItems;
    int result = 0;
    if (!enableContainerDex) {
      // Container DEX files share a single string_ids table across all sections, so moving classes
      // between sections does not save or duplicate strings.
      result += internalCountItemsWithReferenceCountOne(fileItems.strings, items.strings);
    }
    result += internalCountItemsWithReferenceCountOne(fileItems.methods, items.methods);
    result += internalCountItemsWithReferenceCountOne(fileItems.fields, items.fields);
    result += internalCountItemsWithReferenceCountOne(fileItems.protos, items.protos);
    result += internalCountItemsWithReferenceCountOne(fileItems.types, items.types);
    result += internalCountItemsWithReferenceCountOne(fileItems.callSites, items.callSites);
    result += internalCountItemsWithReferenceCountOne(fileItems.methodHandles, items.methodHandles);
    return result;
  }

  private static <T> int internalCountItemsWithReferenceCountOne(
      Reference2IntMap<T> map, T[] array) {
    int count = 0;
    for (T item : array) {
      if (map.getInt(item) == 1) {
        count++;
      }
    }
    return count;
  }

  // Counts how many items in `items` are currently absent from `targetFile`, stopping early as soon
  // as the count exceeds `maxAllowedCost`.
  private int countNewItemsBounded(
      ExpandedClassItems items, VirtualFile targetFile, int maxAllowedCost) {
    VirtualFileIndexedItemCollection fileItems = targetFile.indexedItems;
    int cost = 0;
    if (!enableContainerDex) {
      cost = countMissingBounded(fileItems.strings, items.strings, cost, maxAllowedCost);
      if (cost > maxAllowedCost) {
        return cost;
      }
    }
    cost = countMissingBounded(fileItems.methods, items.methods, cost, maxAllowedCost);
    if (cost > maxAllowedCost) {
      return cost;
    }
    cost = countMissingBounded(fileItems.fields, items.fields, cost, maxAllowedCost);
    if (cost > maxAllowedCost) {
      return cost;
    }
    cost = countMissingBounded(fileItems.protos, items.protos, cost, maxAllowedCost);
    if (cost > maxAllowedCost) {
      return cost;
    }
    cost = countMissingBounded(fileItems.types, items.types, cost, maxAllowedCost);
    if (cost > maxAllowedCost) {
      return cost;
    }
    cost = countMissingBounded(fileItems.callSites, items.callSites, cost, maxAllowedCost);
    if (cost > maxAllowedCost) {
      return cost;
    }
    return countMissingBounded(fileItems.methodHandles, items.methodHandles, cost, maxAllowedCost);
  }

  private static <T> int countMissingBounded(
      Reference2IntMap<T> map, T[] array, int currentCost, int maxAllowedCost) {
    for (T item : array) {
      if (map.getInt(item) == 0 && ++currentCost > maxAllowedCost) {
        return currentCost;
      }
    }
    return currentCost;
  }

  private static final class TargetCandidate implements Comparable<TargetCandidate> {

    private final FileRefinementState target;
    private final int cost;

    TargetCandidate(FileRefinementState target, int cost) {
      this.target = target;
      this.cost = cost;
    }

    FileRefinementState getTarget() {
      return target;
    }

    @Override
    public int compareTo(TargetCandidate other) {
      int cmp = Integer.compare(cost, other.cost);
      // Break ties by virtual file ID for deterministic priority-queue polling.
      return cmp != 0 ? cmp : Integer.compare(target.file.getId(), other.target.file.getId());
    }
  }

  // Mutable refinement state for one VirtualFile, tracking remaining candidate classes, staged
  // incoming moves during a file's move phase, and lazy section-capacity upper bounds.
  private static final class FileRefinementState {

    final VirtualFile file;
    private final int maxFields;
    private final int maxTypes;
    boolean active = true;

    DexProgramClass[] classes;
    ExpandedClassItems[] classItems;
    int size;

    // Expanded items staged for moving into this file during Phase 2 before their reference counts
    // are flushed into file.indexedItems in applyCommittedMoves().
    final List<ExpandedClassItems> acceptedItems = new ArrayList<>();

    // Instead of inserting into target reference-count maps on every accepted move during Phase 2,
    // acceptMove increments conservative upper bounds by the full item-array lengths. Only when an
    // upper bound + incoming length could exceed the DEX 64K limit does cannotFitDuringMove flush
    // pending items into that map up to *Materialized to obtain the exact deduplicated count.
    private int fieldsUpperBound;
    private int fieldsMaterialized;
    private int methodsUpperBound;
    private int methodsMaterialized;
    private int typesUpperBound;
    private int typesMaterialized;

    FileRefinementState(VirtualFile file) {
      this.file = file;
      this.maxFields = file.getMaxNumberOfFields();
      this.maxTypes = file.getMaxNumberOfTypes();
    }

    void initCandidates(DexProgramClass[] sortedClasses) {
      this.classes = sortedClasses;
      this.classItems = new ExpandedClassItems[sortedClasses.length];
      this.size = sortedClasses.length;
    }

    void removeClass(DexProgramClass clazz) {
      file.indexedItems.classes.remove(clazz);
    }

    void initializeMoveState() {
      assert acceptedItems.isEmpty();
      fieldsUpperBound = file.indexedItems.fields.size();
      fieldsMaterialized = 0;
      methodsUpperBound = file.indexedItems.methods.size();
      methodsMaterialized = 0;
      typesUpperBound = file.indexedItems.types.size();
      typesMaterialized = 0;
    }

    void clearAcceptedItems() {
      acceptedItems.clear();
    }

    int getFieldsMaterialized() {
      return fieldsMaterialized;
    }

    int getMethodsMaterialized() {
      return methodsMaterialized;
    }

    int getTypesMaterialized() {
      return typesMaterialized;
    }

    // Read-only capacity check used concurrently during Phase 1 (`findTargetFiles`), before any
    // moves from the current step have been staged. Since `file.indexedItems` is exact and
    // unmodified in Phase 1, checks directly whether adding the items missing from
    // `file.indexedItems` would exceed this file's DEX field, method, or type limits.
    boolean cannotFit(ExpandedClassItems items) {
      assert file.getTransaction().isEmpty();
      VirtualFileIndexedItemCollection targetItems = file.indexedItems;
      return exceedsMissingLimit(
              items.fields, targetItems.fields, maxFields - targetItems.fields.size())
          || exceedsMissingLimit(
              items.methods,
              targetItems.methods,
              VirtualFile.MAX_ENTRIES - targetItems.methods.size())
          || exceedsMissingLimit(
              items.types, targetItems.types, maxTypes - targetItems.types.size());
    }

    boolean canFitDuringMove(ExpandedClassItems items) {
      return !cannotFitDuringMove(items);
    }

    // Checks whether `items` can fit in this file during Phase 2, accounting for earlier moves
    // already staged in `acceptedItems` that have not yet been flushed into `file.indexedItems`.
    //
    // Unlike `cannotFit`, which runs read-only during Phase 1 against exact `file.indexedItems`
    // maps, Phase 2 defers reference-count updates to `applyCommittedMoves()` so they can run in
    // parallel. Calling `cannotFit` directly would ignore staged moves and risk overflowing DEX
    // field/method/type limits; conversely, eagerly updating `file.indexedItems` on every accepted
    // move would serialize all map writes on the Phase 2 thread.
    //
    // Instead, this uses conservative `*UpperBound` counts (assuming every staged item is distinct)
    // for an O(1) fast path when the target has headroom, and lazily flushes pending staged items
    // for a section into `file.indexedItems` only when that upper bound could exceed the limit,
    // yielding the exact same fit decision as eager updates while keeping Phase 2 fast.
    boolean cannotFitDuringMove(ExpandedClassItems items) {
      VirtualFileIndexedItemCollection targetItems = file.indexedItems;
      if (fieldsUpperBound + items.fields.length > maxFields) {
        fieldsMaterialized =
            materializePending(
                targetItems.fields, fieldsMaterialized, acceptedItems, e -> e.fields);
        fieldsUpperBound = targetItems.fields.size();
        if (exceedsMissingLimit(items.fields, targetItems.fields, maxFields - fieldsUpperBound)) {
          return true;
        }
      }
      if (methodsUpperBound + items.methods.length > VirtualFile.MAX_ENTRIES) {
        methodsMaterialized =
            materializePending(
                targetItems.methods, methodsMaterialized, acceptedItems, e -> e.methods);
        methodsUpperBound = targetItems.methods.size();
        if (exceedsMissingLimit(
            items.methods, targetItems.methods, VirtualFile.MAX_ENTRIES - methodsUpperBound)) {
          return true;
        }
      }
      if (typesUpperBound + items.types.length > maxTypes) {
        typesMaterialized =
            materializePending(targetItems.types, typesMaterialized, acceptedItems, e -> e.types);
        typesUpperBound = targetItems.types.size();
        if (exceedsMissingLimit(items.types, targetItems.types, maxTypes - typesUpperBound)) {
          return true;
        }
      }
      return false;
    }

    void acceptMove(DexProgramClass clazz, ExpandedClassItems items) {
      boolean added = file.indexedItems.classes.add(clazz);
      assert added;
      acceptedItems.add(items);
      fieldsUpperBound += items.fields.length;
      methodsUpperBound += items.methods.length;
      typesUpperBound += items.types.length;
    }

    // Returns true if adding items in `array` not yet in `map` exceeds `missingLimit`.
    private static <T> boolean exceedsMissingLimit(
        T[] array, Reference2IntMap<T> map, int missingLimit) {
      if (missingLimit < 0) {
        return true;
      }
      if (array.length <= missingLimit) {
        return false;
      }
      int missing = 0;
      for (T item : array) {
        if (map.getInt(item) == 0 && ++missing > missingLimit) {
          return true;
        }
      }
      return false;
    }

    private static <T> int materializePending(
        Reference2IntOpenHashMap<T> map,
        int materialized,
        List<ExpandedClassItems> acceptedItems,
        Function<ExpandedClassItems, T[]> itemsGetter) {
      while (materialized < acceptedItems.size()) {
        incrementReferenceCounters(map, itemsGetter.apply(acceptedItems.get(materialized)));
        materialized++;
      }
      return materialized;
    }
  }
}
