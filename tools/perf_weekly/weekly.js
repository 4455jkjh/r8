// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// Loads the weekly result bundles written by tools/perf.py directly from the
// bucket, and reloads the page when new bundles appear.

const BUCKET = "r8-perf-results";
const PREFIX = "weekly/";
const DEFAULT_DAYS = 14;
const TREND_DAYS = 14;
const CUT_BEFORE_SECS = 3 * 86400;
const CUT_AFTER_SECS = 2 * 86400;
const CUT_COMMITS_BEFORE = 10;
const CUT_COMMITS_AFTER = 3;
const DAYS_CHOICES = [14, 28, 56, 112];
const POLL_INTERVAL_MS = 60 * 1000;
const FETCH_CONCURRENCY = 32;
// Same release commits as the old aggregated page: x.y.z-dev and x.y.0-alphaN.
const RELEASE_VERSION = /^\d+\.\d+\.(\d+-dev|0-alpha\d+)$/;

function days() {
  const value = parseInt(
    new URLSearchParams(window.location.search).get("days"),
  );
  return isNaN(value) || value <= 0 ? DEFAULT_DAYS : value;
}

function selectedCutBranch() {
  return new URLSearchParams(window.location.search).get("cut") || "";
}

function setCutBranchParam(branch, defaultBranch) {
  const url = new URL(window.location.href);
  if (!branch || branch === defaultBranch) {
    url.searchParams.delete("cut");
  } else {
    url.searchParams.set("cut", branch);
  }
  window.history.replaceState(null, "", url);
}

function targetPrefix(target) {
  return `${PREFIX}${target}/`;
}

function weekFolderPrefix(target, folder) {
  return `${targetPrefix(target)}${folder}/`;
}

function releasesPrefix(target) {
  return `${targetPrefix(target)}releases/`;
}

function itemCommitHash(name) {
  const parts = name.split("/");
  return parts.length >= 2 ? parts[parts.length - 2] : "";
}

function itemKey(item) {
  return `${item.name}@${item.generation}`;
}

// Returns the ISO week folder of a date, e.g. W43Y26, like perf.py.
function isoWeekFolder(date) {
  const day = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()),
  );
  // The ISO year and week are those of the Thursday of the date's week.
  day.setUTCDate(day.getUTCDate() + 3 - ((day.getUTCDay() + 6) % 7));
  const year = day.getUTCFullYear();
  const firstThursday = new Date(Date.UTC(year, 0, 4));
  firstThursday.setUTCDate(
    firstThursday.getUTCDate() + 3 - ((firstThursday.getUTCDay() + 6) % 7),
  );
  const week = 1 + Math.round((day - firstThursday) / (7 * 86400 * 1000));
  return `W${String(week).padStart(2, "0")}Y${String(year % 100).padStart(2, "0")}`;
}

function weekFoldersInRange(startMillis, endMillis) {
  const folders = new Set();
  for (var t = startMillis; t <= endMillis; t += 86400 * 1000) {
    folders.add(isoWeekFolder(new Date(t)));
  }
  folders.add(isoWeekFolder(new Date(endMillis)));
  return Array.from(folders);
}

function weekFolders(sinceMillis) {
  return weekFoldersInRange(sinceMillis, Date.now());
}

function cutCandidateCommits(cut, mainHistory) {
  if (!cut || !cut.submitted) {
    return null;
  }
  if (mainHistory && mainHistory.length > 0) {
    const idx = mainHistory.findIndex((commit) => commit.hash === cut.hash);
    if (idx !== -1) {
      const start = Math.max(0, idx - CUT_COMMITS_AFTER);
      const end = Math.min(mainHistory.length, idx + CUT_COMMITS_BEFORE + 1);
      return mainHistory
        .slice(start, end)
        .filter(
          (commit) =>
            commit.submitted >= cut.submitted - CUT_BEFORE_SECS &&
            commit.submitted <= cut.submitted + CUT_AFTER_SECS,
        );
    }
  }
  return null;
}

function cutCandidateHashes(cut, mainHistory) {
  const candidates = cutCandidateCommits(cut, mainHistory);
  if (!candidates || candidates.length === 0) {
    return null;
  }
  return new Set(candidates.map((commit) => commit.hash));
}

function cutWeekFolders(cut, mainHistory) {
  if (!cut || !cut.submitted) {
    return [];
  }
  const candidates = cutCandidateCommits(cut, mainHistory);
  if (candidates && candidates.length > 0) {
    const folders = new Set(
      candidates.map((commit) =>
        isoWeekFolder(new Date(commit.submitted * 1000)),
      ),
    );
    return Array.from(folders);
  }
  return weekFoldersInRange(
    (cut.submitted - CUT_BEFORE_SECS) * 1000,
    (cut.submitted + CUT_AFTER_SECS) * 1000,
  );
}

