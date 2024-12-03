// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package twr.twrcloseresourceduplication;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import twr.twrcloseresourceduplication.asm.TwrCloseResourceDuplication$BarDump;
import twr.twrcloseresourceduplication.asm.TwrCloseResourceDuplication$FooDump;
import twr.twrcloseresourceduplication.asm.TwrCloseResourceDuplicationDump;

@RunWith(Parameterized.class)
public class TwrCloseResourceDuplicationTest extends TestBase {

  protected static final String MAIN =
      "twr.twrcloseresourceduplication.TwrCloseResourceDuplication";
  protected static final String FOO =
      "twr.twrcloseresourceduplication.TwrCloseResourceDuplication$Foo";
  protected static final String BAR =
      "twr.twrcloseresourceduplication.TwrCloseResourceDuplication$Bar";

  static final int INPUT_CLASSES = 3;

  protected static final String EXPECTED =
      StringUtils.lines(
          "foo opened 1",
          "foo post close 1",
          "foo opened 2",
          "foo caught from 2: RuntimeException",
          "foo post close 2",
          "bar opened 1",
          "bar post close 1",
          "bar opened 2",
          "bar caught from 2: RuntimeException",
          "bar post close 2");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  protected boolean hasTwrCloseResourceSupport(boolean isDesugaring) {
    return !isDesugaring
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithTwrCloseResourceSupport());
  }

  protected boolean hasTwrCloseResourceApiOutlines() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport());
  }

  protected String getZipFile() throws IOException {
    return ZipUtils.ZipBuilder.builder(temp.newFile("file.zip").toPath())
        // DEX VMs from 4.4 up-to 9.0 including, will fail if no entry is added.
        .addBytes("entry", new byte[1])
        .build()
        .toString();
  }

  protected static List<byte[]> getProgramInputs() throws Exception {
    return ImmutableList.of(
        TwrCloseResourceDuplicationDump.dump(),
        TwrCloseResourceDuplication$FooDump.dump(),
        TwrCloseResourceDuplication$BarDump.dump());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getProgramInputs())
        .run(parameters.getRuntime(), MAIN, getZipFile())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getProgramInputs())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN, getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              // NOTE: The $closeResource helper is _only_ generated by the JDK-9 compiler.
              //
              // There should be two synthetic classes besides the three program classes.
              // One for the desugar version of TWR $closeResource and one for the
              // Throwable.addSuppressed that is still present in the original $closeResource.
              // TODO(b/214329923): If the original $closeResource is pruned this will decrease.
              // TODO(b/168568827): Once we support a nested addSuppressed this will increase.
              int expectedSynthetics = 0;
              if (!hasTwrCloseResourceSupport(true)) {
                expectedSynthetics += 2;
              }
              if (hasTwrCloseResourceApiOutlines()) {
                expectedSynthetics += 1;
              }
              assertEquals(INPUT_CLASSES + expectedSynthetics, inspector.allClasses().size());
            });
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramInputs())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addKeepMainRule(MAIN)
        .addKeepClassAndMembersRules(FOO, BAR)
        .setMinApi(parameters)
        .addDontObfuscate()
        .run(parameters.getRuntime(), MAIN, getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              List<FoundClassSubject> foundClassSubjects = inspector.allClasses();
              Set<String> foundClasses =
                  foundClassSubjects.stream()
                      .map(FoundClassSubject::getFinalName)
                      .collect(Collectors.toSet());
              // R8 will optimize the generated methods for the two cases below where the thrown
              // exception is known or not, thus the synthetic methods will be 2.
              Set<String> nonSyntheticClassOutput = ImmutableSet.of(FOO, BAR, MAIN);
              if (!hasTwrCloseResourceSupport(parameters.isDexRuntime())) {
                Set<String> classOutputWithSynthetics = new HashSet<>(nonSyntheticClassOutput);
                classOutputWithSynthetics.add(
                    SyntheticItemsTestUtils.syntheticApiOutlineClass(
                            Reference.classFromTypeName(BAR), 0)
                        .getTypeName());
                assertEquals(classOutputWithSynthetics, foundClasses);
              } else {
                assertEquals(nonSyntheticClassOutput, foundClasses);
              }
            });
  }
}