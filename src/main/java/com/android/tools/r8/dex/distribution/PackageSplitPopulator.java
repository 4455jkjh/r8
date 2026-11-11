// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import static com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic.Factory.createStartupClassesNonStartupFractionDiagnostic;
import static com.android.tools.r8.errors.StartupClassesOverflowDiagnostic.Factory.createStartupClassesOverflowDiagnostic;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.profile.startup.distribution.MultiStartupDexDistributor;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.threads.BucketProcessor;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Distributes the given classes over the files in package order.
 *
 * <p>The populator avoids package splits. Big packages are split into subpackages if their size
 * exceeds 20% of the dex file. This populator also avoids filling files completely to cater for
 * future growth.
 *
 * <p>The populator cycles through the files until all classes have been successfully placed and
 * adds new files to the passed in map if it can't fit in the existing files.
 */
public class PackageSplitPopulator {

  private static int comparePackageAndClassName(
      String originalA, int indexA, String originalB, int indexB) {
    if (indexA == indexB) {
      return originalA.compareTo(originalB);
    }
    int minPrefix = Math.min(indexA, indexB);
    for (int i = 0; i < minPrefix; i++) {
      char ca = originalA.charAt(i);
      char cb = originalB.charAt(i);
      if (ca != cb) {
        return ca - cb;
      }
    }
    return indexA - indexB;
  }

  private static final class ClassDistributionEntry implements Comparable<ClassDistributionEntry> {
    final DexProgramClass clazz;
    final String originalName;
    final int packageEndIndex;
    private PrecollectedClassItems precollected;

    ClassDistributionEntry(
        DexProgramClass clazz, String originalName, PrecollectedClassItems precollected) {
      this.clazz = clazz;
      this.originalName = originalName;
      this.packageEndIndex = originalName.lastIndexOf('.');
      this.precollected = precollected;
    }

    PrecollectedClassItems getPrecollected() {
      return precollected;
    }

    void clearPrecollected() {
      precollected = null;
    }

    @Override
    public int compareTo(ClassDistributionEntry other) {
      return comparePackageAndClassName(
          originalName, packageEndIndex, other.originalName, other.packageEndIndex);
    }
  }

  private static final class PartitionedEntries {
    private static final PartitionedEntries EMPTY =
        new PartitionedEntries(Collections.emptyList(), Collections.emptyList());

    final List<ClassDistributionEntry> startupEntries;
    final List<ClassDistributionEntry> nonStartupEntries;

    PartitionedEntries(
        List<ClassDistributionEntry> startupEntries,
        List<ClassDistributionEntry> nonStartupEntries) {
      this.startupEntries = startupEntries;
      this.nonStartupEntries = nonStartupEntries;
    }

    static PartitionedEntries merge(PartitionedEntries left, PartitionedEntries right) {
      return new PartitionedEntries(
          mergeSortedLists(left.startupEntries, right.startupEntries),
          mergeSortedLists(left.nonStartupEntries, right.nonStartupEntries));
    }
  }

  /**
   * Android suggests com.company.product for package names, so the components will be at level 4
   */
  private static final int MINIMUM_PREFIX_LENGTH = 4;

  private static final int MAXIMUM_PREFIX_LENGTH = 7;

  /**
   * We allow 1/MIN_FILL_FACTOR of a file to remain empty when moving to the next file, i.e., a
   * rollback with less than 1/MAX_FILL_FACTOR of the total classes in a file will move to the next
   * file.
   */
  private static final int MIN_FILL_FACTOR = 5;

  private final AppView<?> appView;
  private final Collection<DexProgramClass> inputClasses;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private final VirtualFileCycler cycler;
  private final StartupProfile startupProfile;

  public PackageSplitPopulator(
      List<VirtualFile> files,
      List<VirtualFile> filesForDistribution,
      AppView<?> appView,
      Collection<DexProgramClass> classes,
      StartupProfile startupProfile,
      IntBox nextFileId) {
    this.appView = appView;
    this.inputClasses = classes;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.cycler = new VirtualFileCycler(files, filesForDistribution, appView, nextFileId);
    this.startupProfile = startupProfile;
  }

  private static boolean coveredByPrefix(
      String originalName, String currentPrefix, int packageEndIndex) {
    if (currentPrefix == null) {
      return false;
    }
    int prefixLen = currentPrefix.length();
    if (currentPrefix.endsWith(".*")) {
      return originalName.regionMatches(0, currentPrefix, 0, prefixLen - 2);
    }
    return packageEndIndex == prefixLen && originalName.startsWith(currentPrefix);
  }