async function listObjects(prefix) {
  const items = [];
  var pageToken = "";
  do {
    const params = new URLSearchParams({
      prefix: prefix,
      fields: "nextPageToken,items(name,generation)",
    });
    if (pageToken) {
      params.set("pageToken", pageToken);
    }
    const response = await fetch(
      `https://storage.googleapis.com/storage/v1/b/${BUCKET}/o?${params}`,
      { cache: "no-store" },
    );
    if (!response.ok) {
      throw new Error(`Listing ${prefix} failed: HTTP ${response.status}`);
    }
    const data = await response.json();
    for (const item of data.items || []) {
      if (item.name.endsWith(".json")) {
        items.push(item);
      }
    }
    pageToken = data.nextPageToken || "";
  } while (pageToken);
  return items;
}

function shouldFetchWindowItem(
  item,
  fetchSinceSecs,
  includeTryCommits,
  cutHashes,
  historyByHash,
) {
  if (item.name.includes("/try/")) {
    return includeTryCommits;
  }
  if (historyByHash.size === 0) {
    return true;
  }
  const hash = itemCommitHash(item.name);
  const commit = historyByHash.get(hash);
  if (!commit) {
    return true;
  }
  return (
    commit.submitted >= fetchSinceSecs ||
    Boolean(cutHashes && cutHashes.has(hash))
  );
}

function shouldFetchCutItem(item, cutHashes, historyByHash) {
  if (item.name.includes("/try/")) {
    return false;
  }
  if (!cutHashes) {
    return true;
  }
  const hash = itemCommitHash(item.name);
  return cutHashes.has(hash) || !historyByHash.has(hash);
}

// Bundles are fetched pinned to their generation with a sliding worker pool,
// so a bundle is only downloaded again when it is rewritten.
async function fetchBundles(items) {
  const results = new Array(items.length);
  let nextIndex = 0;
  const workerCount = Math.min(FETCH_CONCURRENCY, items.length);
  const workers = [];
  for (let w = 0; w < workerCount; w++) {
    workers.push(
      (async () => {
        while (nextIndex < items.length) {
          const idx = nextIndex++;
          const item = items[idx];
          try {
            const response = await fetch(
              `https://storage.googleapis.com/storage/v1/b/${BUCKET}/o/` +
                `${encodeURIComponent(item.name)}?alt=media&generation=${item.generation}`,
            );
            if (response.ok) {
              results[idx] = {
                name: item.name,
                bundle: await response.json(),
              };
            }
          } catch (e) {
            // Ignore individual bundle fetch failures.
          }
        }
      })(),
    );
  }
  await Promise.all(workers);
  return results.filter(Boolean);
}

// The first-parent main history and release cuts, deployed by
// tools/perf_weekly/deploy.py.
async function fetchMainHistory() {
  try {
    const response = await fetch("./commits.json", { cache: "no-store" });
    if (!response.ok) {
      return { main: null, devBranch: null, releaseCuts: [] };
    }
    const data = await response.json();
    return {
      main: Array.isArray(data.main) ? data.main : null,
      devBranch: data.dev_branch || null,
      releaseCuts: Array.isArray(data.release_cuts) ? data.release_cuts : [],
    };
  } catch (e) {
    return { main: null, devBranch: null, releaseCuts: [] };
  }
}

function compareVersionsDescending(a, b) {
  const partsA = a.version.split(/[.-]/);
  const partsB = b.version.split(/[.-]/);
  for (var i = 0; i < Math.max(partsA.length, partsB.length); i++) {
    const x = partsA[i] || "";
    const y = partsB[i] || "";
    const numberX = parseInt(x.replace(/\D/g, ""));
    const numberY = parseInt(y.replace(/\D/g, ""));
    if (x !== y) {
      if (!isNaN(numberX) && !isNaN(numberY) && numberX !== numberY) {
        return numberY - numberX;
      }
      return y.localeCompare(x);
    }
  }
  return 0;
}

function extractTargetBenchmarks(bundle, target) {
  if (!bundle?.benchmarks) {
    return null;
  }
  if (bundle.target) {
    return bundle.target === target ? bundle.benchmarks : null;
  }
  const nested = bundle.benchmarks[target];
  if (nested && typeof nested === "object" && !Array.isArray(nested.results)) {
    return nested;
  }
  return bundle.benchmarks;
}

