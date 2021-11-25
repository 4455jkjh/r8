// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_6_0;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.kotlin.TestKotlinClass.AccessorKind;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinAccessorTest extends AbstractR8KotlinTestBase {

  private static final String JAVA_LANG_STRING = "java.lang.String";

  private static final TestKotlinCompanionClass ACCESSOR_COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("accessors.Accessor")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final String PROPERTIES_PACKAGE_NAME = "properties";

  private static final TestKotlinCompanionClass COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_LATE_INIT_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionLateInitProperties")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_INNER_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForInnerClass")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_LAMBDA_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForLambda")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("indirectPropertyGetter", JAVA_LANG_STRING, Visibility.PRIVATE);

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public R8KotlinAccessorTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void testCompanionProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrimitiveProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, R8TestBuilder::noClassStaticizing)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "primitiveProp";
              FieldSubject fieldSubject = checkFieldIsKept(outerClass, "int", propertyName);
              assertTrue(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
                assertTrue(fieldSubject.getField().accessFlags.isPrivate());
                checkMethodIsKept(outerClass, getterAccessor);
                checkMethodIsRemoved(outerClass, setterAccessor);
            });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrivateProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, R8TestBuilder::noClassStaticizing)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
                assertTrue(fieldSubject.getField().accessFlags.isPrivate());

                checkMethodIsKept(outerClass, getterAccessor);
                checkMethodIsRemoved(outerClass, setterAccessor);
            });
  }

  @Test
  public void testCompanionProperty_internalPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_useInternalProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, R8TestBuilder::noClassStaticizing)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "internalProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);

              assertTrue(fieldSubject.getField().accessFlags.isPrivate());
              checkMethodIsKept(outerClass, getterAccessor);
              checkMethodIsRemoved(outerClass, setterAccessor);
            });
  }

  @Test
  public void testCompanionProperty_publicPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePublicProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, R8TestBuilder::noClassStaticizing)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "publicProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);

                assertTrue(fieldSubject.getField().accessFlags.isPrivate());
                checkMethodIsKept(outerClass, getterAccessor);
                checkMethodIsRemoved(outerClass, setterAccessor);
            });
  }

  @Test
  public void testCompanionLateInitProperty_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePrivateLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, R8TestBuilder::noClassStaticizing)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "privateLateInitProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(
                      propertyName, AccessorKind.FROM_COMPANION);
              assertTrue(fieldSubject.getField().accessFlags.isPrivate());
              checkMethodIsKept(outerClass, getterAccessor);
              checkMethodIsRemoved(outerClass, setterAccessor);
            });
  }

  @Test
  public void testCompanionLateInitProperty_internalPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_useInternalLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass)
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getOuterClassName()));
  }

  @Test
  public void testCompanionLateInitProperty_publicPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePublicLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass)
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getOuterClassName()));
  }

  @Test
  public void testAccessor() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("accessors.AccessorKt", "accessor_accessPropertyFromCompanionClass");
    runTest(
            "accessors",
            mainClass,
            builder -> {
              builder.addClasspathFiles(kotlinc.getKotlinAnnotationJar());
              builder.noClassStaticizing();
            })
        .inspect(
            inspector -> {
              // The classes are removed entirely as a result of member value propagation, inlining,
              // and the fact that the classes do not have observable side effects.
              checkClassIsRemoved(inspector, testedClass.getOuterClassName());
              checkClassIsRemoved(inspector, testedClass.getClassName());
            });
  }

  @Test
  public void testAccessorFromPrivate() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("accessors.AccessorKt",
        "accessor_accessPropertyFromOuterClass");
    runTest("accessors", mainClass)
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, testedClass.getOuterClassName());
              checkClassIsRemoved(inspector, testedClass.getClassName());
            });
  }

  @Test
  public void testAccessorForInnerClassIsRemovedWhenNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath(
            "accessors.PropertyAccessorForInnerClassKt", "noUseOfPropertyAccessorFromInnerClass");
    runTest("accessors", mainClass)
        .inspect(
            inspector -> {
              // Class is removed because the instantiation of the inner class has no side effects.
              checkClassIsRemoved(inspector, PROPERTY_ACCESS_FOR_INNER_CLASS.getClassName());
            });
  }

  @Test
  public void testPrivatePropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivatePropertyAccessorFromInnerClass");
    runTest("accessors", mainClass)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject classSubject = checkClassIsKept(inspector, testedClass.getClassName());

              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);
              assertFalse(fieldSubject.getField().accessFlags.isStatic());

              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
                assertTrue(fieldSubject.getField().accessFlags.isPrivate());
                checkMethodIsKept(classSubject, getterAccessor);
                checkMethodIsRemoved(classSubject, setterAccessor);
            });
  }

  @Test
  public void testPrivateLateInitPropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivateLateInitPropertyAccessorFromInnerClass");
    runTest("accessors", mainClass)
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testAccessorForLambdaIsRemovedWhenNotUsed() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "noUseOfPropertyAccessorFromLambda");
    runTest("accessors", mainClass)
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testAccessorForLambdaCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePropertyAccessorFromLambda");
    runTest("accessors", mainClass)
        .inspect(
            inspector -> {
              if (allowAccessModification) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject classSubject = checkClassIsKept(inspector, testedClass.getClassName());

              // For kotlin 1.6 we completely remove the field and accessors. We are unable to
              // remove the entire class because we are not reprocessing TestMain.main.
              String propertyName = "property";
              if (kotlinParameters.isNewerThanOrEqualTo(KOTLINC_1_6_0)) {
                checkFieldIsAbsent(classSubject, JAVA_LANG_STRING, propertyName);
                return;
              }

              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);
              assertFalse(fieldSubject.getField().accessFlags.isStatic());
              assertTrue(fieldSubject.getField().accessFlags.isPrivate());

              AccessorKind accessorKind =
                  kotlinc.getCompilerVersion().isGreaterThanOrEqualTo(KOTLINC_1_5_0)
                      ? AccessorKind.FROM_INNER
                      : AccessorKind.FROM_LAMBDA;
              MemberNaming.MethodSignature getterAccessor =
                  testedClass.getGetterAccessorForProperty(propertyName, accessorKind);
              MemberNaming.MethodSignature setterAccessor =
                  testedClass.getSetterAccessorForProperty(propertyName, accessorKind);
              checkMethodIsKept(classSubject, getterAccessor);
              checkMethodIsKept(classSubject, setterAccessor);
            });
  }

  @Test
  public void testStaticFieldAccessorWithJasmin() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("Foo");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticField("aField", "I", "5");
    classBuilder.addMainMethod(
        ".limit stack 1",
        "invokestatic Foo$Inner/readField()V",
        "return"
    );
    classBuilder.addStaticMethod("access$field", Collections.emptyList(), "I",
        ".limit stack 1",
        "getstatic Foo.aField I",
        "ireturn");

    classBuilder = jasminBuilder.addClass("Foo$Inner");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticMethod("readField", Collections.emptyList(), "V",
        ".limit stack 2",
        "getstatic java/lang/System.out Ljava/io/PrintStream;",
        "invokestatic Foo/access$field()I",
        "invokevirtual java/io/PrintStream/println(I)V",
        "return"
    );

    Path javaOutput = writeToJar(jasminBuilder);
    ProcessResult javaResult = ToolHelper.runJava(javaOutput, "Foo");
    if (javaResult.exitCode != 0) {
      System.err.println(javaResult.stderr);
      Assert.fail();
    }

    AndroidApp app = compileWithR8(jasminBuilder.build(),
        keepMainProguardConfiguration("Foo") + "\n-dontobfuscate");
    String artOutput = runOnArt(app, "Foo");
    assertEquals(javaResult.stdout, artOutput);
  }
}
