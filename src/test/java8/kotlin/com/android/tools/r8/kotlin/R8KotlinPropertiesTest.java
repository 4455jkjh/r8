// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8KotlinPropertiesTest extends AbstractR8KotlinTestBase {

  private static final String PACKAGE_NAME = "properties";

  private static final String JAVA_LANG_STRING = "java.lang.String";

  private static final TestKotlinClass MUTABLE_PROPERTY_CLASS =
      new TestKotlinClass("properties.MutableProperty")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  private static final TestKotlinClass USER_DEFINED_PROPERTY_CLASS =
      new TestKotlinClass("properties.UserDefinedProperty")
          .addProperty("durationInMilliSeconds", "int", Visibility.PUBLIC)
          .addProperty("durationInSeconds", "int", Visibility.PUBLIC);

  private static final TestKotlinClass LATE_INIT_PROPERTY_CLASS =
      new TestKotlinClass("properties.LateInitProperty")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedLateInitProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_LATE_INIT_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionLateInitProperties")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinClass OBJECT_PROPERTY_CLASS =
      new TestKotlinClass("properties.ObjectProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestFileLevelKotlinClass FILE_PROPERTY_CLASS =
      new TestFileLevelKotlinClass("properties.FilePropertiesKt")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final Consumer<InternalOptions> disableAggressiveClassOptimizations =
      options -> {
        options.enableClassInlining = false;
        options.getVerticalClassMergerOptions().disable();
      };

  private static final BiFunction<AndroidApiLevel, List<Path>, R8TestCompileResult>
      compiledResults = memoizeBiFunction(R8KotlinPropertiesTest::compileR8);

  @Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilers().build());
  }

  public R8KotlinPropertiesTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(parameters, kotlinParameters, true);
  }

  private static R8TestCompileResult compileR8(AndroidApiLevel apiLevel, List<Path> classpath)
      throws Exception {
    return testForR8(getStaticTemp(), apiLevel == null ? Backend.CF : Backend.DEX)
        .addProgramFiles(classpath)
        .addKeepMainRule(JASMIN_MAIN_CLASS)
        .allowAccessModification()
        .enableProguardTestOptions()
        .addDontObfuscate()
        .applyIf(apiLevel != null, b -> b.setMinApi(apiLevel))
        .addOptionsModification(disableAggressiveClassOptimizations)
        .compile();
  }

  @Override
  protected R8TestCompileResult compileWithR8(
      String mainClass, ThrowableConsumer<R8FullTestBuilder> configuration) {
    return compiledResults.apply(testParameters.getApiLevel(), ImmutableList.copyOf(classpath));
  }

  @Test
  public void testMutableProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_useProtectedProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties/LateInitPropertyKt", "lateInitProperty_usePrivateLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties/LateInitPropertyKt", "lateInitProperty_useProtectedLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties/LateInitPropertyKt", "lateInitProperty_useInternalLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties/LateInitPropertyKt", "lateInitProperty_usePublicLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testUserDefinedProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties/UserDefinedPropertyKt", "userDefinedProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector ->
                checkClassIsRemoved(inspector, USER_DEFINED_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testUserDefinedProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties/UserDefinedPropertyKt", "userDefinedProperty_useProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector ->
                checkClassIsRemoved(inspector, USER_DEFINED_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testCompanionProperty_primitivePropertyCannotBeInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties.CompanionPropertiesKt", "companionProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());
              checkClassIsRemoved(inspector, "properties.CompanionProperties");
            });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties.CompanionPropertiesKt", "companionProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());
              checkClassIsRemoved(inspector, "properties.CompanionProperties");
            });
  }

  @Test
  public void testCompanionProperty_internalPropertyCannotBeInlined() throws Exception {
    String mainClass =
        addMainToClasspath(
            "properties.CompanionPropertiesKt", "companionProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, "properties.CompanionProperties"));
  }

  @Test
  public void testCompanionProperty_publicPropertyCannotBeInlined() throws Exception {
    String mainClass =
        addMainToClasspath("properties.CompanionPropertiesKt", "companionProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());
              checkClassIsRemoved(inspector, "properties.CompanionProperties");
            });
  }

  @Test
  public void testCompanionProperty_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.CompanionLateInitPropertiesKt",
            "companionLateInitProperties_usePrivateLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, testedClass.getClassName());
              checkClassIsRemoved(inspector, testedClass.getOuterClassName());
            });
  }

  @Test
  public void testCompanionProperty_internalLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.CompanionLateInitPropertiesKt",
            "companionLateInitProperties_useInternalLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, testedClass.getClassName());
              checkClassIsRemoved(inspector, testedClass.getOuterClassName());
            });
  }

  @Test
  public void testCompanionProperty_publicLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.CompanionLateInitPropertiesKt",
            "companionLateInitProperties_usePublicLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_primitivePropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.ObjectPropertiesKt", "objectProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.ObjectPropertiesKt", "objectProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_internalPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.ObjectPropertiesKt", "objectProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_publicPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.ObjectPropertiesKt", "objectProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.ObjectPropertiesKt", "objectProperties_useLateInitPrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_internalLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.ObjectPropertiesKt", "objectProperties_useLateInitInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_publicLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath(
            "properties.ObjectPropertiesKt", "objectProperties_useLateInitPublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_primitivePropertyIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_internalPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_publicPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_useLateInitPrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_internalLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_useLateInitInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_publicLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("properties.FilePropertiesKt", "fileProperties_useLateInitPublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }
}
