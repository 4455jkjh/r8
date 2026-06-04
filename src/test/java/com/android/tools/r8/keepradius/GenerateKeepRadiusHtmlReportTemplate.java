// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import static com.android.tools.r8.cfmethodgeneration.CodeGenerationBase.javaFormatRawOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("NewClassNamingConvention")
@RunWith(Parameterized.class)
public class GenerateKeepRadiusHtmlReportTemplate extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws IOException {
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8),
        generateKeepRadiusHtmlReportTemplate());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8),
        generateKeepRadiusHtmlReportTemplate());
  }

  private static String generateKeepRadiusHtmlReportTemplate() throws IOException {
    String java =
        StringUtils.lines(
            MethodGenerationBase.getHeaderString(
                2026, GenerateKeepRadiusHtmlReportTemplate.class.getSimpleName()),
            "package com.android.tools.r8.keepradius;",
            "import java.nio.charset.StandardCharsets;",
            "import java.util.Base64;",
            "public class KeepRadiusHtmlReportTemplate {",
            "  public static String getHtmlTemplate() {",
            "    return decodeBase64(" + encodeToString(getHtml()) + ");",
            "  }",
            "  public static String getSummaryHtmlTemplate() {",
            "    return decodeBase64(" + encodeToString(getSummaryHtml()) + ");",
            "  }",
            "  private static String decodeBase64(String string) {",
            "    return new String(Base64.getDecoder().decode(string), StandardCharsets.UTF_8);",
            "  }",
            "}");
    return javaFormatRawOutput(java);
  }

  private static String getHtml() throws IOException {
    String html = FileUtils.readTextFile(getReportHtmlFile());

    // Embed stylesheet.
    html =
        replace(
            html,
            "<link rel=\"stylesheet\" href=\"style.css\" />",
            String.join("", "<style>", FileUtils.readTextFile(getReportCssFile()), "</style>"));

    // Embed proto schema.
    html =
        replace(
            html,
            "<script id=\"keepradius-proto\" type=\"text/plain\"></script>",
            String.join(
                "",
                "<script id=\"keepradius-proto\" type=\"text/plain\">",
                FileUtils.readTextFile(getProtoFile()),
                "</script>"));

    // Embed JavaScript.
    html =
        replace(
            html,
            "<script src=\"main.js\"></script>",
            String.join(
                "", "<script>", FileUtils.readTextFile(getReportMainJsFile()), "</script>"));
    html =
        replace(
            html,
            "<script src=\"utils.js\"></script>",
            String.join(
                "", "<script>", FileUtils.readTextFile(getReportUtilsJsFile()), "</script>"));

    return html;
  }

  private static String getSummaryHtml() throws IOException {
    String html = FileUtils.readTextFile(getSummaryHtmlFile());

    // Embed stylesheet.
    html =
        replace(
            html,
            "<link rel=\"stylesheet\" href=\"style.css\" />",
            String.join("", "<style>", FileUtils.readTextFile(getSummaryCssFile()), "</style>"));

    // Embed proto schema.
    html =
        replace(
            html,
            "<script id=\"keepradius-proto\" type=\"text/plain\"></script>",
            String.join(
                "",
                "<script id=\"keepradius-proto\" type=\"text/plain\">",
                FileUtils.readTextFile(getSummaryProtoFile()),
                "</script>"));

    // Embed JavaScript.
    html =
        replace(
            html,
            "<script src=\"main.js\"></script>",
            String.join("", "<script>", FileUtils.readTextFile(getSummaryJsFile()), "</script>"));

    return html;
  }

  private static String replace(String html, String subject, String replacement) {
    String newHtml = html.replace(subject, replacement);
    assertNotEquals(html, newHtml);
    return newHtml;
  }

  private static String encodeToString(String string) {
    String result = Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    // The maximum length of a String literal is 65,535 bytes, imposed by the Java Virtual Machine
    // (JVM) specification.
    StringBuilder builder = new StringBuilder("String.join(\"\"");
    while (!result.isEmpty()) {
      if (result.length() <= 1000) {
        builder.append(", \"").append(result).append("\"");
        break;
      }
      String part = result.substring(0, 1000);
      builder.append(", \"").append(part).append("\"");
      result = result.substring(1000);
    }
    return builder.append(")").toString();
  }

  private static Path getGeneratedFile() {
    return Paths.get(
        ToolHelper.KEEP_RADIUS_SOURCE_DIR,
        "com/android/tools/r8/keepradius/KeepRadiusHtmlReportTemplate.java");
  }

  private static Path getReportHtmlFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/report/index.html");
  }

  private static Path getReportMainJsFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/report/main.js");
  }

  private static Path getReportUtilsJsFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/report/utils.js");
  }

  private static Path getReportCssFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/report/style.css");
  }

  private static Path getProtoFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_PROTO_DIR, "keepradius.proto");
  }

  private static Path getSummaryHtmlFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/summary/index.html");
  }

  private static Path getSummaryJsFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/summary/main.js");
  }

  private static Path getSummaryCssFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_WEB_DIR, "templates/summary/style.css");
  }

  private static Path getSummaryProtoFile() {
    return Paths.get(ToolHelper.KEEP_RADIUS_PROTO_DIR, "keepradiussummary.proto");
  }

  public static void main(String[] args) throws IOException {
    FileUtils.writeTextFile(getGeneratedFile(), generateKeepRadiusHtmlReportTemplate());
  }
}
