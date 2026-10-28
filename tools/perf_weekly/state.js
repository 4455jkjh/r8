// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
import chart from "./chart.js";
import dom from "./dom.js";
import url from "./url.js";
import weekly from "./weekly.js";

var commits = null;
var mainCommits = [];
var tryCommits = [];
var releaseCuts = [];
var releaseCut = null;
var cutCommits = [];
var cutLoader = null;
var releaseLoader = null;
var cutLoading = false;
var cutSelectionSeq = 0;
var recentMainCommits = [];
var trendCommits = [];
var targetName = "";

const benchmarks = new Set();
const selectedBenchmarks = new Set();

const legends = new Set();
const selectedLegends = new Set();

const zoom = { left: -1, right: -1 };

function forEachBenchmark(callback) {
  for (const benchmark of benchmarks.values()) {
    callback(benchmark, selectedBenchmarks.has(benchmark));
  }
}

function forEachSelectedBenchmark(callback) {
  forEachBenchmark((benchmark, selected) => {
    if (selected) {
      callback(benchmark);
    }
  });
}

function getCommit(index, zoom) {
  return getCommits(zoom)[index];
}

function getCommitFromContext(context) {
  const elementInfo = context[0];
  var commit;
  if (elementInfo.dataset.type == "line") {
    commit = getCommit(elementInfo.dataIndex, zoom);
  } else {
    console.assert(elementInfo.dataset.type == "scatter");
    commit = getCommit(elementInfo.raw.x, zoom);
  }
  return commit;
}

function getCommitDescriptionFromContext(context) {
  const commit = getCommitFromContext(context);
  const elementInfo = context[0];
  const dataset = chart.get().data.datasets[elementInfo.datasetIndex];
  return (
    `App: ${dataset.benchmark}\n` +
    `Author: ${commit.author}\n` +
    `Submitted: ${new Date(commit.submitted * 1000).toLocaleString()}\n` +
    `Hash: ${commit.hash}\n` +
    `Index: ${commit.index}`
  );
}

function getCommits(zoom) {
  const commitsForSelectedBranch = commits.filter((commit) =>
    dom.isCommitSelected(commit),
  );
  if (zoom) {
    return commitsForSelectedBranch.slice(zoom.left, zoom.right);
  }
  return commitsForSelectedBranch;
}

function getTryCommitsByParentHash(loadedTryCommits) {
  const result = {};
  for (var i = 0; i < loadedTryCommits.length; i++) {
    const commit = loadedTryCommits[i];
    const parentHash = commit["parent_hash"];
    if (parentHash in result) {
      result[parentHash].push(commit);
    } else {
      result[parentHash] = [commit];
    }
  }
  return result;
}

function hasLegend(legend) {
  return legends.has(legend);
}

// Loads the weekly bundles of the given target, e.g. "r8-full".
function importCommits(target, includeTryCommits = false) {
  targetName = target;
  const trySelector = document.getElementById("try-selector");
  if (trySelector) {
    trySelector.hidden = !includeTryCommits;
  }
  const includeReleases = url.contains("release");
  return weekly
    .load(target, includeTryCommits, includeReleases)
    .then((loaded) => {
      mainCommits = loaded.mainCommits || [];
      tryCommits = includeTryCommits ? loaded.tryCommits || [] : [];
      releaseCuts = loaded.releaseCuts || [];
      releaseCut = loaded.releaseCut || null;
      cutCommits = loaded.cutCommits || [];
      cutLoader = loaded.cutLoader || null;
      releaseLoader = loaded.releaseLoader || null;
      cutLoading = false;
      recentMainCommits = loaded.recentMainCommits || [];
      trendCommits = loaded.trendCommits || [];
      return processCommits(loaded.commits, tryCommits);
    });
}

function ensureReleasesLoaded(onLoaded) {
  if (!releaseLoader || releaseLoader.isLoaded()) {
    if (onLoaded) {
      onLoaded();
    }
    return Promise.resolve();
  }
  return releaseLoader
    .ensureLoaded()
    .then((releaseCommits) => {
      for (const commit of releaseCommits) {
        for (const benchmark in commit.benchmarks) {
          benchmarks.add(benchmark);
        }
      }
      processCommits(mainCommits.concat(releaseCommits), tryCommits);
      if (onLoaded) {
        onLoaded();
      }
    })
    .catch((e) => {
      console.warn("Loading release bundles failed", e);
      if (onLoaded) {
        onLoaded();
      }
    });
}

function selectReleaseCut(branch, onUpdate) {
  const cut = releaseCuts.find((candidate) => candidate.branch === branch);
  if (!cut) {
    return Promise.resolve();
  }
  releaseCut = cut;
  weekly.setCutBranchParam(cut.branch, releaseCuts[0]?.branch || "");
  const seq = ++cutSelectionSeq;
  if (!cutLoader || cutLoader.isLoaded(cut)) {
    cutCommits = cutLoader ? cutLoader.getCommits(cut) : [];
    cutLoading = false;
    if (onUpdate) {
      onUpdate();
    }
    return Promise.resolve();
  }
  cutLoading = true;
  if (onUpdate) {
    onUpdate();
  }
  return cutLoader
    .loadCommits(cut)
    .then((loadedCutCommits) => {
      if (seq !== cutSelectionSeq) {
        return;
      }
      cutCommits = loadedCutCommits;
      cutLoading = false;
      if (onUpdate) {
        onUpdate();
      }
    })
    .catch((e) => {
      console.warn(`Loading ${cut.branch} cut bundles failed`, e);
      if (seq !== cutSelectionSeq) {
        return;
      }
      cutCommits = [];
      cutLoading = false;
      if (onUpdate) {
        onUpdate();
      }
    });
}

