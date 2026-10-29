// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import dom from "./dom.js";
import state from "./state.js";

const PAGES = [
  { target: "r8-full", label: "R8", href: "r8.html" },
  { target: "d8", label: "D8", href: "d8.html" },
  { target: "build", label: "Build", href: "build.html" },
  { target: "retrace", label: "Retrace", href: "retrace.html" },
];

const METRICS = [
  {
    id: "code_size",
    label: () => "DEX size",
    kind: "bytes",
    flatPct: 0.05,
    note: (cut) =>
      cut
        ? `${cut.branch} cut vs latest main commit`
        : "First vs latest main commit in the last 14 days",
  },
  {
    id: "ins_code_size",
    label: () => "Instruction size",
    kind: "bytes",
    flatPct: 0.05,
    note: (cut) =>
      cut
        ? `${cut.branch} cut vs latest main commit`
        : "First vs latest main commit in the last 14 days",
  },
  {
    id: "runtime",
    label: () => `Runtime (${dom.getSelectedRuntimeLabel()})`,
    kind: "seconds",
    flatPct: 0.5,
    note: (cut) =>
      cut
        ? `Median of up to 3 runs at ${cut.branch} cut vs latest 3 main runs`
        : "Median of first 3 vs last 3 runs in the last 14 days",
  },
];

const shortDateFormatter = new Intl.DateTimeFormat(undefined, {
  month: "short",
  day: "numeric",
});

function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0
    ? (sorted[mid - 1] + sorted[mid]) / 2
    : sorted[mid];
}

function geomeanPct(entries) {
  if (entries.length === 0) {
    return null;
  }
  const sumLog = entries.reduce((acc, e) => acc + Math.log(e.ratio), 0);
  return (Math.exp(sumLog / entries.length) - 1) * 100;
}

function formatSignedBytes(bytes) {
  if (bytes === 0) {
    return "0 B";
  }
  return (bytes > 0 ? "+" : "-") + formatAbsoluteBytes(Math.abs(bytes));
}