function mergeTargetBundles(bundles, target, main, releases, tries) {
  const relPrefix = releasesPrefix(target);
  const sorted = bundles
    .slice()
    .sort((a, b) => (a.bundle?.run?.time || 0) - (b.bundle?.run?.time || 0));
  for (const { name, bundle } of sorted) {
    if (!bundle?.commit?.hash) {
      continue;
    }
    const results = extractTargetBenchmarks(bundle, target);
    if (!results) {
      continue;
    }
    const commits =
      name.startsWith(relPrefix) || name.startsWith(PREFIX + "releases/")
        ? releases
        : name.includes("/try/")
          ? tries
          : main;
    if (!commits) {
      continue;
    }
    const hash = bundle.commit.hash;
    if (!commits.has(hash)) {
      commits.set(hash, { ...bundle.commit, benchmarks: {} });
    }
    Object.assign(commits.get(hash).benchmarks, results);
  }
}

function buildMainViews(main, mainHistory, sinceSecs) {
  if (mainHistory && mainHistory.length > 0) {
    const inHistory = new Set(mainHistory.map((commit) => commit.hash));
    const newestHistorySecs = mainHistory[0].submitted;
    const unindexed = Array.from(main.values()).filter(
      (commit) => !commit.version && !inHistory.has(commit.hash),
    );
    const older = unindexed
      .filter((commit) => commit.submitted < newestHistorySecs)
      .sort((a, b) => a.submitted - b.submitted);
    const newer = unindexed
      .filter((commit) => commit.submitted >= newestHistorySecs)
      .sort((a, b) => a.submitted - b.submitted);

    const newerInWindow = newer
      .filter((commit) => commit.submitted >= sinceSecs)
      .slice()
      .reverse();
    const historyInWindow = mainHistory
      .filter((commit) => commit.submitted >= sinceSecs)
      .map((commit) => main.get(commit.hash) || { ...commit, benchmarks: {} });
    const olderInWindow = older
      .filter((commit) => commit.submitted >= sinceSecs)
      .slice()
      .reverse();
    const mainCommits = newerInWindow.concat(historyInWindow, olderInWindow);
    // Like the old page, drop the oldest commits until one has results.
    while (
      mainCommits.length > 0 &&
      Object.keys(mainCommits[mainCommits.length - 1].benchmarks).length == 0
    ) {
      mainCommits.pop();
    }

    const ordered = mainHistory
      .filter((commit) => main.has(commit.hash))
      .map((commit) => main.get(commit.hash))
      .reverse();
    return {
      mainCommits: mainCommits,
      chronologicalMain: older.concat(ordered, newer),
    };
  }

  const mainValues = Array.from(main.values()).filter(
    (commit) => !commit.version,
  );
  return {
    mainCommits: mainValues
      .filter((commit) => commit.submitted >= sinceSecs)
      .sort((a, b) => b.submitted - a.submitted),
    chronologicalMain: mainValues
      .slice()
      .sort((a, b) => a.submitted - b.submitted),
  };
}

function filterCutCommits(chronologicalMain, cut) {
  if (!cut) {
    return [];
  }
  return chronologicalMain.filter(
    (commit) =>
      commit.submitted >= cut.submitted - CUT_BEFORE_SECS &&
      commit.submitted <= cut.submitted + CUT_AFTER_SECS,
  );
}

// Merges the bundles into commits in the format of the old aggregated JSON
// files: main commits newest first followed by release commits, try commits
// with their parent_hash, and chronological main commits around the release
// cut and in the recent window for the sidebar.
function toCommits(
  bundles,
  target,
  sinceSecs,
  trendSinceSecs,
  historyInfo,
  releaseCut,
) {
  const main = new Map();
  const releases = new Map();
  const tries = new Map();
  mergeTargetBundles(bundles, target, main, releases, tries);

  const { mainCommits, chronologicalMain } = buildMainViews(
    main,
    historyInfo.main,
    sinceSecs,
  );
  const cutCommits = filterCutCommits(chronologicalMain, releaseCut);
  const recentSinceSecs = Math.min(sinceSecs, trendSinceSecs);
  const recentMainCommits = chronologicalMain.filter(
    (commit) => commit.submitted >= recentSinceSecs,
  );
  const trendCommits = chronologicalMain.filter(
    (commit) => commit.submitted >= trendSinceSecs,
  );

  const releaseCommits = Array.from(releases.values())
    .filter((commit) => commit.version && RELEASE_VERSION.test(commit.version))
    .sort(compareVersionsDescending);
  const mainHashes = new Set(mainCommits.map((commit) => commit.hash));
  const tryCommits = Array.from(tries.values()).filter(
    (commit) =>
      mainHashes.has(commit.parent_hash) && !mainHashes.has(commit.hash),
  );
  return {
    main: main,
    releases: releases,
    mainCommits: mainCommits,
    releaseCommits: releaseCommits,
    chronologicalMain: chronologicalMain,
    commits: mainCommits.concat(releaseCommits),
    tryCommits: tryCommits,
    releaseCut: releaseCut,
    cutCommits: cutCommits,
    recentMainCommits: recentMainCommits,
    trendCommits: trendCommits,
  };
}