function processCommits(loadedCommits, loadedTryCommits) {
  const loadedTryCommitsByParentHash =
    getTryCommitsByParentHash(loadedTryCommits);
  // Amend the commits with their unique index.
  var mainIndex = 0;
  var releaseIndex = 0;
  const processedCommits = [];
  for (var i = loadedCommits.length - 1; i >= 0; i--) {
    const commit = loadedCommits[i];
    commit.index = commit.version ? releaseIndex++ : mainIndex++;
    processedCommits.push(commit);

    // Include the try commits for this commit on main.
    const hash = commit["hash"];
    if (hash in loadedTryCommitsByParentHash) {
      console.assert(!commit.version, "Try jobs for release not supported");
      const tryCommitsForCurrentCommit = loadedTryCommitsByParentHash[hash];
      for (var j = 0; j < tryCommitsForCurrentCommit.length; j++) {
        const tryCommitForCurrentCommit = tryCommitsForCurrentCommit[j];
        tryCommitForCurrentCommit.index = mainIndex++;
      }
      processedCommits.push(...tryCommitsForCurrentCommit);
    }
  }
  commits = processedCommits;
  return processedCommits;
}

function initializeBenchmarks() {
  const isRelease = url.contains("release");
  const branchBenchmarks = new Set();
  for (const commit of commits.values()) {
    const inSelectedBranch = isRelease
      ? Boolean(commit.version)
      : !commit.version;
    for (const benchmark in commit.benchmarks) {
      benchmarks.add(benchmark);
      if (inSelectedBranch) {
        branchBenchmarks.add(benchmark);
      }
    }
  }
  for (const benchmark of benchmarks.values()) {
    if (url.matches(benchmark)) {
      selectedBenchmarks.add(benchmark);
    }
  }
  if (selectedBenchmarks.size == 0) {
    const candidates =
      branchBenchmarks.size > 0 ? branchBenchmarks : benchmarks;
    if (candidates.size > 0) {
      const randomBenchmarkIndex = Math.floor(Math.random() * candidates.size);
      const randomBenchmark = Array.from(candidates)[randomBenchmarkIndex];
      selectedBenchmarks.add(randomBenchmark);
    }
  }
}

function initializeLegends(legendsInfo) {
  for (var legend in legendsInfo) {
    legends.add(legend);
    if (url.contains(legend)) {
      selectedLegends.add(legend);
    }
  }
  if (selectedLegends.size == 0) {
    for (let [legend, legendInfo] of Object.entries(legendsInfo)) {
      if (legendInfo.default) {
        selectedLegends.add(legend);
      }
    }
  }
}

function initializeZoom() {
  const filteredCommits = resetZoom();
  for (const urlOption of url.values()) {
    if (urlOption.startsWith("L")) {
      var left = parseInt(urlOption.substring(1));
      if (isNaN(left)) {
        continue;
      }
      left = left >= 0 ? left : filteredCommits.length + left;
      if (left < 0) {
        zoom.left = 0;
      } else if (left >= filteredCommits.length) {
        zoom.left = filteredCommits.length - 1;
      } else {
        zoom.left = left;
      }
    }
  }
}

function resetZoom() {
  const filteredCommits = getCommits(null);
  zoom.left = Math.max(0, filteredCommits.length - 75);
  zoom.right = filteredCommits.length;
  return filteredCommits;
}

function handleKeyDownEvent(e, callback) {
  if (selectedBenchmarks.size != 1) {
    return;
  }
  const [selectedBenchmark] = selectedBenchmarks;
  var benchmarkToSelect = null;
  var previousBenchmark = null;
  for (const benchmark of benchmarks.values()) {
    if (previousBenchmark != null) {
      if (e.key == "ArrowLeft" && benchmark == selectedBenchmark) {
        benchmarkToSelect = previousBenchmark;
        break;
      } else if (
        e.key === "ArrowRight" &&
        previousBenchmark == selectedBenchmark
      ) {
        benchmarkToSelect = benchmark;
        break;
      }
    }
    previousBenchmark = benchmark;
  }
  if (benchmarkToSelect != null) {
    selectedBenchmarks.clear();
    selectedBenchmarks.add(benchmarkToSelect);
    document.getElementById(selectedBenchmark).checked = false;
    document.getElementById(benchmarkToSelect).checked = true;
    callback();
  }
}

function isLegendSelected(legend) {
  return selectedLegends.has(legend);
}

export default {
  benchmarks: benchmarks,
  commits: getCommits,
  legends: legends,
  selectedBenchmarks: selectedBenchmarks,
  selectedLegends: selectedLegends,
  forEachBenchmark: forEachBenchmark,
  forEachSelectedBenchmark: forEachSelectedBenchmark,
  getCommit: getCommit,
  getCommitFromContext: getCommitFromContext,
  getCommitDescriptionFromContext: getCommitDescriptionFromContext,
  handleKeyDownEvent: handleKeyDownEvent,
  hasLegend: hasLegend,
  initializeBenchmarks: initializeBenchmarks,
  initializeLegends: initializeLegends,
  initializeZoom: initializeZoom,
  importCommits: importCommits,
  isLegendSelected: isLegendSelected,
  resetZoom: resetZoom,
  cutCommits: () => cutCommits,
  ensureReleasesLoaded: ensureReleasesLoaded,
  isCutLoading: () => cutLoading,
  recentMainCommits: () => recentMainCommits,
  releaseCut: () => releaseCut,
  releaseCuts: () => releaseCuts,
  selectReleaseCut: selectReleaseCut,
  target: () => targetName,
  trendCommits: () => trendCommits,
  zoom: zoom,
};