function formatAbsoluteBytes(bytes) {
  if (bytes < 1024) {
    return `${Math.round(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatSignedSeconds(seconds) {
  const abs = Math.abs(seconds);
  if (abs < 0.00005) {
    return "0 ms";
  }
  const sign = seconds > 0 ? "+" : "-";
  if (abs < 0.00995) {
    return `${sign}${(abs * 1000).toFixed(1)} ms`;
  }
  if (Math.round(abs * 1000) < 1000) {
    return `${sign}${Math.round(abs * 1000)} ms`;
  }
  if (Number(abs.toFixed(1)) < 60) {
    return `${sign}${abs.toFixed(1)} s`;
  }
  const rounded = Math.round(abs);
  const mins = Math.floor(rounded / 60);
  const secs = rounded % 60;
  return `${sign}${mins}m ${secs}s`;
}

function formatAbsoluteSeconds(seconds) {
  if (seconds < 0.00995) {
    return `${(seconds * 1000).toFixed(1)} ms`;
  }
  if (Math.round(seconds * 1000) < 1000) {
    return `${Math.round(seconds * 1000)} ms`;
  }
  if (Number(seconds.toFixed(2)) < 60) {
    return `${seconds.toFixed(2)} s`;
  }
  const rounded = Math.round(seconds);
  const mins = Math.floor(rounded / 60);
  const secs = rounded % 60;
  return `${mins}m ${secs}s`;
}

function formatPctBadge(pct) {
  if (pct === null || !Number.isFinite(pct)) {
    return "—";
  }
  if (Math.abs(pct) < 0.005) {
    return "0.00%";
  }
  const glyph = pct < 0 ? "▼" : "▲";
  return `${glyph} ${Math.abs(pct).toFixed(2)}%`;
}

function formatSignedPct(pct) {
  if (Math.abs(pct) < 0.005) {
    return "0.00%";
  }
  const sign = pct > 0 ? "+" : "";
  return `${sign}${pct.toFixed(2)}%`;
}

function toneClass(pct, threshold = 0.01) {
  if (pct === null || !Number.isFinite(pct) || Math.abs(pct) <= threshold) {
    return "trend-flat";
  }
  return pct < 0 ? "trend-good" : "trend-bad";
}

function readMetricValue(benchmark, commit, metric) {
  if (!(benchmark in commit.benchmarks)) {
    return NaN;
  }
  if (metric.kind === "seconds") {
    return window.getAllResults(benchmark, commit, metric.id, (results) =>
      dom.transformRuntimeData(results).ns_to_s(),
    );
  }
  return window.getSingleResult(benchmark, commit, metric.id);
}

function collectRuns(benchmark, commits, metric) {
  const runs = [];
  for (const commit of commits) {
    const value = readMetricValue(benchmark, commit, metric);
    if (Number.isFinite(value) && value > 0) {
      runs.push({ commit: commit, value: value });
    }
  }
  return runs;
}

function pickBaselineSlice(cutRuns, cut, metric) {
  let anchorIdx = cutRuns.findIndex((r) => r.commit.hash === cut.hash);
  let usedPostCutFallback = false;
  if (anchorIdx === -1) {
    for (let i = cutRuns.length - 1; i >= 0; i--) {
      if (cutRuns[i].commit.submitted <= cut.submitted) {
        anchorIdx = i;
        break;
      }
    }
    if (anchorIdx === -1) {
      anchorIdx = 0;
      usedPostCutFallback = true;
    }
  }
  if (metric.kind !== "seconds") {
    return { anchor: cutRuns[anchorIdx], slice: [cutRuns[anchorIdx]] };
  }
  const slice = usedPostCutFallback
    ? cutRuns.slice(0, Math.min(3, cutRuns.length))
    : cutRuns.slice(Math.max(0, anchorIdx - 2), anchorIdx + 1);
  return { anchor: cutRuns[anchorIdx], slice: slice };
}

function summarizeEntries(entries, metric, cut) {
  if (entries.length === 0) {
    return null;
  }

  let improved = 0;
  let regressed = 0;
  let unchanged = 0;
  let totalDelta = 0;
  for (const entry of entries) {
    totalDelta += entry.delta;
    if (entry.pct < -metric.flatPct) {
      improved++;
    } else if (entry.pct > metric.flatPct) {
      regressed++;
    } else {
      unchanged++;
    }
  }

  const fullEntries = entries.filter((e) => !e.benchmark.endsWith("Partial"));
  const partialEntries = entries.filter((e) => e.benchmark.endsWith("Partial"));

  return {
    metric: metric,
    note: metric.note(cut),
    entries: entries,
    geomean: geomeanPct(entries),
    fullGeomean:
      fullEntries.length > 0 && partialEntries.length > 0
        ? geomeanPct(fullEntries)
        : null,
    partialGeomean:
      fullEntries.length > 0 && partialEntries.length > 0
        ? geomeanPct(partialEntries)
        : null,
    fullCount: fullEntries.length,
    partialCount: partialEntries.length,
    totalDelta: totalDelta,
    improved: improved,
    regressed: regressed,
    unchanged: unchanged,
  };
}

function computeReleaseCutSummary(cut, cutCommits, recentCommits, metric) {
  const entries = [];
  for (const benchmark of state.benchmarks.values()) {
    const cutRuns = collectRuns(benchmark, cutCommits, metric);
    const recentRuns = collectRuns(benchmark, recentCommits, metric);
    if (cutRuns.length === 0 || recentRuns.length === 0) {
      continue;
    }
    const latestRun = recentRuns[recentRuns.length - 1];
    const sameDumpRecentRuns = recentRuns.filter((r) =>
      state.hasSameDump(benchmark, r.commit, latestRun.commit),
    );
    const { anchor, slice: rawStartSlice } = pickBaselineSlice(
      cutRuns,
      cut,
      metric,
    );
    if (!state.hasSameDump(benchmark, anchor.commit, latestRun.commit)) {
      continue;
    }
    const startSlice = rawStartSlice.filter((r) =>
      state.hasSameDump(benchmark, r.commit, anchor.commit),
    );
    const start = median(startSlice.map((r) => r.value));
    let end;
    let endCommit;
    if (metric.kind === "seconds") {
      const baselineEnd = startSlice[startSlice.length - 1];
      const baselineEndIdx = sameDumpRecentRuns.findIndex(
        (r) => r.commit.hash === baselineEnd.commit.hash,
      );
      const afterAnchor =
        baselineEndIdx !== -1
          ? sameDumpRecentRuns.slice(baselineEndIdx + 1)
          : sameDumpRecentRuns.filter(
              (r) => r.commit.submitted > baselineEnd.commit.submitted,
            );
      const endSlice =
        afterAnchor.length > 0
          ? afterAnchor.slice(-3)
          : sameDumpRecentRuns[sameDumpRecentRuns.length - 1].commit.hash ===
              baselineEnd.commit.hash
            ? startSlice
            : [];
      if (endSlice.length === 0) {
        continue;
      }
      end = median(endSlice.map((r) => r.value));
      endCommit = endSlice[endSlice.length - 1].commit;
    } else {
      if (
        latestRun.commit.submitted < anchor.commit.submitted &&
        latestRun.commit.hash !== anchor.commit.hash
      ) {
        continue;
      }
      end = latestRun.value;
      endCommit = latestRun.commit;
    }
    const delta = end - start;
    const ratio = end / start;
    const pct = (ratio - 1) * 100;
    entries.push({
      benchmark: benchmark,
      start: start,
      end: end,
      delta: delta,
      ratio: ratio,
      pct: pct,
      startHash: anchor.commit.hash,
      endHash: endCommit.hash,
    });
  }
  return summarizeEntries(entries, metric, cut);
}

function computeTrendSummary(commits, metric) {
  const entries = [];
  for (const benchmark of state.benchmarks.values()) {
    const allRuns = collectRuns(benchmark, commits, metric);
    if (allRuns.length < 2) {
      continue;
    }
    const latestCommit = allRuns[allRuns.length - 1].commit;
    const runs = allRuns.filter((r) =>
      state.hasSameDump(benchmark, r.commit, latestCommit),
    );
    if (runs.length < 2) {
      continue;
    }
    const start =
      metric.kind === "seconds" && runs.length >= 6
        ? median(runs.slice(0, 3).map((r) => r.value))
        : runs[0].value;
    const end =
      metric.kind === "seconds" && runs.length >= 6
        ? median(runs.slice(-3).map((r) => r.value))
        : runs[runs.length - 1].value;
    const delta = end - start;
    const ratio = end / start;
    const pct = (ratio - 1) * 100;
    entries.push({
      benchmark: benchmark,
      start: start,
      end: end,
      delta: delta,
      ratio: ratio,
      pct: pct,
      startHash: runs[0].commit.hash,
      endHash: runs[runs.length - 1].commit.hash,
    });
  }
  return summarizeEntries(entries, metric, null);
}

function shortBenchmarkName(name) {
  if (name.endsWith("AppPartial")) {
    return name.slice(0, -"AppPartial".length) + " (partial)";
  }
  if (name.endsWith("App") && name.length > 6) {
    return name.slice(0, -"App".length);
  }
  return name;
}

function renderNav(currentTarget) {
  const nav = document.createElement("nav");
  nav.className = "sidebar-nav";
  nav.setAttribute("aria-label", "Perf targets");
  const search = window.location.search || "";
  for (const page of PAGES) {
    const link = document.createElement("a");
    link.href = page.href + search;
    link.textContent = page.label;
    link.className =
      "sidebar-nav-link" + (page.target === currentTarget ? " is-active" : "");
    nav.appendChild(link);
  }
  return nav;
}

function renderCommitLink(hash, title) {
  const link = document.createElement("a");
  link.className = "sidebar-commit-link";
  link.href = `https://r8.googlesource.com/r8/+/${hash}`;
  link.target = "_blank";
  link.rel = "noopener";
  link.textContent = hash.slice(0, 7);
  if (title) {
    link.title = title;
  }
  return link;
}

function renderMoverRow(entry, summary, onSelectBenchmark) {
  const metric = summary.metric;
  const isBytes = metric.kind === "bytes";
  const formatDelta = isBytes ? formatSignedBytes : formatSignedSeconds;
  const formatAbs = isBytes ? formatAbsoluteBytes : formatAbsoluteSeconds;

  const row = document.createElement("button");
  row.type = "button";
  const isSelected = state.selectedBenchmarks.has(entry.benchmark);
  row.className = "trend-mover" + (isSelected ? " is-selected" : "");
  row.title =
    `${entry.benchmark}: ${formatAbs(entry.start)} (${entry.startHash.slice(0, 7)}) → ` +
    `${formatAbs(entry.end)} (${entry.endHash.slice(0, 7)}) ` +
    "(click to inspect, Shift-click to toggle)";
  row.onclick = (event) => {
    onSelectBenchmark(
      entry.benchmark,
      event.shiftKey || event.metaKey || event.ctrlKey,
    );
  };

  const nameSpan = document.createElement("span");
  nameSpan.className = "trend-mover-name";
  nameSpan.textContent = shortBenchmarkName(entry.benchmark);

  const valSpan = document.createElement("span");
  valSpan.className = `trend-mover-val ${toneClass(entry.pct, metric.flatPct)}-text`;
  valSpan.textContent = `${formatDelta(entry.delta)} (${formatSignedPct(entry.pct)})`;

  row.append(nameSpan, valSpan);
  return row;
}

function renderMoverGroup(
  label,
  entries,
  emptyLabel,
  summary,
  onSelectBenchmark,
) {
  const group = document.createElement("div");
  group.className = "trend-mover-group";

  const heading = document.createElement("div");
  heading.className = "trend-mover-group-label";
  heading.textContent = label;
  group.appendChild(heading);

  if (entries.length === 0) {
    const empty = document.createElement("div");
    empty.className = "trend-mover-empty";
    empty.textContent = emptyLabel;
    group.appendChild(empty);
    return group;
  }

  for (const entry of entries) {
    group.appendChild(renderMoverRow(entry, summary, onSelectBenchmark));
  }
  return group;
}

function renderMetricCard(summary, onSelectBenchmark) {
  const metric = summary.metric;
  const isBytes = metric.kind === "bytes";
  const formatDelta = isBytes ? formatSignedBytes : formatSignedSeconds;

  const card = document.createElement("section");
  card.className = "trend-card";

  const head = document.createElement("div");
  head.className = "trend-card-head";

  const title = document.createElement("h3");
  title.className = "trend-card-title";
  title.textContent = metric.label();

  const badge = document.createElement("span");
  badge.className = `trend-badge ${toneClass(summary.geomean)}`;
  badge.textContent = formatPctBadge(summary.geomean);
  badge.title = `Geometric mean across ${summary.entries.length} benchmarks (${summary.note})`;

  head.appendChild(title);
  head.appendChild(badge);
  card.appendChild(head);

  const meta = document.createElement("div");
  meta.className = "trend-meta";

  const netSpan = document.createElement("span");
  netSpan.textContent = `Net ${formatDelta(summary.totalDelta)}`;
  netSpan.title = `Sum of changes across all ${summary.entries.length} benchmarks`;

  const countsSpan = document.createElement("span");
  countsSpan.className = "trend-counts";
  countsSpan.title = `${summary.improved} improved, ${summary.regressed} regressed, ${summary.unchanged} within ±${metric.flatPct}%`;

  const impSpan = document.createElement("span");
  impSpan.className = summary.improved > 0 ? "trend-good-text" : "";
  impSpan.textContent = `${summary.improved}↓`;

  const regSpan = document.createElement("span");
  regSpan.className = summary.regressed > 0 ? "trend-bad-text" : "";
  regSpan.textContent = `${summary.regressed}↑`;

  const flatSpan = document.createElement("span");
  flatSpan.textContent = `${summary.unchanged}=`;

  countsSpan.append(impSpan, " ", regSpan, " ", flatSpan);
  meta.append(netSpan, countsSpan);
  card.appendChild(meta);

  if (summary.fullGeomean !== null && summary.partialGeomean !== null) {
    const split = document.createElement("div");
    split.className = "trend-split";

    const fullEl = document.createElement("span");
    const fullLabel = document.createElement("span");
    fullLabel.textContent = `Full (${summary.fullCount}): `;
    const fullVal = document.createElement("strong");
    fullVal.className = toneClass(summary.fullGeomean) + "-text";
    fullVal.textContent = formatSignedPct(summary.fullGeomean);
    fullEl.append(fullLabel, fullVal);

    const partEl = document.createElement("span");
    const partLabel = document.createElement("span");
    partLabel.textContent = `Partial (${summary.partialCount}): `;
    const partVal = document.createElement("strong");
    partVal.className = toneClass(summary.partialGeomean) + "-text";
    partVal.textContent = formatSignedPct(summary.partialGeomean);
    partEl.append(partLabel, partVal);

    split.append(fullEl, partEl);
    card.appendChild(split);
  }

  const minAbsDelta = isBytes ? 1 : 0.00005;
  const topRegressions = summary.entries
    .filter((e) => e.delta >= minAbsDelta && e.pct > 0)
    .sort((a, b) => b.pct - a.pct || b.delta - a.delta)
    .slice(0, 3);
  const topImprovements = summary.entries
    .filter((e) => e.delta <= -minAbsDelta && e.pct < 0)
    .sort((a, b) => a.pct - b.pct || a.delta - b.delta)
    .slice(0, 3);

  const list = document.createElement("div");
  list.className = "trend-movers";
  list.appendChild(
    renderMoverGroup(
      "Top regressions",
      topRegressions,
      "No regressions",
      summary,
      onSelectBenchmark,
    ),
  );
  list.appendChild(
    renderMoverGroup(
      "Top improvements",
      topImprovements,
      "No improvements",
      summary,
      onSelectBenchmark,
    ),
  );

  if (
    state.selectedBenchmarks.size >= 1 &&
    state.selectedBenchmarks.size <= 3
  ) {
    const shown = new Set(
      topRegressions.concat(topImprovements).map((e) => e.benchmark),
    );
    const byName = new Map(summary.entries.map((e) => [e.benchmark, e]));
    const extraSelected = [];
    for (const selected of state.selectedBenchmarks.values()) {
      const entry = byName.get(selected);
      if (entry && !shown.has(entry.benchmark)) {
        extraSelected.push(entry);
        shown.add(entry.benchmark);
      }
    }
    if (extraSelected.length > 0) {
      list.appendChild(
        renderMoverGroup(
          "Selected",
          extraSelected,
          "",
          summary,
          onSelectBenchmark,
        ),
      );
    }
  }

  card.appendChild(list);

  return card;
}

var selectBenchmarkCallback = null;

function update(onSelectBenchmark) {
  if (onSelectBenchmark) {
    selectBenchmarkCallback = onSelectBenchmark;
  }
  const handleSelectBenchmark = selectBenchmarkCallback || (() => {});
  const container = document.getElementById("trend-sidebar");
  if (!container) {
    return;
  }
  const cuts = state.releaseCuts();
  const cut = state.releaseCut();
  const cutCommits = state.cutCommits();
  const recentCommits = state.recentMainCommits();
  const trendCommits = state.trendCommits();
  container.replaceChildren();
  container.appendChild(renderNav(state.target()));

  const header = document.createElement("div");
  header.className = "sidebar-header";

  const heading = document.createElement("h2");
  heading.className = "sidebar-title";
  if (cut) {
    const prefix = cut.devBranch ? `Main (${cut.devBranch}) vs` : "Main vs";
    if (cuts.length > 0) {
      const labelSpan = document.createElement("span");
      labelSpan.textContent = prefix;
      const select = document.createElement("select");
      select.className = "sidebar-cut-select";
      select.setAttribute("aria-label", "Release branch cut");
      select.title = "Select release branch cut to compare main against";
      for (const candidate of cuts) {
        const option = document.createElement("option");
        option.value = candidate.branch;
        option.textContent = `${candidate.branch} cut`;
        option.selected = candidate.branch === cut.branch;
        select.appendChild(option);
      }
      select.onchange = () => {
        state.selectReleaseCut(select.value, () => update());
      };
      heading.append(labelSpan, select);
    } else {
      heading.textContent = `${prefix} ${cut.branch} cut`;
    }
  } else {
    heading.textContent = "2-Week Trend";
  }
  header.appendChild(heading);

  const sub = document.createElement("div");
  sub.className = "sidebar-subtitle";
  if (cut) {
    const cutDate = shortDateFormatter.format(new Date(cut.submitted * 1000));
    const cutLink = renderCommitLink(
      cut.hash,
      `${cut.branch} cut: ${cut.hash}${cut.title ? " — " + cut.title : ""}`,
    );
    if (recentCommits.length > 0) {
      const latest = recentCommits[recentCommits.length - 1];
      const latestDate = shortDateFormatter.format(
        new Date(latest.submitted * 1000),
      );
      const latestLink = renderCommitLink(
        latest.hash,
        `Latest main with results: ${latest.hash}${latest.title ? " — " + latest.title : ""}`,
      );
      sub.append(cutLink, ` (${cutDate}) → `, latestLink, ` (${latestDate})`);
    } else {
      sub.append(cutLink, ` (${cutDate})`);
    }
  } else if (trendCommits.length >= 2) {
    const firstDate = shortDateFormatter.format(
      new Date(trendCommits[0].submitted * 1000),
    );
    const lastDate = shortDateFormatter.format(
      new Date(trendCommits[trendCommits.length - 1].submitted * 1000),
    );
    sub.textContent = `main · ${firstDate} – ${lastDate} · ${trendCommits.length} commits`;
  } else {
    sub.textContent = "main · last 14 days";
  }
  header.appendChild(sub);
  container.appendChild(header);

  if (cut && state.isCutLoading()) {
    const loading = document.createElement("p");
    loading.className = "sidebar-empty";
    loading.textContent = `Loading ${cut.branch} cut results…`;
    container.appendChild(loading);
    return;
  }

  let renderedAny = false;
  for (const metric of METRICS) {
    const summary = cut
      ? computeReleaseCutSummary(cut, cutCommits, recentCommits, metric)
      : computeTrendSummary(trendCommits, metric);
    if (summary) {
      container.appendChild(renderMetricCard(summary, handleSelectBenchmark));
      renderedAny = true;
    }
  }
  if (!renderedAny) {
    const empty = document.createElement("p");
    empty.className = "sidebar-empty";
    empty.textContent = cut
      ? `Not enough results around the ${cut.branch} cut or on recent main.`
      : "Not enough main commits in the last 14 days.";
    container.appendChild(empty);
  }
}

export default {
  update: update,
};
