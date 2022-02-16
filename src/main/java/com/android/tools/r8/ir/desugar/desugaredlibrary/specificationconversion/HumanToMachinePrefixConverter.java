// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachinePrefixConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final MachineRewritingFlags.Builder builder;
  private final String synthesizedPrefix;
  private final Map<DexString, DexString> descriptorPrefix;
  private final Map<DexString, Map<DexString, DexString>> descriptorDifferentPrefix;
  private final Set<DexString> usedPrefix = Sets.newIdentityHashSet();

  public HumanToMachinePrefixConverter(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      String synthesizedPrefix,
      HumanRewritingFlags rewritingFlags) {
    this.appInfo = appInfo;
    this.builder = builder;
    this.synthesizedPrefix = synthesizedPrefix;
    this.descriptorPrefix = convertRewritePrefix(rewritingFlags.getRewritePrefix());
    this.descriptorDifferentPrefix =
        convertRewriteDifferentPrefix(rewritingFlags.getRewriteDerivedPrefix());
  }

  public void convertPrefixFlags(
      HumanRewritingFlags rewritingFlags, BiConsumer<String, Set<DexString>> warnConsumer) {
    rewriteClasses();
    rewriteValues(rewritingFlags.getRetargetCoreLibMember());
    rewriteValues(rewritingFlags.getCustomConversions());
    rewriteEmulatedInterface(rewritingFlags.getEmulateLibraryInterface());
    rewriteRetargetKeys(rewritingFlags.getRetargetCoreLibMember());
    warnIfUnusedPrefix(warnConsumer);
  }

  private void warnIfUnusedPrefix(BiConsumer<String, Set<DexString>> warnConsumer) {
    Set<DexString> prefixes = Sets.newIdentityHashSet();
    prefixes.addAll(descriptorPrefix.keySet());
    prefixes.addAll(descriptorDifferentPrefix.keySet());
    prefixes.removeAll(usedPrefix);
    warnConsumer.accept("The following prefixes do not match any type: ", prefixes);
  }

  public DexType convertJavaNameToDesugaredLibrary(DexType type) {
    String convertedPrefix = DescriptorUtils.getJavaTypeFromBinaryName(synthesizedPrefix);
    String interfaceType = type.toString();
    int firstPackage = interfaceType.indexOf('.');
    return appInfo
        .dexItemFactory()
        .createType(
            DescriptorUtils.javaTypeToDescriptor(
                convertedPrefix + interfaceType.substring(firstPackage + 1)));
  }

  private void rewriteRetargetKeys(Map<DexMethod, DexType> retarget) {
    for (DexMethod dexMethod : retarget.keySet()) {
      DexType type = convertJavaNameToDesugaredLibrary(dexMethod.holder);
      builder.rewriteDerivedTypeOnly(dexMethod.holder, type);
    }
  }

  private void rewriteEmulatedInterface(Map<DexType, DexType> emulateLibraryInterface) {
    emulateLibraryInterface.forEach(builder::rewriteDerivedTypeOnly);
  }

  private void rewriteValues(
      Map<?, DexType> flags) {
    for (DexType type : flags.values()) {
      registerType(type);
    }
  }

  private void rewriteClasses() {
    for (DexClass clazz : appInfo.app().asDirect().libraryClasses()) {
      registerType(clazz.type);
      registerDifferentType(clazz.type);
    }
    for (DexClass clazz : appInfo.classes()) {
      registerType(clazz.type);
      registerDifferentType(clazz.type);
    }
  }

  private void registerType(DexType type) {
    DexType rewrittenType = rewrittenType(type);
    if (rewrittenType != null) {
      builder.rewriteType(type, rewrittenType);
    }
  }

  private void registerDifferentType(DexType type) {
    DexString prefix = prefixMatching(type, descriptorDifferentPrefix.keySet());
    if (prefix == null) {
      return;
    }
    descriptorDifferentPrefix
        .get(prefix)
        .forEach(
            (k, v) -> {
              DexString typeDescriptor =
                  type.descriptor.withNewPrefix(prefix, k, appInfo.dexItemFactory());
              DexString rewrittenTypeDescriptor =
                  type.descriptor.withNewPrefix(prefix, v, appInfo.dexItemFactory());
              builder.rewriteType(
                  appInfo.dexItemFactory().createType(typeDescriptor),
                  appInfo.dexItemFactory().createType(rewrittenTypeDescriptor));
            });
    usedPrefix.add(prefix);
  }

  private DexString prefixMatching(DexType type, Set<DexString> prefixes) {
    DexString prefixToMatch = type.descriptor.withoutArray(appInfo.dexItemFactory());
    for (DexString prefix : prefixes) {
      if (prefixToMatch.startsWith(prefix)) {
        return prefix;
      }
    }
    return null;
  }

  private DexType rewrittenType(DexType type) {
    DexString prefix = prefixMatching(type, descriptorPrefix.keySet());
    if (prefix == null) {
      return null;
    }
    DexString rewrittenTypeDescriptor =
        type.descriptor.withNewPrefix(
            prefix, descriptorPrefix.get(prefix), appInfo.dexItemFactory());
    usedPrefix.add(prefix);
    return appInfo.dexItemFactory().createType(rewrittenTypeDescriptor);
  }

  private ImmutableMap<DexString, Map<DexString, DexString>> convertRewriteDifferentPrefix(
      Map<String, Map<String, String>> rewriteDerivedPrefix) {
    ImmutableMap.Builder<DexString, Map<DexString, DexString>> mapBuilder = ImmutableMap.builder();
    for (String key : rewriteDerivedPrefix.keySet()) {
      mapBuilder.put(toDescriptorPrefix(key), convertRewritePrefix(rewriteDerivedPrefix.get(key)));
    }
    return mapBuilder.build();
  }

  private ImmutableMap<DexString, DexString> convertRewritePrefix(
      Map<String, String> rewritePrefix) {
    ImmutableMap.Builder<DexString, DexString> mapBuilder = ImmutableMap.builder();
    for (String key : rewritePrefix.keySet()) {
      mapBuilder.put(toDescriptorPrefix(key), toDescriptorPrefix(rewritePrefix.get(key)));
    }
    return mapBuilder.build();
  }

  private DexString toDescriptorPrefix(String prefix) {
    return appInfo
        .dexItemFactory()
        .createString("L" + DescriptorUtils.getBinaryNameFromJavaType(prefix));
  }
}
