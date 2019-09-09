// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CoreLibDesugarTestBase extends TestBase {

  @Deprecated
  protected boolean requiresCoreLibDesugaring(TestParameters parameters) {
    // TODO(b/134732760): Use the two other APIS instead.
    return requiresEmulatedInterfaceCoreLibDesugaring(parameters)
        && requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected boolean requiresEmulatedInterfaceCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }

  protected boolean requiresRetargetCoreLibMemberDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < AndroidApiLevel.O.getLevel();
  }

  protected boolean requiresAnyCoreLibDesugaring(TestParameters parameters) {
    return requiresRetargetCoreLibMemberDesugaring(parameters);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel) throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, "", false);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, true);
  }

  protected Path buildDesugaredLibrary(AndroidApiLevel apiLevel, String keepRules, boolean shrink)
      throws RuntimeException {
    return buildDesugaredLibrary(apiLevel, keepRules, shrink, ImmutableList.of());
  }

  protected Path buildDesugaredLibrary(
      AndroidApiLevel apiLevel, String keepRules, boolean shrink, List<Path> additionalProgramFiles)
      throws RuntimeException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    try {
      // If we compile extended library here, it means we use TestNG.
      // TestNG requires annotations, hence we disable AnnotationRemoval.
      // This implies that extra warning are generated if this is set.
      boolean disableL8AnnotationRemovalForTesting = !additionalProgramFiles.isEmpty();
      TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
      Path desugaredLib = temp.newFolder().toPath().resolve("desugar_jdk_libs_dex.zip");
      L8Command.Builder l8Builder =
          L8Command.builder(diagnosticsHandler)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addProgramFiles(ToolHelper.getDesugarJDKLibs())
              .addProgramFiles(additionalProgramFiles)
              .addDesugaredLibraryConfiguration("default")
              .setMinApiLevel(apiLevel.getLevel())
              .setOutput(desugaredLib, OutputMode.DexIndexed);
      if (shrink) {
        l8Builder.addProguardConfiguration(
            Arrays.asList(keepRules.split(System.lineSeparator())), Origin.unknown());
      }
      ToolHelper.runL8(
          l8Builder.build(),
          options -> {
            if (disableL8AnnotationRemovalForTesting) {
              options.testing.disableL8AnnotationRemoval = true;
            }
          });
      if (!disableL8AnnotationRemovalForTesting) {
        assertTrue(
            diagnosticsHandler.getInfos().stream()
                .noneMatch(
                    string ->
                        string
                            .getDiagnosticMessage()
                            .startsWith(
                                "Invalid parameter counts in MethodParameter attributes.")));
      }
      return desugaredLib;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(lines[i], lines[i + 1]);
    }
  }

  protected KeepRuleConsumer createKeepRuleConsumer(TestParameters parameters) {
    if (requiresAnyCoreLibDesugaring(parameters)) {
      return new PresentKeepRuleConsumer();
    }
    return new AbsentKeepRuleConsumer();
  }

  public interface KeepRuleConsumer extends StringConsumer {

    String get();
  }

  public static class AbsentKeepRuleConsumer implements KeepRuleConsumer {

    public String get() {
      return null;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }
  }

  public static class PresentKeepRuleConsumer implements KeepRuleConsumer {

    StringBuilder stringBuilder = new StringBuilder();
    String result = null;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      stringBuilder.append(string);
    }

    public void finished(DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      result = stringBuilder.toString();
      stringBuilder = null;
    }

    public String get() {
      // TODO(clement): remove that branch once StringConsumer has finished again.
      if (stringBuilder != null) {
        finished(null);
      }

      assert stringBuilder == null;
      assert result != null;
      return result;
    }
  }
}
