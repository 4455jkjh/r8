// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Annotation;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassInstantiate;
import com.android.tools.r8.keepanno.ast.KeepConstraint.ClassOpenHierarchy;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldGet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.FieldSet;
import com.android.tools.r8.keepanno.ast.KeepConstraint.GenericSignature;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Lookup;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodInvoke;
import com.android.tools.r8.keepanno.ast.KeepConstraint.MethodReplace;
import com.android.tools.r8.keepanno.ast.KeepConstraint.Name;
import com.android.tools.r8.keepanno.ast.KeepConstraint.NeverInline;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRelax;
import com.android.tools.r8.keepanno.ast.KeepConstraint.VisibilityRestrict;
import com.android.tools.r8.keepanno.ast.KeepConstraintVisitor;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.shaking.KeepAnnotationCollectionInfo.RetentionInfo;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.shaking.rules.ApplicableRulesEvaluator.InitialRules;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class KeepAnnotationMatcher {

  public static ApplicableRulesEvaluator computeInitialRules(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      List<KeepDeclaration> keepDeclarations,
      ExecutorService executorService)
      throws ExecutionException {
    return computeInitialRules(appView, keepDeclarations, executorService, null);
  }

  public static ApplicableRulesEvaluator computeInitialRules(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      List<KeepDeclaration> keepDeclarations,
      ExecutorService executorService,
      ImmediateAppSubtypingInfo subtypingInfo)
      throws ExecutionException {
    assert executorService != null;
    if (keepDeclarations.isEmpty()) {
      return ApplicableRulesEvaluator.empty();
    }
    KeepAnnotationMatcherPredicates predicates = new KeepAnnotationMatcherPredicates();
    ApplicableRulesEvaluator.Builder builder = ApplicableRulesEvaluator.initialRulesBuilder();
    List<NormalizedSchema> fullScanSchemas = new ArrayList<>();
    TaskCollection<InitialRules> tasks = new TaskCollection<>(appView.options(), executorService);
    for (KeepDeclaration declaration : keepDeclarations) {
      // Normalize each declaration once up front into indexed class and member patterns so we can
      // inspect root class 0 before launching tasks (to separate direct lookups from full program
      // scans) and share one schema across all partitions.
      NormalizedSchema schema = new NormalizedSchema(declaration);
      // EdgeMatcher starts matching at class 0, and schema.classes lists mandatory precondition
      // classes before optional consequence-only classes. Only partition class 0 across
      // fullScanSchemas when class 0 is mandatory (!isOptionalClass(0)) or the sole class in the
      // schema (classes.size() == 1), where failing to match class 0 cannot still match subsequent
      // optional classes via continueWithNoClass(0).
      boolean singleOrMandatoryRoot =
          !schema.classes.isEmpty() && (!schema.isOptionalClass(0) || schema.classes.size() == 1);
      if (singleOrMandatoryRoot && EdgeMatcher.usesFullClassScanAtRoot(schema, subtypingInfo)) {
        fullScanSchemas.add(schema);
      } else {
        tasks.submit(() -> processSchema(schema, appView.appInfo(), predicates, subtypingInfo));
      }
    }
    if (!fullScanSchemas.isEmpty()) {
      List<DexProgramClass> classes = appView.appInfo().classes();
      int partitionSize = Math.max(128, classes.size() / 128);
      for (List<DexProgramClass> partition : Lists.partition(classes, partitionSize)) {
        tasks.submit(
            () ->
                processRootPartition(
                    partition, fullScanSchemas, appView.appInfo(), predicates, subtypingInfo));
      }
    }
    tasks.await(builder::merge);
    return builder.build(appView);
  }

  private static InitialRules processSchema(
      NormalizedSchema schema,
      AppInfoWithClassHierarchy appInfo,
      KeepAnnotationMatcherPredicates predicates,
      ImmediateAppSubtypingInfo subtypingInfo) {
    InitialRules initialRules = new InitialRules();
    EdgeMatcher matcher = new EdgeMatcher(appInfo, predicates, subtypingInfo);
    matchSchemaIntoInitialRules(schema, matcher, null, predicates, initialRules);
    return initialRules;
  }

  private static InitialRules processRootPartition(
      List<DexProgramClass> classes,
      List<NormalizedSchema> fullScanSchemas,
      AppInfoWithClassHierarchy appInfo,
      KeepAnnotationMatcherPredicates predicates,
      ImmediateAppSubtypingInfo subtypingInfo) {
    InitialRules initialRules = new InitialRules();
    EdgeMatcher matcher = new EdgeMatcher(appInfo, predicates, subtypingInfo);
    for (NormalizedSchema schema : fullScanSchemas) {
      matchSchemaIntoInitialRules(schema, matcher, classes, predicates, initialRules);
    }
    return initialRules;
  }

  private static void matchSchemaIntoInitialRules(
      NormalizedSchema schema,
      EdgeMatcher matcher,
      List<DexProgramClass> classes,
      KeepAnnotationMatcherPredicates predicates,
      InitialRules initialRules) {
    schema.declaration.match(
        edge ->
            matcher.forEachMatch(
                schema,
                classes,
                result -> {
                  if (result.preconditions.isEmpty()) {
                    createKeepInfo(result, initialRules.getRootConsequences(), predicates);
                  } else {
                    initialRules.addConditionalRule(
                        new PendingInitialConditionalRule(
                            result.preconditions,
                            createKeepInfo(
                                result, MinimumKeepInfoCollection.create(), predicates)));
                  }
                }),
        check ->
            matcher.forEachMatch(
                schema,
                classes,
                result -> {
                  assert result.preconditions.isEmpty();
                  createCheckDiscardInfo(check, result, initialRules.getRootConsequences());
                }));
  }

  private static void createCheckDiscardInfo(
      KeepCheck check, MatchResult result, MinimumKeepInfoCollection keepInfoCollection) {
    boolean isRemovedCheck = check.getKind() == KeepCheckKind.REMOVED;
    ListUtils.forEachWithIndex(
        result.consequences,
        (item, i) -> {
          applyCheckDiscardConstraints(isRemovedCheck, keepInfoCollection, item);
          // If a check-discard is annotated on a type, then it applies to all members of the type.
          if (item.isClass()) {
            item.asProgramClass()
                .forEachProgramMember(
                    member ->
                        applyCheckDiscardConstraints(isRemovedCheck, keepInfoCollection, member));
          }
        });
  }

  private static void applyCheckDiscardConstraints(
      boolean isRemovedCheck,
      MinimumKeepInfoCollection keepInfoCollection,
      ProgramDefinition item) {
    Joiner<?, ?, ?> joiner = keepInfoCollection.getOrCreateMinimumKeepInfoFor(item.getReference());
    joiner.setCheckDiscarded();
    if (isRemovedCheck) {
      joiner.disallowOptimization();
    }
  }

  private static MinimumKeepInfoCollection createKeepInfo(
      MatchResult result,
      MinimumKeepInfoCollection minimumKeepInfoCollection,
      KeepAnnotationMatcherPredicates predicates) {
    ListUtils.forEachWithIndex(
        result.consequences,
        (item, i) -> {
          Joiner<?, ?, ?> joiner =
              minimumKeepInfoCollection.getOrCreateMinimumKeepInfoFor(item.getReference());
          updateWithConstraints(
              item, joiner, result.constraints.get(i), result.declaration, predicates);
        });
    return minimumKeepInfoCollection;
  }

  private static void updateWithConstraints(
      ProgramDefinition item,
      Joiner<?, ?, ?> joiner,
      KeepConstraints constraints,
      KeepDeclaration declaration,
      KeepAnnotationMatcherPredicates predicates) {
    constraints.forEachAccept(
        new KeepConstraintVisitor() {

          @Override
          public void onLookup(Lookup constraint) {
            joiner.disallowShrinking();
            joiner.addRule(new KeepAnnotationFakeProguardRule(declaration.getMetaInfo()));
          }

          @Override
          public void onName(Name constraint) {
            joiner.disallowMinification();
            if (item.isProgramClass()) {
              joiner.asClassJoiner().disallowRepackaging();
            }
          }

          @Override
          public void onVisibilityRelax(VisibilityRelax constraint) {
            // R8 will never restrict the access, so this constraint is implicitly maintained.
          }

          @Override
          public void onVisibilityRestrict(VisibilityRestrict constraint) {
            joiner.disallowAccessModification();
          }

          @Override
          public void onNeverInline(NeverInline constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onClassInstantiate(ClassInstantiate constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onClassOpenHierarchy(ClassOpenHierarchy constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onMethodInvoke(MethodInvoke constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onMethodReplace(MethodReplace constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldGet(FieldGet constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldSet(FieldSet constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onFieldReplace(FieldReplace constraint) {
            joiner.disallowOptimization();
          }

          @Override
          public void onGenericSignature(GenericSignature constraint) {
            joiner.disallowSignatureRemoval();
          }

          @Override
          public void onAnnotation(Annotation constraint) {
            KeepAnnotationPattern pattern = constraint.asAnnotationPattern();
            if (pattern.getNamePattern().isAny()) {
              joiner.disallowAnnotationRemoval(toRetentionInfo(pattern));
            } else {
              item.getDefinition()
                  .annotations()
                  .forEach(
                      annotation -> {
                        if (predicates.matchesAnnotation(annotation, pattern)) {
                          joiner.disallowAnnotationRemoval(
                              toRetentionInfo(pattern), annotation.getAnnotationType());
                        }
                      });
            }
          }

          private RetentionInfo toRetentionInfo(KeepAnnotationPattern pattern) {
            if (pattern.includesRuntimeRetention() && pattern.includesClassRetention()) {
              return RetentionInfo.getRetainAll();
            }
            if (pattern.includesRuntimeRetention()) {
              return RetentionInfo.getRetainVisible();
            }
            if (pattern.includesClassRetention()) {
              return RetentionInfo.getRetainInvisible();
            }
            return RetentionInfo.getRetainNone();
          }
        });
  }

  public static class MatchResult {
    private final KeepDeclaration declaration;
    private final List<ProgramDefinition> preconditions;
    private final List<ProgramDefinition> consequences;
    private final List<KeepConstraints> constraints;

    public MatchResult(
        KeepDeclaration declaration,
        List<ProgramDefinition> preconditions,
        List<ProgramDefinition> consequences,
        List<KeepConstraints> constraints) {
      this.declaration = declaration;
      this.preconditions = preconditions;
      this.consequences = consequences;
      this.constraints = constraints;
    }
  }

  public static class EdgeMatcher {

    private final AppInfoWithClassHierarchy appInfo;
    private final KeepAnnotationMatcherPredicates predicates;
    private final ImmediateAppSubtypingInfo subtypingInfo;

    private NormalizedSchema schema;
    private Assignment assignment;
    private Consumer<MatchResult> callback;

    public EdgeMatcher(
        AppInfoWithClassHierarchy appInfo, KeepAnnotationMatcherPredicates predicates) {
      this(appInfo, predicates, null);
    }

    public EdgeMatcher(
        AppInfoWithClassHierarchy appInfo,
        KeepAnnotationMatcherPredicates predicates,
        ImmediateAppSubtypingInfo subtypingInfo) {
      this.appInfo = appInfo;
      this.predicates = predicates;
      this.subtypingInfo = subtypingInfo;
    }

    public void forEachMatch(KeepDeclaration declaration, Consumer<MatchResult> callback) {
      forEachMatch(new NormalizedSchema(declaration), null, callback);
    }

    private void forEachMatch(
        NormalizedSchema sharedSchema,
        List<DexProgramClass> classes,
        Consumer<MatchResult> callback) {
      this.schema = sharedSchema;
      this.assignment = new Assignment(sharedSchema);
      this.callback = callback;
      if (classes != null) {
        KeepClassItemPattern classPattern = sharedSchema.classes.get(0);
        for (DexProgramClass clazz : classes) {
          if (predicates.matchesClass(clazz, classPattern, appInfo)) {
            continueWithClass(0, clazz);
          }
        }
      } else {
        findMatchingClass(0);
      }
      this.schema = null;
      this.assignment = null;
      this.callback = null;
    }

    private static boolean usesFullClassScanAtRoot(
        NormalizedSchema schema, ImmediateAppSubtypingInfo subtypingInfo) {
      assert !schema.classes.isEmpty();
      KeepClassItemPattern firstClass = schema.classes.get(0);
      if (firstClass.getClassNamePattern().isExact()) {
        return false;
      }
      if (firstClass.getInstanceOfPattern().getClassNamePattern().isExact()
          && subtypingInfo != null) {
        return false;
      }
      return true;
    }

    private boolean matchesClassNameAndAnnotations(
        DexClass candidate, KeepClassItemPattern classPattern) {
      return predicates.matchesClassName(candidate.getType(), classPattern.getClassNamePattern())
          && predicates.matchesAnnotatedBy(
              candidate.annotations(), classPattern.getAnnotatedByPattern());
    }

    private void foundMatch() {
      MatchResult match = assignment.createMatch(schema);
      // We might not have found any matching consequences and this is not an actual match.
      if (!match.consequences.isEmpty()) {
        callback.accept(match);
      }
    }

    private void findMatchingClass(int classIndex) {
      if (classIndex == schema.classes.size()) {
        // All classes and their members are assigned, so report the assignment.
        foundMatch();
        return;
      }
      KeepClassItemPattern classPattern = schema.classes.get(classIndex);
      if (classPattern.getClassNamePattern().isExact()) {
        DexType type =
            appInfo
                .dexItemFactory()
                .lookupType(classPattern.getClassNamePattern().getExactDescriptor());
        if (type == null) {
          continueWithNoClass(classIndex);
          return;
        }
        DexClass clazz = appInfo.definitionFor(type);
        if (clazz == null) {
          continueWithNoClass(classIndex);
          return;
        }
        if (!predicates.matchesClass(clazz, classPattern, appInfo)) {
          continueWithNoClass(classIndex);
          return;
        }
        continueWithClass(classIndex, clazz);
        return;
      }
      // Avoid falling through to the full program-class scan (which walks supertypes upward on
      // every class in appInfo.classes()) when instanceOf targets an exact defined type; instead,
      // directly visit only its transitive program subclasses via subtypingInfo.
      KeepInstanceOfPattern instanceOfPattern = classPattern.getInstanceOfPattern();
      if (instanceOfPattern.getClassNamePattern().isExact() && subtypingInfo != null) {
        DexType superType =
            appInfo
                .dexItemFactory()
                .lookupType(instanceOfPattern.getClassNamePattern().getExactDescriptor());
        if (superType == null) {
          return;
        }
        DexClass superClass = appInfo.definitionFor(superType);
        // If superClass is null, superType is a missing class or interface referenced in the app.
        // ImmediateAppSubtypingInfo only indexes defined DexClasses, whereas traverseSuperTypes in
        // matchesClass still visits missing supertype DexTypes, so fall through in that case.
        if (superClass != null) {
          if (instanceOfPattern.isInclusive()
              && superClass.isProgramClass()
              && matchesClassNameAndAnnotations(superClass, classPattern)) {
            continueWithClass(classIndex, superClass);
          }
          subtypingInfo.forEachTransitiveProgramSubclass(
              superClass,
              subClass -> {
                if (matchesClassNameAndAnnotations(subClass, classPattern)) {
                  continueWithClass(classIndex, subClass);
                }
              });
          return;
        }
      }
      // TODO(b/323816623): This repeated iteration on all classes must be avoided.
      for (DexProgramClass clazz : appInfo.classes()) {
        if (predicates.matchesClass(clazz, classPattern, appInfo)) {
          continueWithClass(classIndex, clazz);
        }
      }
    }

    private void continueWithClass(int classIndex, DexClass clazz) {
      assignment.setClass(classIndex, clazz);
      IntList classMemberIndexList = schema.classMembers.get(classIndex);
      findMatchingMember(0, classMemberIndexList, clazz, classIndex + 1);
    }

    private void continueWithNoClass(int classIndex) {
      continueWithNoClassClearingMembers(classIndex, 0, null);
    }

    private void continueWithNoClassClearingMembers(
        int classIndex, int memberInHolderIndex, IntList memberIndexTranslation) {
      if (schema.isOptionalClass(classIndex)) {
        assignment.setClass(classIndex, null);
        for (int i = 0; i < memberInHolderIndex; i++) {
          assignment.setMember(memberIndexTranslation.getInt(i), null);
        }
        findMatchingClass(classIndex + 1);
      }
    }

    private void findMatchingMember(
        int memberInHolderIndex,
        IntList memberIndexTranslation,
        DexClass holder,
        int nextClassIndex) {
      if (memberInHolderIndex == memberIndexTranslation.size()) {
        // All members of this class are assigned, continue search for the next class.
        findMatchingClass(nextClassIndex);
        return;
      }
      int memberIndex = memberIndexTranslation.getInt(memberInHolderIndex);
      KeepMemberItemPattern memberItemPattern = schema.members.get(memberIndex);
      BooleanBox didContinue = new BooleanBox(false);
      Consumer<Definition> continueWithMember =
          m -> {
            didContinue.isTrue();
            continueWithMember(
                m, memberIndex, memberInHolderIndex + 1, memberIndexTranslation, nextClassIndex);
          };
      memberItemPattern
          .getMemberPattern()
          .match(
              generalMemberPattern -> {
                if (!holder.hasMethodsOrFields() && generalMemberPattern.isAllMembers()) {
                  // The empty class can only match the "all member" pattern but with no assignment.
                  continueWithMember.accept(holder);
                } else {
                  holder.forEachClassMember(
                      m -> {
                        if (predicates.matchesGeneralMember(
                            m.getDefinition(), generalMemberPattern)) {
                          continueWithMember.accept(m);
                        }
                      });
                }
              },
              fieldPattern ->
                  holder.forEachClassFieldMatching(
                      f -> predicates.matchesField(f, fieldPattern, appInfo), continueWithMember),
              methodPattern ->
                  holder.forEachClassMethodMatching(
                      m -> predicates.matchesMethod(m, methodPattern, appInfo),
                      continueWithMember));
      if (didContinue.isFalse()) {
        // No match for the member pattern existed, continue with next class.
        continueWithNoClassClearingMembers(
            nextClassIndex - 1, memberInHolderIndex, memberIndexTranslation);
      }
    }

    private void continueWithMember(
        Definition definition,
        int memberIndex,
        int nextMemberInHolderIndex,
        IntList memberIndexTranslation,
        int nextClassIndex) {
      if (definition.isMember()) {
        assignment.setMember(memberIndex, definition.asMember());
      } else {
        assert definition.isProgramClass();
        assert !definition.asProgramClass().hasMethodsOrFields();
        assignment.setEmptyMemberMatch(memberIndex);
      }
      findMatchingMember(
          nextMemberInHolderIndex,
          memberIndexTranslation,
          definition.getContextClass(),
          nextClassIndex);
    }
  }

  /**
   * The normalized edge schema maps an edge into integer indexed class patterns and member
   * patterns. The preconditions and consequences are then index references to these pattern. Each
   * index denotes the identity of an item, thus the same reference must share the same item found
   * by a pattern.
   */
  private static class NormalizedSchema {

    final KeepDeclaration declaration;
    final Reference2IntMap<KeepBindingSymbol> symbolToKey = new Reference2IntOpenHashMap<>();
    final List<KeepClassItemPattern> classes = new ArrayList<>();
    final List<KeepMemberItemPattern> members = new ArrayList<>();
    final List<IntList> classMembers = new ArrayList<>();
    final IntList preconditions = new IntArrayList();
    final IntList consequences = new IntArrayList();
    final List<KeepConstraints> constraints = new ArrayList<>();
    int preconditionClassesCount = -1;
    int preconditionMembersCount = -1;

    NormalizedSchema(KeepDeclaration declaration) {
      this.declaration = declaration;
      declaration.match(
          edge -> {
            edge.getPreconditions().forEach(this::addPrecondition);
            preconditionClassesCount = classes.size();
            preconditionMembersCount = members.size();
            edge.getConsequences().forEachTarget(this::addConsequence);
          },
          check -> {
            preconditionClassesCount = 0;
            preconditionMembersCount = 0;
            consequences.add(defineBindingReference(check.getItemReference()));
          });
    }

    private KeepItemPattern getItemForBinding(KeepBindingSymbol symbol) {
      KeepBindings bindings = declaration.apply(KeepEdge::getBindings, KeepCheck::getBindings);
      return bindings.get(symbol).getItem();
    }

    boolean isOptionalClass(int classIndex) {
      return classIndex >= preconditionClassesCount;
    }

    static boolean isClassKeyReference(int keyRef) {
      return keyRef >= 0;
    }

    private int encodeClassKey(int key) {
      assert isClassKeyReference(key);
      return key;
    }

    static int decodeClassKeyReference(int key) {
      assert isClassKeyReference(key);
      return key;
    }

    private int encodeMemberKey(int key) {
      assert key >= 0;
      return -(key + 1);
    }

    static int decodeMemberKeyReference(int key) {
      assert !isClassKeyReference(key);
      assert key < 0;
      return -(key + 1);
    }

    private int defineBindingReference(KeepBindingReference reference) {
      KeepBindingSymbol name = reference.getName();
      if (symbolToKey.containsKey(name)) {
        return symbolToKey.getInt(name);
      }
      int value = defineItemPattern(getItemForBinding(name));
      symbolToKey.put(name, value);
      return value;
    }

    private int defineItemPattern(KeepItemPattern item) {
      if (item.isClassItemPattern()) {
        int classIndex = classes.size();
        classes.add(item.asClassItemPattern());
        classMembers.add(new IntArrayList());
        return encodeClassKey(classIndex);
      } else {
        KeepBindingReference reference = item.asMemberItemPattern().getClassReference();
        int classIndex = defineBindingReference(reference);
        int memberIndex = members.size();
        members.add(item.asMemberItemPattern());
        classMembers.get(classIndex).add(memberIndex);
        return encodeMemberKey(memberIndex);
      }
    }

    void addPrecondition(KeepCondition condition) {
      preconditions.add(defineBindingReference(condition.getItem()));
    }

    private void addConsequence(KeepTarget target) {
      KeepBindingReference reference = target.getItem();
      consequences.add(defineBindingReference(reference));
      constraints.add(target.getConstraints());
    }
  }

  /**
   * The assignment contains the full matching of the pattern, if a matching was found. The
   * assignment is mutable and updated during the search. When a match is found the required
   * information must be copied over immediately by creating a match result.
   */
  private static class Assignment {

    final List<DexClass> classes;
    final List<DexClassAndMember<?, ?>> members;
    boolean hasEmptyMembers = false;

    private Assignment(NormalizedSchema schema) {
      classes = Arrays.asList(new DexClass[schema.classes.size()]);
      members = Arrays.asList(new DexClassAndMember<?, ?>[schema.members.size()]);
    }

    Definition getItemForReference(int keyReference) {
      if (NormalizedSchema.isClassKeyReference(keyReference)) {
        return classes.get(NormalizedSchema.decodeClassKeyReference(keyReference));
      }
      return members.get(NormalizedSchema.decodeMemberKeyReference(keyReference));
    }

    void setClass(int index, DexClass type) {
      classes.set(index, type);
    }

    void setMember(int index, DexClassAndMember<?, ?> member) {
      members.set(index, member);
    }

    void setEmptyMemberMatch(int index) {
      hasEmptyMembers = true;
      members.set(index, null);
    }

    MatchResult createMatch(NormalizedSchema schema) {
      return new MatchResult(
          schema.declaration,
          schema.preconditions.isEmpty()
              ? Collections.emptyList()
              : getItemList(schema.preconditions, false, Definition::isProgramDefinition),
          getItemList(schema.consequences, true, Definition::isProgramDefinition),
          getConstraints(schema));
    }

    @SuppressWarnings("unchecked")
    private <T extends Definition> List<T> getItemList(
        IntList indexReferences, boolean allowUnset, Predicate<Definition> predicate) {
      assert !indexReferences.isEmpty();
      List<T> definitions = new ArrayList<>(indexReferences.size());
      for (int i = 0; i < indexReferences.size(); i++) {
        Definition item = getItemForReference(indexReferences.getInt(i));
        assert item != null || hasEmptyMembers || allowUnset;
        if (item != null && predicate.test(item)) {
          definitions.add((T) item);
        }
      }
      return definitions;
    }

    private List<KeepConstraints> getConstraints(NormalizedSchema schema) {
      if (!hasEmptyMembers) {
        return schema.constraints;
      }
      // Since members may have a matching "empty" pattern, we need to prune those from the
      // constraints, so it matches the consequence list.
      ImmutableList.Builder<KeepConstraints> builder = ImmutableList.builder();
      for (int i = 0; i < schema.consequences.size(); i++) {
        Definition item = getItemForReference(schema.consequences.getInt(i));
        if (item != null) {
          builder.add(schema.constraints.get(i));
        }
      }
      return builder.build();
    }
  }
}
