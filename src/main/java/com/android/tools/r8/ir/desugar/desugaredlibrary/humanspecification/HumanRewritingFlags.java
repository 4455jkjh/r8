// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HumanRewritingFlags {

  private final Map<String, String> rewritePrefix;
  private final Map<String, Map<String, String>> rewriteDerivedPrefix;
  private final Map<DexType, DexType> emulatedInterfaces;
  private final Map<DexField, DexType> retargetStaticField;
  private final Map<DexMethod, DexType> retargetMethod;
  private final Map<DexMethod, DexType> retargetMethodEmulatedDispatch;
  private final Map<DexType, DexType> legacyBackport;
  private final Map<DexType, DexType> customConversions;
  private final Set<DexMethod> dontRewriteInvocation;
  private final Set<DexType> dontRetarget;
  private final Map<DexType, Set<DexMethod>> wrapperConversions;
  private final Map<DexMethod, MethodAccessFlags> amendLibraryMethod;
  private final Map<DexField, FieldAccessFlags> amendLibraryField;

  HumanRewritingFlags(
      Map<String, String> rewritePrefix,
      Map<String, Map<String, String>> rewriteDerivedPrefix,
      Map<DexType, DexType> emulateLibraryInterface,
      Map<DexField, DexType> retargetStaticField,
      Map<DexMethod, DexType> retargetMethod,
      Map<DexMethod, DexType> retargetMethodEmulatedDispatch,
      Map<DexType, DexType> legacyBackport,
      Map<DexType, DexType> customConversion,
      Set<DexMethod> dontRewriteInvocation,
      Set<DexType> dontRetarget,
      Map<DexType, Set<DexMethod>> wrapperConversion,
      Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
      Map<DexField, FieldAccessFlags> amendLibraryField) {
    this.rewritePrefix = rewritePrefix;
    this.rewriteDerivedPrefix = rewriteDerivedPrefix;
    this.emulatedInterfaces = emulateLibraryInterface;
    this.retargetStaticField = retargetStaticField;
    this.retargetMethod = retargetMethod;
    this.retargetMethodEmulatedDispatch = retargetMethodEmulatedDispatch;
    this.legacyBackport = legacyBackport;
    this.customConversions = customConversion;
    this.dontRewriteInvocation = dontRewriteInvocation;
    this.dontRetarget = dontRetarget;
    this.wrapperConversions = wrapperConversion;
    this.amendLibraryMethod = amendLibraryMethod;
    this.amendLibraryField = amendLibraryField;
  }

  public static HumanRewritingFlags empty() {
    return new HumanRewritingFlags(
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of());
  }

  public static Builder builder(Reporter reporter, Origin origin) {
    return new Builder(reporter, origin);
  }

  public Builder newBuilder(Reporter reporter, Origin origin) {
    return new Builder(
        reporter,
        origin,
        rewritePrefix,
        rewriteDerivedPrefix,
        emulatedInterfaces,
        retargetStaticField,
        retargetMethod,
        retargetMethodEmulatedDispatch,
        legacyBackport,
        customConversions,
        dontRewriteInvocation,
        dontRetarget,
        wrapperConversions,
        amendLibraryMethod,
        amendLibraryField);
  }

  public Map<String, String> getRewritePrefix() {
    return rewritePrefix;
  }

  public Map<String, Map<String, String>> getRewriteDerivedPrefix() {
    return rewriteDerivedPrefix;
  }

  public Map<DexType, DexType> getEmulatedInterfaces() {
    return emulatedInterfaces;
  }

  public Map<DexField, DexType> getRetargetStaticField() {
    return retargetStaticField;
  }

  public Map<DexMethod, DexType> getRetargetMethod() {
    return retargetMethod;
  }

  public Map<DexMethod, DexType> getRetargetMethodEmulatedDispatch() {
    return retargetMethodEmulatedDispatch;
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return legacyBackport;
  }

  public Map<DexType, DexType> getCustomConversions() {
    return customConversions;
  }

  public Set<DexMethod> getDontRewriteInvocation() {
    return dontRewriteInvocation;
  }

  public Set<DexType> getDontRetarget() {
    return dontRetarget;
  }

  public Map<DexType, Set<DexMethod>> getWrapperConversions() {
    return wrapperConversions;
  }

  public Map<DexMethod, MethodAccessFlags> getAmendLibraryMethod() {
    return amendLibraryMethod;
  }

  public Map<DexField, FieldAccessFlags> getAmendLibraryField() {
    return amendLibraryField;
  }

  public boolean isEmpty() {
    return rewritePrefix.isEmpty()
        && rewriteDerivedPrefix.isEmpty()
        && emulatedInterfaces.isEmpty()
        && retargetMethod.isEmpty()
        && retargetMethodEmulatedDispatch.isEmpty()
        && retargetStaticField.isEmpty();
  }

  public static class Builder {

    private final Reporter reporter;
    private final Origin origin;

    private final Map<String, String> rewritePrefix;
    private final Map<String, Map<String, String>> rewriteDerivedPrefix;
    private final Map<DexType, DexType> emulatedInterfaces;
    private final Map<DexField, DexType> retargetStaticField;
    private final Map<DexMethod, DexType> retargetMethod;
    private final Map<DexMethod, DexType> retargetMethodEmulatedDispatch;
    private final Map<DexType, DexType> legacyBackport;
    private final Map<DexType, DexType> customConversions;
    private final Set<DexMethod> dontRewriteInvocation;
    private final Set<DexType> dontRetarget;
    private final Map<DexType, Set<DexMethod>> wrapperConversions;
    private final Map<DexMethod, MethodAccessFlags> amendLibraryMethod;
    private final Map<DexField, FieldAccessFlags> amendLibraryField;

    Builder(Reporter reporter, Origin origin) {
      this(
          reporter,
          origin,
          new HashMap<>(),
          new HashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          Sets.newIdentityHashSet(),
          Sets.newIdentityHashSet(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>());
    }

    Builder(
        Reporter reporter,
        Origin origin,
        Map<String, String> rewritePrefix,
        Map<String, Map<String, String>> rewriteDerivedPrefix,
        Map<DexType, DexType> emulateLibraryInterface,
        Map<DexField, DexType> retargetStaticField,
        Map<DexMethod, DexType> retargetMethod,
        Map<DexMethod, DexType> retargetMethodEmulatedDispatch,
        Map<DexType, DexType> backportCoreLibraryMember,
        Map<DexType, DexType> customConversions,
        Set<DexMethod> dontRewriteInvocation,
        Set<DexType> dontRetargetLibMember,
        Map<DexType, Set<DexMethod>> wrapperConversions,
        Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
        Map<DexField, FieldAccessFlags> amendLibraryField) {
      this.reporter = reporter;
      this.origin = origin;
      this.rewritePrefix = new HashMap<>(rewritePrefix);
      this.rewriteDerivedPrefix = new HashMap<>(rewriteDerivedPrefix);
      this.emulatedInterfaces = new IdentityHashMap<>(emulateLibraryInterface);
      this.retargetStaticField = new IdentityHashMap<>(retargetStaticField);
      this.retargetMethod = new IdentityHashMap<>(retargetMethod);
      this.retargetMethodEmulatedDispatch = new IdentityHashMap<>(retargetMethodEmulatedDispatch);
      this.legacyBackport = new IdentityHashMap<>(backportCoreLibraryMember);
      this.customConversions = new IdentityHashMap<>(customConversions);
      this.dontRewriteInvocation = Sets.newIdentityHashSet();
      this.dontRewriteInvocation.addAll(dontRewriteInvocation);
      this.dontRetarget = Sets.newIdentityHashSet();
      this.dontRetarget.addAll(dontRetargetLibMember);
      this.wrapperConversions = new IdentityHashMap<>(wrapperConversions);
      this.amendLibraryMethod = new IdentityHashMap<>(amendLibraryMethod);
      this.amendLibraryField = new IdentityHashMap<>(amendLibraryField);
    }

    // Utility to set values.
    private <K, V> void put(Map<K, V> map, K key, V value, String desc) {
      if (map.containsKey(key) && !map.get(key).equals(value)) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Invalid desugared library configuration. "
                    + " Duplicate assignment of key: '"
                    + key
                    + "' in sections for '"
                    + desc
                    + "'",
                origin));
      }
      map.put(key, value);
    }

    public Builder putRewritePrefix(String prefix, String rewrittenPrefix) {
      put(
          rewritePrefix,
          prefix,
          rewrittenPrefix,
          HumanDesugaredLibrarySpecificationParser.REWRITE_PREFIX_KEY);
      return this;
    }

    public Builder putRewriteDerivedPrefix(
        String prefixToMatch, String prefixToRewrite, String rewrittenPrefix) {
      Map<String, String> map =
          rewriteDerivedPrefix.computeIfAbsent(prefixToMatch, k -> new HashMap<>());
      put(
          map,
          prefixToRewrite,
          rewrittenPrefix,
          HumanDesugaredLibrarySpecificationParser.REWRITE_DERIVED_PREFIX_KEY);
      return this;
    }

    public Builder putEmulatedInterface(DexType interfaceType, DexType rewrittenType) {
      put(
          emulatedInterfaces,
          interfaceType,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.EMULATE_INTERFACE_KEY);
      return this;
    }

    public Builder putCustomConversion(DexType dexType, DexType conversionType) {
      put(
          customConversions,
          dexType,
          conversionType,
          HumanDesugaredLibrarySpecificationParser.CUSTOM_CONVERSION_KEY);
      return this;
    }

    public Builder addWrapperConversion(DexType dexType) {
      return addWrapperConversion(dexType, Collections.emptySet());
    }

    public Builder addWrapperConversion(DexType dexType, Set<DexMethod> excludedMethods) {
      wrapperConversions.put(dexType, excludedMethods);
      return this;
    }

    public Builder retargetMethod(DexMethod key, DexType rewrittenType) {
      put(
          retargetMethod,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_KEY);
      return this;
    }

    public Builder retargetStaticField(DexField key, DexType rewrittenType) {
      put(
          retargetStaticField,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_STATIC_FIELD_KEY);
      return this;
    }

    public Builder retargetMethodEmulatedDispatch(DexMethod key, DexType rewrittenType) {
      put(
          retargetMethodEmulatedDispatch,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_EMULATED_DISPATCH_KEY);
      return this;
    }

    public Builder putLegacyBackport(DexType backportType, DexType rewrittenBackportType) {
      put(
          legacyBackport,
          backportType,
          rewrittenBackportType,
          HumanDesugaredLibrarySpecificationParser.BACKPORT_KEY);
      return this;
    }

    public Builder addDontRewriteInvocation(DexMethod dontRewrite) {
      dontRewriteInvocation.add(dontRewrite);
      return this;
    }

    public Builder addDontRetargetLibMember(DexType dontRetargetLibMember) {
      dontRetarget.add(dontRetargetLibMember);
      return this;
    }

    public Builder amendLibraryMethod(DexMethod member, MethodAccessFlags flags) {
      amendLibraryMethod.put(member, flags);
      return this;
    }

    public Builder amendLibraryField(DexField member, FieldAccessFlags flags) {
      amendLibraryField.put(member, flags);
      return this;
    }

    public HumanRewritingFlags build() {
      validate();
      return new HumanRewritingFlags(
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableMap.copyOf(rewriteDerivedPrefix),
          ImmutableMap.copyOf(emulatedInterfaces),
          ImmutableMap.copyOf(retargetStaticField),
          ImmutableMap.copyOf(retargetMethod),
          ImmutableMap.copyOf(retargetMethodEmulatedDispatch),
          ImmutableMap.copyOf(legacyBackport),
          ImmutableMap.copyOf(customConversions),
          ImmutableSet.copyOf(dontRewriteInvocation),
          ImmutableSet.copyOf(dontRetarget),
          ImmutableMap.copyOf(wrapperConversions),
          ImmutableMap.copyOf(amendLibraryMethod),
          ImmutableMap.copyOf(amendLibraryField));
    }

    private void validate() {
      SetView<DexType> dups =
          Sets.intersection(customConversions.keySet(), wrapperConversions.keySet());
      if (!dups.isEmpty()) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Invalid desugared library configuration. "
                    + "Duplicate types in custom conversions and wrapper conversions: "
                    + String.join(
                        ", ", dups.stream().map(DexType::toString).collect(Collectors.toSet())),
                origin));
      }
    }
  }
}