  private static String computeOriginalName(
      DexProgramClass clazz, GraphLens graphLens, ClassNameMapper proguardMap) {
    DexType originalType = graphLens.getOriginalType(clazz.getType());
    return DescriptorUtils.descriptorToJavaType(originalType.toDescriptorString(), proguardMap);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    PartitionedEntries partitioned = buildEntriesInParallel(executorService);
    distributeStartupClasses(partitioned.startupEntries);
    distributeNonStartupClasses(partitioned.nonStartupEntries);
    DexDistributionRefinement.run(appView, cycler, executorService, timing);
  }

  private PartitionedEntries buildEntriesInParallel(ExecutorService executorService)
      throws ExecutionException {
    boolean precollect = PrecollectedClassItems.canPrecollect(appView);
    LensCodeRewriterUtils rewriter = precollect ? new LensCodeRewriterUtils(appView, true) : null;
    return new BucketProcessor<DexProgramClass, PartitionedEntries>() {
      @Override
      protected PartitionedEntries emptyResult() {
        return PartitionedEntries.EMPTY;
      }

      @Override
      protected PartitionedEntries processBucket(List<DexProgramClass> bucket) {
        return buildAndSortBucket(bucket, precollect, rewriter);
      }

      @Override
      protected PartitionedEntries merge(PartitionedEntries left, PartitionedEntries right) {
        return PartitionedEntries.merge(left, right);
      }
    }.process(inputClasses, options, executorService);
  }

  private PartitionedEntries buildAndSortBucket(
      List<DexProgramClass> bucket, boolean precollect, LensCodeRewriterUtils rewriter) {
    PrecollectedClassItems.WorkerCollector collector =
        precollect ? new PrecollectedClassItems.WorkerCollector(appView, rewriter) : null;
    boolean hasStartupProfile = startupProfile != null && !startupProfile.isEmpty();
    GraphLens graphLens = appView.graphLens();
    ClassNameMapper proguardMap = appView.appInfo().app().getProguardMap();
    List<ClassDistributionEntry> nonStartup = new ArrayList<>(bucket.size());
    List<ClassDistributionEntry> startup = new ArrayList<>();
    for (DexProgramClass clazz : bucket) {
      String originalName = computeOriginalName(clazz, graphLens, proguardMap);
      PrecollectedClassItems precollected = collector != null ? collector.collect(clazz) : null;
      ClassDistributionEntry entry = new ClassDistributionEntry(clazz, originalName, precollected);
      if (hasStartupProfile && startupProfile.isStartupClass(clazz.getType())) {
        startup.add(entry);
      } else {
        nonStartup.add(entry);
      }
    }
    startup.sort(null);
    nonStartup.sort(null);
    return new PartitionedEntries(startup, nonStartup);
  }

  private static List<ClassDistributionEntry> mergeSortedLists(
      List<ClassDistributionEntry> left, List<ClassDistributionEntry> right) {
    if (left.isEmpty()) {
      return right;
    }
    if (right.isEmpty()) {
      return left;
    }
    int leftSize = left.size();
    int rightSize = right.size();
    List<ClassDistributionEntry> merged = new ArrayList<>(leftSize + rightSize);
    int i = 0;
    int j = 0;
    while (i < leftSize && j < rightSize) {
      ClassDistributionEntry a = left.get(i);
      ClassDistributionEntry b = right.get(j);
      if (a.compareTo(b) <= 0) {
        merged.add(a);
        i++;
      } else {
        merged.add(b);
        j++;
      }
    }
    while (i < leftSize) {
      merged.add(left.get(i++));
    }
    while (j < rightSize) {
      merged.add(right.get(j++));
    }
    return merged;
  }

  private void distributeStartupClasses(List<ClassDistributionEntry> startupEntries) {
    if (startupEntries.isEmpty()) {
      return;
    }

    assert options.getStartupOptions().hasStartupProfileProviders();

    List<DexProgramClass> startupClasses = ListUtils.map(startupEntries, e -> e.clazz);

    // In practice, all startup classes should fit in a single dex file, so optimistically try to
    // commit the startup classes using a single transaction.
    VirtualFile virtualFile = cycler.next();
    for (ClassDistributionEntry entry : startupEntries) {
      virtualFile.addClass(entry.clazz, entry.getPrecollected());
    }

    boolean isSingleStartupDexFile = hasSpaceForTransaction(virtualFile, options);
    if (isSingleStartupDexFile) {
      virtualFile.commitTransaction();
      virtualFile.setStartupProfile(startupProfile);
    } else {
      virtualFile.abortTransaction();

      // If the above failed, then apply the selected multi startup dex distribution strategy.
      MultiStartupDexDistributor distributor =
          MultiStartupDexDistributor.get(options, startupProfile);
      distributor.distribute(appView, startupClasses, virtualFile, cycler);
      cycler.getFilesForDistribution().forEach(vf -> vf.setStartupProfile(startupProfile));

      options.reporter.warning(
          createStartupClassesOverflowDiagnostic(cycler.getFilesForDistribution().size()));
    }
    for (ClassDistributionEntry entry : startupEntries) {
      entry.clearPrecollected();
    }

    options.reporter.info(
        createStartupClassesNonStartupFractionDiagnostic(startupClasses, startupProfile));

    if (options.getStartupOptions().isMinimalStartupDexEnabled()) {
      // Minimal startup dex only applies to the case where there is a single startup DEX file.
      // When there are multiple startup DEX files, we allow filling up the last startup DEX file
      // with non-startup classes.
      VirtualFile lastFileForDistribution = ListUtils.last(cycler.getFilesForDistribution());
      cycler.clearFilesForDistribution();
      if (!isSingleStartupDexFile
          && !options.getStartupOptions().forceMinimalStartupForMultipleStartupDexFiles) {
        cycler.addFileForDistribution(lastFileForDistribution);
      }
    } else {
      cycler.restart();
    }
  }

