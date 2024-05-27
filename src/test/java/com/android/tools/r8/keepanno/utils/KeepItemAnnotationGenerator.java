// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.classFromTypeName;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cfmethodgeneration.CodeGenerationBase;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class KeepItemAnnotationGenerator {

  public static void main(String[] args) throws IOException {
    Generator.class.getClassLoader().setDefaultAssertionStatus(true);
    Generator.run(
        (file, content) -> {
          try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  public static class EnumReference {
    public final ClassReference enumClass;
    public final String enumValue;

    public EnumReference(ClassReference enumClass, String enumValue) {
      this.enumClass = enumClass;
      this.enumValue = enumValue;
    }

    public String name() {
      return enumValue;
    }
  }

  private static final ClassReference JAVA_STRING = classFromClass(String.class);
  private static final ClassReference JAVA_RETENTION_POLICY = classFromClass(RetentionPolicy.class);

  private static final String PKG = "com.android.tools.r8.keepanno.";
  private static final String AST_PKG = PKG + "ast.";
  private static final String ANNO_PKG = PKG + "annotations.";

  private static final ClassReference ANNOTATION_CONSTANTS = astClass("AnnotationConstants");

  static final ClassReference STRING_PATTERN = annoClass("StringPattern");
  static final ClassReference TYPE_PATTERN = annoClass("TypePattern");
  static final ClassReference CLASS_NAME_PATTERN = annoClass("ClassNamePattern");
  static final ClassReference INSTANCE_OF_PATTERN = annoClass("InstanceOfPattern");
  static final ClassReference ANNOTATION_PATTERN = annoClass("AnnotationPattern");
  static final ClassReference USES_REFLECTION = annoClass("UsesReflection");
  static final ClassReference USED_BY_REFLECTION = annoClass("UsedByReflection");
  static final ClassReference USED_BY_NATIVE = annoClass("UsedByNative");
  static final ClassReference CHECK_REMOVED = annoClass("CheckRemoved");
  static final ClassReference CHECK_OPTIMIZED_OUT = annoClass("CheckOptimizedOut");
  static final ClassReference EXTRACTED_KEEP_ANNOTATIONS = annoClass("ExtractedKeepAnnotations");
  static final ClassReference EXTRACTED_KEEP_ANNOTATION = annoClass("ExtractedKeepAnnotation");
  static final ClassReference KEEP_EDGE = annoClass("KeepEdge");
  static final ClassReference KEEP_BINDING = annoClass("KeepBinding");
  static final ClassReference KEEP_TARGET = annoClass("KeepTarget");
  static final ClassReference KEEP_CONDITION = annoClass("KeepCondition");
  static final ClassReference KEEP_FOR_API = annoClass("KeepForApi");

  public static final ClassReference KEEP_ITEM_KIND = annoClass("KeepItemKind");
  private static final EnumReference KIND_ONLY_CLASS = enumRef(KEEP_ITEM_KIND, "ONLY_CLASS");
  private static final EnumReference KIND_ONLY_MEMBERS = enumRef(KEEP_ITEM_KIND, "ONLY_MEMBERS");
  private static final EnumReference KIND_ONLY_METHODS = enumRef(KEEP_ITEM_KIND, "ONLY_METHODS");
  private static final EnumReference KIND_ONLY_FIELDS = enumRef(KEEP_ITEM_KIND, "ONLY_FIELDS");
  private static final EnumReference KIND_CLASS_AND_MEMBERS =
      enumRef(KEEP_ITEM_KIND, "CLASS_AND_MEMBERS");
  private static final EnumReference KIND_CLASS_AND_METHODS =
      enumRef(KEEP_ITEM_KIND, "CLASS_AND_METHODS");
  private static final EnumReference KIND_CLASS_AND_FIELDS =
      enumRef(KEEP_ITEM_KIND, "CLASS_AND_FIELDS");
  public static final List<EnumReference> KEEP_ITEM_KIND_VALUES =
      ImmutableList.of(
          KIND_ONLY_CLASS,
          KIND_ONLY_MEMBERS,
          KIND_ONLY_METHODS,
          KIND_ONLY_FIELDS,
          KIND_CLASS_AND_MEMBERS,
          KIND_CLASS_AND_METHODS,
          KIND_CLASS_AND_FIELDS);

  static final ClassReference KEEP_CONSTRAINT = annoClass("KeepConstraint");
  private static final EnumReference CONSTRAINT_LOOKUP = enumRef(KEEP_CONSTRAINT, "LOOKUP");
  private static final EnumReference CONSTRAINT_NAME = enumRef(KEEP_CONSTRAINT, "NAME");
  private static final EnumReference CONSTRAINT_VISIBILITY_RELAX =
      enumRef(KEEP_CONSTRAINT, "VISIBILITY_RELAX");
  private static final EnumReference CONSTRAINT_VISIBILITY_RESTRICT =
      enumRef(KEEP_CONSTRAINT, "VISIBILITY_RESTRICT");
  private static final EnumReference CONSTRAINT_VISIBILITY_INVARIANT =
      enumRef(KEEP_CONSTRAINT, "VISIBILITY_INVARIANT");
  private static final EnumReference CONSTRAINT_CLASS_INSTANTIATE =
      enumRef(KEEP_CONSTRAINT, "CLASS_INSTANTIATE");
  private static final EnumReference CONSTRAINT_METHOD_INVOKE =
      enumRef(KEEP_CONSTRAINT, "METHOD_INVOKE");
  private static final EnumReference CONSTRAINT_FIELD_GET = enumRef(KEEP_CONSTRAINT, "FIELD_GET");
  private static final EnumReference CONSTRAINT_FIELD_SET = enumRef(KEEP_CONSTRAINT, "FIELD_SET");
  private static final EnumReference CONSTRAINT_METHOD_REPLACE =
      enumRef(KEEP_CONSTRAINT, "METHOD_REPLACE");
  private static final EnumReference CONSTRAINT_FIELD_REPLACE =
      enumRef(KEEP_CONSTRAINT, "FIELD_REPLACE");
  private static final EnumReference CONSTRAINT_NEVER_INLINE =
      enumRef(KEEP_CONSTRAINT, "NEVER_INLINE");
  private static final EnumReference CONSTRAINT_CLASS_OPEN_HIERARCHY =
      enumRef(KEEP_CONSTRAINT, "CLASS_OPEN_HIERARCHY");
  private static final EnumReference CONSTRAINT_GENERIC_SIGNATURE =
      enumRef(KEEP_CONSTRAINT, "GENERIC_SIGNATURE");
  static final List<EnumReference> KEEP_CONSTRAINT_VALUES =
      ImmutableList.of(
          CONSTRAINT_LOOKUP,
          CONSTRAINT_NAME,
          CONSTRAINT_VISIBILITY_RELAX,
          CONSTRAINT_VISIBILITY_RESTRICT,
          CONSTRAINT_VISIBILITY_INVARIANT,
          CONSTRAINT_CLASS_INSTANTIATE,
          CONSTRAINT_METHOD_INVOKE,
          CONSTRAINT_FIELD_GET,
          CONSTRAINT_FIELD_SET,
          CONSTRAINT_METHOD_REPLACE,
          CONSTRAINT_FIELD_REPLACE,
          CONSTRAINT_NEVER_INLINE,
          CONSTRAINT_CLASS_OPEN_HIERARCHY,
          CONSTRAINT_GENERIC_SIGNATURE);

  static final ClassReference MEMBER_ACCESS_FLAGS = annoClass("MemberAccessFlags");
  private static final EnumReference MEMBER_ACCESS_PUBLIC = enumRef(MEMBER_ACCESS_FLAGS, "PUBLIC");
  private static final EnumReference MEMBER_ACCESS_PROTECTED =
      enumRef(MEMBER_ACCESS_FLAGS, "PROTECTED");
  private static final EnumReference MEMBER_ACCESS_PACKAGE_PRIVATE =
      enumRef(MEMBER_ACCESS_FLAGS, "PACKAGE_PRIVATE");
  private static final EnumReference MEMBER_ACCESS_PRIVATE =
      enumRef(MEMBER_ACCESS_FLAGS, "PRIVATE");
  private static final EnumReference MEMBER_ACCESS_STATIC = enumRef(MEMBER_ACCESS_FLAGS, "STATIC");
  private static final EnumReference MEMBER_ACCESS_FINAL = enumRef(MEMBER_ACCESS_FLAGS, "FINAL");
  private static final EnumReference MEMBER_ACCESS_SYNTHETIC =
      enumRef(MEMBER_ACCESS_FLAGS, "SYNTHETIC");
  static final List<EnumReference> MEMBER_ACCESS_VALUES =
      ImmutableList.of(
          MEMBER_ACCESS_PUBLIC,
          MEMBER_ACCESS_PROTECTED,
          MEMBER_ACCESS_PACKAGE_PRIVATE,
          MEMBER_ACCESS_PRIVATE,
          MEMBER_ACCESS_STATIC,
          MEMBER_ACCESS_FINAL,
          MEMBER_ACCESS_SYNTHETIC);

  static final ClassReference METHOD_ACCESS_FLAGS = annoClass("MethodAccessFlags");
  private static final EnumReference METHOD_ACCESS_SYNCHRONIZED =
      enumRef(METHOD_ACCESS_FLAGS, "SYNCHRONIZED");
  private static final EnumReference METHOD_ACCESS_BRIDGE = enumRef(METHOD_ACCESS_FLAGS, "BRIDGE");
  private static final EnumReference METHOD_ACCESS_NATIVE = enumRef(METHOD_ACCESS_FLAGS, "NATIVE");
  private static final EnumReference METHOD_ACCESS_ABSTRACT =
      enumRef(METHOD_ACCESS_FLAGS, "ABSTRACT");
  private static final EnumReference METHOD_ACCESS_STRICT_FP =
      enumRef(METHOD_ACCESS_FLAGS, "STRICT_FP");
  static final List<EnumReference> METHOD_ACCESS_VALUES =
      ImmutableList.of(
          METHOD_ACCESS_SYNCHRONIZED,
          METHOD_ACCESS_BRIDGE,
          METHOD_ACCESS_NATIVE,
          METHOD_ACCESS_ABSTRACT,
          METHOD_ACCESS_STRICT_FP);

  static final ClassReference FIELD_ACCESS_FLAGS = annoClass("FieldAccessFlags");
  private static final EnumReference FIELD_ACCESS_VOLATILE =
      enumRef(FIELD_ACCESS_FLAGS, "VOLATILE");
  private static final EnumReference FIELD_ACCESS_TRANSIENT =
      enumRef(FIELD_ACCESS_FLAGS, "TRANSIENT");
  static final List<EnumReference> FIELD_ACCESS_VALUES =
      ImmutableList.of(FIELD_ACCESS_VOLATILE, FIELD_ACCESS_TRANSIENT);

  private static final String DEFAULT_INVALID_STRING_PATTERN =
      "@" + simpleName(STRING_PATTERN) + "(exact = \"\")";
  private static final String DEFAULT_INVALID_TYPE_PATTERN =
      "@" + simpleName(TYPE_PATTERN) + "(name = \"\")";
  private static final String DEFAULT_INVALID_CLASS_NAME_PATTERN =
      "@" + simpleName(CLASS_NAME_PATTERN) + "(simpleName = \"\")";
  private static final String DEFAULT_ANY_INSTANCE_OF_PATTERN =
      "@" + simpleName(INSTANCE_OF_PATTERN) + "()";

  private static ClassReference astClass(String simpleName) {
    return classFromTypeName(AST_PKG + simpleName);
  }

  private static ClassReference annoClass(String simpleName) {
    return classFromTypeName(ANNO_PKG + simpleName);
  }

  private static EnumReference enumRef(ClassReference enumClass, String valueName) {
    return new EnumReference(enumClass, valueName);
  }

  public static String quote(String str) {
    return "\"" + str + "\"";
  }

  public static String simpleName(ClassReference clazz) {
    String binaryName = clazz.getBinaryName();
    return binaryName.substring(binaryName.lastIndexOf('/') + 1);
  }

  public static class GroupMember extends DocPrinterBase<GroupMember> {

    final String name;
    String valueType = null;
    String valueDefault = null;

    GroupMember(String name) {
      this.name = name;
    }

    public GroupMember setType(String type) {
      valueType = type;
      return this;
    }

    public GroupMember setValue(String value) {
      valueDefault = value;
      return this;
    }

    @Override
    public GroupMember self() {
      return this;
    }

    void generate(Generator generator) {
      printDoc(generator::println);
      if (isDeprecated()) {
        generator.println("@Deprecated");
      }
      if (valueDefault == null) {
        generator.println(valueType + " " + name + "();");
      } else {
        generator.println(valueType + " " + name + "() default " + valueDefault + ";");
      }
    }

    public void generateConstants(Generator generator) {
      generator.println("public static final String " + name + " = " + quote(name) + ";");
    }

    public GroupMember requiredValue(ClassReference type) {
      assert valueDefault == null;
      return setType(simpleName(type));
    }

    public GroupMember requiredArrayValue(ClassReference type) {
      assert valueDefault == null;
      return setType(simpleName(type) + "[]");
    }

    public GroupMember requiredStringValue() {
      return requiredValue(JAVA_STRING);
    }

    public GroupMember defaultBooleanValue(boolean value) {
      setType("boolean");
      return setValue(value ? "true" : "false");
    }

    public GroupMember defaultValue(ClassReference type, String value) {
      setType(simpleName(type));
      return setValue(value);
    }

    public GroupMember defaultArrayValue(ClassReference type, String value) {
      setType(simpleName(type) + "[]");
      return setValue("{" + value + "}");
    }

    public GroupMember defaultEmptyString() {
      return defaultValue(JAVA_STRING, quote(""));
    }

    public GroupMember defaultObjectClass() {
      return setType("Class<?>").setValue("Object.class");
    }

    public GroupMember defaultArrayEmpty(ClassReference type) {
      return defaultArrayValue(type, "");
    }
  }

  public static class Group {

    final String name;
    final List<GroupMember> members = new ArrayList<>();
    final List<String> footers = new ArrayList<>();
    final LinkedHashMap<String, Group> mutuallyExclusiveGroups = new LinkedHashMap<>();

    boolean mutuallyExclusiveWithOtherGroups = false;

    private Group(String name) {
      this.name = name;
    }

    Group allowMutuallyExclusiveWithOtherGroups() {
      mutuallyExclusiveWithOtherGroups = true;
      return this;
    }

    Group addMember(GroupMember member) {
      members.add(member);
      return this;
    }

    Group addDocFooterParagraph(String footer) {
      footers.add(footer);
      return this;
    }

    void generate(Generator generator) {
      assert !members.isEmpty();
      for (GroupMember member : members) {
        if (member != members.get(0)) {
          generator.println();
        }
        List<String> mutuallyExclusiveProperties = new ArrayList<>();
        for (GroupMember other : members) {
          if (!member.name.equals(other.name)) {
            mutuallyExclusiveProperties.add(other.name);
          }
        }
        mutuallyExclusiveGroups.forEach(
            (unused, group) -> {
              group.members.forEach(m -> mutuallyExclusiveProperties.add(m.name));
            });
        if (mutuallyExclusiveProperties.size() == 1) {
          member.addParagraph(
              "Mutually exclusive with the property `"
                  + mutuallyExclusiveProperties.get(0)
                  + "` also defining "
                  + name
                  + ".");
        } else if (mutuallyExclusiveProperties.size() > 1) {
          member.addParagraph(
              "Mutually exclusive with the following other properties defining " + name + ":");
          member.addUnorderedList(mutuallyExclusiveProperties);
        }
        footers.forEach(member::addParagraph);
        member.generate(generator);
      }
    }

    void generateConstants(Generator generator) {
      if (mutuallyExclusiveWithOtherGroups || members.size() > 1) {
        StringBuilder camelCaseName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
          char c = name.charAt(i);
          if (c == '-') {
            c = Character.toUpperCase(name.charAt(++i));
          }
          camelCaseName.append(c);
        }
        generator.println(
            "public static final String " + camelCaseName + "Group = " + quote(name) + ";");
      }
      for (GroupMember member : members) {
        member.generateConstants(generator);
      }
    }

    public void addMutuallyExclusiveGroups(Group... groups) {
      for (Group group : groups) {
        assert mutuallyExclusiveWithOtherGroups || group.mutuallyExclusiveWithOtherGroups;
        mutuallyExclusiveGroups.computeIfAbsent(
            group.name,
            k -> {
              // Mutually exclusive is bidirectional so link in with other group.
              group.mutuallyExclusiveGroups.put(name, this);
              return group;
            });
      }
    }
  }

  public static class Generator {

    private static final List<Class<?>> ANNOTATION_IMPORTS =
        ImmutableList.of(ElementType.class, Retention.class, RetentionPolicy.class, Target.class);

    private final PrintStream writer;
    private int indent = 0;

    public Generator(PrintStream writer) {
      this.writer = writer;
    }

    public void withIndent(Runnable fn) {
      indent += 2;
      fn.run();
      indent -= 2;
    }

    private void println() {
      println("");
    }

    public void println(String line) {
      // Don't indent empty lines.
      if (line.length() > 0) {
        writer.print(Strings.repeat(" ", indent));
      }
      writer.print(line);
      writer.print('\n');
    }

    private void printCopyRight(int year) {
      println(
          CodeGenerationBase.getHeaderString(
              year, KeepItemAnnotationGenerator.class.getSimpleName()));
    }

    private void printPackage(String pkg) {
      println("package com.android.tools.r8.keepanno." + pkg + ";");
      println();
    }

    private void printImports(Class<?>... imports) {
      printImports(Arrays.asList(imports));
    }

    private void printImports(List<Class<?>> imports) {
      for (Class<?> clazz : imports) {
        println("import " + clazz.getCanonicalName() + ";");
      }
      println();
    }

    private static String KIND_GROUP = "kind";
    private static String CONSTRAINTS_GROUP = "constraints";
    private static String CLASS_GROUP = "class";
    private static String CLASS_NAME_GROUP = "class-name";
    private static String INSTANCE_OF_GROUP = "instance-of";
    private static String CLASS_ANNOTATED_BY_GROUP = "class-annotated-by";
    private static String MEMBER_ANNOTATED_BY_GROUP = "member-annotated-by";
    private static String METHOD_ANNOTATED_BY_GROUP = "method-annotated-by";
    private static String FIELD_ANNOTATED_BY_GROUP = "field-annotated-by";
    private static String ANNOTATION_NAME_GROUP = "annotation-name";

    private Group createDescriptionGroup() {
      return new Group("description")
          .addMember(
              new GroupMember("description")
                  .setDocTitle("Optional description to document the reason for this annotation.")
                  .setDocReturn("The descriptive message. Defaults to no description.")
                  .defaultEmptyString());
    }

    private Group createBindingsGroup() {
      return new Group("bindings")
          .addMember(new GroupMember("bindings").defaultArrayEmpty(KEEP_BINDING));
    }

    private Group createPreconditionsGroup() {
      return new Group("preconditions")
          .addMember(
              new GroupMember("preconditions")
                  .setDocTitle(
                      "Conditions that should be satisfied for the annotation to be in effect.")
                  .setDocReturn(
                      "The list of preconditions. "
                          + "Defaults to no conditions, thus trivially/unconditionally satisfied.")
                  .defaultArrayEmpty(KEEP_CONDITION));
    }

    private Group createConsequencesGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("consequences")
                  .setDocTitle("Consequences that must be kept if the annotation is in effect.")
                  .setDocReturn("The list of target consequences.")
                  .requiredArrayValue(KEEP_TARGET));
    }

    private Group createConsequencesAsValueGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("value")
                  .setDocTitle("Consequences that must be kept if the annotation is in effect.")
                  .setDocReturn("The list of target consequences.")
                  .requiredArrayValue(KEEP_TARGET));
    }

    private Group createAdditionalPreconditionsGroup() {
      return new Group("additional-preconditions")
          .addMember(
              new GroupMember("additionalPreconditions")
                  .setDocTitle("Additional preconditions for the annotation to be in effect.")
                  .setDocReturn(
                      "The list of additional preconditions. "
                          + "Defaults to no additional preconditions.")
                  .defaultArrayEmpty(KEEP_CONDITION));
    }

    private Group createAdditionalTargetsGroup(String docTitle) {
      return new Group("additional-targets")
          .addMember(
              new GroupMember("additionalTargets")
                  .setDocTitle(docTitle)
                  .setDocReturn(
                      "List of additional target consequences. "
                          + "Defaults to no additional target consequences.")
                  .defaultArrayEmpty(KEEP_TARGET));
    }

    private Group stringPatternExactGroup() {
      return new Group("string-exact-pattern")
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("exact")
                  .setDocTitle("Exact string content.")
                  .addParagraph("For example, {@code \"foo\"} or {@code \"java.lang.String\"}.")
                  .defaultEmptyString());
    }

    private Group stringPatternPrefixGroup() {
      return new Group("string-prefix-pattern")
          .addMember(
              new GroupMember("startsWith")
                  .setDocTitle("Matches strings beginning with the given prefix.")
                  .addParagraph(
                      "For example, {@code \"get\"} to match strings such as {@code"
                          + " \"getMyValue\"}.")
                  .defaultEmptyString());
    }

    private Group stringPatternSuffixGroup() {
      return new Group("string-suffix-pattern")
          .addMember(
              new GroupMember("endsWith")
                  .setDocTitle("Matches strings ending with the given suffix.")
                  .addParagraph(
                      "For example, {@code \"Setter\"} to match strings such as {@code"
                          + " \"myValueSetter\"}.")
                  .defaultEmptyString());
    }

    public Group typePatternGroup() {
      return new Group("type-pattern")
          .addMember(
              new GroupMember("name")
                  .setDocTitle("Exact type name as a string.")
                  .addParagraph("For example, {@code \"long\"} or {@code \"java.lang.String\"}.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("constant")
                  .setDocTitle("Exact type from a class constant.")
                  .addParagraph("For example, {@code String.class}.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("classNamePattern")
                  .setDocTitle("Classes matching the class-name pattern.")
                  .defaultValue(CLASS_NAME_PATTERN, DEFAULT_INVALID_CLASS_NAME_PATTERN));
      // TODO(b/248408342): Add more injections on type pattern variants.
      // /** Exact type name as a string to match any array with that type as member. */
      // String arrayOf() default "";
      //
      // /** Exact type as a class constant to match any array with that type as member. */
      // Class<?> arrayOfConstant() default TypePattern.class;
      //
      // /** If true, the pattern matches any primitive type. Such as, boolean, int, etc. */
      // boolean anyPrimitive() default false;
      //
      // /** If true, the pattern matches any array type. */
      // boolean anyArray() default false;
      //
      // /** If true, the pattern matches any class type. */
      // boolean anyClass() default false;
      //
      // /** If true, the pattern matches any reference type, namely: arrays or classes. */
      // boolean anyReference() default false;
    }

    private Group instanceOfPatternInclusive() {
      return new Group("instance-of-inclusive")
          .addMember(
              new GroupMember("inclusive")
                  .setDocTitle("True if the pattern should include the directly matched classes.")
                  .addParagraph(
                      "If false, the pattern is exclusive and only matches classes that are",
                      "strict subclasses of the pattern.")
                  .defaultBooleanValue(true));
    }

    private Group instanceOfPatternClassNamePattern() {
      return new Group("instance-of-class-name-pattern")
          .addMember(
              new GroupMember("classNamePattern")
                  .setDocTitle("Instances of classes matching the class-name pattern.")
                  .defaultValue(CLASS_NAME_PATTERN, DEFAULT_INVALID_CLASS_NAME_PATTERN));
    }

    private Group classNamePatternFullNameGroup() {
      return new Group(CLASS_NAME_GROUP)
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("name")
                  .setDocTitle(
                      "Define the " + CLASS_NAME_GROUP + " pattern by fully qualified class name.")
                  .setDocReturn("The qualified class name that defines the class.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("constant")
                  .setDocTitle(
                      "Define the "
                          + CLASS_NAME_GROUP
                          + " pattern by reference to a Class constant.")
                  .setDocReturn("The class-constant that defines the class.")
                  .defaultObjectClass());
    }

    private Group classNamePatternSimpleNameGroup() {
      return new Group("class-simple-name")
          .addMember(
              new GroupMember("simpleName")
                  .setDocTitle("Exact simple name of the class or interface.")
                  .addParagraph(
                      "For example, the simple name of {@code com.example.MyClass} is {@code"
                          + " MyClass}.")
                  .addParagraph("The default matches any simple name.")
                  .defaultEmptyString());
    }

    private Group classNamePatternPackageGroup() {
      return new Group("class-package-name")
          .addMember(
              new GroupMember("packageName")
                  .setDocTitle("Exact package name of the class or interface.")
                  .addParagraph(
                      "For example, the package of {@code com.example.MyClass} is {@code"
                          + " com.example}.")
                  .addParagraph("The default matches any package.")
                  .defaultEmptyString());
    }

    private Group getKindGroup() {
      return new Group(KIND_GROUP).addMember(getKindMember());
    }

    private static GroupMember getKindMember() {
      return new GroupMember("kind")
          .defaultValue(KEEP_ITEM_KIND, "KeepItemKind.DEFAULT")
          .setDocTitle("Specify the kind of this item pattern.")
          .setDocReturn("The kind for this pattern.")
          .addParagraph("Possible values are:")
          .addUnorderedList(
              docEnumLink(KIND_ONLY_CLASS),
              docEnumLink(KIND_ONLY_MEMBERS),
              docEnumLink(KIND_ONLY_METHODS),
              docEnumLink(KIND_ONLY_FIELDS),
              docEnumLink(KIND_CLASS_AND_MEMBERS),
              docEnumLink(KIND_CLASS_AND_METHODS),
              docEnumLink(KIND_CLASS_AND_FIELDS))
          .addParagraph(
              "If unspecified the default kind for an item depends on its member patterns:")
          .addUnorderedList(
              docEnumLink(KIND_ONLY_CLASS) + " if no member patterns are defined",
              docEnumLink(KIND_ONLY_METHODS) + " if method patterns are defined",
              docEnumLink(KIND_ONLY_FIELDS) + " if field patterns are defined",
              docEnumLink(KIND_ONLY_MEMBERS) + " otherwise.");
    }

    private void forEachKeepConstraintGroups(Consumer<Group> fn) {
      fn.accept(getKeepConstraintsGroup());
      fn.accept(new Group("constrain-annotations").addMember(constrainAnnotations()));
    }

    private Group getKeepConstraintsGroup() {
      return new Group(CONSTRAINTS_GROUP).addMember(constraints()).addMember(constraintAdditions());
    }

    private static GroupMember constraints() {
      return new GroupMember("constraints")
          .setDocTitle("Define the usage constraints of the target.")
          .addParagraph("The specified constraints must remain valid for the target.")
          .addParagraph(
              "The default constraints depend on the kind of the target.",
              "For all targets the default constraints include:")
          .addUnorderedList(
              docEnumLink(CONSTRAINT_LOOKUP),
              docEnumLink(CONSTRAINT_NAME),
              docEnumLink(CONSTRAINT_VISIBILITY_RELAX))
          .addParagraph("For classes the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_CLASS_INSTANTIATE))
          .addParagraph("For methods the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_METHOD_INVOKE))
          .addParagraph("For fields the default constraints also include:")
          .addUnorderedList(docEnumLink(CONSTRAINT_FIELD_GET), docEnumLink(CONSTRAINT_FIELD_SET))
          .setDocReturn("Usage constraints for the target.")
          .defaultArrayEmpty(KEEP_CONSTRAINT);
    }

    private static GroupMember constraintAdditions() {
      return new GroupMember("constraintAdditions")
          .setDocTitle("Add additional usage constraints of the target.")
          .addParagraph(
              "The specified constraints must remain valid for the target",
              "in addition to the default constraints.")
          .addParagraph("The default constraints are documented in " + docLink(constraints()))
          .setDocReturn("Additional usage constraints for the target.")
          .defaultArrayEmpty(KEEP_CONSTRAINT);
    }

    private static GroupMember constrainAnnotations() {
      return new GroupMember("constrainAnnotations")
          .setDocTitle("Patterns for annotations that must remain on the item.")
          .addParagraph(
              "The annotations matching any of the patterns must remain on the item",
              "if the annotation types remain in the program.")
          .addParagraph(
              "Note that if the annotation types themselves are unused/removed,",
              "then their references on the item will be removed too.",
              "If the annotation types themselves are used reflectively then they too need a",
              "keep annotation or rule to ensure they remain in the program.")
          .addParagraph(
              "By default no annotation patterns are defined and no annotations are required to",
              "remain.")
          .setDocReturn("Annotation patterns")
          .defaultArrayEmpty(ANNOTATION_PATTERN);
    }

    private Group annotationNameGroup() {
      return new Group(ANNOTATION_NAME_GROUP)
          .addMember(annotationName())
          .addMember(annotationConstant())
          .addMember(annotationNamePattern())
          .addDocFooterParagraph(
              "If none are specified the default is to match any annotation name.");
    }

    private GroupMember annotationName() {
      return new GroupMember("name")
          .setDocTitle(
              "Define the " + ANNOTATION_NAME_GROUP + " pattern by fully qualified class name.")
          .setDocReturn("The qualified class name that defines the annotation.")
          .defaultEmptyString();
    }

    private GroupMember annotationConstant() {
      return new GroupMember("constant")
          .setDocTitle(
              "Define the "
                  + ANNOTATION_NAME_GROUP
                  + " pattern by reference to a {@code Class} constant.")
          .setDocReturn("The Class constant that defines the annotation.")
          .defaultObjectClass();
    }

    private GroupMember annotationNamePattern() {
      return new GroupMember("namePattern")
          .setDocTitle(
              "Define the "
                  + ANNOTATION_NAME_GROUP
                  + " pattern by reference to a class-name pattern.")
          .setDocReturn("The class-name pattern that defines the annotation.")
          .defaultValue(CLASS_NAME_PATTERN, DEFAULT_INVALID_CLASS_NAME_PATTERN);
    }

    private static GroupMember annotationRetention() {
      return new GroupMember("retention")
          .setDocTitle("Specify which retention policies must be set for the annotations.")
          .addParagraph("Matches annotations with matching retention policies")
          .setDocReturn("Retention policies. By default {@code RetentionPolicy.RUNTIME}.")
          .defaultArrayValue(JAVA_RETENTION_POLICY, "RetentionPolicy.RUNTIME");
    }

    private GroupMember bindingName() {
      return new GroupMember("bindingName")
          .setDocTitle(
              "Name with which other bindings, conditions or targets "
                  + "can reference the bound item pattern.")
          .setDocReturn("Name of the binding.")
          .requiredStringValue();
    }

    private GroupMember classFromBinding() {
      return new GroupMember("classFromBinding")
          .setDocTitle("Define the " + CLASS_GROUP + " pattern by reference to a binding.")
          .setDocReturn("The name of the binding that defines the class.")
          .defaultEmptyString();
    }

    private Group createClassBindingGroup() {
      return new Group(CLASS_GROUP)
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(classFromBinding())
          .addDocFooterParagraph("If none are specified the default is to match any class.");
    }

    private GroupMember className() {
      return new GroupMember("className")
          .setDocTitle("Define the " + CLASS_NAME_GROUP + " pattern by fully qualified class name.")
          .setDocReturn("The qualified class name that defines the class.")
          .defaultEmptyString();
    }

    private GroupMember classConstant() {
      return new GroupMember("classConstant")
          .setDocTitle(
              "Define the " + CLASS_NAME_GROUP + " pattern by reference to a Class constant.")
          .setDocReturn("The class-constant that defines the class.")
          .defaultObjectClass();
    }

    private GroupMember classNamePattern() {
      return new GroupMember("classNamePattern")
          .setDocTitle(
              "Define the " + CLASS_NAME_GROUP + " pattern by reference to a class-name pattern.")
          .setDocReturn("The class-name pattern that defines the class.")
          .defaultValue(CLASS_NAME_PATTERN, DEFAULT_INVALID_CLASS_NAME_PATTERN);
    }

    private Group createClassNamePatternGroup() {
      return new Group(CLASS_NAME_GROUP)
          .addMember(className())
          .addMember(classConstant())
          .addMember(classNamePattern())
          .addDocFooterParagraph("If none are specified the default is to match any class name.");
    }

    private GroupMember instanceOfClassName() {
      return new GroupMember("instanceOfClassName")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .setDocReturn("The qualified class name that defines what instance-of the class must be.")
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstant() {
      return new GroupMember("instanceOfClassConstant")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .setDocReturn("The class constant that defines what instance-of the class must be.")
          .defaultObjectClass();
    }

    private String getInstanceOfExclusiveDoc() {
      return "The pattern is exclusive in that it does not match classes that are"
          + " instances of the pattern, but only those that are instances of classes that"
          + " are subclasses of the pattern.";
    }

    private GroupMember instanceOfClassNameExclusive() {
      return new GroupMember("instanceOfClassNameExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .setDocReturn("The qualified class name that defines what instance-of the class must be.")
          .addParagraph(getInstanceOfExclusiveDoc())
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstantExclusive() {
      return new GroupMember("instanceOfClassConstantExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .addParagraph(getInstanceOfExclusiveDoc())
          .setDocReturn("The class constant that defines what instance-of the class must be.")
          .defaultObjectClass();
    }

    private GroupMember instanceOfPattern() {
      return new GroupMember("instanceOfPattern")
          .setDocTitle("Define the " + INSTANCE_OF_GROUP + " with a pattern.")
          .setDocReturn("The pattern that defines what instance-of the class must be.")
          .defaultValue(INSTANCE_OF_PATTERN, DEFAULT_ANY_INSTANCE_OF_PATTERN);
    }

    private Group createClassInstanceOfPatternGroup() {
      return new Group(INSTANCE_OF_GROUP)
          .addMember(instanceOfClassName())
          .addMember(instanceOfClassNameExclusive())
          .addMember(instanceOfClassConstant())
          .addMember(instanceOfClassConstantExclusive())
          .addMember(instanceOfPattern())
          .addDocFooterParagraph(
              "If none are specified the default is to match any class instance.");
    }

    private String annotatedByDefaultDocFooter(String name) {
      return "If none are specified the default is to match any "
          + name
          + " regardless of what the "
          + name
          + " is annotated by.";
    }

    private Group createAnnotatedByPatternGroup(String name, String groupName) {
      return new Group(groupName)
          .addMember(
              new GroupMember(name + "AnnotatedByClassName")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by fully qualified class name.")
                  .setDocReturn("The qualified class name that defines the annotation.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember(name + "AnnotatedByClassConstant")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by reference to a Class constant.")
                  .setDocReturn("The class-constant that defines the annotation.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember(name + "AnnotatedByClassNamePattern")
                  .setDocTitle(
                      "Define the " + groupName + " pattern by reference to a class-name pattern.")
                  .setDocReturn("The class-name pattern that defines the annotation.")
                  .defaultValue(CLASS_NAME_PATTERN, DEFAULT_INVALID_CLASS_NAME_PATTERN));
    }

    private Group createClassAnnotatedByPatternGroup() {
      String name = "class";
      return createAnnotatedByPatternGroup(name, CLASS_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMemberBindingGroup() {
      return new Group("member")
          .allowMutuallyExclusiveWithOtherGroups()
          .addMember(
              new GroupMember("memberFromBinding")
                  .setDocTitle("Define the member pattern in full by a reference to a binding.")
                  .addParagraph(
                      "Mutually exclusive with all other class and member pattern properties.",
                      "When a member binding is referenced this item is defined to be that item,",
                      "including its class and member patterns.")
                  .setDocReturn("The binding name that defines the member.")
                  .defaultEmptyString());
    }

    private Group createMemberAnnotatedByGroup() {
      String name = "member";
      return createAnnotatedByPatternGroup(name, MEMBER_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForMemberProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMemberAccessGroup() {
      return new Group("member-access")
          .addMember(
              new GroupMember("memberAccess")
                  .setDocTitle("Define the member-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForMemberProperties())
                  .setDocReturn("The member access-flag constraints that must be met.")
                  .defaultArrayEmpty(MEMBER_ACCESS_FLAGS));
    }

    private String getMutuallyExclusiveForMemberProperties() {
      return "Mutually exclusive with all field and method properties "
          + "as use restricts the match to both types of members.";
    }

    private String getMutuallyExclusiveForMethodProperties() {
      return "Mutually exclusive with all field properties.";
    }

    private String getMutuallyExclusiveForFieldProperties() {
      return "Mutually exclusive with all method properties.";
    }

    private String getMethodDefaultDoc(String suffix) {
      return "If none, and other properties define this item as a method, the default matches "
          + suffix
          + ".";
    }

    private String getFieldDefaultDoc(String suffix) {
      return "If none, and other properties define this item as a field, the default matches "
          + suffix
          + ".";
    }

    private Group createMethodAnnotatedByGroup() {
      String name = "method";
      return createAnnotatedByPatternGroup(name, METHOD_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForMethodProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createMethodAccessGroup() {
      return new Group("method-access")
          .addMember(
              new GroupMember("methodAccess")
                  .setDocTitle("Define the method-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method-access flags"))
                  .setDocReturn("The method access-flag constraints that must be met.")
                  .defaultArrayEmpty(METHOD_ACCESS_FLAGS));
    }

    private Group createMethodNameGroup() {
      return new Group("method-name")
          .addMember(
              new GroupMember("methodName")
                  .setDocTitle("Define the method-name pattern by an exact method name.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method name"))
                  .setDocReturn("The exact method name of the method.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("methodNamePattern")
                  .setDocTitle("Define the method-name pattern by a string pattern.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any method name"))
                  .setDocReturn("The string pattern of the method name.")
                  .defaultValue(STRING_PATTERN, DEFAULT_INVALID_STRING_PATTERN));
    }

    private Group createMethodReturnTypeGroup() {
      return new Group("return-type")
          .addMember(
              new GroupMember("methodReturnType")
                  .setDocTitle(
                      "Define the method return-type pattern by a fully qualified type or 'void'.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("The qualified type name of the method return type.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("methodReturnTypeConstant")
                  .setDocTitle("Define the method return-type pattern by a class constant.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("A class constant denoting the type of the method return type.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("methodReturnTypePattern")
                  .setDocTitle("Define the method return-type pattern by a type pattern.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any return type"))
                  .setDocReturn("The pattern of the method return type.")
                  .defaultValue(TYPE_PATTERN, DEFAULT_INVALID_TYPE_PATTERN));
    }

    private Group createMethodParametersGroup() {
      return new Group("parameters")
          .addMember(
              new GroupMember("methodParameters")
                  .setDocTitle(
                      "Define the method parameters pattern by a list of fully qualified types.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any parameters"))
                  .setDocReturn("The list of qualified type names of the method parameters.")
                  .defaultArrayValue(JAVA_STRING, quote("")))
          .addMember(
              new GroupMember("methodParameterTypePatterns")
                  .setDocTitle(
                      "Define the method parameters pattern by a list of patterns on types.")
                  .addParagraph(getMutuallyExclusiveForMethodProperties())
                  .addParagraph(getMethodDefaultDoc("any parameters"))
                  .setDocReturn("The list of type patterns for the method parameters.")
                  .defaultArrayValue(TYPE_PATTERN, DEFAULT_INVALID_TYPE_PATTERN));
    }

    private Group createFieldAnnotatedByGroup() {
      String name = "field";
      return createAnnotatedByPatternGroup(name, FIELD_ANNOTATED_BY_GROUP)
          .addDocFooterParagraph(getMutuallyExclusiveForFieldProperties())
          .addDocFooterParagraph(annotatedByDefaultDocFooter(name));
    }

    private Group createFieldAccessGroup() {
      return new Group("field-access")
          .addMember(
              new GroupMember("fieldAccess")
                  .setDocTitle("Define the field-access pattern by matching on access flags.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field-access flags"))
                  .setDocReturn("The field access-flag constraints that must be met.")
                  .defaultArrayEmpty(FIELD_ACCESS_FLAGS));
    }

    private Group createFieldNameGroup() {
      return new Group("field-name")
          .addMember(
              new GroupMember("fieldName")
                  .setDocTitle("Define the field-name pattern by an exact field name.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field name"))
                  .setDocReturn("The exact field name of the field.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("fieldNamePattern")
                  .setDocTitle("Define the field-name pattern by a string pattern.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any field name"))
                  .setDocReturn("The string pattern of the field name.")
                  .defaultValue(STRING_PATTERN, DEFAULT_INVALID_STRING_PATTERN));
    }

    private Group createFieldTypeGroup() {
      return new Group("field-type")
          .addMember(
              new GroupMember("fieldType")
                  .setDocTitle("Define the field-type pattern by a fully qualified type.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The qualified type name for the field type.")
                  .defaultEmptyString())
          .addMember(
              new GroupMember("fieldTypeConstant")
                  .setDocTitle("Define the field-type pattern by a class constant.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The class constant for the field type.")
                  .defaultObjectClass())
          .addMember(
              new GroupMember("fieldTypePattern")
                  .setDocTitle("Define the field-type pattern by a pattern on types.")
                  .addParagraph(getMutuallyExclusiveForFieldProperties())
                  .addParagraph(getFieldDefaultDoc("any type"))
                  .setDocReturn("The type pattern for the field type.")
                  .defaultValue(TYPE_PATTERN, DEFAULT_INVALID_TYPE_PATTERN));
    }

    private void generateClassAndMemberPropertiesWithClassAndMemberBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(true);
    }

    private void generateClassAndMemberPropertiesWithClassBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(false);
    }

    private void internalGenerateClassAndMemberPropertiesWithBinding(boolean includeMemberBinding) {
      // Class properties.
      {
        Group bindingGroup = createClassBindingGroup();
        Group classNameGroup = createClassNamePatternGroup();
        Group classInstanceOfGroup = createClassInstanceOfPatternGroup();
        Group classAnnotatedByGroup = createClassAnnotatedByPatternGroup();
        bindingGroup.addMutuallyExclusiveGroups(
            classNameGroup, classInstanceOfGroup, classAnnotatedByGroup);

        bindingGroup.generate(this);
        println();
        classNameGroup.generate(this);
        println();
        classInstanceOfGroup.generate(this);
        println();
        classAnnotatedByGroup.generate(this);
        println();
      }

      // Member binding properties.
      Group memberBindingGroup = null;
      if (includeMemberBinding) {
        memberBindingGroup = createMemberBindingGroup();
        memberBindingGroup.generate(this);
        println();
      }

      // The remaining member properties.
      internalGenerateMemberPropertiesNoBinding(memberBindingGroup);
    }

    private Group maybeLink(Group group, Group maybeExclusiveGroup) {
      if (maybeExclusiveGroup != null) {
        maybeExclusiveGroup.addMutuallyExclusiveGroups(group);
      }
      return group;
    }

    private void generateMemberPropertiesNoBinding() {
      internalGenerateMemberPropertiesNoBinding(null);
    }

    private void internalGenerateMemberPropertiesNoBinding(Group memberBindingGroup) {
      // General member properties.
      maybeLink(createMemberAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMemberAccessGroup(), memberBindingGroup).generate(this);
      println();

      // Method properties.
      maybeLink(createMethodAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodAccessGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodNameGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodReturnTypeGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createMethodParametersGroup(), memberBindingGroup).generate(this);
      println();

      // Field properties.
      maybeLink(createFieldAnnotatedByGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldAccessGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldNameGroup(), memberBindingGroup).generate(this);
      println();
      maybeLink(createFieldTypeGroup(), memberBindingGroup).generate(this);
    }

    private void generateStringPattern() {
      printCopyRight(2024);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching strings.")
          .addParagraph("If no properties are set, the default pattern matches any string.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(STRING_PATTERN) + " {");
      println();
      withIndent(
          () -> {
            Group exactGroup = stringPatternExactGroup();
            Group prefixGroup = stringPatternPrefixGroup();
            Group suffixGroup = stringPatternSuffixGroup();
            exactGroup.addMutuallyExclusiveGroups(prefixGroup, suffixGroup);
            exactGroup.generate(this);
            println();
            prefixGroup.generate(this);
            println();
            suffixGroup.generate(this);
          });
      println();
      println("}");
    }

    private void generateTypePattern() {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching types.")
          .addParagraph("If no properties are set, the default pattern matches any type.")
          .addParagraph("All properties on this annotation are mutually exclusive.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(TYPE_PATTERN) + " {");
      println();
      withIndent(() -> typePatternGroup().generate(this));
      println();
      println("}");
    }

    private void generateClassNamePattern() {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching names of classes and interfaces.")
          .addParagraph(
              "If no properties are set, the default pattern matches any name of a class or"
                  + " interface.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(CLASS_NAME_PATTERN) + " {");
      println();
      withIndent(
          () -> {
            Group exactNameGroup = classNamePatternFullNameGroup();
            Group simpleNameGroup = classNamePatternSimpleNameGroup();
            Group packageGroup = classNamePatternPackageGroup();
            exactNameGroup.addMutuallyExclusiveGroups(simpleNameGroup, packageGroup);

            exactNameGroup.generate(this);
            println();
            simpleNameGroup.generate(this);
            println();
            packageGroup.generate(this);
          });
      println();
      println("}");
    }

    private void generateInstanceOfPattern() {
      printCopyRight(2024);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching instances of classes and interfaces.")
          .addParagraph("If no properties are set, the default pattern matches any instance.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(INSTANCE_OF_PATTERN) + " {");
      println();
      withIndent(
          () -> {
            instanceOfPatternInclusive().generate(this);
            println();
            instanceOfPatternClassNamePattern().generate(this);
          });
      println();
      println("}");
    }

    private void generateAnnotationPattern() {
      printCopyRight(2024);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A pattern structure for matching annotations.")
          .addParagraph(
              "If no properties are set, the default pattern matches any annotation",
              "with a runtime retention policy.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(ANNOTATION_PATTERN) + " {");
      println();
      withIndent(
          () -> {
            annotationNameGroup().generate(this);
            println();
            annotationRetention().generate(this);
          });
      println();
      println("}");
    }

    private void generateKeepBinding() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A binding of a keep item.")
          .addParagraph(
              "Bindings allow referencing the exact instance of a match from a condition in other "
                  + " conditions and/or targets. It can also be used to reduce duplication of"
                  + " targets by sharing patterns.")
          .addParagraph("An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepBinding {");
      println();
      withIndent(
          () -> {
            bindingName().generate(this);
            println();
            getKindGroup().generate(this);
            println();
            generateClassAndMemberPropertiesWithClassBinding();
          });
      println();
      println("}");
    }

    private void generateKeepTarget() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A target for a keep edge.")
          .addParagraph(
              "The target denotes an item along with options for what to keep. An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepTarget {");
      println();
      withIndent(
          () -> {
            getKindGroup().generate(this);
            println();
            forEachKeepConstraintGroups(
                g -> {
                  g.generate(this);
                  println();
                });
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      println();
      println("}");
    }

    private void generateKeepCondition() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("A condition for a keep edge.")
          .addParagraph(
              "The condition denotes an item used as a precondition of a rule. An item can be:")
          .addUnorderedList(
              "a pattern on classes;", "a pattern on methods; or", "a pattern on fields.")
          .printDoc(this::println);
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepCondition {");
      println();
      withIndent(
          () -> {
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      println();
      println("}");
    }

    private void generateKeepForApi() {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle(
              "Annotation to mark a class, field or method as part of a library API surface.")
          .addParagraph(
              "When a class is annotated, member patterns can be used to define which members are"
                  + " to be kept. When no member patterns are specified the default pattern matches"
                  + " all public and protected members.")
          .addParagraph(
              "When a member is annotated, the member patterns cannot be used as the annotated"
                  + " member itself fully defines the item to be kept (i.e., itself).")
          .printDoc(this::println);
      println(
          "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
              + " ElementType.CONSTRUCTOR})");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepForApi {");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept as part of the API surface.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addParagraph(
                    "Default kind is",
                    docEnumLink(KIND_CLASS_AND_MEMBERS) + ",",
                    "meaning the annotated class and/or member is to be kept.",
                    "When annotating a class this can be set to",
                    docEnumLink(KIND_ONLY_CLASS),
                    "to avoid patterns on any members.",
                    "That can be useful when the API members are themselves explicitly annotated.")
                .addParagraph(
                    "It is not possible to use",
                    docEnumLink(KIND_ONLY_CLASS),
                    "if annotating a member. Also, it is never valid to use kind",
                    docEnumLink(KIND_ONLY_MEMBERS),
                    "as the API surface must keep the class if any member is to be accessible.")
                .generate(this);
            println();
            generateMemberPropertiesNoBinding();
          });
      println();
      println("}");
    }

    private void generateUsesReflection() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle(
              "Annotation to declare the reflective usages made by a class, method or field.")
          .addParagraph(
              "The annotation's 'value' is a list of targets to be kept if the annotated item is"
                  + " used. The annotated item is a precondition for keeping any of the specified"
                  + " targets. Thus, if an annotated method is determined to be unused by the"
                  + " program, the annotation itself will not be in effect and the targets will not"
                  + " be kept (assuming nothing else is otherwise keeping them).")
          .addParagraph(
              "The annotation's 'additionalPreconditions' is optional and can specify additional"
                  + " conditions that should be satisfied for the annotation to be in effect.")
          .addParagraph(
              "The translation of the "
                  + docLink(USES_REFLECTION)
                  + " annotation into a "
                  + docLink(KEEP_EDGE)
                  + " is as follows:")
          .addParagraph(
              "Assume the item of the annotation is denoted by 'CTX' and referred to as its"
                  + " context.")
          .addCodeBlock(
              annoSimpleName(USES_REFLECTION)
                  + "(value = targets, [additionalPreconditions = preconditions])",
              "==>",
              annoSimpleName(KEEP_EDGE) + "(",
              "  consequences = targets,",
              "  preconditions = {createConditionFromContext(CTX)} + preconditions",
              ")",
              "",
              "where",
              "  KeepCondition createConditionFromContext(ctx) {",
              "    if (ctx.isClass()) {",
              "      return new KeepCondition(classTypeName = ctx.getClassTypeName());",
              "    }",
              "    if (ctx.isMethod()) {",
              "      return new KeepCondition(",
              "        classTypeName = ctx.getClassTypeName(),",
              "        methodName = ctx.getMethodName(),",
              "        methodReturnType = ctx.getMethodReturnType(),",
              "        methodParameterTypes = ctx.getMethodParameterTypes());",
              "    }",
              "    if (ctx.isField()) {",
              "      return new KeepCondition(",
              "        classTypeName = ctx.getClassTypeName(),",
              "        fieldName = ctx.getFieldName()",
              "        fieldType = ctx.getFieldType());",
              "    }",
              "    // unreachable",
              "  }")
          .printDoc(this::println);
      println(
          "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
              + " ElementType.CONSTRUCTOR})");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + simpleName(USES_REFLECTION) + " {");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createConsequencesAsValueGroup().generate(this);
            println();
            createAdditionalPreconditionsGroup().generate(this);
          });
      println("}");
    }

    private void generateUsedByX(String annotationClassName, String doc) {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      DocPrinter.printer()
          .setDocTitle("Annotation to mark a class, field or method as being " + doc + ".")
          .addParagraph(
              "Note: Before using this annotation, consider if instead you can annotate the code"
                  + " that is doing reflection with "
                  + docLink(USES_REFLECTION)
                  + ". Annotating the"
                  + " reflecting code is generally more clear and maintainable, and it also"
                  + " naturally gives rise to edges that describe just the reflected aspects of the"
                  + " program. The "
                  + docLink(USED_BY_REFLECTION)
                  + " annotation is suitable for cases where"
                  + " the reflecting code is not under user control, or in migrating away from"
                  + " rules.")
          .addParagraph(
              "When a class is annotated, member patterns can be used to define which members are"
                  + " to be kept. When no member patterns are specified the default pattern is to"
                  + " match just the class.")
          .addParagraph(
              "When a member is annotated, the member patterns cannot be used as the annotated"
                  + " member itself fully defines the item to be kept (i.e., itself).")
          .printDoc(this::println);
      println(
          "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
              + " ElementType.CONSTRUCTOR})");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + annotationClassName + " {");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createPreconditionsGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept in addition to the annotated class/members.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addParagraph("If unspecified the default kind depends on the annotated item.")
                .addParagraph("When annotating a class the default kind is:")
                .addUnorderedList(
                    docEnumLink(KIND_ONLY_CLASS) + " if no member patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_METHODS) + " if method patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_FIELDS) + " if field patterns are defined;",
                    docEnumLink(KIND_CLASS_AND_MEMBERS) + "otherwise.")
                .addParagraph(
                    "When annotating a method the default kind is: "
                        + docEnumLink(KIND_ONLY_METHODS))
                .addParagraph(
                    "When annotating a field the default kind is: " + docEnumLink(KIND_ONLY_FIELDS))
                .addParagraph(
                    "It is not possible to use "
                        + docEnumLink(KIND_ONLY_CLASS)
                        + " if annotating a member.")
                .generate(this);
            println();
            forEachKeepConstraintGroups(
                g -> {
                  g.generate(this);
                  println();
                });
            generateMemberPropertiesNoBinding();
          });
      println();
      println("}");
    }

    private static String annoSimpleName(ClassReference clazz) {
      return "@" + simpleName(clazz);
    }

    private static String docLink(ClassReference clazz) {
      return "{@link " + simpleName(clazz) + "}";
    }

    private static String docLink(GroupMember member) {
      return "{@link #" + member.name + "}";
    }

    private static String docEnumLink(EnumReference enumRef) {
      return "{@link " + simpleName(enumRef.enumClass) + "#" + enumRef.enumValue + "}";
    }

    private static String docEnumLinkList(EnumReference... values) {
      return StringUtils.join(", ", values, v -> docEnumLink(v), BraceType.TUBORG);
    }

    private void generateConstants() {
      printCopyRight(2023);
      printPackage("ast");
      printImports();
      DocPrinter.printer()
          .setDocTitle(
              "Utility class for referencing the various keep annotations and their structure.")
          .addParagraph(
              "Use of these references avoids polluting the Java namespace with imports of the java"
                  + " annotations which overlap in name with the actual semantic AST types.")
          .printDoc(this::println);
      println("public final class AnnotationConstants {");
      withIndent(
          () -> {
            // Root annotations.
            generateExtractedKeepAnnotationsConstants();
            generateKeepEdgeConstants();
            generateKeepForApiConstants();
            generateUsesReflectionConstants();
            generateUsedByReflectionConstants();
            generateUsedByNativeConstants();
            generateCheckRemovedConstants();
            generateCheckOptimizedOutConstants();
            // Common item fields.
            generateItemConstants();
            // Inner annotation classes.
            generateBindingConstants();
            generateConditionConstants();
            generateTargetConstants();
            generateKindConstants();
            generateConstraintConstants();
            generateMemberAccessConstants();
            generateMethodAccessConstants();
            generateFieldAccessConstants();

            generateStringPatternConstants();
            generateTypePatternConstants();
            generateClassNamePatternConstants();
            generateInstanceOfPatternConstants();
            generateAnnotationPatternConstants();
          });
      println("}");
    }

    void generateGroupConstants(ClassReference annoType, List<Group> groups) {
      generateAnnotationConstants(annoType);
      groups.forEach(g -> g.generateConstants(this));
    }

    private void generateAnnotationConstants(ClassReference clazz) {
      String desc = clazz.getDescriptor();
      println("public static final String DESCRIPTOR = " + quote(desc) + ";");
    }

    private void generateExtractedKeepAnnotationsConstants() {
      println("public static final class ExtractedAnnotations {");
      withIndent(
          () -> {
            generateAnnotationConstants(EXTRACTED_KEEP_ANNOTATIONS);
            new GroupMember("value")
                .setDocTitle("Extracted normalized keep edges.")
                .requiredArrayValue(KEEP_EDGE)
                .generateConstants(this);
          });
      println("}");
      println();
      println("public static final class ExtractedAnnotation {");
      withIndent(
          () -> {
            generateAnnotationConstants(EXTRACTED_KEEP_ANNOTATION);
            new GroupMember("version")
                .setDocTitle("Extraction version used to generate this keep annotation.")
                .requiredStringValue()
                .generateConstants(this);
            new GroupMember("context")
                .setDocTitle("Extraction context from which this keep annotation is generated.")
                .requiredStringValue()
                .generateConstants(this);
            new Group("keep-annotation")
                .addMember(
                    new GroupMember("edge")
                        .setDocTitle("Extracted normalized keep edge.")
                        .requiredValue(KEEP_EDGE))
                .addMember(
                    new GroupMember("checkRemoved")
                        .setDocTitle("Extracted check removed.")
                        .defaultBooleanValue(false))
                .addMember(
                    new GroupMember("checkOptimizedOut")
                        .setDocTitle("Extracted check optimized out.")
                        .defaultBooleanValue(false))
                .generateConstants(this);
          });
      println("}");
      println();
    }

    List<Group> getKeepEdgeGroups() {
      return ImmutableList.of(
          createDescriptionGroup(),
          createBindingsGroup(),
          createPreconditionsGroup(),
          createConsequencesGroup());
    }

    private void generateKeepEdgeConstants() {
      println("public static final class Edge {");
      withIndent(() -> generateGroupConstants(KEEP_EDGE, getKeepEdgeGroups()));
      println("}");
      println();
    }

    List<Group> getKeepForApiGroups() {
      return ImmutableList.of(
          createDescriptionGroup(), createAdditionalTargetsGroup("."), createMemberAccessGroup());
    }

    private void generateKeepForApiConstants() {
      println("public static final class ForApi {");
      withIndent(() -> generateGroupConstants(KEEP_FOR_API, getKeepForApiGroups()));
      println("}");
      println();
    }

    List<Group> getUsesReflectionGroups() {
      return ImmutableList.of(
          createDescriptionGroup(),
          createConsequencesAsValueGroup(),
          createAdditionalPreconditionsGroup());
    }

    private void generateUsesReflectionConstants() {
      println("public static final class UsesReflection {");
      withIndent(() -> generateGroupConstants(USES_REFLECTION, getUsesReflectionGroups()));
      println("}");
      println();
    }

    List<Group> getUsedByReflectionGroups() {
      ImmutableList.Builder<Group> builder = ImmutableList.builder();
      builder.addAll(getItemGroups());
      builder.add(getKindGroup());
      forEachExtraUsedByReflectionGroup(builder::add);
      forEachKeepConstraintGroups(builder::add);
      return builder.build();
    }

    private void forEachExtraUsedByReflectionGroup(Consumer<Group> fn) {
      fn.accept(createDescriptionGroup());
      fn.accept(createPreconditionsGroup());
      fn.accept(createAdditionalTargetsGroup("."));
    }

    private void generateUsedByReflectionConstants() {
      println("public static final class UsedByReflection {");
      withIndent(
          () -> {
            generateAnnotationConstants(USED_BY_REFLECTION);
            forEachExtraUsedByReflectionGroup(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    List<Group> getUsedByNativeGroups() {
      return getUsedByReflectionGroups();
    }

    private void generateUsedByNativeConstants() {
      println("public static final class UsedByNative {");
      withIndent(
          () -> {
            generateAnnotationConstants(USED_BY_NATIVE);
            println("// Content is the same as " + simpleName(USED_BY_REFLECTION) + ".");
          });
      println("}");
      println();
    }

    private void generateCheckRemovedConstants() {
      println("public static final class CheckRemoved {");
      withIndent(
          () -> {
            generateAnnotationConstants(CHECK_REMOVED);
          });
      println("}");
      println();
    }

    private void generateCheckOptimizedOutConstants() {
      println("public static final class CheckOptimizedOut {");
      withIndent(
          () -> {
            generateAnnotationConstants(CHECK_OPTIMIZED_OUT);
          });
      println("}");
      println();
    }

    private List<Group> getItemGroups() {
      return ImmutableList.of(
          // Bindings.
          createClassBindingGroup(),
          createMemberBindingGroup(),
          // Classes.
          createClassNamePatternGroup(),
          createClassInstanceOfPatternGroup(),
          createClassAnnotatedByPatternGroup(),
          // Members.
          createMemberAnnotatedByGroup(),
          createMemberAccessGroup(),
          // Methods.
          createMethodAnnotatedByGroup(),
          createMethodAccessGroup(),
          createMethodNameGroup(),
          createMethodReturnTypeGroup(),
          createMethodParametersGroup(),
          // Fields.
          createFieldAnnotatedByGroup(),
          createFieldAccessGroup(),
          createFieldNameGroup(),
          createFieldTypeGroup());
    }

    private void generateItemConstants() {
      DocPrinter.printer()
          .setDocTitle("Item properties common to binding items, conditions and targets.")
          .printDoc(this::println);
      println("public static final class Item {");
      withIndent(() -> getItemGroups().forEach(g -> g.generateConstants(this)));
      println("}");
      println();
    }

    List<Group> getBindingGroups() {
      return ImmutableList.<Group>builder()
          .addAll(getItemGroups())
          .add(new Group("binding-name").addMember(bindingName()))
          .build();
    }

    private void generateBindingConstants() {
      println("public static final class Binding {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_BINDING);
            bindingName().generateConstants(this);
          });
      println("}");
      println();
    }

    List<Group> getConditionGroups() {
      return getItemGroups();
    }

    private void generateConditionConstants() {
      println("public static final class Condition {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_CONDITION);
          });
      println("}");
      println();
    }

    List<Group> getTargetGroups() {
      ImmutableList.Builder<Group> builder = ImmutableList.builder();
      builder.addAll(getItemGroups());
      forEachExtraTargetGroup(builder::add);
      return builder.build();
    }

    private void forEachExtraTargetGroup(Consumer<Group> fn) {
      fn.accept(getKindGroup());
      forEachKeepConstraintGroups(fn);
    }

    private void generateTargetConstants() {
      println("public static final class Target {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_TARGET);
            forEachExtraTargetGroup(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    private void generateKindConstants() {
      println("public static final class Kind {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_ITEM_KIND);
            for (KeepItemKind value : KeepItemKind.values()) {
              if (value != KeepItemKind.DEFAULT) {
                println(
                    "public static final String "
                        + value.name()
                        + " = "
                        + quote(value.name())
                        + ";");
              }
            }
          });
      println("}");
      println();
    }

    private void generateConstraintConstants() {
      println("public static final class Constraints {");
      withIndent(
          () -> {
            generateAnnotationConstants(KEEP_CONSTRAINT);
            for (EnumReference constraint : KEEP_CONSTRAINT_VALUES) {
              println(
                  "public static final String "
                      + constraint.enumValue
                      + " = "
                      + quote(constraint.enumValue)
                      + ";");
            }
          });
      println("}");
      println();
    }

    private boolean isAccessPropertyNegation(EnumReference enumReference) {
      return enumReference.name().startsWith("NON_");
    }

    private boolean isMemberAccessProperty(EnumReference enumReference) {
      for (EnumReference memberAccessValue : MEMBER_ACCESS_VALUES) {
        if (memberAccessValue.enumValue.equals(enumReference.enumValue)) {
          return true;
        }
      }
      return false;
    }

    private void generateMemberAccessConstants() {
      println("public static final class MemberAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(MEMBER_ACCESS_FLAGS);
            println("public static final String NEGATION_PREFIX = \"NON_\";");
            for (EnumReference value : MEMBER_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private void generateMethodAccessConstants() {
      println("public static final class MethodAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(METHOD_ACCESS_FLAGS);
            for (EnumReference value : METHOD_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert !isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private void generateFieldAccessConstants() {
      println("public static final class FieldAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(FIELD_ACCESS_FLAGS);
            for (EnumReference value : FIELD_ACCESS_VALUES) {
              assert !isAccessPropertyNegation(value);
              assert !isMemberAccessProperty(value);
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    List<Group> getStringPatternGroups() {
      return ImmutableList.of(
          stringPatternExactGroup(), stringPatternPrefixGroup(), stringPatternSuffixGroup());
    }

    private void generateStringPatternConstants() {
      println("public static final class StringPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(STRING_PATTERN);
            getStringPatternGroups().forEach(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    List<Group> getTypePatternGroups() {
      return ImmutableList.of(typePatternGroup());
    }

    private void generateTypePatternConstants() {
      println("public static final class TypePattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(TYPE_PATTERN);
            getTypePatternGroups().forEach(g -> g.generateConstants((this)));
          });
      println("}");
      println();
    }

    private void generateClassNamePatternConstants() {
      println("public static final class ClassNamePattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(CLASS_NAME_PATTERN);
            classNamePatternFullNameGroup().generateConstants(this);
            classNamePatternSimpleNameGroup().generateConstants(this);
            classNamePatternPackageGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateInstanceOfPatternConstants() {
      println("public static final class InstanceOfPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(INSTANCE_OF_PATTERN);
            instanceOfPatternInclusive().generateConstants(this);
            instanceOfPatternClassNamePattern().generateConstants(this);
          });
      println("}");
      println();
    }

    List<Group> getAnnotationPatternGroups() {
      return ImmutableList.of(
          annotationNameGroup(), new Group("retention").addMember(annotationRetention()));
    }

    private void generateAnnotationPatternConstants() {
      println("public static final class AnnotationPattern {");
      withIndent(
          () -> {
            generateAnnotationConstants(ANNOTATION_PATTERN);
            getAnnotationPatternGroups().forEach(g -> g.generateConstants(this));
          });
      println("}");
      println();
    }

    private static void writeFile(Path file, Consumer<Generator> fn, BiConsumer<Path, String> write)
        throws IOException {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteStream);
      Generator generator = new Generator(printStream);
      fn.accept(generator);
      String formatted = byteStream.toString();
      if (file.toString().endsWith(".java")) {
        formatted = CodeGenerationBase.formatRawOutput(formatted);
      }
      Path resolved = Paths.get(ToolHelper.getProjectRoot()).resolve(file);
      write.accept(resolved, formatted);
    }

    public static Path source(ClassReference clazz) {
      return Paths.get("src", "keepanno", "java").resolve(clazz.getBinaryName() + ".java");
    }

    public static void run(BiConsumer<Path, String> write) throws IOException {
      Path projectRoot = Paths.get(ToolHelper.getProjectRoot());
      writeFile(
          Paths.get("doc/keepanno-guide.md"),
          generator -> KeepAnnoMarkdownGenerator.generateMarkdownDoc(generator, projectRoot),
          write);

      writeFile(source(ANNOTATION_CONSTANTS), Generator::generateConstants, write);
      writeFile(source(STRING_PATTERN), Generator::generateStringPattern, write);
      writeFile(source(TYPE_PATTERN), Generator::generateTypePattern, write);
      writeFile(source(CLASS_NAME_PATTERN), Generator::generateClassNamePattern, write);
      writeFile(source(INSTANCE_OF_PATTERN), Generator::generateInstanceOfPattern, write);
      writeFile(source(ANNOTATION_PATTERN), Generator::generateAnnotationPattern, write);
      writeFile(source(KEEP_BINDING), Generator::generateKeepBinding, write);
      writeFile(source(KEEP_TARGET), Generator::generateKeepTarget, write);
      writeFile(source(KEEP_CONDITION), Generator::generateKeepCondition, write);
      writeFile(source(KEEP_FOR_API), Generator::generateKeepForApi, write);
      writeFile(source(USES_REFLECTION), Generator::generateUsesReflection, write);
      writeFile(
          source(USED_BY_REFLECTION),
          g -> g.generateUsedByX("UsedByReflection", "accessed reflectively"),
          write);
      writeFile(
          source(USED_BY_NATIVE),
          g -> g.generateUsedByX("UsedByNative", "accessed from native code via JNI"),
          write);
    }
  }
}
