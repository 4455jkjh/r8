// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MachineDesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final MachineTopLevelFlags topLevelFlags;
  private final MachineRewritingFlags rewritingFlags;

  public static MachineDesugaredLibrarySpecification empty() {
    return new MachineDesugaredLibrarySpecification(
        false, MachineTopLevelFlags.empty(), MachineRewritingFlags.builder().build()) {
      @Override
      public boolean isSupported(DexReference reference) {
        return false;
      }
    };
  }

  public static MachineDesugaredLibrarySpecification withOnlyRewriteTypeForTesting(
      Map<DexType, DexType> rewriteTypeForTesting) {
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    rewriteTypeForTesting.forEach(builder::rewriteType);
    return new MachineDesugaredLibrarySpecification(
        true, MachineTopLevelFlags.empty(), builder.build());
  }

  public MachineDesugaredLibrarySpecification(
      boolean libraryCompilation,
      MachineTopLevelFlags topLevelFlags,
      MachineRewritingFlags rewritingFlags) {
    this.libraryCompilation = libraryCompilation;
    this.topLevelFlags = topLevelFlags;
    this.rewritingFlags = rewritingFlags;
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public AndroidApiLevel getRequiredCompilationAPILevel() {
    return topLevelFlags.getRequiredCompilationAPILevel();
  }

  public String getSynthesizedLibraryClassesPackagePrefix() {
    return topLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
  }

  public String getIdentifier() {
    return topLevelFlags.getIdentifier();
  }

  public String getJsonSource() {
    return topLevelFlags.getJsonSource();
  }

  public boolean supportAllCallbacksFromLibrary() {
    return topLevelFlags.supportAllCallbacksFromLibrary();
  }

  public List<String> getExtraKeepRules() {
    return topLevelFlags.getExtraKeepRules();
  }

  public Map<DexType, DexType> getRewriteType() {
    return rewritingFlags.getRewriteType();
  }

  public Map<DexType, DexType> getRewriteDerivedTypeOnly() {
    return rewritingFlags.getRewriteDerivedTypeOnly();
  }

  public Map<DexMethod, DexMethod> getStaticRetarget() {
    return rewritingFlags.getStaticRetarget();
  }

  public Map<DexMethod, DexMethod> getNonEmulatedVirtualRetarget() {
    return rewritingFlags.getNonEmulatedVirtualRetarget();
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedVirtualRetarget() {
    return rewritingFlags.getEmulatedVirtualRetarget();
  }

  public void forEachRetargetHolder(Consumer<DexType> consumer) {
    rewritingFlags.forEachRetargetHolder(consumer);
  }

  public Map<DexType, EmulatedInterfaceDescriptor> getEmulatedInterfaces() {
    return rewritingFlags.getEmulatedInterfaces();
  }

  public EmulatedDispatchMethodDescriptor getEmulatedInterfaceEmulatedDispatchMethodDescriptor(
      DexMethod method) {
    return rewritingFlags.getEmulatedInterfaceEmulatedDispatchMethodDescriptor(method);
  }

  public Set<DexType> getEmulatedInterfaceRewrittenTypes() {
    return rewritingFlags.getEmulatedInterfaceRewrittenTypes();
  }

  public Map<DexType, List<DexMethod>> getWrappers() {
    return rewritingFlags.getWrappers();
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return rewritingFlags.getLegacyBackport();
  }

  public Set<DexType> getDontRetarget() {
    return rewritingFlags.getDontRetarget();
  }

  public Map<DexType, Pair<DexType, DexString>> getCustomConversions() {
    return rewritingFlags.getCustomConversions();
  }

  public boolean hasRetargeting() {
    return rewritingFlags.hasRetargeting();
  }

  public boolean hasEmulatedInterfaces() {
    return rewritingFlags.hasEmulatedInterfaces();
  }

  public boolean isSupported(DexReference reference) {
    // Support through type rewriting.
    if (rewritingFlags.getRewriteType().containsKey(reference.getContextType())) {
      return true;
    }
    if (!reference.isDexMethod()) {
      return false;
    }
    // Support through retargeting.
    DexMethod dexMethod = reference.asDexMethod();
    if (getStaticRetarget().containsKey(dexMethod)
        || getNonEmulatedVirtualRetarget().containsKey(dexMethod)
        || getEmulatedVirtualRetarget().containsKey(dexMethod)) {
      return true;
    }
    // Support through emulated interface.
    for (EmulatedInterfaceDescriptor descriptor : getEmulatedInterfaces().values()) {
      if (descriptor.getEmulatedMethods().containsKey(dexMethod)) {
        return true;
      }
    }
    return false;
  }
}
