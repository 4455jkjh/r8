// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRequirement.Kind;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The keep requirements of the app according to a reflection log, and the facts about classes and
 * members that the log reveals.
 *
 * <p>Only requests that returned (or whose outcome is unknown) give requirements. Requests that
 * threw are optional probes, such as checks for optional libraries, and give no requirements.
 */
final class KeepRequirements {

  /** A member known to exist from the log. */
  static final class KnownMember {

    private final MemberSignature signature;
    private final boolean isPublic;
    private final boolean isVolatile;

    KnownMember(MemberSignature signature, boolean isPublic, boolean isVolatile) {
      this.signature = signature;
      this.isPublic = isPublic;
      this.isVolatile = isVolatile;
    }

    MemberSignature getSignature() {
      return signature;
    }

    boolean isPublic() {
      return isPublic;
    }

    boolean isVolatile() {
      return isVolatile;
    }
  }

  // Flags that are only reliable when the InnerClasses and EnclosingMethod attributes are kept.
  private static final Set<String> NESTING_FLAGS =
      ImmutableSet.of("ANONYMOUS_CLASS", "LOCAL_CLASS", "MEMBER_CLASS");

  private final Map<String, KeepRequirement> requirements = new HashMap<>();
  private final List<ReflectiveEvent> probes = new ArrayList<>();
  private final List<ReflectiveEvent> nestingAttributeEvents = new ArrayList<>();
  private final List<ReflectiveEvent> signatureAttributeEvents = new ArrayList<>();
  private final Map<String, Map<String, Boolean>> classFlags = new HashMap<>();
  private final Map<String, Set<String>> supertypes = new HashMap<>();
  private final Set<String> observedClasses = new TreeSet<>();

  private KeepRequirements() {}

  /** Analyzes the events, of which the ones for which {@code threw} holds are probes. */
  static KeepRequirements analyze(
      Collection<ReflectiveEvent> events, Predicate<ReflectiveEvent> threw) {
    KeepRequirements result = new KeepRequirements();
    Map<ReflectiveEvent, List<KeepRequirement>> threwEvents = new LinkedHashMap<>();
    for (ReflectiveEvent event : events) {
      result.recordObservedClasses(event);
      List<KeepRequirement> eventRequirements = requirementsFor(event);
      if (threw.test(event)) {
        threwEvents.put(event, eventRequirements);
        continue;
      }
      result.recordFacts(event);
      for (KeepRequirement requirement : eventRequirements) {
        result.add(requirement);
      }
    }
    // A request that threw is only an optional probe if nothing else requires its target.
    threwEvents.forEach(
        (event, eventRequirements) -> {
          boolean required =
              !eventRequirements.isEmpty()
                  && eventRequirements.stream().allMatch(result::isRequired);
          if (!required) {
            result.probes.add(event);
          }
        });
    return result;
  }

  private boolean isRequired(KeepRequirement requirement) {
    if (requirements.containsKey(requirement.getKey())) {
      return true;
    }
    if (!requirement.isMember()) {
      return false;
    }
    MemberSignature member = requirement.getMember();
    if (!member.getType().equals(MemberSignature.UNKNOWN_TYPE)) {
      return false;
    }
    for (KeepRequirement other : requirements.values()) {
      if (other.getKind() == requirement.getKind()
          && other.getClassName().equals(requirement.getClassName())
          && other.getMember().getName().equals(member.getName())
          && Objects.equals(other.getMember().getParameters(), member.getParameters())) {
        return true;
      }
    }
    return false;
  }

  private void add(KeepRequirement requirement) {
    KeepRequirement existing = requirements.putIfAbsent(requirement.getKey(), requirement);
    if (existing != null) {
      existing.merge(requirement);
    }
  }

  private static boolean isUserClass(String className) {
    return className != null && !TypeNames.isPlatformClass(className);
  }

