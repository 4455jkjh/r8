// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.keepradius.proto.BuildInfo;
import com.android.tools.r8.keepradius.proto.FieldReference;
import com.android.tools.r8.keepradius.proto.FileOrigin;
import com.android.tools.r8.keepradius.proto.GlobalKeepRuleKeepRadius;
import com.android.tools.r8.keepradius.proto.KeepConstraint;
import com.android.tools.r8.keepradius.proto.KeepConstraints;
import com.android.tools.r8.keepradius.proto.KeepRadius;
import com.android.tools.r8.keepradius.proto.KeepRadiusContainer;
import com.android.tools.r8.keepradius.proto.KeepRuleKeepRadius;
import com.android.tools.r8.keepradius.proto.KeepRuleTag;
import com.android.tools.r8.keepradius.proto.KeptClassInfo;
import com.android.tools.r8.keepradius.proto.KeptFieldInfo;
import com.android.tools.r8.keepradius.proto.KeptMethodInfo;
import com.android.tools.r8.keepradius.proto.MavenCoordinate;
import com.android.tools.r8.keepradius.proto.MethodReference;
import com.android.tools.r8.keepradius.proto.ProtoReference;
import com.android.tools.r8.keepradius.proto.TextFileOrigin;
import com.android.tools.r8.keepradius.proto.TypeReference;
import com.android.tools.r8.keepradius.proto.TypeReferenceList;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.MavenOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.EnqueuerResult;
import com.android.tools.r8.shaking.GlobalConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardKeepRuleModifiers;
import com.android.tools.r8.utils.OriginUtils;
import com.android.tools.r8.utils.internal.ArrayUtils;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RootSetKeepRadiusSerializer {

  private final AppView<?> appView;
  private final AppInfoWithLiveness appInfo;

  private final KeepRadiusContainer.Builder container = KeepRadiusContainer.newBuilder();

  // Ids.
  private final Reference2IntMap<RootSetKeepRadiusForRule> ruleIds =
      new Reference2IntOpenHashMap<>();

  // Origins.
  private final Map<Origin, FileOrigin> origins = new HashMap<>();

  // References. Intentionally using HashMap for DexTypeList since it is not canonicalized.
  private final Map<DexField, FieldReference> fieldReferences = new IdentityHashMap<>();
  private final Map<DexMethod, MethodReference> methodReferences = new IdentityHashMap<>();
  private final Map<DexProto, ProtoReference> protoReferences = new IdentityHashMap<>();
  private final Map<DexType, TypeReference> typeReferences = new IdentityHashMap<>();
  private final Map<DexTypeList, TypeReferenceList> typeReferenceLists = new HashMap<>();

  // Kept items. LinkedHashMap for deterministic output when writing to the proto container.
  private final Map<DexType, KeptClassInfo.Builder> keptClassInfos = new LinkedHashMap<>();
  private final Map<DexField, KeptFieldInfo.Builder> keptFieldInfos = new LinkedHashMap<>();
  private final Map<DexMethod, KeptMethodInfo.Builder> keptMethodInfos = new LinkedHashMap<>();

  private final Map<Wrapper<KeepConstraints>, KeepConstraints> keepConstraints = new HashMap<>();

  public RootSetKeepRadiusSerializer(AppView<?> appView, EnqueuerResult enqueuerResult) {
    this.appView = appView;
    this.appInfo = enqueuerResult.getAppInfo();
  }

  public KeepRadiusContainer serialize(RootSetKeepRadius keepRadius, KeepRadiusOptions options) {
    Collection<RootSetKeepRadiusForRule> sortedKeepRadius =
        keepRadius.getKeepRadiusWithDeterministicOrder();
    Map<RootSetKeepRadiusForRule, Collection<RootSetKeepRadiusForRule>> subsumedByInfo =
        keepRadius.getSubsumedByInfo(options);
    for (RootSetKeepRadiusForRule keepRadiusForRule : sortedKeepRadius) {
      ruleIds.put(keepRadiusForRule, ruleIds.size());
    }
    for (RootSetKeepRadiusForRule keepRadiusForRule : sortedKeepRadius) {
      int ruleId = ruleIds.getInt(keepRadiusForRule);
      KeepRuleKeepRadius.Builder ruleProto =
          KeepRuleKeepRadius.newBuilder()
              .setId(ruleId)
              .setKeepRadius(serializeKeepRadius(keepRadiusForRule, ruleId, subsumedByInfo))
              .setConstraintsId(serializeConstraints(keepRadiusForRule).getId())
              .setOrigin(serializeTextFileOrigin(keepRadiusForRule.getRule()))
              .setSource(keepRadiusForRule.getSource());
      if (KeepRadiusKeepRuleClassifier.isPackageWideKeepRule(keepRadiusForRule.getRule())) {
        ruleProto.addTags(KeepRuleTag.PACKAGE_WIDE);
      }
      container.addKeepRuleKeepRadiusTable(ruleProto);
    }
    serializeGlobalKeepRuleBlastRadii(ruleIds.size());
    keptClassInfos.values().forEach(container::addKeptClassInfoTable);
    keptFieldInfos.values().forEach(container::addKeptFieldInfoTable);
    keptMethodInfos.values().forEach(container::addKeptMethodInfoTable);
    KeepRadiusContainer result = container.setBuildInfo(serializeBuildInfo()).build();
    assert validate(result);
    return result;
  }

  private KeepRadius serializeKeepRadius(
      RootSetKeepRadiusForRule keepRadiusForRule,
      int ruleId,
      Map<RootSetKeepRadiusForRule, Collection<RootSetKeepRadiusForRule>> subsumedByInfo) {
    KeepRadius.Builder keepRadius = KeepRadius.newBuilder();
    // Populate subsumed by.
    Collection<RootSetKeepRadiusForRule> dominators = subsumedByInfo.get(keepRadiusForRule);
    if (dominators != null) {
      Iterator<RootSetKeepRadiusForRule> iterator = dominators.iterator();
      int[] dominatorIds =
          ArrayUtils.initialize(new int[dominators.size()], i -> ruleIds.getInt(iterator.next()));
      Arrays.sort(dominatorIds);
      for (int dominatorId : dominatorIds) {
        keepRadius.addSubsumedBy(dominatorId);
      }
    }
    // Populate keep radius.
    for (DexType matchedClass : keepRadiusForRule.getMatchedClassesWithDeterministicOrder()) {
      KeptClassInfo.Builder keptClassInfo =
          keptClassInfos.computeIfAbsent(
              matchedClass,
              k ->
                  KeptClassInfo.newBuilder()
                      .setId(keptClassInfos.size())
                      .setClassReferenceId(serializeTypeReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      keepRadius.addClassKeepRadius(keptClassInfo.getId());
      keptClassInfo.addKeptBy(ruleId);
    }
    for (DexField matchedField : keepRadiusForRule.getMatchedFieldsWithDeterministicOrder()) {
      KeptFieldInfo.Builder keptFieldInfo =
          keptFieldInfos.computeIfAbsent(
              matchedField,
              k ->
                  KeptFieldInfo.newBuilder()
                      .setId(keptFieldInfos.size())
                      .setFieldReferenceId(serializeFieldReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      keepRadius.addFieldKeepRadius(keptFieldInfo.getId());
      keptFieldInfo.addKeptBy(ruleId);
    }
    for (DexMethod matchedMethod : keepRadiusForRule.getMatchedMethodsWithDeterministicOrder()) {
      KeptMethodInfo.Builder keptMethodInfo =
          keptMethodInfos.computeIfAbsent(
              matchedMethod,
              k ->
                  KeptMethodInfo.newBuilder()
                      .setId(keptMethodInfos.size())
                      .setMethodReferenceId(serializeMethodReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      keepRadius.addMethodKeepRadius(keptMethodInfo.getId());
      keptMethodInfo.addKeptBy(ruleId);
    }
    return keepRadius.build();
  }

  private BuildInfo serializeBuildInfo() {
    int classCount = 0, fieldCount = 0, methodCount = 0;
    int liveClassCount = 0, liveFieldCount = 0, liveMethodCount = 0;
    for (DexProgramClass clazz : appInfo.classes()) {
      classCount++;
      fieldCount += clazz.getFieldCollection().size();
      methodCount += clazz.getMethodCollection().size();
      if (appInfo.isLiveProgramClass(clazz)) {
        liveClassCount++;
        liveFieldCount += Iterables.size(clazz.fields(appInfo::isReachableOrReferencedField));
        liveMethodCount += Iterables.size(clazz.methods(appInfo::isLiveOrTargetedMethod));
      }
    }
    return BuildInfo.newBuilder()
        .setClassCount(classCount)
        .setFieldCount(fieldCount)
        .setMethodCount(methodCount)
        .setLiveClassCount(liveClassCount)
        .setLiveFieldCount(liveFieldCount)
        .setLiveMethodCount(liveMethodCount)
        .build();
  }

  private KeepConstraints serializeConstraints(RootSetKeepRadiusForRule keepRadiusForRule) {
    KeepConstraints.Builder builder = KeepConstraints.newBuilder().setId(keepConstraints.size());
    ProguardKeepRuleModifiers modifiers = keepRadiusForRule.getRule().getModifiers();
    if (!modifiers.allowsObfuscation) {
      builder.addConstraints(KeepConstraint.DONT_OBFUSCATE);
    }
    if (!modifiers.allowsOptimization) {
      builder.addConstraints(KeepConstraint.DONT_OPTIMIZE);
    }
    if (!modifiers.allowsShrinking) {
      builder.addConstraints(KeepConstraint.DONT_SHRINK);
    }
    KeepConstraints constraints = builder.build();
    Wrapper<KeepConstraints> wrapper = KeepConstraintsEquivalence.doWrap(constraints);
    KeepConstraints previous = keepConstraints.putIfAbsent(wrapper, constraints);
    if (previous != null) {
      return previous;
    }
    container.addKeepConstraintsTable(constraints);
    return constraints;
  }

  private FieldReference serializeFieldReference(DexField field) {
    return fieldReferences.computeIfAbsent(
        field,
        k -> {
          FieldReference fieldReference =
              FieldReference.newBuilder()
                  .setId(fieldReferences.size())
                  .setClassReferenceId(serializeTypeReference(field.getHolderType()).getId())
                  .setTypeReferenceId(serializeTypeReference(field.getType()).getId())
                  .setName(field.getName().toString())
                  .build();
          container.addFieldReferenceTable(fieldReference);
          return fieldReference;
        });
  }

  private void serializeGlobalKeepRuleBlastRadii(int nextRuleId) {
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    if (proguardConfiguration == null) {
      return;
    }
    // The iteration over the global rules should have deterministic iteration order since the
    // global rules lists are populated in-order by the ProguardConfigurationParser.
    for (GlobalConfigurationRule rule : proguardConfiguration.getGlobalRules()) {
      container.addGlobalKeepRuleKeepRadiusTable(
          GlobalKeepRuleKeepRadius.newBuilder()
              .setId(nextRuleId++)
              .setOrigin(serializeTextFileOrigin(rule))
              .setSource(rule.getSource())
              .build());
    }
  }

  private MethodReference serializeMethodReference(DexMethod method) {
    return methodReferences.computeIfAbsent(
        method,
        k -> {
          MethodReference methodReference =
              MethodReference.newBuilder()
                  .setId(methodReferences.size())
                  .setClassReferenceId(serializeTypeReference(method.getHolderType()).getId())
                  .setProtoReferenceId(serializeProtoReference(method.getProto()).getId())
                  .setName(method.getName().toString())
                  .build();
          container.addMethodReferenceTable(methodReference);
          return methodReference;
        });
  }

  private FileOrigin serializeOrigin(DexReference reference) {
    DexProgramClass clazz = asProgramClassOrNull(appInfo.definitionFor(reference.getContextType()));
    assert clazz != null;
    Origin origin = clazz.getOrigin();
    if (origin instanceof ArchiveEntryOrigin) {
      // The class file entry inside the archive is immediately clear from the reference,
      // so do not create a unique origin in the keep radius data per class file.
      return serializeOrigin(origin.parent());
    }
    return serializeOrigin(origin);
  }

  private FileOrigin serializeOrigin(Origin origin) {
    return origins.computeIfAbsent(
        origin,
        o -> {
          // TODO(b/441055269): Set the filename correctly.
          FileOrigin.Builder fileOriginBuilder = FileOrigin.newBuilder().setId(origins.size());
          if (o instanceof PathOrigin) {
            fileOriginBuilder.setFilename(((PathOrigin) o).getPath().toString());
          } else {
            fileOriginBuilder.setFilename(o.toString());
          }
          MavenOrigin mavenOrigin = OriginUtils.getMavenOrigin(origin);
          if (mavenOrigin != null) {
            fileOriginBuilder.setMavenCoordinate(
                MavenCoordinate.newBuilder()
                    .setArtifactId(mavenOrigin.getModule())
                    .setGroupId(mavenOrigin.getGroup())
                    .setVersion(mavenOrigin.getVersion())
                    .build());
          }
          if (OriginUtils.isProvidedByBuildSystem(origin)) {
            fileOriginBuilder.setProvidedByBuildSystem(true);
          }
          FileOrigin fileOrigin = fileOriginBuilder.build();
          container.addFileOriginTable(fileOrigin);
          return fileOrigin;
        });
  }

  private TextFileOrigin serializeTextFileOrigin(GlobalConfigurationRule rule) {
    return serializeTextFileOrigin(rule.getOrigin(), rule.getPosition());
  }

  private TextFileOrigin serializeTextFileOrigin(ProguardKeepRuleBase rule) {
    return serializeTextFileOrigin(rule.getOrigin(), rule.getPosition());
  }

  private TextFileOrigin serializeTextFileOrigin(Origin origin, Position position) {
    int line, column;
    if (position instanceof TextRange) {
      TextRange textRange = (TextRange) position;
      line = textRange.getStart().getLine();
      column = textRange.getStart().getColumn();
    } else {
      line = -1;
      column = -1;
    }
    return TextFileOrigin.newBuilder()
        .setFileOriginId(serializeOrigin(origin).getId())
        .setLineNumber(line)
        .setColumnNumber(column)
        .build();
  }

  private ProtoReference serializeProtoReference(DexProto proto) {
    return protoReferences.computeIfAbsent(
        proto,
        k -> {
          ProtoReference protoReference =
              ProtoReference.newBuilder()
                  .setId(protoReferences.size())
                  .setParametersId(serializeTypeReferenceList(proto.getParameters()).getId())
                  .setReturnTypeId(serializeTypeReference(proto.getReturnType()).getId())
                  .build();
          container.addProtoReferenceTable(protoReference);
          return protoReference;
        });
  }

  private TypeReference serializeTypeReference(DexType type) {
    return typeReferences.computeIfAbsent(
        type,
        k -> {
          TypeReference typeReference =
              TypeReference.newBuilder()
                  .setId(typeReferences.size())
                  .setJavaDescriptor(type.toDescriptorString())
                  .build();
          container.addTypeReferenceTable(typeReference);
          return typeReference;
        });
  }

  private TypeReferenceList serializeTypeReferenceList(DexTypeList types) {
    return typeReferenceLists.computeIfAbsent(
        types,
        k -> {
          TypeReferenceList.Builder builder =
              TypeReferenceList.newBuilder().setId(typeReferenceLists.size());
          for (DexType type : types) {
            builder.addTypeReferenceIds(serializeTypeReference(type).getId());
          }
          TypeReferenceList typeReferenceList = builder.build();
          container.addTypeReferenceListTable(typeReferenceList);
          return typeReferenceList;
        });
  }

  @SuppressWarnings("UnusedVariable")
  private boolean validate(KeepRadiusContainer container) {
    // TODO(b/441055269): Check that ids of ClassFileInJarOrigin and FileOrigin are non-overlapping.
    // TODO(b/441055269): Check that ids of GlobalKeepRuleKeepRadius and KeepRuleKeepRadius are
    //  non-overlapping.
    // TODO(b/441055269): Check that the reference constants pools do not contain duplicates.
    return true;
  }

  private static class KeepConstraintsEquivalence extends Equivalence<KeepConstraints> {

    private static final KeepConstraintsEquivalence INSTANCE = new KeepConstraintsEquivalence();

    public static Wrapper<KeepConstraints> doWrap(KeepConstraints constraints) {
      return INSTANCE.wrap(constraints);
    }

    @Override
    protected boolean doEquivalent(KeepConstraints a, KeepConstraints b) {
      if (a.getConstraintsCount() != b.getConstraintsCount()) {
        return false;
      }
      int flags = 0;
      for (int i = 0; i < a.getConstraintsCount(); i++) {
        flags ^= 1 << a.getConstraints(i).getNumber();
        flags ^= 1 << b.getConstraints(i).getNumber();
      }
      return flags == 0;
    }

    @Override
    protected int doHash(KeepConstraints constraints) {
      int hash = 0;
      for (KeepConstraint constraint : constraints.getConstraintsList()) {
        hash |= 1 << constraint.getNumber();
      }
      return hash;
    }
  }
}
