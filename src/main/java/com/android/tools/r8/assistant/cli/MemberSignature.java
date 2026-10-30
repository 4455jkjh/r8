// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The signature of a field, method or constructor, with types as Java type names.
 *
 * <p>The after-call results of the reflection log summarize members as {@code "<type> <name>"} for
 * fields, {@code "<returnType> <name>(<parameters>)"} for methods and {@code
 * "<init>(<parameters>)"} for constructors.
 */
final class MemberSignature implements Comparable<MemberSignature> {

  enum Kind {
    FIELD,
    METHOD,
    CONSTRUCTOR
  }

  static final String UNKNOWN_TYPE = "***";

  private final Kind kind;
  private final String type;
  private final String name;
  private final List<String> parameters;

  private MemberSignature(Kind kind, String type, String name, List<String> parameters) {
    this.kind = kind;
    this.type = type;
    this.name = name;
    this.parameters = parameters;
  }

  static MemberSignature field(String type, String name) {
    return new MemberSignature(Kind.FIELD, normalizeType(type), name, null);
  }

  static MemberSignature method(String returnType, String name, List<String> parameters) {
    return new MemberSignature(Kind.METHOD, normalizeType(returnType), name, parameters);
  }

  static MemberSignature constructor(List<String> parameters) {
    return new MemberSignature(Kind.CONSTRUCTOR, "void", "<init>", parameters);
  }

  private static String normalizeType(String type) {
    return TypeNames.isMissing(type) ? UNKNOWN_TYPE : TypeNames.toTypeName(type);
  }

  /** Parses a member summary from an after-call result, or returns null if it is malformed. */
  static MemberSignature parse(Kind kind, String summary) {
    String text = summary.trim();
    if (kind == Kind.FIELD) {
      int space = text.lastIndexOf(' ');
      if (space <= 0 || text.indexOf('(') >= 0) {
        return null;
      }
      return field(text.substring(0, space).trim(), text.substring(space + 1).trim());
    }
    int open = text.indexOf('(');
    int close = text.lastIndexOf(')');
    if (open < 0 || close < open) {
      return null;
    }
    List<String> parameters = parseParameters(text.substring(open + 1, close));
    String head = text.substring(0, open).trim();
    if (kind == Kind.CONSTRUCTOR) {
      return constructor(parameters);
    }
    int space = head.lastIndexOf(' ');
    if (space <= 0) {
      return null;
    }
    return method(head.substring(0, space).trim(), head.substring(space + 1).trim(), parameters);
  }

  private static List<String> parseParameters(String text) {
    List<String> parameters = new ArrayList<>();
    if (text.trim().isEmpty()) {
      return parameters;
    }
    for (String parameter : text.split(",", -1)) {
      parameters.add(TypeNames.toTypeName(parameter.trim()));
    }
    return parameters;
  }

  Kind getKind() {
    return kind;
  }

  boolean isField() {
    return kind == Kind.FIELD;
  }

  boolean isConstructor() {
    return kind == Kind.CONSTRUCTOR;
  }

  /** The field type or return type, or {@link #UNKNOWN_TYPE} if not known. */
  String getType() {
    return type;
  }

  String getName() {
    return name;
  }

  /** The parameter types, or null for fields. */
  List<String> getParameters() {
    return parameters;
  }

  /** The member as it appears in the body of a keep rule, e.g. {@code int count;}. */
  String toRuleMember() {
    switch (kind) {
      case FIELD:
        return type + " " + name + ";";
      case METHOD:
        return type + " " + name + "(" + String.join(", ", parameters) + ");";
      default:
        return "<init>(" + String.join(", ", parameters) + ");";
    }
  }

  /** The member in the after-call result format, e.g. {@code void run(int, java.lang.String)}. */
  @Override
  public String toString() {
    switch (kind) {
      case FIELD:
        return type + " " + name;
      case METHOD:
        return type + " " + name + "(" + String.join(", ", parameters) + ")";
      default:
        return "<init>(" + String.join(", ", parameters) + ")";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MemberSignature)) {
      return false;
    }
    MemberSignature other = (MemberSignature) o;
    return kind == other.kind
        && type.equals(other.type)
        && name.equals(other.name)
        && Objects.equals(parameters, other.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, type, name, parameters);
  }

  @Override
  public int compareTo(MemberSignature other) {
    int result = kind.compareTo(other.kind);
    if (result != 0) {
      return result;
    }
    result = name.compareTo(other.name);
    if (result != 0) {
      return result;
    }
    return toString().compareTo(other.toString());
  }
}