  /** The requirements implied by a single event (regardless of its outcome). */
  static List<KeepRequirement> requirementsFor(ReflectiveEvent event) {
    List<KeepRequirement> result = new ArrayList<>();
    String target = TypeNames.getClassName(ReflectiveEvents.getTargetName(event));
    MemberSignature member = ReflectiveEvents.getMember(event);
    boolean isPublic = ReflectiveEvents.isPublicLookup(event);
    switch (event.getEventType()) {
      case CLASS_AS_SUBCLASS:
      case CLASS_CAST:
      case CLASS_FLAG:
      case CLASS_FOR_NAME:
      case CLASS_GET_COMPONENT_TYPE:
      case CLASS_GET_PACKAGE:
      case CLASS_GET_SUPERCLASS:
      case CLASS_IS_ASSIGNABLE_FROM:
      case CLASS_IS_INSTANCE:
      case CLASS_LOADER_LOAD_CLASS:
        for (String className : ReflectiveEvents.getClassNames(event)) {
          addClass(result, TypeNames.getClassName(className), false, event);
        }
        for (String className : ReflectiveEvents.getResultClassNames(event)) {
          addClass(result, TypeNames.getClassName(className), false, event);
        }
        break;
      case CLASS_GET_NAME:
        if (isUserClass(target)) {
          KeepRequirement requirement = KeepRequirement.forName(target);
          requirement.addEvent(event);
          result.add(requirement);
        }
        break;
      case CLASS_GET_CONSTRUCTOR:
      case CLASS_GET_DECLARED_CONSTRUCTOR:
      case CLASS_NEW_INSTANCE:
        // The class is kept as well, since the log does not show how the class was obtained.
        addClass(result, target, false, event);
        addMember(result, target, member, isPublic, false, event);
        break;
      case CLASS_GET_DECLARED_FIELD:
      case CLASS_GET_DECLARED_METHOD:
      case CLASS_GET_FIELD:
      case CLASS_GET_METHOD:
        addMember(result, target, member, isPublic, false, event);
        break;
      case ATOMIC_FIELD_UPDATER_NEW_UPDATER:
        addMember(result, target, member, false, true, event);
        break;
      case CLASS_GET_CONSTRUCTORS:
      case CLASS_GET_DECLARED_CONSTRUCTORS:
        addClass(result, target, false, event);
        addAllMembers(result, Kind.ALL_CONSTRUCTORS, target, event);
        break;
      case CLASS_GET_DECLARED_FIELDS:
      case CLASS_GET_FIELDS:
        addAllMembers(result, Kind.ALL_FIELDS, target, event);
        break;
      case CLASS_GET_DECLARED_METHODS:
      case CLASS_GET_METHODS:
        addAllMembers(result, Kind.ALL_METHODS, target, event);
        break;
      case SERVICE_LOADER_LOAD:
        addClass(result, target, false, event);
        addAllMembers(result, Kind.ALL_METHODS, target, event);
        break;
      case PROXY_NEW_PROXY_INSTANCE:
        for (String className : ReflectiveEvents.getClassNames(event)) {
          String interfaceName = TypeNames.getClassName(className);
          addClass(result, interfaceName, true, event);
          addAllMembers(result, Kind.ALL_METHODS, interfaceName, event);
        }
        break;
      case RESOURCE_BUNDLE_GET_BUNDLE:
        addBundleClasses(result, event);
        break;
      case CLASS_GET_GENERIC_INTERFACES:
      case CLASS_GET_GENERIC_SUPERCLASS:
        addGenericSignatures(result, target, event);
        break;
    }
    return result;
  }

  /**
   * The ResourceBundle class returned by getBundle and its nullary constructor. Bundles are looked
   * up by name for the locale of the device, so a class named after the base name is kept with the
   * classes of all other locales ({@code <base>} and {@code <base>_*}). Bundles of the platform,
   * such as java.util.PropertyResourceBundle for a properties file, need no rule.
   */
  private static void addBundleClasses(List<KeepRequirement> result, ReflectiveEvent event) {
    String baseName = event.asResourceBundleGetBundle().getBaseName();
    for (String name : ReflectiveEvents.getResultClassNames(event)) {
      String className = TypeNames.getClassName(name);
      if (!isUserClass(className)) {
        continue;
      }
      boolean isNamedAfterBase =
          !TypeNames.isMissing(baseName)
              && (className.equals(baseName) || className.startsWith(baseName + "_"));
      KeepRequirement classRequirement = KeepRequirement.forClass(className, false);
      KeepRequirement constructorRequirement =
          KeepRequirement.forMember(
              className, MemberSignature.constructor(Collections.emptyList()), false, false);
      for (KeepRequirement requirement : Arrays.asList(classRequirement, constructorRequirement)) {
        if (isNamedAfterBase) {
          requirement.setBundleBaseName(baseName);
        }
        requirement.addEvent(event);
        result.add(requirement);
      }
    }
  }

