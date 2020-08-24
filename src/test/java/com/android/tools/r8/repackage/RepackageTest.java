// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassAllowRenamingDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassAllowRenamingIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicKeptMethodAllowRenamingOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicKeptMethodOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnKeptClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnKeptClassAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.KeptClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.KeptClassAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.ReachableClassWithKeptMethod;
import com.android.tools.r8.repackage.testclasses.repackagetest.ReachableClassWithKeptMethodAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.TestClass;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageTest extends TestBase {

  private static final String FLATTEN_PACKAGE_HIERARCHY = "flattenpackagehierarchy";
  private static final String REPACKAGE_CLASSES = "repackageclasses";
  private static final String REPACKAGE_DIR = "foo";

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "KeptClass.publicMethod()",
          "KeptClass.packagePrivateMethod()",
          "KeptClass.packagePrivateMethod()",
          "KeptClassAllowRenaming.publicMethod()",
          "KeptClassAllowRenaming.packagePrivateMethod()",
          "KeptClassAllowRenaming.packagePrivateMethod()",
          "ReachableClassWithKeptMethod.publicMethod()",
          "ReachableClassWithKeptMethod.packagePrivateMethod()",
          "ReachableClassWithKeptMethod.packagePrivateMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.publicMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.packagePrivateMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.packagePrivateMethod()",
          "ReachableClass.publicMethod()",
          "ReachableClass.packagePrivateMethod()",
          "ReachableClass.packagePrivateMethod()");

  private final boolean allowAccessModification;
  private final boolean enableExperimentalRepackaging;
  private final String flattenPackageHierarchyOrRepackageClasses;
  private final TestParameters parameters;

  @Parameters(name = "{3}, allow access modification: {0}, experimental: {1}, kind: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageTest(
      boolean allowAccessModification,
      boolean enableExperimentalRepackaging,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    this.allowAccessModification = allowAccessModification;
    this.enableExperimentalRepackaging = enableExperimentalRepackaging;
    this.flattenPackageHierarchyOrRepackageClasses = flattenPackageHierarchyOrRepackageClasses;
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse(allowAccessModification);
    assumeFalse(enableExperimentalRepackaging);
    assumeTrue(flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY));
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(!enableExperimentalRepackaging || parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getClassFilesForTestPackage(TestClass.class.getPackage()))
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-" + flattenPackageHierarchyOrRepackageClasses + " \"" + REPACKAGE_DIR + "\"",
            "-keep class " + KeptClass.class.getTypeName(),
            "-keep,allowobfuscation class " + KeptClassAllowRenaming.class.getTypeName(),
            "-keepclassmembers class " + ReachableClassWithKeptMethod.class.getTypeName() + " {",
            "  <methods>;",
            "}",
            "-keepclassmembers,allowobfuscation class "
                + ReachableClassWithKeptMethodAllowRenaming.class.getTypeName()
                + " {",
            "  <methods>;",
            "}")
        .allowAccessModification(allowAccessModification)
        .addOptionsModification(
            options ->
                options.testing.enableExperimentalRepackaging = enableExperimentalRepackaging)
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    forEachClass(
        (clazz, eligibleForRepackaging) -> {
          ClassSubject subject = inspector.clazz(clazz);
          assertThat(subject, isPresent());
          if (eligibleForRepackaging) {
            assertEquals(
                clazz.getTypeName(),
                flattenPackageHierarchyOrRepackageClasses.equals(FLATTEN_PACKAGE_HIERARCHY)
                    ? REPACKAGE_DIR + ".a"
                    : REPACKAGE_DIR + "",
                subject.getDexProgramClass().getType().getPackageName());
          } else {
            assertEquals(
                clazz.getTypeName(),
                RepackageTest.class.getPackage().getName() + ".testclasses.repackagetest",
                subject.getDexProgramClass().getType().getPackageName());
          }
        });
  }

  /**
   * For each test class, calls {@param consumer} with a boolean that indicates if the class is
   * eligible for repackaging (or it needs to stay in its original package).
   */
  private void forEachClass(BiConsumer<Class<?>, Boolean> consumer) {
    // TODO(b/165783399): This should be renamed to markAlwaysEligible() and always pass `true` to
    //  the consumer, since these classes should be repackaged independent of
    //  -allowaccessmodification.
    Consumer<Class<?>> markShouldAlwaysBeEligible =
        clazz -> consumer.accept(clazz, allowAccessModification || enableExperimentalRepackaging);
    Consumer<Class<?>> markEligibleWithAllowAccessModification =
        clazz -> consumer.accept(clazz, allowAccessModification);

    // 1) -keep class KeptClass

    // 1.A) Accessing a public method on a kept class is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnKeptClass.class);

    // 1.B) Accessing a package-private method on a kept class requires -allowaccessmodification.
    markEligibleWithAllowAccessModification.accept(
        AccessPackagePrivateMethodOnKeptClassDirect.class);

    // 1.C) Accessing a package-private method that accesses a package-private method on a kept
    //      class requires -allowaccessmodification.
    markEligibleWithAllowAccessModification.accept(
        AccessPackagePrivateMethodOnKeptClassIndirect.class);

    // 2) -keep,allowobfuscation class KeptClass

    // 2.A, 2.B, 2.C) Accessing a method on a kept class that is allowed to be renamed is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnKeptClassAllowRenaming.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateMethodOnKeptClassAllowRenamingDirect.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateMethodOnKeptClassAllowRenamingIndirect.class);

    // 3) -keepclassmembers class ReachableClassWithKeptMethod { <methods>; }

    // 3.A, 3.B, 3.C) Accessing a kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicKeptMethodOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateKeptMethodOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateKeptMethodOnReachableClassIndirect.class);

    // 4) -keepclassmembers,allowobfuscation class ReachableClassWithKeptMethod { <methods>; }

    // 4.A, 4.B, 4.C) Accessing a kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicKeptMethodAllowRenamingOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassIndirect.class);

    // 5) No keep rule.

    // 5.A, 5.B, 5.C) Accessing a non-kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateMethodOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateMethodOnReachableClassIndirect.class);
  }
}
