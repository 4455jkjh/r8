// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.dex.FileWriter.Layout;
import com.android.tools.r8.dex.FileWriter.MixedSectionOffsets;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexWritableCode;
import com.android.tools.r8.graph.DexWritableCode.DexWritableCacheKey;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.utils.LebUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class DefaultMixedSectionLayoutStrategy extends MixedSectionLayoutStrategy {

  final AppView<?> appView;
  final MixedSectionOffsets mixedSectionOffsets;
  final VirtualFile virtualFile;
  final Layout layout;

  public DefaultMixedSectionLayoutStrategy(
      AppView<?> appView,
      MixedSectionOffsets mixedSectionOffsets,
      VirtualFile virtualFile,
      Layout layout) {
    this.appView = appView;
    this.mixedSectionOffsets = mixedSectionOffsets;
    this.virtualFile = virtualFile;
    this.layout = layout;
  }

  @Override
  public Collection<DexAnnotation> getAnnotationLayout() {
    return mixedSectionOffsets.getAnnotations();
  }

  @Override
  public Collection<DexAnnotationDirectory> getAnnotationDirectoryLayout() {
    return mixedSectionOffsets.getAnnotationDirectories();
  }

  @Override
  public Collection<DexAnnotationSet> getAnnotationSetLayout() {
    return mixedSectionOffsets.getAnnotationSets();
  }

  @Override
  public Collection<ParameterAnnotationsList> getAnnotationSetRefListLayout() {
    return mixedSectionOffsets.getAnnotationSetRefLists();
  }

  @Override
  public Collection<DexProgramClass> getClassDataLayout() {
    return mixedSectionOffsets.getClassesWithData();
  }

  @Override
  public Collection<ProgramMethod> getCodeLayout() {
    return getCodeLayoutForClasses(mixedSectionOffsets.getClassesWithData());
  }

  private static class DeduplicatedCodeCounts {
    private Object2IntOpenHashMap<DexWritableCacheKey> counts;
    private final AppView<?> appView;
    private long totalCodeSize = 0;

    private DeduplicatedCodeCounts(AppView<?> appView) {
      this.appView = appView;
    }

    void recordCode(DexWritableCode code, ProgramMethod method, int alignedSize) {
      if (!code.canBeCanonicalized(appView.options()) || addCode(code, method)) {
        totalCodeSize += alignedSize;
      }
    }

    private boolean addCode(DexWritableCode code, ProgramMethod method) {
      assert appView.options().canUseCanonicalizedCodeObjects();
      if (counts == null) {
        counts = new Object2IntOpenHashMap<>();
      }
      DexWritableCacheKey cacheKey = code.getCacheLookupKey(method, appView.dexItemFactory());
      int previous = counts.addTo(cacheKey, 1);
      return previous == 0;
    }

    int getCount(ProgramMethod method) {
      DexWritableCode code = method.getDefinition().getCode().asDexWritableCode();
      if (!code.canBeCanonicalized(appView.options())) {
        return 1;
      }
      DexWritableCacheKey cacheLookupKey = code.getCacheLookupKey(method, appView.dexItemFactory());
      assert counts.containsKey(cacheLookupKey);
      return counts.getInt(cacheLookupKey);
    }

    long getTotalCodeSize() {
      return totalCodeSize;
    }
  }

  final Collection<ProgramMethod> getCodeLayoutForClasses(Collection<DexProgramClass> classes) {
    ObjectToOffsetMapping mapping = virtualFile.getObjectMapping();
    ProgramMethodMap<String> codeToDexSortingKeyMap = ProgramMethodMap.create();
    Reference2IntMap<DexEncodedMethod> codeToAlignedSizeMap = new Reference2IntOpenHashMap<>();
    List<ProgramMethod> codesSorted = new ArrayList<>();
    DeduplicatedCodeCounts codeCounts = new DeduplicatedCodeCounts(appView);
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            DexWritableCode code = method.getDefinition().getDexWritableCodeOrNull();
            assert code != null || method.getDefinition().shouldNotHaveCode();
            if (code != null) {
              int alignedSize =
                  FileWriter.alignSize(4, FileWriter.sizeOfCodeItem(code, appView, mapping));
              codeToAlignedSizeMap.put(method.getDefinition(), alignedSize);
              codeCounts.recordCode(code, method, alignedSize);
              codesSorted.add(method);
              codeToDexSortingKeyMap.put(
                  method, getKeyForDexCodeSorting(method, appView.app().getProguardMap()));
            }
          });
    }
    Comparator<ProgramMethod> defaultCodeSorting =
        Comparator.comparing(codeToDexSortingKeyMap::get);
    int codesOffset = layout.getCodesOffset();
    if (appView.options().getTestingOptions().enableCodeItemSizePerReferenceLayout
        && LebUtils.crossesUleb128SizeBoundary(
            codesOffset, codesOffset + codeCounts.getTotalCodeSize())) {
      // Sort by code size per reference (aligned size / number of methods sharing the code) so
      // that more code_off entries in the class data fit in fewer ULEB128 bytes.
      Reference2IntMap<DexEncodedMethod> codeToCountMap =
          computeCodeCounts(codesSorted, codeCounts);
      Comparator<ProgramMethod> codeSizePerReference =
          (m1, m2) ->
              Long.compare(
                  (long) codeToAlignedSizeMap.getInt(m1.getDefinition())
                      * codeToCountMap.getInt(m2.getDefinition()),
                  (long) codeToAlignedSizeMap.getInt(m2.getDefinition())
                      * codeToCountMap.getInt(m1.getDefinition()));
      codesSorted.sort(
          codeSizePerReference
              .thenComparingInt(method -> codeToCountMap.getInt(method.getDefinition()))
              .thenComparing(defaultCodeSorting));
    } else if (appView.options().canUseCanonicalizedCodeObjects()) {
      Reference2IntMap<DexEncodedMethod> codeToCountMap =
          computeCodeCounts(codesSorted, codeCounts);
      codesSorted.sort(
          Comparator.<ProgramMethod>comparingInt(
                  method -> codeToCountMap.getInt(method.getDefinition()))
              .thenComparing(defaultCodeSorting));
    } else {
      codesSorted.sort(defaultCodeSorting);
    }
    return codesSorted;
  }

  private static Reference2IntMap<DexEncodedMethod> computeCodeCounts(
      List<ProgramMethod> methods, DeduplicatedCodeCounts codeCounts) {
    Reference2IntMap<DexEncodedMethod> codeToCountMap =
        new Reference2IntOpenHashMap<>(methods.size());
    for (ProgramMethod method : methods) {
      codeToCountMap.put(method.getDefinition(), codeCounts.getCount(method));
    }
    return codeToCountMap;
  }

  private static String getKeyForDexCodeSorting(ProgramMethod method, ClassNameMapper proguardMap) {
    // TODO(b/173999869): Could this instead compute sorting using dex items?
    Signature signature;
    String originalClassName;
    if (proguardMap != null) {
      signature = proguardMap.originalSignatureOf(method.getReference());
      originalClassName = proguardMap.originalNameOf(method.getHolderType());
    } else {
      signature = MethodSignature.fromDexMethod(method.getReference());
      originalClassName = method.getHolderType().toSourceString();
    }
    return originalClassName + signature;
  }

  @Override
  public Collection<DexEncodedArray> getEncodedArrayLayout() {
    return mixedSectionOffsets.getEncodedArrays();
  }

  @Override
  public Collection<DexString> getStringDataLayout() {
    return mixedSectionOffsets.getStringData();
  }

  @Override
  public Collection<DexTypeList> getTypeListLayout() {
    return mixedSectionOffsets.getTypeLists();
  }
}
