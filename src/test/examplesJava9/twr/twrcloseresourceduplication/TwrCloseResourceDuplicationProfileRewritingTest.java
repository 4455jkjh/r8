// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twr.twrcloseresourceduplication;

import static com.android.tools.r8.desugar.LibraryFilesHelper.getJdk11LibraryFiles;
import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.getDefaultSyntheticItemsTestUtils;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwrCloseResourceDuplicationProfileRewritingTest
    extends TwrCloseResourceDuplicationTest {

  @Test
  public void testD8ProfileRewriting() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(TwrCloseResourceDuplicationTest.getProgramInputs())
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(options -> options.testing.enableSyntheticSharing = false)
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder ->
                testBuilder
                    .addLibraryFiles(getJdk11LibraryFiles(temp))
                    .addDefaultRuntimeLibrary(parameters),
            testBuilder ->
                testBuilder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST)))
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), MAIN, getZipFile())
        .assertSuccessWithOutput(TwrCloseResourceDuplicationTest.EXPECTED);
  }

  @Test
  public void testR8ProfileRewriting() throws Exception {
    parameters.assumeR8TestParameters();
    Box<SyntheticItemsTestUtils> syntheticItems = new Box<>();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(TwrCloseResourceDuplicationTest.getProgramInputs())
        .addKeepMainRule(MAIN)
        .addKeepClassAndMembersRules(FOO, BAR)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(
            options -> {
              options.desugarSpecificOptions().minimizeSyntheticNames = true;
              options.testing.enableSyntheticSharing = false;
            })
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder ->
                testBuilder
                    .addLibraryFiles(getJdk11LibraryFiles(temp))
                    .addDefaultRuntimeLibrary(parameters)
                    .addOptionsModification(
                        options ->
                            options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces()),
            testBuilder ->
                testBuilder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST)))
        .collectSyntheticItems()
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectSyntheticItems(syntheticItems::set)
        .inspectResidualArtProfile(
            (profileInspector, inspector) ->
                inspectR8(profileInspector, inspector, syntheticItems.get()))
        .run(parameters.getRuntime(), MAIN, getZipFile())
        .assertSuccessWithOutput(TwrCloseResourceDuplicationTest.EXPECTED);
  }

  private ExternalArtProfile getArtProfile() {
    List<TypeReference> closeResourceFormalParameters =
        ImmutableList.of(
            Reference.classFromClass(Throwable.class),
            Reference.classFromClass(AutoCloseable.class));
    return ExternalArtProfile.builder()
        .addMethodRule(
            Reference.method(
                Reference.classFromTypeName(FOO),
                "foo",
                ImmutableList.of(Reference.classFromClass(String.class)),
                null))
        .addMethodRule(
            // NOTE: The $closeResource helper is _only_ generated by the JDK-9 compiler.
            Reference.method(
                Reference.classFromTypeName(FOO),
                "$closeResource",
                closeResourceFormalParameters,
                null))
        .addMethodRule(
            Reference.method(
                Reference.classFromTypeName(BAR),
                "bar",
                ImmutableList.of(Reference.classFromClass(String.class)),
                null))
        .addMethodRule(
            // NOTE: The $closeResource helper is _only_ generated by the JDK-9 compiler.
            Reference.method(
                Reference.classFromTypeName(BAR),
                "$closeResource",
                closeResourceFormalParameters,
                null))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        hasTwrCloseResourceSupport(true),
        getDefaultSyntheticItemsTestUtils());
  }

  private void inspectR8(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      SyntheticItemsTestUtils syntheticItems) {
    inspect(
        profileInspector,
        inspector,
        hasTwrCloseResourceSupport(parameters.isDexRuntime()),
        syntheticItems);
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean hasTwrCloseResourceSupport,
      SyntheticItemsTestUtils syntheticItems) {
    int expectedClassCount = 3;
    if (!hasTwrCloseResourceSupport) {
      expectedClassCount += 8;
    }
    if (hasTwrCloseResourceApiOutlines()) {
      expectedClassCount += 4;
    }
    InternalOptions options = inspector.getApplication().options;
    options.setMinApiLevel(parameters.getApiLevel());
    if (options.shouldDesugarAutoCloseable()) {
      expectedClassCount += 12;
    }
    inspector
        .allClasses()
        .forEach(c -> System.out.println(c.getDexProgramClass().toSourceString()));
    assertEquals(expectedClassCount, inspector.allClasses().size());
    assertThat(inspector.clazz(MAIN), isPresent());

    // Class Foo has two methods foo() and $closeResource().
    ClassSubject fooClassSubject = inspector.clazz(FOO);
    assertThat(fooClassSubject, isPresent());

    MethodSubject fooMethodSubject = fooClassSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethodSubject, isPresent());

    MethodSubject fooCloseResourceMethodSubject =
        fooClassSubject.uniqueMethodWithOriginalName("$closeResource");
    assertThat(fooCloseResourceMethodSubject, isPresent());

    // Class Bar has two methods bar() and $closeResource().
    ClassSubject barClassSubject = inspector.clazz(BAR);
    assertThat(barClassSubject, isPresent());

    MethodSubject barMethodSubject = barClassSubject.uniqueMethodWithOriginalName("bar");
    assertThat(barMethodSubject, isPresent());

    MethodSubject barCloseResourceMethodSubject =
        barClassSubject.uniqueMethodWithOriginalName("$closeResource");
    assertThat(barCloseResourceMethodSubject, isPresent());

    profileInspector
        .assertContainsClassRules(fooClassSubject, barClassSubject)
        .assertContainsMethodRules(
            fooMethodSubject,
            fooCloseResourceMethodSubject,
            barMethodSubject,
            barCloseResourceMethodSubject);

    // There is 1 backport, 2 synthetic API outlines, and 3 twr classes for both Foo and Bar.
    for (String clazz : ImmutableList.of(FOO, BAR)) {
      ClassSubject syntheticApiOutlineClassSubject0 =
          inspector.syntheticClass(
              syntheticItems.syntheticApiOutlineClass(Reference.classFromTypeName(clazz), 0));
      assertThat(syntheticApiOutlineClassSubject0, isPresentIf(hasTwrCloseResourceApiOutlines()));

      ClassSubject syntheticApiOutlineClassSubject1 =
          inspector.syntheticClass(
              syntheticItems.syntheticApiOutlineClass(Reference.classFromTypeName(clazz), 1));
      assertThat(syntheticApiOutlineClassSubject1, isPresentIf(hasTwrCloseResourceApiOutlines()));

      int initialSyntheticId = hasTwrCloseResourceApiOutlines() ? 2 : 0;

      ClassSubject syntheticBackportClassSubject =
          inspector.syntheticClass(
              syntheticItems.syntheticBackportClass(
                  Reference.classFromTypeName(clazz), initialSyntheticId));
      assertThat(syntheticBackportClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject3 =
          inspector.syntheticClass(
              syntheticItems.syntheticTwrCloseResourceClass(
                  Reference.classFromTypeName(clazz), initialSyntheticId + 1));
      assertThat(
          syntheticTwrCloseResourceClassSubject3, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject4 =
          inspector.syntheticClass(
              syntheticItems.syntheticTwrCloseResourceClass(
                  Reference.classFromTypeName(clazz), initialSyntheticId + 2));
      assertThat(
          syntheticTwrCloseResourceClassSubject4, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject5 =
          inspector.syntheticClass(
              syntheticItems.syntheticTwrCloseResourceClass(
                  Reference.classFromTypeName(clazz), initialSyntheticId + 3));
      assertThat(
          syntheticTwrCloseResourceClassSubject5, notIf(isPresent(), hasTwrCloseResourceSupport));

      profileInspector.applyIf(
          hasTwrCloseResourceApiOutlines(),
          i ->
              i.assertContainsClassRules(
                      syntheticApiOutlineClassSubject0, syntheticApiOutlineClassSubject1)
                  .assertContainsMethodRules(
                      syntheticApiOutlineClassSubject0.uniqueMethod(),
                      syntheticApiOutlineClassSubject1.uniqueMethod()));

      profileInspector.applyIf(
          !hasTwrCloseResourceSupport,
          i ->
              i.assertContainsClassRules(
                      syntheticBackportClassSubject,
                      syntheticTwrCloseResourceClassSubject3,
                      syntheticTwrCloseResourceClassSubject4,
                      syntheticTwrCloseResourceClassSubject5)
                  .assertContainsMethodRules(
                      syntheticBackportClassSubject.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject3.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject4.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject5.uniqueMethod()));
    }

    profileInspector.applyIf(
        options.shouldDesugarAutoCloseable(),
        i ->
            i.assertContainsClassRules(
                    getCloseDispatcherSyntheticClasses(inspector, syntheticItems))
                .assertContainsMethodRules(
                    Arrays.stream(getCloseDispatcherSyntheticClasses(inspector, syntheticItems))
                        .map(ClassSubject::uniqueMethod)
                        .toArray(MethodSubject[]::new)));

    profileInspector.assertContainsNoOtherRules();
  }

  private static ClassSubject[] getCloseDispatcherSyntheticClasses(
      CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    return new ClassSubject[] {
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableDispatcherClass(
              Reference.classFromTypeName(FOO), 0)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableDispatcherClass(
              Reference.classFromTypeName(FOO), 1)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableDispatcherClass(
              Reference.classFromTypeName(BAR), 0)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableDispatcherClass(
              Reference.classFromTypeName(BAR), 1)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableForwarderClass(Reference.classFromTypeName(FOO), 2)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableForwarderClass(Reference.classFromTypeName(FOO), 3)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableForwarderClass(Reference.classFromTypeName(BAR), 2)),
      inspector.syntheticClass(
          syntheticItems.syntheticAutoCloseableForwarderClass(Reference.classFromTypeName(BAR), 3)),
      inspector.syntheticClass(
          syntheticItems.syntheticThrowIAEClass(Reference.classFromTypeName(FOO), 4)),
      inspector.syntheticClass(
          syntheticItems.syntheticThrowIAEClass(Reference.classFromTypeName(FOO), 5)),
      inspector.syntheticClass(
          syntheticItems.syntheticThrowIAEClass(Reference.classFromTypeName(BAR), 4)),
      inspector.syntheticClass(
          syntheticItems.syntheticThrowIAEClass(Reference.classFromTypeName(BAR), 5))
    };
  }
}
