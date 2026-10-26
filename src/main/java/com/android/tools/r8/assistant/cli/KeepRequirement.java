// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Something the app needs at runtime according to the reflection log, e.g. that a class can be
 * found by its name or that a field is present.
 */
final class KeepRequirement implements Comparable<KeepRequirement> {

  enum Kind {
    // The class is present and has its original name (e.g. Class.forName).
    CLASS,
    // The class has its original name when it is present (e.g. Class.getName).
    NAME,
    // The class keeps its generic signature, which names a parameterized supertype (e.g.
    // Class.getGenericSuperclass). With -keepattributes Signature, any keep rule that matches the
    // class keeps it, directly or through extends or implements of the supertype.
    SIGNATURE,
    // The class stays a class of its own (e.g. is not merged into its subclass), because it is the
    // parameterized supertype in the generic signature of another class. Any keep rule that matches
    // the class does this.
    GENERIC_SUPERTYPE,
    CONSTRUCTOR,
    ALL_CONSTRUCTORS,
    FIELD,
    ALL_FIELDS,
    METHOD,
    ALL_METHODS
  }

  private final Kind kind;
  private final String className;
  private final MemberSignature member;
  // For SIGNATURE: the parameterized supertype, e.g. com.google.gson.reflect.TypeToken.
  private final String supertype;
  private final boolean publicOnly;

  private boolean isPublic = false;
  private boolean isVolatile = false;
  private boolean isInterface = false;
  private boolean isSupertypeInterface = false;
  private String bundleBaseName = null;
  private boolean resultMembersKnown = false;
  private final Set<MemberSignature> resultMembers = new TreeSet<>();
  private final List<ReflectiveEvent> events = new ArrayList<>();

  private KeepRequirement(
      Kind kind, String className, MemberSignature member, String supertype, boolean publicOnly) {
    this.kind = kind;
    this.className = className;
    this.member = member;
    this.supertype = supertype;
    this.publicOnly = publicOnly;
  }

  static KeepRequirement forClass(String className, boolean isInterface) {
    KeepRequirement requirement = new KeepRequirement(Kind.CLASS, className, null, null, false);
    requirement.isInterface = isInterface;
    return requirement;
  }

  static KeepRequirement forName(String className) {
    return new KeepRequirement(Kind.NAME, className, null, null, false);
  }

  /** The generic signature of a class, which names the parameterized supertype. */
  static KeepRequirement forSignature(
      String className, String supertype, boolean isSupertypeInterface) {
    KeepRequirement requirement =
        new KeepRequirement(Kind.SIGNATURE, className, null, supertype, false);
    requirement.isSupertypeInterface = isSupertypeInterface;
    return requirement;
  }

  static KeepRequirement forGenericSupertype(String className) {
    return new KeepRequirement(Kind.GENERIC_SUPERTYPE, className, null, null, false);
  }

  static KeepRequirement forMember(
      String className, MemberSignature member, boolean isPublic, boolean isVolatile) {
    Kind kind;
    switch (member.getKind()) {
      case FIELD:
        kind = Kind.FIELD;
        break;
      case METHOD:
        kind = Kind.METHOD;
        break;
      default:
        kind = Kind.CONSTRUCTOR;
        break;
    }
    KeepRequirement requirement = new KeepRequirement(kind, className, member, null, false);
    requirement.isPublic = isPublic;
    requirement.isVolatile = isVolatile;
    return requirement;
  }

  static KeepRequirement forAllMembers(Kind kind, String className, boolean publicOnly) {
    assert kind == Kind.ALL_CONSTRUCTORS || kind == Kind.ALL_FIELDS || kind == Kind.ALL_METHODS;
    return new KeepRequirement(kind, className, null, null, publicOnly);
  }

  /** The key identifying equal requirements, which are merged. */
  String getKey() {
    return className
        + " "
        + kind
        + (member == null ? "" : " " + member)
        + (supertype == null ? "" : " " + supertype)
        + (publicOnly ? " public" : "");
  }

  void merge(KeepRequirement other) {
    assert getKey().equals(other.getKey());
    isPublic |= other.isPublic;
    isVolatile |= other.isVolatile;
    isInterface |= other.isInterface;
    isSupertypeInterface |= other.isSupertypeInterface;
    if (bundleBaseName == null) {
      bundleBaseName = other.bundleBaseName;
    }
    if (other.resultMembersKnown) {
      resultMembersKnown = true;
      resultMembers.addAll(other.resultMembers);
    }
    for (ReflectiveEvent event : other.events) {
      addEvent(event);
    }
  }

  void addEvent(ReflectiveEvent event) {
    if (!events.contains(event)) {
      events.add(event);
    }
  }

  void addResultMembers(Set<MemberSignature> members) {
    resultMembersKnown = true;
    resultMembers.addAll(members);
  }

  Kind getKind() {
    return kind;
  }

  String getClassName() {
    return className;
  }

  boolean isClassLevel() {
    return kind == Kind.CLASS
        || kind == Kind.NAME
        || kind == Kind.SIGNATURE
        || kind == Kind.GENERIC_SUPERTYPE;
  }

  /**
   * For {@link Kind#SIGNATURE}, the parameterized supertype named by the generic signature, or null
   * if its raw type could not be resolved at runtime.
   */
  String getSupertype() {
    return supertype;
  }

  /** For {@link Kind#SIGNATURE}, true if the supertype is an interface. */
  boolean isSupertypeInterface() {
    return isSupertypeInterface;
  }

