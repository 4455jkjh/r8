// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.keep.Unspecified;
import androidx.annotation.keep.UsesReflectionToReadClassAnnotation;
import com.android.tools.r8.keepanno.ast.KeepAnnotationParserException;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.AnnotationContentBuilder;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class KeepUsesReflectionToReadClassAnnotationTest extends KeepAnnoTestExtractedRulesBase {

  private static final String KEEP_VARIANT_RETAIN_ANNOTATION =
      "-keep,allowshrinking,allowoptimization,allowobfuscation";
  private static final String KEEP_VARIANT_ANNO_CLASS_CONSTANT =
      "-keep,allowoptimization,allowobfuscation";
  private static final String KEEP_VARIANT_ANNO_CLASS_NAME = "-keep,allowoptimization";
  private static final String FINALIZE_MEMBER = "{ void finalize(); }";

  private static final String CONDITION_METHOD_FOO = "{ void foo(java.lang.Class); }";
  private static final String CONDITION_NO_ARGS_INIT = "{ void <init>(); }";
  private static final String CONDITION_INT_INIT = "{ void <init>(int); }";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        createParametersR8RulesOnly(getTestParameters().withNoneRuntime().build()),
        getKotlinTestParameters().withLatestCompiler().build());
  }

  private static final class ExpectedReadAnnotationSpec {
    final String annotationClass;
    final boolean annotationByName;
    final String targetClass;
    final boolean includeSubclasses;

    ExpectedReadAnnotationSpec(
        Class<?> annotationClass,
        boolean annotationByName,
        Class<?> targetClass,
        boolean includeSubclasses) {
      this.annotationClass = annotationClass.getTypeName();
      this.annotationByName = annotationByName;
      this.targetClass = targetClass != null ? targetClass.getTypeName() : null;
      this.includeSubclasses = includeSubclasses;
    }
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass, String conditionMembers, List<ExpectedReadAnnotationSpec> specs) {
    Consumer<ExpectedKeepRule.Builder> setCondition =
        b -> {
          b.setConditionClass(conditionClass);
          if (conditionMembers != null) {
            b.setConditionMembers(conditionMembers);
          }
        };
    ExpectedRules.Builder builder =
        ExpectedRules.builder().add(ExpectedKeepAttributesRule.buildAllRuntimeVisibleAnnotations());
    for (ExpectedReadAnnotationSpec spec : specs) {
      if (spec.targetClass == null) {
        builder.add(
            ExpectedKeepRule.builder()
                .apply(setCondition)
                .setKeepVariant(KEEP_VARIANT_RETAIN_ANNOTATION)
                .setConsequentClassAnnotation(spec.annotationClass)
                .setConsequentClass("**")
                .setConsequentMembers(FINALIZE_MEMBER)
                .build());
      } else {
        builder.add(
            ExpectedKeepRule.builder()
                .apply(setCondition)
                .setKeepVariant(KEEP_VARIANT_RETAIN_ANNOTATION)
                .setConsequentClassAnnotation(spec.annotationClass)
                .setConsequentClass(spec.targetClass)
                .setConsequentMembers(FINALIZE_MEMBER)
                .build());
        if (spec.includeSubclasses) {
          builder.add(
              ExpectedKeepRule.builder()
                  .apply(setCondition)
                  .setKeepVariant(KEEP_VARIANT_RETAIN_ANNOTATION)
                  .setConsequentClassAnnotation(spec.annotationClass)
                  .setConsequentExtendsClass(spec.targetClass)
                  .setConsequentMembers(FINALIZE_MEMBER)
                  .build());
        }
      }
      builder.add(
          ExpectedKeepRule.builder()
              .apply(setCondition)
              .setKeepVariant(
                  spec.annotationByName
                      ? KEEP_VARIANT_ANNO_CLASS_NAME
                      : KEEP_VARIANT_ANNO_CLASS_CONSTANT)
              .setConsequentClass(spec.annotationClass)
              .setConsequentMembers(FINALIZE_MEMBER)
              .build());
    }
    return builder.build();
  }

  private static ExpectedRules getExpectedRulesJava(
      Class<?> conditionClass,
      String conditionMembers,
      Class<?> annotationClass,
      boolean annotationByName,
      Class<?> targetClass,
      boolean includeSubclasses) {
    return getExpectedRulesJava(
        conditionClass,
        conditionMembers,
        ImmutableList.of(
            new ExpectedReadAnnotationSpec(
                annotationClass, annotationByName, targetClass, includeSubclasses)));
  }

  private static void buildUsesReflectionToReadClassAnnotation(
      AnnotationBuilder builder, Object annotation, Object clazz) {
    AnnotationContentBuilder ab =
        builder.setAnnotationClass(
            Reference.classFromClass(UsesReflectionToReadClassAnnotation.class));
    if (annotation instanceof String) {
      ab.setField("annotationName", annotation);
    } else if (annotation != null) {
      assertTrue(annotation instanceof Class<?> || annotation instanceof Type);
      ab.setField("annotationConstant", annotation);
    }
    if (clazz instanceof String) {
      ab.setField("className", clazz);
    } else if (clazz != null) {
      assertTrue(clazz instanceof Class<?> || clazz instanceof Type);
      ab.setField("classConstant", clazz);
    }
  }

  private static void buildUsesReflectionToReadClassAnnotationMultiple(
      AnnotationBuilder builder, Object anno1, Object clazz1, Object anno2, Object clazz2) {
    builder
        .setAnnotationClass(
            Reference.classFromBinaryName(
                Reference.classFromClass(UsesReflectionToReadClassAnnotation.class).getBinaryName()
                    + "$Container"))
        .buildArray(
            "value",
            builder1 ->
                builder1
                    .setAnnotationField(
                        null,
                        builder2 ->
                            buildUsesReflectionToReadClassAnnotation(builder2, anno1, clazz1))
                    .setAnnotationField(
                        null,
                        builder3 ->
                            buildUsesReflectionToReadClassAnnotation(builder3, anno2, clazz2)));
  }

  private static MethodPredicate noArgsConstructorPredicate(Class<?> clazz) {
    MethodReference ctor =
        Reference.method(Reference.classFromClass(clazz), "<init>", ImmutableList.of(), null);
    return MethodPredicate.onReference(ctor);
  }

  private static MethodPredicate intConstructorPredicate(Class<?> clazz) {
    MethodReference ctor =
        Reference.method(
            Reference.classFromClass(clazz),
            "<init>",
            ImmutableList.of(Reference.primitiveFromDescriptor("I")),
            null);
    return MethodPredicate.onReference(ctor);
  }

  @Test
  public void testAnnotationConstantAndClassConstantAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, null, Anno1.class, false, KeptClass.class, true));
  }

  @Test
  public void testAnnotationConstantAndClassConstantAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            Anno1.class,
            false,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationConstantAndClassConstantAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_NO_ARGS_INIT,
            Anno1.class,
            false,
            KeptClass.class,
            true));
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                intConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_INT_INIT,
            Anno1.class,
            false,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationConstantAndClassNameAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, null, Anno1.class, false, KeptClass.class, true));
  }

  @Test
  public void testAnnotationConstantAndClassNameAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            Anno1.class,
            false,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationConstantAndClassNameAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class, KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_NO_ARGS_INIT,
            Anno1.class,
            false,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationConstantAndOmittedClassAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder -> buildUsesReflectionToReadClassAnnotation(builder, Anno1.class, null))),
        getExpectedRulesJava(ClassWithAnnotation.class, null, Anno1.class, false, null, true));
  }

  @Test
  public void testAnnotationConstantAndOmittedClassAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder -> buildUsesReflectionToReadClassAnnotation(builder, Anno1.class, null))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, CONDITION_METHOD_FOO, Anno1.class, false, null, true));
  }

  @Test
  public void testAnnotationConstantAndOmittedClassAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder -> buildUsesReflectionToReadClassAnnotation(builder, Anno1.class, null))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, CONDITION_NO_ARGS_INIT, Anno1.class, false, null, true));
  }

  @Test
  public void testAnnotationNameAndClassConstantAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, null, Anno1.class, true, KeptClass.class, true));
  }

  @Test
  public void testAnnotationNameAndClassConstantAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            Anno1.class,
            true,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationNameAndClassConstantAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_NO_ARGS_INIT,
            Anno1.class,
            true,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationNameAndClassNameAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, null, Anno1.class, true, KeptClass.class, true));
  }

  @Test
  public void testAnnotationNameAndClassNameAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            Anno1.class,
            true,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationNameAndClassNameAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), KeptClass.class.getTypeName()))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_NO_ARGS_INIT,
            Anno1.class,
            true,
            KeptClass.class,
            true));
  }

  @Test
  public void testAnnotationNameAndOmittedClassAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), null))),
        getExpectedRulesJava(ClassWithAnnotation.class, null, Anno1.class, true, null, true));
  }

  @Test
  public void testAnnotationNameAndOmittedClassAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), null))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, CONDITION_METHOD_FOO, Anno1.class, true, null, true));
  }

  @Test
  public void testAnnotationNameAndOmittedClassAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotation(
                        builder, Anno1.class.getTypeName(), null))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, CONDITION_NO_ARGS_INIT, Anno1.class, true, null, true));
  }

  @Test
  public void testExplicitDefaultSentinelsTreatedAsUnset() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    builder
                        .setAnnotationClass(
                            Reference.classFromClass(UsesReflectionToReadClassAnnotation.class))
                        .setField("annotationConstant", Anno1.class)
                        .setField("annotationName", "")
                        .setField("classConstant", Unspecified.class)
                        .setField("className", ""))),
        getExpectedRulesJava(
            ClassWithAnnotation.class, CONDITION_METHOD_FOO, Anno1.class, false, null, true));
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    builder
                        .setAnnotationClass(
                            Reference.classFromClass(UsesReflectionToReadClassAnnotation.class))
                        .setField("annotationConstant", Unspecified.class)
                        .setField("annotationName", Anno1.class.getTypeName())
                        .setField("classConstant", KeptClass.class)
                        .setField("className", ""))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            Anno1.class,
            true,
            KeptClass.class,
            true));
  }

  @Test
  public void testMultipleAnnotationsAnnotatedOnClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnClass(
                ClassWithAnnotation.class,
                builder ->
                    buildUsesReflectionToReadClassAnnotationMultiple(
                        builder, Anno1.class, KeptClass.class, Anno2.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            null,
            ImmutableList.of(
                new ExpectedReadAnnotationSpec(Anno1.class, false, KeptClass.class, true),
                new ExpectedReadAnnotationSpec(Anno2.class, false, KeptClass.class, true))));
  }

  @Test
  public void testMultipleAnnotationsAnnotatedOnMethod() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotationMultiple(
                        builder, Anno1.class, KeptClass.class, Anno2.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            ImmutableList.of(
                new ExpectedReadAnnotationSpec(Anno1.class, false, KeptClass.class, true),
                new ExpectedReadAnnotationSpec(Anno2.class, false, KeptClass.class, true))));
  }

  @Test
  public void testMultipleAnnotationsAnnotatedOnConstructor() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                noArgsConstructorPredicate(ClassWithAnnotation.class),
                builder ->
                    buildUsesReflectionToReadClassAnnotationMultiple(
                        builder, Anno1.class, KeptClass.class, Anno2.class, KeptClass.class))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_NO_ARGS_INIT,
            ImmutableList.of(
                new ExpectedReadAnnotationSpec(Anno1.class, false, KeptClass.class, true),
                new ExpectedReadAnnotationSpec(Anno2.class, false, KeptClass.class, true))));
  }

  @Test
  public void testMultipleAnnotationsMixedConstantNameAndOmittedClass() throws Exception {
    testExtractedRules(
        ImmutableList.of(
            setAnnotationOnMethod(
                ClassWithAnnotation.class,
                MethodPredicate.onName("foo"),
                builder ->
                    buildUsesReflectionToReadClassAnnotationMultiple(
                        builder, Anno1.class, KeptClass.class, Anno2.class.getTypeName(), null))),
        getExpectedRulesJava(
            ClassWithAnnotation.class,
            CONDITION_METHOD_FOO,
            ImmutableList.of(
                new ExpectedReadAnnotationSpec(Anno1.class, false, KeptClass.class, true),
                new ExpectedReadAnnotationSpec(Anno2.class, true, null, true))));
  }

  @Test
  public void testInvalidMissingAnnotationConstantAndAnnotationName() throws Exception {
    byte[] onClass =
        setAnnotationOnClass(
            ClassWithAnnotation.class,
            builder -> buildUsesReflectionToReadClassAnnotation(builder, null, KeptClass.class));
    byte[] onMethod =
        setAnnotationOnMethod(
            ClassWithAnnotation.class,
            MethodPredicate.onName("foo"),
            builder -> buildUsesReflectionToReadClassAnnotation(builder, null, KeptClass.class));
    byte[] onConstructor =
        setAnnotationOnMethod(
            ClassWithAnnotation.class,
            noArgsConstructorPredicate(ClassWithAnnotation.class),
            builder -> buildUsesReflectionToReadClassAnnotation(builder, null, KeptClass.class));
    for (byte[] bytes : ImmutableList.of(onClass, onMethod, onConstructor)) {
      KeepAnnotationParserException error =
          assertThrows(
              KeepAnnotationParserException.class,
              () -> testExtractedRules(ImmutableList.of(bytes), ExpectedRules.builder().build()));
      assertThat(
          error.getMessage(),
          containsString("Expected one of 'annotationConstant' or 'annotationName'"));
    }
  }

  @Test
  public void testInvalidBothAnnotationConstantAndAnnotationName() throws Exception {
    byte[] bytes =
        setAnnotationOnMethod(
            ClassWithAnnotation.class,
            MethodPredicate.onName("foo"),
            builder ->
                builder
                    .setAnnotationClass(
                        Reference.classFromClass(UsesReflectionToReadClassAnnotation.class))
                    .setField("annotationConstant", Anno1.class)
                    .setField("annotationName", Anno1.class.getTypeName()));
    KeepAnnotationParserException error =
        assertThrows(
            KeepAnnotationParserException.class,
            () -> testExtractedRules(ImmutableList.of(bytes), ExpectedRules.builder().build()));
    assertThat(error.getMessage(), containsString("Mutually exclusive"));
    assertThat(error.getMessage(), containsString("annotationConstant"));
    assertThat(error.getMessage(), containsString("annotationName"));
  }

  @Test
  public void testInvalidBothClassConstantAndClassName() throws Exception {
    byte[] bytes =
        setAnnotationOnMethod(
            ClassWithAnnotation.class,
            MethodPredicate.onName("foo"),
            builder ->
                builder
                    .setAnnotationClass(
                        Reference.classFromClass(UsesReflectionToReadClassAnnotation.class))
                    .setField("annotationConstant", Anno1.class)
                    .setField("classConstant", KeptClass.class)
                    .setField("className", KeptClass.class.getTypeName()));
    KeepAnnotationParserException error =
        assertThrows(
            KeepAnnotationParserException.class,
            () -> testExtractedRules(ImmutableList.of(bytes), ExpectedRules.builder().build()));
    assertThat(error.getMessage(), containsString("Mutually exclusive"));
    assertThat(error.getMessage(), containsString("classConstant"));
    assertThat(error.getMessage(), containsString("className"));
  }

  @Test
  public void testInvalidUnexpectedProperty() throws Exception {
    byte[] bytes =
        setAnnotationOnMethod(
            ClassWithAnnotation.class,
            MethodPredicate.onName("foo"),
            builder ->
                builder
                    .setAnnotationClass(
                        Reference.classFromClass(UsesReflectionToReadClassAnnotation.class))
                    .setField("annotationConstant", Anno1.class)
                    .setField("unknownProperty", "foo"));
    assertThrows(
        KeepAnnotationParserException.class,
        () -> testExtractedRules(ImmutableList.of(bytes), ExpectedRules.builder().build()));
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Anno1 {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Anno2 {}

  // Test class without annotation to be used by multiple tests inserting annotations using a
  // transformer.
  static class ClassWithAnnotation {

    public int bar;

    public ClassWithAnnotation() {}

    public ClassWithAnnotation(int x) {
      this.bar = x;
    }

    public void foo(Class<KeptClass> clazz) throws Exception {
      if (clazz != null) {
        System.out.println(clazz.getAnnotations().length + bar);
      }
    }

    public static void main(String[] args) throws Exception {
      ClassWithAnnotation instance = new ClassWithAnnotation();
      instance.bar = args.length == 0 ? 0 : 1;
      instance.foo(args.length == 0 ? KeptClass.class : null);
    }
  }

  @Anno1
  @Anno2
  static class KeptClass {}
}