function addDaysSelector(selectedDays) {
  const select = document.createElement("select");
  select.title = "Days of main history to load";
  const choices = new Set(DAYS_CHOICES.concat([selectedDays]));
  for (const choice of Array.from(choices).sort((a, b) => a - b)) {
    const option = document.createElement("option");
    option.value = choice;
    option.textContent = `Last ${choice} days`;
    option.selected = choice === selectedDays;
    select.appendChild(option);
  }
  select.onchange = () => {
    const params = new URLSearchParams(window.location.search);
    params.set("days", select.value);
    window.location.search = params.toString();
  };
  document.getElementById("days-selector").replaceChildren(select);
}

function pollForNewBundles(pollPrefixes, includeTryCommits, seen) {
  let inFlight = false;
  setInterval(async () => {
    if (inFlight) {
      return;
    }
    inFlight = true;
    try {
      const lists = await Promise.all(
        Array.from(pollPrefixes).map(listObjects),
      );
      const allItems = lists.flat();
      const items = includeTryCommits
        ? allItems
        : allItems.filter((item) => !item.name.includes("/try/"));
      if (items.some((item) => !seen.has(itemKey(item)))) {
        window.location.reload();
      }
    } catch (e) {
      console.warn("Polling for new perf results failed", e);
    } finally {
      inFlight = false;
    }
  }, POLL_INTERVAL_MS);
}

