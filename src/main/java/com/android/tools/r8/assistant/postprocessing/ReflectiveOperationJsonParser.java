// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing;

import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ReflectiveOperationJsonParser {

  private final DexItemFactory factory;

  public ReflectiveOperationJsonParser(DexItemFactory factory) {
    this.factory = factory;
  }

  public List<ReflectiveEvent> parse(Path file) throws IOException {
    return parse(file, null);
  }

  // Parses the file, passing the lines that cannot be parsed, such as malformed lines and events of
  // unknown types, to the consumer. Throws for such lines if the consumer is null.
  public List<ReflectiveEvent> parse(Path file, Consumer<String> skippedLineConsumer)
      throws IOException {
    List<ReflectiveEvent> result = new ArrayList<>();
    Int2ReferenceMap<ReflectiveEvent> eventsById = new Int2ReferenceOpenHashMap<>();
    List<String> lines = Files.readAllLines(file);
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      try {
        parseLine(line, result, eventsById);
      } catch (RuntimeException e) {
        if (skippedLineConsumer == null) {
          throw e;
        }
        skippedLineConsumer.accept(line);
      }
    }
    return result;
  }

  private void parseLine(
      String line, List<ReflectiveEvent> result, Int2ReferenceMap<ReflectiveEvent> eventsById) {
    JsonObject event = JsonParser.parseString(line).getAsJsonObject();
    if (event.isEmpty()) {
      return;
    }
    JsonElement returnedElement = event.get("returned");
    if (returnedElement != null) {
      ReflectiveEvent returnedEvent = eventsById.get(returnedElement.getAsInt());
      if (returnedEvent != null) {
        returnedEvent.addResult(toResult(event.get("result")));
      }
      return;
    }
    ReflectiveEventType eventType = ReflectiveEventType.valueOf(event.get("event").getAsString());
    JsonElement stackElement = event.get("stack");
    String[] stack = stackElement != null ? toStringArray(stackElement) : null;
    JsonElement argsElement = event.get("args");
    String[] args = argsElement != null ? toStringArray(argsElement) : null;
    ReflectiveEvent reflectiveEvent = ReflectiveEvent.instantiate(eventType, stack, args, factory);
    JsonElement idElement = event.get("id");
    if (idElement != null) {
      reflectiveEvent.setId(idElement.getAsInt());
      // Overwrite any earlier event with the same id (e.g. in concatenated multi-process logs)
      // so subsequent returned lines attach to the latest request with that id.
      eventsById.put(reflectiveEvent.getId(), reflectiveEvent);
    }
    result.add(reflectiveEvent);
  }

  private Object toResult(JsonElement resultElement) {
    if (resultElement == null || resultElement.isJsonNull()) {
      return null;
    }
    if (resultElement.isJsonArray()) {
      return Arrays.asList(toStringArray(resultElement));
    }
    JsonPrimitive primitive = resultElement.getAsJsonPrimitive();
    return primitive.isBoolean() ? primitive.getAsBoolean() : primitive.getAsString();
  }

  private String[] toStringArray(JsonElement argsElement) {
    JsonArray jsonArray = argsElement.getAsJsonArray();
    String[] strings = new String[jsonArray.size()];
    for (int i = 0; i < strings.length; i++) {
      JsonElement element = jsonArray.get(i);
      strings[i] = element.isJsonNull() ? null : element.getAsString();
    }
    return strings;
  }
}