  /**
   * For each parameterized supertype {@code R<...>} returned by getGenericSuperclass or
   * getGenericInterfaces, the generic signature of the reflected class and, if R is part of the
   * app, R itself. A parameterized supertype whose raw type could not be resolved only gives the
   * generic signature. Supertypes that are not parameterized need nothing.
   */
  private static void addGenericSignatures(
      List<KeepRequirement> result, String target, ReflectiveEvent event) {
    if (!isUserClass(target)) {
      return;
    }
    boolean isInterface = event.getEventType() == ReflectiveEventType.CLASS_GET_GENERIC_INTERFACES;
    List<String> supertypes = new ArrayList<>(ReflectiveEvents.getParameterizedResultTypes(event));
    if (ReflectiveEvents.hasUnresolvedParameterizedResult(event)) {
      supertypes.add(null);
    }
    for (String supertype : supertypes) {
      KeepRequirement signature = KeepRequirement.forSignature(target, supertype, isInterface);
      signature.addEvent(event);
      result.add(signature);
      if (isUserClass(supertype)) {
        KeepRequirement genericSupertype = KeepRequirement.forGenericSupertype(supertype);
        genericSupertype.addEvent(event);
        result.add(genericSupertype);
      }
    }
  }

  private static void addClass(
      List<KeepRequirement> result, String className, boolean isInterface, ReflectiveEvent event) {
    if (isUserClass(className)) {
      KeepRequirement requirement = KeepRequirement.forClass(className, isInterface);
      requirement.addEvent(event);
      result.add(requirement);
    }
  }

  private static void addMember(
      List<KeepRequirement> result,
      String className,
      MemberSignature member,
      boolean isPublic,
      boolean isVolatile,
      ReflectiveEvent event) {
    if (member != null && isUserClass(className)) {
      KeepRequirement requirement =
          KeepRequirement.forMember(className, member, isPublic, isVolatile);
      requirement.addEvent(event);
      result.add(requirement);
    }
  }

  private static void addAllMembers(
      List<KeepRequirement> result, Kind kind, String className, ReflectiveEvent event) {
    if (isUserClass(className)) {
      boolean publicOnly = ReflectiveEvents.isPublicLookup(event);
      KeepRequirement requirement = KeepRequirement.forAllMembers(kind, className, publicOnly);
      requirement.addEvent(event);
      if (ReflectiveEvents.hasResultMembers(event)) {
        Set<MemberSignature> members = new TreeSet<>();
        for (String summary : ReflectiveEvents.getResultMembers(event)) {
          MemberSignature member = MemberSignature.parse(requirement.getAllMembersKind(), summary);
          if (member != null) {
            members.add(member);
          }
        }
        requirement.addResultMembers(members);
      }
      result.add(requirement);
    }
  }

  private void recordObservedClasses(ReflectiveEvent event) {
    for (String className : ReflectiveEvents.getClassNames(event)) {
      addObservedClass(TypeNames.getClassName(className));
    }
    for (String frame : ReflectiveEvents.getStack(event)) {
      if (frame != null) {
        addObservedClass(ReflectiveEvents.getFrameClassName(frame));
      }
    }
    for (String className : ReflectiveEvents.getResultClassNames(event)) {
      addObservedClass(TypeNames.getClassName(className));
    }
  }

  private void addObservedClass(String className) {
    if (isUserClass(className) && !className.isEmpty()) {
      observedClasses.add(className);
    }
  }

