// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.dex.FileWriter.MixedSectionOffsets;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.collections.LinkedProgramMethodSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class StartupMixedSectionLayoutStrategy extends DefaultMixedSectionLayoutStrategy {

  private final StartupOrder startupOrderForWriting;

  private final LinkedHashSet<DexAnnotation> annotationLayout;
  private final LinkedHashSet<DexAnnotationDirectory> annotationDirectoryLayout;
  private final LinkedHashSet<DexAnnotationSet> annotationSetLayout;
  private final LinkedHashSet<ParameterAnnotationsList> annotationSetRefListLayout;
  private final LinkedHashSet<DexProgramClass> classDataLayout;
  private final LinkedProgramMethodSet codeLayout;
  private final LinkedHashSet<DexEncodedArray> encodedArrayLayout;
  private final LinkedHashSet<DexString> stringDataLayout;
  private final LinkedHashSet<DexTypeList> typeListLayout;

  public StartupMixedSectionLayoutStrategy(
      AppView<?> appView,
      MixedSectionOffsets mixedSectionOffsets,
      StartupOrder startupOrderForWriting,
      VirtualFile virtualFile) {
    super(appView, mixedSectionOffsets);
    this.startupOrderForWriting = startupOrderForWriting;

    // Initialize startup layouts.
    this.annotationLayout = new LinkedHashSet<>(mixedSectionOffsets.getAnnotations().size());
    this.annotationDirectoryLayout =
        new LinkedHashSet<>(mixedSectionOffsets.getAnnotationDirectories().size());
    this.annotationSetLayout = new LinkedHashSet<>(mixedSectionOffsets.getAnnotationSets().size());
    this.annotationSetRefListLayout =
        new LinkedHashSet<>(mixedSectionOffsets.getAnnotationSetRefLists().size());
    this.classDataLayout = new LinkedHashSet<>(mixedSectionOffsets.getClassesWithData().size());
    this.codeLayout = ProgramMethodSet.createLinked(mixedSectionOffsets.getCodes().size());
    this.encodedArrayLayout = new LinkedHashSet<>(mixedSectionOffsets.getEncodedArrays().size());
    this.stringDataLayout = new LinkedHashSet<>(mixedSectionOffsets.getStringData().size());
    this.typeListLayout = new LinkedHashSet<>(mixedSectionOffsets.getTypeLists().size());

    // Add startup items to startup layouts.
    collectStartupItems(virtualFile);
  }

  private void collectStartupItems(VirtualFile virtualFile) {
    Map<DexType, DexProgramClass> virtualFileDefinitions =
        MapUtils.newIdentityHashMap(
            builder ->
                virtualFile.classes().forEach(clazz -> builder.accept(clazz.getType(), clazz)),
            virtualFile.classes().size());
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(appView, true);
    StartupIndexedItemCollection indexedItemCollection = new StartupIndexedItemCollection();
    for (DexType startupClass : startupOrderForWriting.getClasses()) {
      DexProgramClass definition = virtualFileDefinitions.get(startupClass);
      if (definition != null) {
        definition.collectIndexedItems(appView, indexedItemCollection, rewriter);
      }
    }
  }

  private static <T> Collection<T> amendStartupLayout(
      Collection<T> startupLayout, Collection<T> defaultLayout) {
    startupLayout.addAll(defaultLayout);
    return startupLayout;
  }

  @Override
  public Collection<DexAnnotation> getAnnotationLayout() {
    return amendStartupLayout(annotationLayout, super.getAnnotationLayout());
  }

  @Override
  public Collection<DexAnnotationDirectory> getAnnotationDirectoryLayout() {
    return amendStartupLayout(annotationDirectoryLayout, super.getAnnotationDirectoryLayout());
  }

  @Override
  public Collection<DexAnnotationSet> getAnnotationSetLayout() {
    return amendStartupLayout(annotationSetLayout, super.getAnnotationSetLayout());
  }

  @Override
  public Collection<ParameterAnnotationsList> getAnnotationSetRefListLayout() {
    return amendStartupLayout(annotationSetRefListLayout, super.getAnnotationSetRefListLayout());
  }

  @Override
  public Collection<DexProgramClass> getClassDataLayout() {
    return amendStartupLayout(classDataLayout, super.getClassDataLayout());
  }

  @Override
  public Collection<ProgramMethod> getCodeLayout() {
    Set<DexProgramClass> nonStartupClasses =
        new LinkedHashSet<>(mixedSectionOffsets.getClassesWithData());
    nonStartupClasses.removeIf(clazz -> startupOrderForWriting.contains(clazz.getType()));
    return amendStartupLayout(codeLayout, super.getCodeLayoutForClasses(nonStartupClasses));
  }

  @Override
  public Collection<DexEncodedArray> getEncodedArrayLayout() {
    return amendStartupLayout(encodedArrayLayout, super.getEncodedArrayLayout());
  }

  @Override
  public Collection<DexString> getStringDataLayout() {
    return amendStartupLayout(stringDataLayout, super.getStringDataLayout());
  }

  @Override
  public Collection<DexTypeList> getTypeListLayout() {
    return amendStartupLayout(typeListLayout, super.getTypeListLayout());
  }

  private class StartupIndexedItemCollection implements IndexedItemCollection {

    @Override
    public boolean addClass(DexProgramClass clazz) {
      classDataLayout.add(clazz);
      typeListLayout.add(clazz.getInterfaces());
      clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, codeLayout::add);
      DexAnnotationDirectory annotationDirectory =
          mixedSectionOffsets.getAnnotationDirectoryForClass(clazz);
      if (annotationDirectory != null) {
        annotationDirectoryLayout.add(annotationDirectory);
        annotationDirectory.visitAnnotations(
            annotationLayout::add, annotationSetLayout::add, annotationSetRefListLayout::add);
      }
      DexEncodedArray staticFieldValues = mixedSectionOffsets.getStaticFieldValuesForClass(clazz);
      if (staticFieldValues != null) {
        encodedArrayLayout.add(staticFieldValues);
      }
      return true;
    }

    @Override
    public boolean addField(DexField field) {
      return true;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return true;
    }

    @Override
    public boolean addString(DexString string) {
      return stringDataLayout.add(string);
    }

    @Override
    public boolean addProto(DexProto proto) {
      typeListLayout.add(proto.getParameters());
      return true;
    }

    @Override
    public boolean addType(DexType type) {
      return true;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      encodedArrayLayout.add(callSite.getEncodedArray());
      return true;
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return true;
    }
  }
}
