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
import javax.xml.stream.XMLInputFactory

public fun processRawXml(reader: Reader, model: ResourceShrinkerModel) {
  processResourceToolsAttributes(reader).forEach { key, value ->
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

private fun processResourceToolsAttributes(utfReader: Reader?): Map<String, String> {
  val toolsAttributes = mutableMapOf<String, String>()
  utfReader.use { reader: Reader? ->
    val factory = XMLInputFactory.newInstance()
    val xmlStreamReader = factory.createXMLStreamReader(reader)

    var rootElementProcessed = false
    while (!rootElementProcessed && xmlStreamReader.hasNext()) {
      xmlStreamReader.next()
      if (xmlStreamReader.isStartElement) {
        if (xmlStreamReader.localName == "resources") {
          for (i in 0 until xmlStreamReader.attributeCount) {
            val namespace = "http://schemas.android.com/tools"
            if (xmlStreamReader.getAttributeNamespace(i) == namespace) {
              toolsAttributes.put(
                xmlStreamReader.getAttributeLocalName(i),
                xmlStreamReader.getAttributeValue(i),
              )
            }
          }
        }
        rootElementProcessed = true
      }
    }
  }
  return toolsAttributes.toMap()
}
