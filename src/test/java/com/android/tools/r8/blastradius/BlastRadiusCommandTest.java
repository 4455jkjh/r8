// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.google.protobuf.AbstractMessage;
import com.sun.tools.javac.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BlastRadiusCommandTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Box<byte[]> dataConsumer = new Box<>();
    InMemoryStringConsumer reportConsumer = new InMemoryStringConsumer();
    R8Command command =
        R8Command.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addProguardConfiguration(List.of("-keep class * { *; }"), Origin.unknown())
            .setConfigurationAnalysisDataConsumer(
                (ByteArrayConsumer.ArrayConsumer) dataConsumer::set)
            .setConfigurationAnalysisHtmlReportConsumer(reportConsumer)
            .setMinApiLevel(AndroidApiLevel.getDefault().getLevel())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build();
    R8.run(command);

    // Build the proto message from the data.
    assertTrue(dataConsumer.isSet());
    BlastRadiusContainer container = BlastRadiusContainer.parseFrom(dataConsumer.get());
    assertNotNull(container);

    // Check that the report consumer received the HTML.
    assertThat(reportConsumer.value, startsWith("<!DOCTYPE html>"));
    assertThat(reportConsumer.value, containsString(encodeMessageToString(container)));
  }

  public static String encodeMessageToString(AbstractMessage message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    message.writeTo(baos);
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }

  private static class InMemoryStringConsumer implements StringConsumer {

    public String value = null;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      value = string;
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