  private void distributeNonStartupClasses(List<ClassDistributionEntry> entries) {
    List<ClassDistributionEntry> nonPackageEntries = addPackageEntries(entries);
    addNonPackageEntries(cycler, nonPackageEntries);
  }

  @SuppressWarnings("ReferenceEquality")
  private List<ClassDistributionEntry> addPackageEntries(List<ClassDistributionEntry> entries) {
    int prefixLength = MINIMUM_PREFIX_LENGTH;
    int transactionStartIndex = 0;
    String currentPrefix = null;
    Object2IntMap<String> packageAssignments = new Object2IntOpenHashMap<>();
    VirtualFile current = cycler.ensureFile().next();
    List<ClassDistributionEntry> uncommittedEntriesInFile = new ArrayList<>();
    List<ClassDistributionEntry> nonPackageEntries = new ArrayList<>();
    for (int classIndex = 0; classIndex < entries.size(); classIndex++) {
      ClassDistributionEntry entry = entries.get(classIndex);
      DexProgramClass clazz = entry.clazz;
      String originalName = entry.originalName;
      if (!coveredByPrefix(originalName, currentPrefix, entry.packageEndIndex)) {
        if (currentPrefix != null) {
          commitAndClearPrecollected(current, uncommittedEntriesInFile);
          assert verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);
          // Reset the cycler to again iterate over all files, starting with the current one.
          cycler.restart();
          // Try to reduce the prefix length if possible. Only do this on a successful commit.
          prefixLength = MINIMUM_PREFIX_LENGTH - 1;
        }
        String newPrefix;
        // Also, we need to avoid new prefixes that are a prefix of previously used prefixes, as
        // otherwise we might generate an overlap that will trigger problems when reusing the
        // package mapping generated here. For example, if an existing map contained
        //   com.android.foo.*
        // but we now try to place some new subpackage
        //   com.android.bar.*,
        // we locally could use
        //   com.android.*.
        // However, when writing out the final package map, we get overlapping patterns
        // com.android.* and com.android.foo.*.
        do {
          newPrefix = extractPrefixToken(++prefixLength, originalName, false);
        } while (currentPrefix != null && currentPrefix.startsWith(newPrefix));
        // Don't set the current prefix if we did not extract one.
        if (!newPrefix.isEmpty()) {
          currentPrefix = extractPrefixToken(prefixLength, originalName, true);
        }
        transactionStartIndex = classIndex;
      }

      if (currentPrefix == null) {
        assert clazz.superType != null;
        // We don't have a package, add this to a list of classes that we will add last.
        assert current.getTransaction().isEmpty();
        nonPackageEntries.add(entry);
        continue;
      }

      assert clazz.superType != null || clazz.type == dexItemFactory.objectType;
      current.addClass(clazz, entry.getPrecollected());
      uncommittedEntriesInFile.add(entry);

      if (hasSpaceForTransaction(current, options)) {
        continue;
      }

      int numberOfClassesInTransaction = classIndex - transactionStartIndex + 1;
      int numberOfClassesInVirtualFileWithTransaction = current.getNumberOfClasses();

      current.abortTransaction();
      uncommittedEntriesInFile.clear();

      // We allow for a final rollback that has at most 20% of classes in it.
      // This is a somewhat random number that was empirically chosen.
      if (numberOfClassesInTransaction
              > numberOfClassesInVirtualFileWithTransaction / MIN_FILL_FACTOR
          && prefixLength < MAXIMUM_PREFIX_LENGTH) {
        // Go back to previous start index.
        classIndex = transactionStartIndex - 1;
        currentPrefix = null;
        prefixLength++;
        continue;
      }

      // Reset the state to after the last commit and cycle through files.
      // The idea is that we do not increase the number of files, so it has to fit somewhere.
      if (!cycler.hasNext()) {
        // Special case where we simply will never be able to fit the current package into
        // one dex file. This is currently the case for Strings in jumbo tests, see b/33227518.
        if (current.isEmpty()) {
          for (int j = transactionStartIndex; j <= classIndex; j++) {
            nonPackageEntries.add(entries.get(j));
          }
          transactionStartIndex = classIndex + 1;
        }
        // All files are filled up to the 20% mark.
        cycler.addFile();
      }

      // Go back to previous start index.
      classIndex = transactionStartIndex - 1;
      current = cycler.next();
      currentPrefix = null;
      prefixLength = MINIMUM_PREFIX_LENGTH;
    }

