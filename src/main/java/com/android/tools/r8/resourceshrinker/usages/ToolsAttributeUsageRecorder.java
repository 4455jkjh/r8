/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.resourceshrinker.usages;

import static com.android.SdkConstants.VALUE_STRICT;

import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class ToolsAttributeUsageRecorder {

  private static final String TOOLS_NAMESPACE = "http://schemas.android.com/tools";

  private ToolsAttributeUsageRecorder() {}

  public static void processRawXml(Reader reader, ResourceShrinkerModel model) throws IOException {
    processRawXml(reader, model, "");
  }

  public static void processRawXml(Reader reader, ResourceShrinkerModel model, String location)
      throws IOException {
    processResourceToolsAttributes(reader, model, location)
        .forEach(
            (key, value) -> {
              switch (key) {
                case "keep":
                  model.getResourceStore().recordKeepToolAttribute(value);
                  break;
                case "discard":
                  model.getResourceStore().recordDiscardToolAttribute(value);
                  break;
                case "shrinkMode":
                  if (VALUE_STRICT.equals(value)) {
                    model.getResourceStore().setSafeMode(false);
                  }
                  break;
                default:
                  break;
              }
            });
  }

  private static ImmutableMap<String, String> processResourceToolsAttributes(
      Reader utfReader, ResourceShrinkerModel model, String location) throws IOException {
    if (utfReader == null) {
      return ImmutableMap.of();
    }
    String content;
    try (Reader reader = utfReader) {
      content = CharStreams.toString(reader);
    }
    if (isBlank(content)) {
      return ImmutableMap.of();
    }
    Map<String, String> toolsAttributes = new LinkedHashMap<>();
    try (StringReader reader = new StringReader(content)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      XMLStreamReader xmlStreamReader = factory.createXMLStreamReader(reader);
      boolean rootElementFound = false;
      while (xmlStreamReader.hasNext()) {
        xmlStreamReader.next();
        if (xmlStreamReader.isStartElement() && !rootElementFound) {
          rootElementFound = true;
          if ("resources".equals(xmlStreamReader.getLocalName())) {
            for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
              if (TOOLS_NAMESPACE.equals(xmlStreamReader.getAttributeNamespace(i))) {
                toolsAttributes.put(
                    xmlStreamReader.getAttributeLocalName(i), xmlStreamReader.getAttributeValue(i));
              }
            }
          }
        }
      }
    } catch (XMLStreamException | RuntimeException e) {
      model.getDebugReporter().info(() -> formatErrorMessage(e, location));
    }
    return ImmutableMap.copyOf(toolsAttributes);
  }

  private static String formatErrorMessage(Exception e, String location) {
    int line = -1;
    int col = -1;
    String rawMsg = e.getMessage() != null ? e.getMessage() : e.toString();
    String msg;
    if (e instanceof XMLStreamException) {
      XMLStreamException xmlException = (XMLStreamException) e;
      Location loc = xmlException.getLocation();
      if (loc != null) {
        if (loc.getLineNumber() > 0) {
          line = loc.getLineNumber();
        }
        if (loc.getColumnNumber() > 0) {
          col = loc.getColumnNumber();
        }
      }
      int messageIdx = rawMsg.indexOf("Message: ");
      msg =
          messageIdx != -1
              ? trimWhitespace(rawMsg.substring(messageIdx + "Message: ".length()))
              : trimWhitespace(rawMsg);
    } else {
      msg = trimWhitespace(rawMsg);
    }
    String detail = trimTrailingDots(msg);

    StringBuilder locPart = new StringBuilder();
    if (!location.isEmpty()) {
      locPart.append(" from ").append(location);
    }
    if (line > 0 && col > 0) {
      locPart.append(" at line ").append(line).append(", column ").append(col);
    } else if (line > 0) {
      locPart.append(" at line ").append(line);
    }

    String lowerDetail = Ascii.toLowerCase(detail);
    boolean isDtdOrEntityError =
        lowerDetail.contains("referenced, but not declared")
            || lowerDetail.contains("doctype")
            || lowerDetail.contains("dtd");
    String dtdExplanation =
        isDtdOrEntityError ? " External DTD / entity references are unsupported and ignored." : "";

    return "Failed to parse XML keep rules" + locPart + ": " + detail + "." + dtdExplanation;
  }

  private static boolean isBlank(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!isWhitespace(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static String trimWhitespace(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isWhitespace(value.charAt(start))) {
      start++;
    }
    while (end > start && isWhitespace(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static String trimTrailingDots(String value) {
    int end = value.length();
    while (end > 0 && value.charAt(end - 1) == '.') {
      end--;
    }
    return value.substring(0, end);
  }
}
