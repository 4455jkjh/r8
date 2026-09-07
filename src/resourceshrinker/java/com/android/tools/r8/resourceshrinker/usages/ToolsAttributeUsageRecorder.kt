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

package com.android.tools.r8.resourceshrinker.usages

import com.android.SdkConstants.VALUE_STRICT
import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel
import java.io.Reader
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException

@JvmOverloads
public fun processRawXml(reader: Reader, model: ResourceShrinkerModel, location: String = "") {
  processResourceToolsAttributes(reader, model, location).forEach { key, value ->
    when (key) {
      "keep" -> model.resourceStore.recordKeepToolAttribute(value)
      "discard" -> model.resourceStore.recordDiscardToolAttribute(value)
      "shrinkMode" ->
        if (value == VALUE_STRICT) {
          model.resourceStore.safeMode = false
        }
    }
  }
}

private fun processResourceToolsAttributes(
  utfReader: Reader?,
  model: ResourceShrinkerModel,
  location: String,
): Map<String, String> {
  val content = utfReader?.use { it.readText() } ?: return emptyMap()
  if (content.isBlank()) {
    return emptyMap()
  }
  val toolsAttributes = mutableMapOf<String, String>()
  runCatching {
      StringReader(content).use { reader ->
        val factory =
          XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
          }
        val xmlStreamReader = factory.createXMLStreamReader(reader)

        var rootElementFound = false
        while (xmlStreamReader.hasNext()) {
          xmlStreamReader.next()
          if (xmlStreamReader.isStartElement) {
            if (!rootElementFound) {
              rootElementFound = true
              if (xmlStreamReader.localName == "resources") {
                for (i in 0 until xmlStreamReader.attributeCount) {
                  val namespace = "http://schemas.android.com/tools"
                  if (xmlStreamReader.getAttributeNamespace(i) == namespace) {
                    toolsAttributes[xmlStreamReader.getAttributeLocalName(i)] =
                      xmlStreamReader.getAttributeValue(i)
                  }
                }
              }
            }
          }
        }
      }
    }
    .onFailure { e -> model.debugReporter.info { formatErrorMessage(e, location) } }
  return toolsAttributes.toMap()
}

private fun formatErrorMessage(e: Throwable, location: String): String {
  val (line, col, detail) =
    if (e is XMLStreamException) {
      val loc = e.location
      val lineNumber = loc?.lineNumber?.takeIf { it > 0 }
      val columnNumber = loc?.columnNumber?.takeIf { it > 0 }
      val rawMsg = e.message ?: e.toString()
      val msg =
        if (rawMsg.contains("Message: ")) {
          rawMsg.substringAfter("Message: ").trim()
        } else {
          rawMsg.trim()
        }
      Triple(lineNumber, columnNumber, msg.trimEnd('.'))
    } else {
      Triple(null, null, (e.message ?: e.toString()).trim().trimEnd('.'))
    }

  val locPart = buildString {
    if (location.isNotEmpty()) {
      append(" from $location")
    }
    if (line != null && col != null) {
      append(" at line $line, column $col")
    } else if (line != null) {
      append(" at line $line")
    }
  }

  val isDtdOrEntityError =
    detail.contains("referenced, but not declared", ignoreCase = true) ||
      detail.contains("doctype", ignoreCase = true) ||
      detail.contains("dtd", ignoreCase = true)
  val dtdExplanation =
    if (isDtdOrEntityError) {
      " External DTD / entity references are unsupported and ignored."
    } else {
      ""
    }

  return "Failed to parse XML keep rules$locPart: $detail.$dtdExplanation"
}