async function load(
  target,
  includeTryCommits = false,
  includeReleases = false,
) {
  const selectedDays = days();
  const nowMillis = Date.now();
  const sinceMillis = nowMillis - selectedDays * 86400 * 1000;
  const trendSinceMillis = nowMillis - TREND_DAYS * 86400 * 1000;
  const fetchSinceMillis = Math.min(sinceMillis, trendSinceMillis);
  const fetchSinceSecs = fetchSinceMillis / 1000;
  const chartFolders = weekFolders(fetchSinceMillis);
  const chartFoldersSet = new Set(chartFolders);
  const folderItems = new Map();
  const folderPromises = new Map();

  function ensureFolderListed(folder) {
    if (folderItems.has(folder)) {
      return Promise.resolve(folderItems.get(folder));
    }
    if (!folderPromises.has(folder)) {
      const promise = listObjects(weekFolderPrefix(target, folder))
        .then((items) => {
          folderItems.set(folder, items);
          return items;
        })
        .finally(() => {
          folderPromises.delete(folder);
        });
      folderPromises.set(folder, promise);
    }
    return folderPromises.get(folder);
  }

  const windowListsPromise = Promise.all(chartFolders.map(ensureFolderListed));
  const releaseItemsPromise = includeReleases
    ? listObjects(releasesPrefix(target))
    : Promise.resolve([]);
  const historyInfo = await fetchMainHistory();
  const historyByHash = new Map(
    (historyInfo.main || []).map((commit) => [commit.hash, commit]),
  );
  const releaseCuts = historyInfo.releaseCuts.map((cut) => ({
    branch: cut.branch,
    hash: cut.hash,
    submitted: cut.submitted,
    title: cut.title || "",
    devBranch: historyInfo.devBranch,
  }));
  const requestedBranch = selectedCutBranch();
  const initialCut =
    releaseCuts.find((cut) => cut.branch === requestedBranch) ||
    releaseCuts[0] ||
    null;
  const initialCutHashes = cutCandidateHashes(initialCut, historyInfo.main);
  const extraCutFolders = cutWeekFolders(initialCut, historyInfo.main).filter(
    (folder) => !chartFoldersSet.has(folder),
  );
  const [windowLists, releaseItems, extraCutLists] = await Promise.all([
    windowListsPromise,
    releaseItemsPromise,
    Promise.all(extraCutFolders.map(ensureFolderListed)),
  ]);
  const windowItems = windowLists.flat();
  const extraCutItems = extraCutLists.flat();

  const seen = new Set();
  for (const item of windowItems) {
    if (includeTryCommits || !item.name.includes("/try/")) {
      seen.add(itemKey(item));
    }
  }
  for (const item of releaseItems) {
    seen.add(itemKey(item));
  }
  const pollPrefixes = new Set(
    chartFolders.map((folder) => weekFolderPrefix(target, folder)),
  );
  if (includeReleases) {
    pollPrefixes.add(releasesPrefix(target));
  }

  const fetchedItemKeys = new Set();
  const itemsToFetch = [];
  function queueItem(item) {
    const key = itemKey(item);
    if (!fetchedItemKeys.has(key)) {
      fetchedItemKeys.add(key);
      itemsToFetch.push(item);
    }
  }

  for (const item of windowItems) {
    if (
      shouldFetchWindowItem(
        item,
        fetchSinceSecs,
        includeTryCommits,
        initialCutHashes,
        historyByHash,
      )
    ) {
      queueItem(item);
    }
  }
  for (const item of extraCutItems) {
    if (shouldFetchCutItem(item, initialCutHashes, historyByHash)) {
      queueItem(item);
    }
  }
  for (const item of releaseItems) {
    queueItem(item);
  }

  const bundles = await fetchBundles(itemsToFetch);
  addDaysSelector(selectedDays);
  pollForNewBundles(pollPrefixes, includeTryCommits, seen);
  const parsed = toCommits(
    bundles,
    target,
    sinceMillis / 1000,
    trendSinceMillis / 1000,
    historyInfo,
    initialCut,
  );
  let chronologicalMain = parsed.chronologicalMain;
  const loadedCutBranches = new Set();
  if (initialCut) {
    loadedCutBranches.add(initialCut.branch);
  }

  const cutLoader = {
    isLoaded: (cut) => !cut || loadedCutBranches.has(cut.branch),
    getCommits: (cut) => filterCutCommits(chronologicalMain, cut),
    loadCommits: async (cut) => {
      if (!cut) {
        return [];
      }
      const folders = cutWeekFolders(cut, historyInfo.main);
      const lists = await Promise.all(folders.map(ensureFolderListed));
      const cutHashes = cutCandidateHashes(cut, historyInfo.main);
      const missingItems = [];
      for (const item of lists.flat()) {
        if (!shouldFetchCutItem(item, cutHashes, historyByHash)) {
          continue;
        }
        const key = itemKey(item);
        if (!fetchedItemKeys.has(key)) {
          fetchedItemKeys.add(key);
          missingItems.push(item);
        }
      }
      if (missingItems.length > 0) {
        const fetched = await fetchBundles(missingItems);
        mergeTargetBundles(fetched, target, parsed.main, null, null);
        chronologicalMain = buildMainViews(
          parsed.main,
          historyInfo.main,
          sinceMillis / 1000,
        ).chronologicalMain;
      }
      loadedCutBranches.add(cut.branch);
      return filterCutCommits(chronologicalMain, cut);
    },
  };

  let releasesLoaded = includeReleases;
  let releasesPromise = null;
  const releaseLoader = {
    isLoaded: () => releasesLoaded,
    ensureLoaded: () => {
      if (releasesLoaded) {
        return Promise.resolve(parsed.releaseCommits);
      }
      if (!releasesPromise) {
        releasesPromise = (async () => {
          const relPrefix = releasesPrefix(target);
          const relItems = await listObjects(relPrefix);
          for (const item of relItems) {
            seen.add(itemKey(item));
          }
          pollPrefixes.add(relPrefix);
          const missingRelItems = [];
          for (const item of relItems) {
            const key = itemKey(item);
            if (!fetchedItemKeys.has(key)) {
              fetchedItemKeys.add(key);
              missingRelItems.push(item);
            }
          }
          if (missingRelItems.length > 0) {
            const fetched = await fetchBundles(missingRelItems);
            mergeTargetBundles(fetched, target, null, parsed.releases, null);
          }
          parsed.releaseCommits = Array.from(parsed.releases.values())
            .filter(
              (commit) =>
                commit.version && RELEASE_VERSION.test(commit.version),
            )
            .sort(compareVersionsDescending);
          releasesLoaded = true;
          return parsed.releaseCommits;
        })().catch((e) => {
          releasesPromise = null;
          throw e;
        });
      }
      return releasesPromise;
    },
  };

  return {
    commits: parsed.commits,
    mainCommits: parsed.mainCommits,
    mainHistory: historyInfo.main || [],
    tryCommits: parsed.tryCommits,
    releaseCuts: releaseCuts,
    releaseCut: parsed.releaseCut,
    cutCommits: parsed.cutCommits,
    cutLoader: cutLoader,
    releaseLoader: releaseLoader,
    recentMainCommits: parsed.recentMainCommits,
    trendCommits: parsed.trendCommits,
  };
}

export default {
  load: load,
  setCutBranchParam: setCutBranchParam,
};