    commitAndClearPrecollected(current, uncommittedEntriesInFile);
    assert currentPrefix == null
        || verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);

    return nonPackageEntries;
  }

  private static void commitAndClearPrecollected(
      VirtualFile file, List<ClassDistributionEntry> uncommittedEntries) {
    file.commitTransaction();
    for (ClassDistributionEntry committed : uncommittedEntries) {
      committed.clearPrecollected();
    }
    uncommittedEntries.clear();
  }

  private static String extractPrefixToken(int prefixLength, String className, boolean addStar) {
    int index = 0;
    int lastIndex = 0;
    int segmentCount = 0;
    while (lastIndex != -1 && segmentCount++ < prefixLength) {
      index = lastIndex;
      lastIndex = className.indexOf('.', index + 1);
    }
    String prefix = className.substring(0, index);
    if (addStar && segmentCount >= prefixLength) {
      // Full match, add a * to also match sub-packages.
      prefix += ".*";
    }
    return prefix;
  }

  private boolean verifyPackageToVirtualFileAssignment(
      Object2IntMap<String> packageAssignments, String packageName, VirtualFile virtualFile) {
    assert !packageAssignments.containsKey(packageName);
    packageAssignments.put(packageName, virtualFile.getId());
    return true;
  }

  private boolean hasSpaceForTransaction(VirtualFile current, InternalOptions options) {
    return !isFullEnough(current, options);
  }

  private boolean isFullEnough(VirtualFile current, InternalOptions options) {
    if (options.testing.limitNumberOfClassesPerDex > 0
        && current.getNumberOfClasses() > options.testing.limitNumberOfClassesPerDex) {
      return true;
    }
    int maxEntries = VirtualFile.MAX_ENTRIES;
    int maxFieldEntries = current.getMaxNumberOfFields();
    int maxTypeEntries = current.getMaxNumberOfTypes();
    if (options.testing.enableClassToDexDistributionRefinement(options, current)
        && options.testing.classToDexDistributionRefinementLegRoomPercentage > 0) {
      // Leave a bit of room for the refinement to be more effective.
      double legRoomFraction =
          options.testing.classToDexDistributionRefinementLegRoomPercentage / 100.0;
      maxEntries -= (int) (maxEntries * legRoomFraction);
      maxFieldEntries -= (int) (maxFieldEntries * legRoomFraction);
      maxTypeEntries -= (int) (maxTypeEntries * legRoomFraction);
    }
    return current.isFull(maxEntries, maxFieldEntries, maxTypeEntries);
  }

  private void addNonPackageEntries(
      VirtualFileCycler cycler, List<ClassDistributionEntry> nonPackageEntries) {
    if (nonPackageEntries.isEmpty()) {
      return;
    }
    cycler.restart();
    VirtualFile current = getVirtualFile(cycler);
    for (ClassDistributionEntry entry : nonPackageEntries) {
      DexProgramClass clazz = entry.clazz;
      if (isFullEnough(current, options)) {
        current = getVirtualFile(cycler);
      }
      boolean wasEmpty = current.isEmpty();
      current.addClass(clazz, entry.getPrecollected());
      while (isFullEnough(current, options)) {
        if (wasEmpty) {
          if (current.isFull()) {
            throw new InternalCompilerError(
                "Class " + clazz.toString() + " does not fit into a single dex file.");
          }
          break;
        }
        current.abortTransaction();
        current = getVirtualFile(cycler);
        wasEmpty = current.isEmpty();
        current.addClass(clazz, entry.getPrecollected());
      }
      current.commitTransaction();
      entry.clearPrecollected();
    }
  }

  @SuppressWarnings("AssignmentExpression")
  private VirtualFile getVirtualFile(VirtualFileCycler cycler) {
    VirtualFile current = null;
    while (cycler.hasNext() && isFullEnough(current = cycler.next(), options)) {}
    if (current == null || isFullEnough(current, options)) {
      current = cycler.addFile();
    }
    return current;
  }
}
