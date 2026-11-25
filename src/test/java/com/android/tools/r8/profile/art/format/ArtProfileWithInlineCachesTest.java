// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.format;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData.EntryValue;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.ArtProfileRuleConsumer;
import com.android.tools.r8.profile.art.diagnostic.HumanReadableArtProfileParserErrorDiagnostic;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtProfileWithInlineCachesTest extends TestBase {

  private static final ClassReference MAIN_CLASS = Reference.classFromClass(Main.class);
  private static final ClassReference SUPER_CLASS = Reference.classFromClass(Super.class);
  private static final ClassReference SUB_A_CLASS = Reference.classFromClass(SubA.class);
  private static final ClassReference SUB_B_CLASS = Reference.classFromClass(SubB.class);
  private static final ClassReference SUB_C_CLASS = Reference.classFromClass(SubC.class);
  private static final ClassReference OTHER_CLASS = Reference.classFromClass(Other.class);

  private static final MethodReference MAIN_METHOD = MethodReferenceUtils.mainMethod(Main.class);
  private static final MethodReference MONO_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mMono");
  private static final MethodReference POLY_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mPoly");
  private static final MethodReference MISSING_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mMissing");
  private static final MethodReference MEGAMORPHIC_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mMegamorphic");
  private static final MethodReference TARGETED_SINGLE_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mTargetedSingle");
  private static final MethodReference TARGETED_MULTI_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mTargetedMulti");
  private static final MethodReference TARGETED_MARKERS_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mTargetedMarkers");
  private static final MethodReference ARRAYS_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mArrays");
  private static final MethodReference SINGLE_ARRAY_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mSingleArray");
  private static final MethodReference DUPLICATE_RECEIVER_METHOD =
      MethodReferenceUtils.methodFromMethod(Main.class, "mDuplicateReceiver");

  private static final TypeReference OBJECT_ARRAY =
      Reference.typeFromDescriptor("[Ljava/lang/Object;");
  private static final TypeReference STRING_ARRAY =
      Reference.typeFromDescriptor("[Ljava/lang/String;");
  private static final TypeReference INT_ARRAY = Reference.typeFromDescriptor("[I");

  private static Path validProfile;
  private static Path invalidProfile;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    validProfile = getStaticTemp().newFile("valid-profile.txt").toPath();
    FileUtils.writeTextFile(validProfile, getValidInputLines());

    invalidProfile = getStaticTemp().newFile("invalid-profile.txt").toPath();
    List<String> invalidInput = new ArrayList<>(getInvalidRules());
    invalidInput.add(MAIN_CLASS.getDescriptor());
    FileUtils.writeTextFile(invalidProfile, invalidInput);
  }

  private static List<String> getValidInputLines() {
    return ImmutableList.of(
        MAIN_CLASS.getDescriptor(),
        "HSP"
            + MethodReferenceUtils.toSmaliString(MAIN_METHOD)
            + "+"
            + SUB_A_CLASS.getDescriptor()
            + " # trailing inline cache comment",
        "H" + MethodReferenceUtils.toSmaliString(MONO_METHOD) + "+" + SUB_A_CLASS.getDescriptor(),
        "HP"
            + MethodReferenceUtils.toSmaliString(POLY_METHOD)
            + "+"
            + SUB_A_CLASS.getDescriptor()
            + ","
            + SUB_B_CLASS.getDescriptor(),
        "H" + MethodReferenceUtils.toSmaliString(MISSING_METHOD) + "+missing_types",
        "H" + MethodReferenceUtils.toSmaliString(MEGAMORPHIC_METHOD) + "+megamorphic_types",
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_SINGLE_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor(),
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_MULTI_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor()
            + "]"
            + OTHER_CLASS.getDescriptor()
            + SUB_B_CLASS.getDescriptor()
            + ","
            + SUB_C_CLASS.getDescriptor(),
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_MARKERS_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + "missing_types]"
            + OTHER_CLASS.getDescriptor()
            + "megamorphic_types",
        "H"
            + MethodReferenceUtils.toSmaliString(ARRAYS_METHOD)
            + "+][Ljava/lang/Object;[Ljava/lang/String;,[I",
        "H" + MethodReferenceUtils.toSmaliString(SINGLE_ARRAY_METHOD) + "+[Ljava/lang/String;,[I",
        "H"
            + MethodReferenceUtils.toSmaliString(DUPLICATE_RECEIVER_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor()
            + "]"
            + SUPER_CLASS.getDescriptor()
            + SUB_B_CLASS.getDescriptor());
  }

  private static List<String> getExpectedValidOutputLines() {
    return ImmutableList.of(
        MAIN_CLASS.getDescriptor(),
        "HSP" + MethodReferenceUtils.toSmaliString(MAIN_METHOD) + "+" + SUB_A_CLASS.getDescriptor(),
        "H" + MethodReferenceUtils.toSmaliString(MONO_METHOD) + "+" + SUB_A_CLASS.getDescriptor(),
        "HP"
            + MethodReferenceUtils.toSmaliString(POLY_METHOD)
            + "+"
            + SUB_A_CLASS.getDescriptor()
            + ","
            + SUB_B_CLASS.getDescriptor(),
        "H" + MethodReferenceUtils.toSmaliString(MISSING_METHOD) + "+missing_types",
        "H" + MethodReferenceUtils.toSmaliString(MEGAMORPHIC_METHOD) + "+megamorphic_types",
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_SINGLE_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor(),
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_MULTI_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor()
            + "]"
            + OTHER_CLASS.getDescriptor()
            + SUB_B_CLASS.getDescriptor()
            + ","
            + SUB_C_CLASS.getDescriptor(),
        "H"
            + MethodReferenceUtils.toSmaliString(TARGETED_MARKERS_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + "missing_types]"
            + OTHER_CLASS.getDescriptor()
            + "megamorphic_types",
        "H"
            + MethodReferenceUtils.toSmaliString(ARRAYS_METHOD)
            + "+][Ljava/lang/Object;[Ljava/lang/String;,[I",
        "H" + MethodReferenceUtils.toSmaliString(SINGLE_ARRAY_METHOD) + "+[Ljava/lang/String;,[I",
        "H"
            + MethodReferenceUtils.toSmaliString(DUPLICATE_RECEIVER_METHOD)
            + "+]"
            + SUPER_CLASS.getDescriptor()
            + SUB_A_CLASS.getDescriptor()
            + ","
            + SUB_B_CLASS.getDescriptor());
  }

  private static List<String> getInvalidRules() {
    String prefix = "HSP" + MethodReferenceUtils.toSmaliString(MAIN_METHOD);
    String superDesc = SUPER_CLASS.getDescriptor();
    String subADesc = SUB_A_CLASS.getDescriptor();
    String subBDesc = SUB_B_CLASS.getDescriptor();
    String otherDesc = OTHER_CLASS.getDescriptor();
    String testBinaryName = binaryName(ArtProfileWithInlineCachesTest.class);
    return ImmutableList.of(
        // Trailing '+'
        prefix + "+",
        // Multiple '+'
        prefix + "+" + subADesc + "+" + subBDesc,
        // Empty ']' segments
        prefix + "+]",
        prefix + "+]]" + superDesc + subADesc,
        prefix + "+]" + superDesc + subADesc + "]",
        prefix + "+]" + superDesc + "]" + otherDesc + subADesc,
        // Missing targets after receiver
        prefix + "+]" + superDesc,
        prefix + "+][Ljava/lang/Object;",
        // Bare primitive receiver or target
        prefix + "+]I" + subADesc,
        prefix + "+I",
        prefix + "+V",
        prefix + "+]" + superDesc + "I",
        // Void array
        prefix + "+[V",
        prefix + "+][V" + subADesc,
        prefix + "+]" + superDesc + "[V",
        // Empty / leading / trailing commas
        prefix + "+," + subADesc,
        prefix + "+" + subADesc + ",",
        prefix + "+" + subADesc + ",," + subBDesc,
        prefix + "+]" + superDesc + "," + subADesc,
        prefix + "+]" + superDesc + subADesc + ",",
        // Invalid descriptors / wildcards / unexpected ']'
        prefix + "+invalid_class",
        prefix + "+[",
        prefix + "+L" + testBinaryName + "$Sub*;",
        prefix + "+]L" + testBinaryName + "$Super?;" + subADesc,
        prefix + "+" + subADesc + "]" + otherDesc + subBDesc);
  }

  private static List<String> getExpectedInvalidOutputLines() {
    return ImmutableList.of(MAIN_CLASS.getDescriptor());
  }

  @Test
  public void testValidD8() throws Exception {
    parameters.assumeDexRuntime();
    Path outputProfile = temp.newFile("valid-d8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();
    testForD8()
        .addProgramClasses(Main.class, Super.class, SubA.class, SubB.class, SubC.class, Other.class)
        .addArtProfileForRewriting(
            getArtProfileProvider(validProfile),
            createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
        .release()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
    assertEquals(getExpectedValidOutputLines(), FileUtils.readAllLines(outputProfile));
    inspectValidResidualArtProfile(new ArtProfileInspector(residualProfileBox.get()));
  }

  @Test
  public void testValidR8() throws Exception {
    Path outputProfile = temp.newFile("valid-r8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();
    testForR8(parameters)
        .addProgramClasses(Main.class, Super.class, SubA.class, SubB.class, SubC.class, Other.class)
        .addKeepAllClassesRule()
        .addArtProfileForRewriting(
            getArtProfileProvider(validProfile),
            createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
    assertEquals(getExpectedValidOutputLines(), FileUtils.readAllLines(outputProfile));
    inspectValidResidualArtProfile(new ArtProfileInspector(residualProfileBox.get()));
  }

  @Test
  public void testInvalidD8() throws Exception {
    parameters.assumeDexRuntime();
    Path outputProfile = temp.newFile("invalid-d8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();
    testForD8()
        .addProgramClasses(Main.class, Super.class, SubA.class, SubB.class, SubC.class, Other.class)
        .addArtProfileForRewriting(
            getArtProfileProvider(invalidProfile),
            createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
        .release()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspectInvalidDiagnostics);
    assertEquals(getExpectedInvalidOutputLines(), FileUtils.readAllLines(outputProfile));
    inspectInvalidResidualArtProfile(new ArtProfileInspector(residualProfileBox.get()));
  }

  @Test
  public void testInvalidR8() throws Exception {
    Path outputProfile = temp.newFile("invalid-r8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();
    testForR8(parameters)
        .addProgramClasses(Main.class, Super.class, SubA.class, SubB.class, SubC.class, Other.class)
        .addKeepAllClassesRule()
        .addArtProfileForRewriting(
            getArtProfileProvider(invalidProfile),
            createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(this::inspectInvalidDiagnostics);
    assertEquals(getExpectedInvalidOutputLines(), FileUtils.readAllLines(outputProfile));
    inspectInvalidResidualArtProfile(new ArtProfileInspector(residualProfileBox.get()));
  }

  private ArtProfileProvider getArtProfileProvider(Path profilePath) {
    return new ArtProfileProvider() {

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        try {
          profileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(profilePath), parserBuilder -> {});
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(profilePath);
      }
    };
  }

  private ArtProfileConsumer createCombinedArtProfileConsumer(
      Path outputProfile, Box<ExternalArtProfile> residualProfileBox) {
    ArtProfileConsumer humanReadableConsumer = ArtProfileConsumerUtils.create(outputProfile);
    ArtProfileConsumer ruleConsumerDelegate =
        ArtProfileTestingUtils.createResidualArtProfileConsumer(residualProfileBox::set);
    return new ArtProfileConsumer() {

      @Override
      public TextOutputStream getHumanReadableArtProfileConsumer() {
        return humanReadableConsumer.getHumanReadableArtProfileConsumer();
      }

      @Override
      public ArtProfileRuleConsumer getRuleConsumer() {
        return ruleConsumerDelegate.getRuleConsumer();
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        humanReadableConsumer.finished(handler);
        ruleConsumerDelegate.finished(handler);
      }
    };
  }

  private void inspectValidResidualArtProfile(ArtProfileInspector profileInspector) {
    profileInspector
        .assertContainsClassRule(MAIN_CLASS)
        .inspectMethodRule(
            MAIN_METHOD,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertIsStartup()
                    .assertIsPostStartup()
                    .assertHasSingleInlineCache(SUB_A_CLASS))
        .inspectMethodRule(
            MONO_METHOD,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertNotStartup()
                    .assertNotPostStartup()
                    .assertHasSingleInlineCache(SUB_A_CLASS))
        .inspectMethodRule(
            POLY_METHOD,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertNotStartup()
                    .assertIsPostStartup()
                    .assertHasSingleInlineCache(SUB_A_CLASS, SUB_B_CLASS))
        .inspectMethodRule(
            MISSING_METHOD,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.createSingle(EntryValue.missingTypes())))
        .inspectMethodRule(
            MEGAMORPHIC_METHOD,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.createSingle(EntryValue.megamorphicTypes())))
        .inspectMethodRule(
            TARGETED_SINGLE_METHOD,
            ruleInspector -> ruleInspector.assertHasTargetedInlineCache(SUPER_CLASS, SUB_A_CLASS))
        .inspectMethodRule(
            TARGETED_MULTI_METHOD,
            ruleInspector ->
                ruleInspector
                    .assertHasTargetedInlineCache(SUPER_CLASS, SUB_A_CLASS)
                    .assertHasTargetedInlineCache(OTHER_CLASS, SUB_B_CLASS, SUB_C_CLASS))
        .inspectMethodRule(
            TARGETED_MARKERS_METHOD,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.builder()
                        .addTargetedInlineCache(SUPER_CLASS, EntryValue.missingTypes())
                        .addTargetedInlineCache(OTHER_CLASS, EntryValue.megamorphicTypes())
                        .build()))
        .inspectMethodRule(
            ARRAYS_METHOD,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(OBJECT_ARRAY, STRING_ARRAY, INT_ARRAY))
        .inspectMethodRule(
            SINGLE_ARRAY_METHOD,
            ruleInspector -> ruleInspector.assertHasSingleInlineCache(STRING_ARRAY, INT_ARRAY))
        .inspectMethodRule(
            DUPLICATE_RECEIVER_METHOD,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(SUPER_CLASS, SUB_A_CLASS, SUB_B_CLASS))
        .assertContainsNoOtherRules();
  }

  private void inspectInvalidDiagnostics(TestDiagnosticMessages diagnostics) {
    List<Matcher<Diagnostic>> expectedDiagnostics = new ArrayList<>();
    List<String> invalidRules = getInvalidRules();
    for (int i = 0; i < invalidRules.size(); i++) {
      String expectedMessage =
          "Unable to parse rule at line " + (i + 1) + " from ART profile: " + invalidRules.get(i);
      expectedDiagnostics.add(
          allOf(
              diagnosticType(HumanReadableArtProfileParserErrorDiagnostic.class),
              diagnosticMessage(equalTo(expectedMessage))));
    }
    diagnostics.assertInfosMatch(expectedDiagnostics);
  }

  private void inspectInvalidResidualArtProfile(ArtProfileInspector profileInspector) {
    profileInspector.assertContainsClassRule(MAIN_CLASS).assertContainsNoOtherRules();
  }

  static class Super {}

  static class SubA extends Super {}

  static class SubB extends Super {}

  static class SubC extends Other {}

  static class Other {}

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }

    static void mMono() {}

    static void mPoly() {}

    static void mMissing() {}

    static void mMegamorphic() {}

    static void mTargetedSingle() {}

    static void mTargetedMulti() {}

    static void mTargetedMarkers() {}

    static void mArrays() {}

    static void mSingleArray() {}

    static void mDuplicateReceiver() {}
  }
}
