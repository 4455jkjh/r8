// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.graph.DexItemFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection logs written by {@code ReflectiveOperationJsonLogger}, read with {@link
 * ReflectiveOperationJsonParser} and merged in the order given.
 *
 * <p>When after-call hooks are enabled, the logger writes an event id on each request and a
 * returned line for each distinct result of a request that returned normally, so a request with an
 * id and without a returned line threw. A log without event ids and returned lines has no outcome
 * information, and its requests are treated as successful. Lookups of fields and methods that
 * failed are logged without a member type, so they threw even when the log has no outcome
 * information.
 */
final class ParsedLogs {

  /** The outcome of a reflective request. */
  enum Outcome {
    RETURNED,
    THREW,
    UNKNOWN
  }

  /** A log file and what was read from it. */
  private static final class LogFile {

    private final Path path;
    private int eventCount = 0;
    private int skippedLineCount = 0;
    private boolean hasOutcomes = false;

    private LogFile(Path path) {
      this.path = path;
    }
  }

  private final List<ReflectiveEvent> events = new ArrayList<>();
  private final List<LogFile> files = new ArrayList<>();
  private final Map<ReflectiveEvent, LogFile> eventFiles = new IdentityHashMap<>();

  private ParsedLogs() {}

  static ParsedLogs read(List<Path> paths) throws IOException {
    ParsedLogs logs = new ParsedLogs();
    ReflectiveOperationJsonParser parser = new ReflectiveOperationJsonParser(new DexItemFactory());
    for (Path path : paths) {
      LogFile file = new LogFile(path);
      List<ReflectiveEvent> fileEvents = parser.parse(path, line -> file.skippedLineCount++);
      for (ReflectiveEvent event : fileEvents) {
        file.hasOutcomes |= event.getId() >= 0 || event.hasReturned();
        logs.eventFiles.put(event, file);
      }
      file.eventCount = fileEvents.size();
      logs.events.addAll(fileEvents);
      logs.files.add(file);
    }
    return logs;
  }

  List<ReflectiveEvent> getEvents() {
    return events;
  }

  boolean isEmpty() {
    return events.isEmpty();
  }

  /** The log file that the event was read from. */
  Path getFile(ReflectiveEvent event) {
    return eventFiles.get(event).path;
  }

  Outcome getOutcome(ReflectiveEvent event) {
    if (event.isClassGetMember() && !event.asClassGetMember().hasMemberType()) {
      return Outcome.THREW;
    }
    if (eventFiles.get(event).hasOutcomes && event.getId() >= 0) {
      return event.neverReturned() ? Outcome.THREW : Outcome.RETURNED;
    }
    return Outcome.UNKNOWN;
  }

  boolean threw(ReflectiveEvent event) {
    return getOutcome(event) == Outcome.THREW;
  }

  /** Counts the events that threw, for summaries. */
  int countThrown(Collection<ReflectiveEvent> events) {
    int count = 0;
    for (ReflectiveEvent event : events) {
      if (threw(event)) {
        count++;
      }
    }
    return count;
  }

  /** Problems found while reading the logs. */
  List<String> getWarnings() {
    List<String> warnings = new ArrayList<>();
    for (LogFile file : files) {
      if (file.skippedLineCount > 0) {
        warnings.add(
            file.path
                + ": skipped "
                + CommandUtils.plural(file.skippedLineCount, "line", "lines")
                + " that could not be read (malformed, or events of a type this version does not"
                + " know)");
      }
    }
    return warnings;
  }

  /** Describes how outcomes are known, for the headers of reports. */
  String describeOutcomes() {
    int withOutcomes = 0;
    int withEvents = 0;
    for (LogFile file : files) {
      if (file.eventCount > 0) {
        withEvents++;
        if (file.hasOutcomes) {
          withOutcomes++;
        }
      }
    }
    if (withOutcomes == withEvents) {
      return "known (after-call results present)";
    }
    if (withOutcomes == 0) {
      return "unknown (no after-call results; all requests treated as successful)";
    }
    return "known for "
        + withOutcomes
        + " of "
        + withEvents
        + " logs (requests in the other logs are treated as successful)";
  }
}