  private void recordFacts(ReflectiveEvent event) {
    String target = TypeNames.getClassName(ReflectiveEvents.getTargetName(event));
    if (!isUserClass(target)) {
      return;
    }
    String qualifier = ReflectiveEvents.getQualifier(event);
    boolean isFlag = event.isClassFlagEvent();
    if (isFlag) {
      for (Object result : event.getResults()) {
        if (result instanceof Boolean) {
          classFlags.computeIfAbsent(target, k -> new HashMap<>()).put(qualifier, (Boolean) result);
        }
      }
    }
    if (TypeNames.isNestedClassName(target)
        && ((event.isClassGetName()
                && ("SIMPLE_NAME".equals(qualifier) || "CANONICAL_NAME".equals(qualifier)))
            || (isFlag && NESTING_FLAGS.contains(qualifier)))) {
      nestingAttributeEvents.add(event);
    }
    if (ReflectiveEvents.isSupertypeLookup(event)) {
      for (String name : ReflectiveEvents.getResultClassNames(event)) {
        String supertype = TypeNames.getClassName(name);
        if (supertype != null) {
          supertypes.computeIfAbsent(target, k -> new TreeSet<>()).add(supertype);
        }
      }
    }
    if (event.isClassGetGenericSupertypes() && ReflectiveEvents.hasParameterizedResult(event)) {
      signatureAttributeEvents.add(event);
    }
  }

  /** All requirements, sorted by class name and kind. */
  List<KeepRequirement> getRequirements() {
    List<KeepRequirement> result = new ArrayList<>(requirements.values());
    Collections.sort(result);
    return result;
  }

  /** The requests that threw and whose targets are not otherwise required. */
  List<ReflectiveEvent> getProbes() {
    return probes;
  }

  /**
   * Events that depend on the InnerClasses and EnclosingMethod attributes, such as {@code
   * getSimpleName()} on a nested class.
   */
  List<ReflectiveEvent> getNestingAttributeEvents() {
    return nestingAttributeEvents;
  }

  /**
   * Events that depend on the Signature attribute, such as {@code getGenericSuperclass()} on an app
   * class.
   */
  List<ReflectiveEvent> getSignatureAttributeEvents() {
    return signatureAttributeEvents;
  }

  /** All user classes seen in the log, including the classes in stack frames. */
  Set<String> getObservedClasses() {
    return observedClasses;
  }

  /**
   * The supertypes of a class that the log shows, such as the results of getSuperclass() and
   * getGenericInterfaces(), and their supertypes in turn.
   */
  Set<String> getKnownSupertypes(String className) {
    Set<String> result = new TreeSet<>();
    Deque<String> worklist = new ArrayDeque<>();
    worklist.add(className);
    while (!worklist.isEmpty()) {
      for (String supertype : supertypes.getOrDefault(worklist.poll(), Collections.emptySet())) {
        if (result.add(supertype)) {
          worklist.add(supertype);
        }
      }
    }
    return result;
  }

  /** The value of a {@code Class.isXxx()} flag if the log shows it, or null. */
  Boolean getClassFlag(String className, String flag) {
    Map<String, Boolean> flags = classFlags.get(className);
    Boolean value = flags == null ? null : flags.get(flag);
    if (value != null || !flag.equals("INTERFACE")) {
      return value;
    }
    for (KeepRequirement requirement : requirements.values()) {
      if (requirement.getClassName().equals(className)) {
        if (requirement.isInterface()) {
          return true;
        }
        if (requirement.getKind() == Kind.CONSTRUCTOR) {
          // Interfaces have no constructors.
          return false;
        }
      }
    }
    return null;
  }

  /** The members of a class that the log shows to exist. */
  List<KnownMember> getKnownMembers(String className) {
    List<KnownMember> result = new ArrayList<>();
    for (KeepRequirement requirement : requirements.values()) {
      if (!requirement.getClassName().equals(className)) {
        continue;
      }
      if (requirement.isMember()) {
        result.add(
            new KnownMember(
                requirement.getMember(), requirement.isPublic(), requirement.isVolatile()));
      } else if (requirement.isAllMembers()) {
        for (MemberSignature member : requirement.getResultMembers()) {
          result.add(new KnownMember(member, requirement.isPublicOnly(), false));
        }
      }
    }
    return result;
  }
}
