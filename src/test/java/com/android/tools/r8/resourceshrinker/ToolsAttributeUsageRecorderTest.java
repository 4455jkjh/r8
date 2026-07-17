// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.resourceshrinker.usages.ToolsAttributeUsageRecorderKt;
import com.android.tools.r8.utils.ResourceShrinkerUtils;
import java.io.StringReader;
import org.junit.Test;

public class ToolsAttributeUsageRecorderTest {

  @Test
  public void testProcessRawXmlKeepAttributes() {
    ResourceShrinkerModel model = new ResourceShrinkerModel(NoDebugReporter.INSTANCE, true);
    assertTrue(model.isSafeMode());

    String xml =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources xmlns:tools=\"http://schemas.android.com/tools\"\n"
            + "    tools:keep=\"@layout/foo,@layout/bar\"\n"
            + "    tools:discard=\"@layout/unused\"\n"
            + "    tools:shrinkMode=\"strict\" />\n";

    ToolsAttributeUsageRecorderKt.processRawXml(new StringReader(xml), model);

    assertFalse(model.isSafeMode());
  }

  @Test
  public void testProcessRawXmlXXEIgnoredSafely() {
    StringBuilder loggedOutput = new StringBuilder();
    TestDiagnosticMessages diagnostics = new TestDiagnosticMessagesImpl();
    ShrinkerDebugReporter reporter =
        ResourceShrinkerUtils.shrinkerDebugReporterFromStringConsumer(
            (s, ignored) -> loggedOutput.append(s), diagnostics);

    ResourceShrinkerModel model = new ResourceShrinkerModel(reporter, true);

    String xmlWithXXE =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!DOCTYPE resources [\n"
            + "  <!ENTITY xxe SYSTEM \"http://127.0.0.1:9999/xxe_test\">\n"
            + "]>\n"
            + "<resources xmlns:tools=\"http://schemas.android.com/tools\"\n"
            + "    tools:keep=\"&xxe;\" />\n";

    ToolsAttributeUsageRecorderKt.processRawXml(
        new StringReader(xmlWithXXE), model, "path/to/my_rules.xml");

    assertTrue(loggedOutput.toString().contains("path/to/my_rules.xml"));
    assertTrue(
        loggedOutput
            .toString()
            .contains("External DTD / entity references are unsupported and ignored"));
    diagnostics
        .assertOnlyInfos()
        .assertAllInfosMatch(
            diagnosticMessage(
                containsString("External DTD / entity references are unsupported and ignored")));
  }
}