  /**
   * For {@link Kind#SIGNATURE}, true if the supertype is part of the app, so that the class can be
   * kept through it ({@code class * extends <supertype>}) instead of by its name, which changes
   * with the code for anonymous classes.
   */
  boolean hasProgramSupertype() {
    return kind == Kind.SIGNATURE && supertype != null && !TypeNames.isPlatformClass(supertype);
  }

  /**
   * The base name of the ResourceBundle family that the class belongs to, or null. A family is kept
   * with a single rule for the base name and all of its locales.
   */
  String getBundleBaseName() {
    return bundleBaseName;
  }

  void setBundleBaseName(String bundleBaseName) {
    this.bundleBaseName = bundleBaseName;
  }

  boolean isMember() {
    return kind == Kind.CONSTRUCTOR || kind == Kind.FIELD || kind == Kind.METHOD;
  }

  boolean isAllMembers() {
    return kind == Kind.ALL_CONSTRUCTORS || kind == Kind.ALL_FIELDS || kind == Kind.ALL_METHODS;
  }

  /** The member for {@link Kind#CONSTRUCTOR}, {@link Kind#FIELD} and {@link Kind#METHOD}. */
  MemberSignature getMember() {
    return member;
  }

  /** The kind of members covered by an all-members requirement. */
  MemberSignature.Kind getAllMembersKind() {
    switch (kind) {
      case ALL_CONSTRUCTORS:
        return MemberSignature.Kind.CONSTRUCTOR;
      case ALL_FIELDS:
        return MemberSignature.Kind.FIELD;
      case ALL_METHODS:
        return MemberSignature.Kind.METHOD;
      default:
        throw new IllegalStateException("Not an all-members requirement: " + kind);
    }
  }

  /** For all-members requirements, true if only public members are needed (e.g. getFields()). */
  boolean isPublicOnly() {
    return publicOnly;
  }

  /** True if the member is known to be public (e.g. found by getField()). */
  boolean isPublic() {
    return isPublic || publicOnly;
  }

  /** True if the field must stay volatile (e.g. for an atomic field updater). */
  boolean isVolatile() {
    return isVolatile;
  }

  /** True if the class is known to be an interface (e.g. a proxy interface). */
  boolean isInterface() {
    return isInterface;
  }

  /** True if the members returned by the bulk lookup are known from after-call results. */
  boolean hasResultMembers() {
    return resultMembersKnown;
  }

  Set<MemberSignature> getResultMembers() {
    return Collections.unmodifiableSet(resultMembers);
  }

  List<ReflectiveEvent> getEvents() {
    return events;
  }

  /** The member as it appears in the body of a keep rule, e.g. {@code volatile int state;}. */
  String toRuleMember() {
    switch (kind) {
      case CONSTRUCTOR:
      case METHOD:
        return member.toRuleMember();
      case FIELD:
        return (isVolatile ? "volatile " : "") + member.toRuleMember();
      case ALL_CONSTRUCTORS:
        return (publicOnly ? "public " : "") + "<init>(...);";
      case ALL_FIELDS:
        return (publicOnly ? "public " : "") + "<fields>;";
      case ALL_METHODS:
        return (publicOnly ? "public " : "") + "<methods>;";
      default:
        throw new IllegalStateException("Not a member requirement: " + kind);
    }
  }

  /** A short human readable description, e.g. {@code field com.example.Foo.count}. */
  String describe() {
    switch (kind) {
      case CLASS:
        return (isInterface ? "interface " : "class ") + className;
      case NAME:
        return "name of " + className;
      case SIGNATURE:
        return "generic signature of "
            + className
            + " ("
            + (isSupertypeInterface ? "implements " : "extends ")
            + (supertype == null
                ? "a parameterized type whose raw type is unknown"
                : supertype + "<...>")
            + ")";
      case GENERIC_SUPERTYPE:
        return "generic supertype " + className;
      case CONSTRUCTOR:
        return "constructor " + className + "(" + String.join(", ", member.getParameters()) + ")";
      case FIELD:
        return "field " + className + "." + member.getName();
      case METHOD:
        return "method "
            + className
            + "."
            + member.getName()
            + "("
            + String.join(", ", member.getParameters())
            + ")";
      case ALL_CONSTRUCTORS:
        return "all " + (publicOnly ? "public " : "") + "constructors of " + className;
      case ALL_FIELDS:
        return "all " + (publicOnly ? "public " : "") + "fields of " + className;
      default:
        return "all " + (publicOnly ? "public " : "") + "methods of " + className;
    }
  }

  /**
   * For the requirements of generic signatures, which any keep rule that matches the class meets,
   * describes the rule that is missing when no rule meets it, or returns null.
   */
  String describeMissingRule() {
    switch (kind) {
      case SIGNATURE:
        if (supertype == null) {
          return "no keep rule matches " + className;
        }
        return "no keep rule matches "
            + className
            + ", directly or through "
            + (isSupertypeInterface ? "implements " : "extends ")
            + supertype;
      case GENERIC_SUPERTYPE:
        return "no keep rule matches " + className;
      default:
        return null;
    }
  }

  @Override
  public int compareTo(KeepRequirement other) {
    int result = className.compareTo(other.className);
    if (result != 0) {
      return result;
    }
    result = kind.compareTo(other.kind);
    if (result != 0) {
      return result;
    }
    return getKey().compareTo(other.getKey());
  }

  @Override
  public String toString() {
    return describe();
  }
}
