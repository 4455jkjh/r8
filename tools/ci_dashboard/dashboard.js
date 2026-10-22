// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

(() => {
  "use strict";

  const BUILDBUCKET_HOST = "https://cr-buildbucket.appspot.com";
  const MILO_PROJECT_UI_BASE = "https://ci.chromium.org/ui/p/";
  const MILO_BUILD_BASE = "https://ci.chromium.org/ui/b/";
  const GITILES_COMMIT_BASE = "https://r8.googlesource.com/r8/+/";
  const GERRIT_CL_BASE = "https://r8-review.googlesource.com/c/r8/+/";
  const KOKORO_BUCKET = "r8-test-results";
  const KOKORO_PREFIX = "ci-dashboard/kokoro/";
  const FUSION2_KOKORO_JOB_BASE = "https://fusion2.corp.google.com/ci/kokoro/";
  const FUSION2_INVOCATION_BASE =
    "https://fusion2.corp.google.com/invocations/";

  const POLL_INTERVAL_MS = 60 * 1000;
  const VERSION_CHECK_MS = 60 * 1000;
  const SAFETY_RELOAD_MS = 6 * 60 * 60 * 1000;
  const STALE_THRESHOLD_SECS = 24 * 3600;
  const RELEASE_ACTIVE_DAYS = 30;
  const RETIRED_BRANCH_BUILDER_DAYS = 7;
  const ERROR_BANNER_THRESHOLD_MS = 3 * 60 * 1000;
  const MAIN_CONSOLE_ROWS = 36;
  const BRANCH_MODAL_ROWS = 22;

  const state = {
    hidePassingBuilders: false,
    luciConfig: null,
    commitsByBranch: {},
    releaseCuts: {},
    kokoroObjectCache: new Map(),
    lastKokoroRecords: [],
    lastBbPayload: null,
    deployedVersion: null,
    lastSuccessPollMs: 0,
    lastAttemptPollMs: 0,
    lastPollDurationMs: 0,
    lastErrorMsg: null,
    isFetching: false,
    currentModel: null,
  };

  function miloBuilderUrl(project, bucket, builder) {
    return `${MILO_PROJECT_UI_BASE}${encodeURIComponent(project || "r8")}/builders/${encodeURIComponent(bucket || "ci")}/${encodeURIComponent(builder)}`;
  }

  function kokoroJobUrl(job) {
    return `${FUSION2_KOKORO_JOB_BASE}${encodeURIComponent("prod:" + job)}`;
  }

  function kokoroBuildUrl(buildId, job) {
    return buildId
      ? `${FUSION2_INVOCATION_BASE}${encodeURIComponent(buildId)}`
      : kokoroJobUrl(job);
  }

  function kokoroShortName(job) {
    return String(job || "")
      .replace(/^r8\//, "")
      .replace(/_release$/, "");
  }

  function stripPrpcPrefix(text) {
    const idx = text.indexOf("\n");
    if (text.startsWith(")]}'") && idx !== -1) {
      return text.slice(idx + 1);
    }
    return text;
  }

  async function prpcCall(service, method, payload) {
    const url = `${BUILDBUCKET_HOST}/prpc/${service}/${method}`;
    const resp = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(payload),
    });
    if (!resp.ok) {
      throw new Error(`Buildbucket ${method} HTTP ${resp.status}`);
    }
    const raw = await resp.text();
    return JSON.parse(stripPrpcPrefix(raw));
  }

  function formatCategoryLabel(rawCategory) {
    return String(rawCategory || "").replace(/\|/g, " / ");
  }

  function baseChipWeight(label) {
    const isNumeric = /^[0-9.]+$/.test(label);
    if (isNumeric) {
      if (label.length === 1) return 22;
      if (label.length === 2) return 27;
      if (label.length === 3) return 31;
      return 37;
    }
    return Math.max(38, label.length * 7.2 + 12);
  }

  // Computes dynamic column widths purely from label & category span lengths — zero hardcoded names.
  function computeColumnWeights(cols, catSpans) {
    const weights = cols.map((c) => baseChipWeight(c.label));
    for (const cs of catSpans) {
      const minCatWeight = cs.label.length * 8.2 + 16;
      let spanSum = 0;
      for (let i = cs.startIdx; i < cs.startIdx + cs.span; i++) {
        spanSum += weights[i];
      }
      if (spanSum < minCatWeight && cs.span > 0) {
        const extraEach = (minCatWeight - spanSum) / cs.span;
        for (let i = cs.startIdx; i < cs.startIdx + cs.span; i++) {
          weights[i] += extraEach;
        }
      }
    }
    return weights;
  }

  function formatCompactAge(epochSecs, nowSecs = Date.now() / 1000) {
    if (!epochSecs || Number.isNaN(epochSecs)) {
      return "";
    }
    const diff = Math.max(0, Math.round(nowSecs - epochSecs));
    if (diff < 60) return `${diff}s`;
    const mins = Math.floor(diff / 60);
    if (mins < 60) return `${mins}m`;
    const hours = Math.floor(mins / 60);
    if (hours < 36) return `${hours}h`;
    return `${Math.floor(hours / 24)}d`;
  }

  function formatRelativeAge(epochSecs, nowSecs = Date.now() / 1000) {
    if (!epochSecs || Number.isNaN(epochSecs)) {
      return "";
    }
    const diff = Math.max(0, Math.round(nowSecs - epochSecs));
    if (diff < 60) {
      return `${diff}s ago`;
    }
    const mins = Math.floor(diff / 60);
    if (mins < 60) {
      return `${mins}m ago`;
    }
    const hours = Math.floor(mins / 60);
    if (hours < 36) {
      const remMins = mins % 60;
      return hours < 6 && remMins > 0
        ? `${hours}h ${remMins}m ago`
        : `${hours}h ago`;
    }
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  function parseIsoSecs(isoStr) {
    if (!isoStr) return null;
    const ms = Date.parse(isoStr);
    return Number.isNaN(ms) ? null : ms / 1000;
  }

  function compareSemverDesc(a, b) {
    const pa = a.split(".").map((x) => parseInt(x, 10) || 0);
    const pb = b.split(".").map((x) => parseInt(x, 10) || 0);
    const len = Math.max(pa.length, pb.length);
    for (let i = 0; i < len; i++) {
      const da = pa[i] || 0;
      const db = pb[i] || 0;
      if (da !== db) return db - da;
    }
    return b.localeCompare(a);
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  async function loadCommitsAndVersion() {
    try {
      const resp = await fetch("commits.json", { cache: "no-store" });
      if (resp.ok) {
        const data = await resp.json();
        if (data && data.luci_config) {
          state.luciConfig = data.luci_config;
        }
        if (data && data.branches) {
          state.commitsByBranch = data.branches;
        }
        if (data && data.release_cuts) {
          state.releaseCuts = data.release_cuts;
        }
      }
    } catch (_) {
      // Keep the last loaded config and commits.
    }

    try {
      const vResp = await fetch("version.json", { cache: "no-store" });
      if (vResp.ok) {
        const vData = await vResp.json();
        if (vData && vData.assets_hash) {
          if (
            state.deployedVersion &&
            state.deployedVersion !== vData.assets_hash
          ) {
            window.location.reload();
            return;
          }
          state.deployedVersion = vData.assets_hash;
        }
      }
    } catch (_) {
      // Retry on the next poll.
    }
  }

  // Ensures any brand-new commit seen on Buildbucket before commits.json refreshes still appears at the top.
  function resolveBranchCommits(branchName, branchBuildsLists) {
    const baseList = [...(state.commitsByBranch[branchName] || [])];
    const knownShas = new Set(baseList.map((c) => c.sha));
    const newestKnownSecs =
      baseList[0] && baseList[0].time ? baseList[0].time : 0;
    const unseenMap = new Map();

    for (const bList of branchBuildsLists) {
      for (const b of bList || []) {
        const gc = b && b.input && b.input.gitilesCommit;
        const sha = gc && gc.id;
        if (!sha || knownShas.has(sha)) continue;
        const tSecs = parseIsoSecs(b.createTime) || 0;
        if (baseList.length === 0 || tSecs >= newestKnownSecs - 900) {
          const prev = unseenMap.get(sha);
          if (!prev || (tSecs > 0 && tSecs < prev.time)) {
            unseenMap.set(sha, {
              sha,
              author: "…",
              subject: "",
              time: tSecs,
            });
          }
        }
      }
    }

    const prepended = [...unseenMap.values()].sort(
      (a, b) => (b.time || 0) - (a.time || 0),
    );
    return [...prepended, ...baseList];
  }

  function resolveBlamelist(commits, firstFailSha, lastGreenSha) {
    if (!firstFailSha) {
      return {
        shortSha: "?",
        authors: [],
        commitCount: 0,
        subject: "",
        commitTime: null,
      };
    }
    const idxFail = commits.findIndex(
      (c) => c.sha.startsWith(firstFailSha) || firstFailSha.startsWith(c.sha),
    );
    const idxGreen = lastGreenSha
      ? commits.findIndex(
          (c) =>
            c.sha.startsWith(lastGreenSha) || lastGreenSha.startsWith(c.sha),
        )
      : -1;

    if (idxFail !== -1 && idxGreen !== -1 && idxFail < idxGreen) {
      const slice = commits.slice(idxFail, idxGreen);
      const authors = [];
      for (const c of slice) {
        if (c.author && !authors.includes(c.author)) authors.push(c.author);
      }
      return {
        shortSha: firstFailSha.slice(0, 8),
        fullSha: commits[idxFail].sha,
        authors,
        commitCount: slice.length,
        subject: commits[idxFail].subject || "",
        commitTime: commits[idxFail].time || null,
      };
    }

    if (idxFail !== -1) {
      const c = commits[idxFail];
      return {
        shortSha: firstFailSha.slice(0, 8),
        fullSha: c.sha,
        authors: c.author ? [c.author] : [],
        commitCount: 1,
        subject: c.subject || "",
        commitTime: c.time || null,
      };
    }

    return {
      shortSha: firstFailSha.slice(0, 8),
      fullSha: firstFailSha,
      authors: [],
      commitCount: 1,
      subject: "",
      commitTime: null,
    };
  }

  function computeMainBuilderStale(
    autoTriggered,
    latestCompletedSha,
    commits,
    nowSecs,
  ) {
    if (!autoTriggered || !latestCompletedSha) {
      return false;
    }
    const idx = commits.findIndex(
      (c) =>
        c.sha.startsWith(latestCompletedSha) ||
        latestCompletedSha.startsWith(c.sha),
    );
    if (idx > 0) {
      const oldestUnbuilt = commits[idx - 1];
      if (
        oldestUnbuilt &&
        oldestUnbuilt.time &&
        nowSecs - oldestUnbuilt.time > STALE_THRESHOLD_SECS
      ) {
        return true;
      }
    }
    return false;
  }

  function statusToCellKind(status) {
    switch (status) {
      case "SUCCESS":
        return "green";
      case "FAILURE":
        return "red";
      case "INFRA_FAILURE":
        return "infra";
      case "STARTED":
        return "running";
      case "SCHEDULED":
        return "scheduled";
      default:
        return "empty";
    }
  }

  // Computes exact LUCI Milo console rowspan cells across `visibleRows` commits for one builder column.
  function computeConsoleSlots(
    builds,
    commitIdxMap,
    visibleRows,
    nowSecs,
    options = {},
  ) {
    const slots = Array.from({ length: visibleRows }, () => ({
      type: "empty",
    }));
    if (!builds || builds.length === 0) {
      return slots;
    }

    const scheduledAtIdx = new Map();
    const batchAtIdx = new Map();

    for (const b of builds) {
      if (!b || b.status === "CANCELED") continue;
      const sha = b.input && b.input.gitilesCommit && b.input.gitilesCommit.id;
      if (!sha) continue;
      const idx = commitIdxMap.get(sha);
      if (idx === undefined) continue;

      if (b.status === "SCHEDULED") {
        if (!scheduledAtIdx.has(idx)) {
          scheduledAtIdx.set(idx, b);
        }
      } else if (
        b.status === "STARTED" ||
        b.status === "SUCCESS" ||
        b.status === "FAILURE" ||
        b.status === "INFRA_FAILURE"
      ) {
        const existing = batchAtIdx.get(idx);
        if (!existing) {
          batchAtIdx.set(idx, b);
        } else if (b.status === "STARTED" && existing.status !== "STARTED") {
          batchAtIdx.set(idx, b);
        }
      }
    }

    for (const [idx, b] of scheduledAtIdx.entries()) {
      if (idx >= 0 && idx < visibleRows) {
        slots[idx] = {
          type: "cell",
          kind: "scheduled",
          span: 1,
          commitCount: 1,
          buildId: b.id,
          buildUrl: b.buildUrl || null,
          buildNumber: b.number || null,
          status: b.status,
          ageText: formatRelativeAge(parseIsoSecs(b.createTime), nowSecs),
        };
      }
    }

    const sortedBatch = [...batchAtIdx.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([idx, build]) => ({ idx, build }));

    const hasMoreHistory = options.hasCompleteHistory
      ? false
      : builds.length >= 35;

    for (let i = 0; i < sortedBatch.length; i++) {
      const cur = sortedBatch[i];
      const next = sortedBatch[i + 1];
      const startIdx = cur.idx;
      let endIdx;
      if (next) {
        endIdx = next.idx - 1;
      } else if (hasMoreHistory) {
        endIdx = Math.max(startIdx, visibleRows - 1);
      } else {
        endIdx = startIdx;
      }

      if (startIdx >= visibleRows) {
        break;
      }

      const visStart = Math.max(0, startIdx);
      const visEnd = Math.min(visibleRows - 1, endIdx);
      if (visStart > visEnd) continue;

      const b = cur.build;
      const refSecs = parseIsoSecs(b.endTime || b.startTime || b.createTime);
      slots[visStart] = {
        type: "cell",
        kind: statusToCellKind(b.status),
        span: visEnd - visStart + 1,
        commitCount: endIdx - startIdx + 1,
        buildId: b.id,
        buildUrl: b.buildUrl || null,
        buildNumber: b.number || null,
        status: b.status,
        ageText: formatRelativeAge(refSecs, nowSecs),
      };
      for (let r = visStart + 1; r <= visEnd; r++) {
        slots[r] = { type: "spanned" };
      }
    }

    return slots;
  }

  function analyzeBuilderColumn(
    entry,
    builds,
    branchName,
    commits,
    commitIdxMap,
    visibleRows,
    nowSecs,
  ) {
    const project = entry.project || "r8";
    const bucket = entry.bucket || "ci";
    const builderName = entry.builder;
    const label = entry.short_name || builderName;
    const category = formatCategoryLabel(entry.category || bucket);
    const autoTriggered = entry.auto_triggered !== false;
    const builderUrl =
      entry.builderUrl || miloBuilderUrl(project, bucket, builderName);

    const startedBuilds = builds.filter((b) => b.status === "STARTED");
    const completed = builds.filter(
      (b) =>
        b.status !== "STARTED" &&
        b.status !== "SCHEDULED" &&
        b.status !== "CANCELED",
    );

    const slots = computeConsoleSlots(
      builds,
      commitIdxMap,
      visibleRows,
      nowSecs,
      {
        hasCompleteHistory: Boolean(entry.hasCompleteHistory),
      },
    );

    if (completed.length === 0) {
      return {
        project,
        bucket,
        builder: builderName,
        builderUrl,
        shortName: label,
        label,
        category,
        autoTriggered,
        branch: branchName,
        kind: branchName === "main" && !autoTriggered ? "idle" : "green",
        isBuilding: startedBuilds.length > 0,
        latestBuildId: startedBuilds[0] ? startedBuilds[0].id : null,
        latestBuildUrl: startedBuilds[0]
          ? startedBuilds[0].buildUrl ||
            `${MILO_BUILD_BASE}${encodeURIComponent(startedBuilds[0].id)}`
          : builderUrl,
        latestSha: null,
        latestEndSecs: null,
        latestAgeText: "",
        streak: 0,
        blame: null,
        slots,
      };
    }

    const latest = completed[0];
    const latestSha =
      (latest.input &&
        latest.input.gitilesCommit &&
        latest.input.gitilesCommit.id) ||
      null;
    const latestEndSecs = parseIsoSecs(latest.endTime || latest.createTime);

    let kind = "green";
    if (latest.status === "FAILURE") {
      kind = "red";
    } else if (latest.status === "INFRA_FAILURE") {
      kind = "infra";
    } else if (
      branchName === "main" &&
      computeMainBuilderStale(autoTriggered, latestSha, commits, nowSecs)
    ) {
      kind = "stale";
    }

    let streak = 0;
    let firstFailBuild = null;
    let lastGreenBuild = null;

    if (kind === "red" || kind === "infra") {
      for (const b of completed) {
        if (b.status === "FAILURE" || b.status === "INFRA_FAILURE") {
          streak += 1;
          firstFailBuild = b;
        } else if (b.status === "SUCCESS") {
          lastGreenBuild = b;
          break;
        }
      }
    }

    let blame = null;
    if (firstFailBuild) {
      const ffSha =
        firstFailBuild.input &&
        firstFailBuild.input.gitilesCommit &&
        firstFailBuild.input.gitilesCommit.id;
      const lgSha =
        lastGreenBuild &&
        lastGreenBuild.input &&
        lastGreenBuild.input.gitilesCommit &&
        lastGreenBuild.input.gitilesCommit.id;
      const resolved = resolveBlamelist(commits, ffSha, lgSha);
      const failTimeSecs =
        resolved.commitTime ||
        parseIsoSecs(
          firstFailBuild.endTime ||
            firstFailBuild.startTime ||
            firstFailBuild.createTime,
        );
      blame = {
        ...resolved,
        failBuildId: firstFailBuild.id,
        failTimeSecs,
        ageText: formatRelativeAge(failTimeSecs, nowSecs),
      };
    }

    return {
      project,
      bucket,
      builder: builderName,
      builderUrl,
      shortName: label,
      label,
      category,
      autoTriggered,
      branch: branchName,
      kind,
      rawStatus: latest.status,
      isBuilding: startedBuilds.length > 0,
      latestBuildId: latest.id,
      latestBuildUrl:
        latest.buildUrl || `${MILO_BUILD_BASE}${encodeURIComponent(latest.id)}`,
      latestSha,
      latestEndSecs,
      latestAgeText: formatRelativeAge(latestEndSecs, nowSecs),
      streak,
      blame,
      slots,
    };
  }

  function worstKind(kinds) {
    if (kinds.includes("red")) return "red";
    if (kinds.includes("infra")) return "infra";
    if (kinds.includes("stale")) return "stale";
    return "green";
  }

  function parsePresubmitBuildItem(b, qMeta, nowSecs) {
    const tags = {};
    for (const t of b.tags || []) {
      if (t && t.key) tags[t.key] = t.value;
    }
    const gerritChange =
      (b.input && b.input.gerritChanges && b.input.gerritChanges[0]) || {};
    const changeNum = gerritChange.change || null;
    const patchsetNum = gerritChange.patchset || null;
    const clOwner = (tags.cq_cl_owner || tags.cq_triggerer || "").replace(
      /@google\.com$/,
      "",
    );
    const createSecs = parseIsoSecs(b.createTime);
    const startSecs = parseIsoSecs(b.startTime);
    const refSecs =
      b.status === "STARTED" ? startSecs || createSecs : createSecs;

    return {
      id: b.id,
      project: qMeta.project || "r8",
      bucket: qMeta.bucket || "try",
      builder: qMeta.builder,
      status: b.status,
      changeNum,
      patchsetNum,
      clLabel: changeNum ? `${changeNum}/${patchsetNum || 1}` : "–",
      clUrl: changeNum
        ? `${GERRIT_CL_BASE}${changeNum}/${patchsetNum || ""}`
        : null,
      owner: clOwner || "–",
      ageText: formatRelativeAge(refSecs, nowSecs),
      createSecs,
      startSecs,
    };
  }

  function cqModesLabel(modes) {
    const list = Array.isArray(modes) ? modes : [];
    const hasFull = list.includes("FULL_RUN");
    const hasDry = list.includes("DRY_RUN");
    if (hasFull && !hasDry) return "CQ+2";
    if (hasDry && !hasFull) return "CQ+1";
    return "CQ";
  }

  function buildPresubmitTile(presubmitQueues, nowSecs) {
    const queues = Array.isArray(presubmitQueues) ? presubmitQueues : [];
    const rows = queues.map((q) => {
      const runningItems = (q.runningBuilds || []).map((b) =>
        parsePresubmitBuildItem(b, q, nowSecs),
      );
      const scheduledItems = (q.scheduledBuilds || []).map((b) =>
        parsePresubmitBuildItem(b, q, nowSecs),
      );
      const oldestRunningAge =
        runningItems.length > 0
          ? formatRelativeAge(
              Math.min(
                ...runningItems.map(
                  (x) => x.startSecs || x.createSecs || nowSecs,
                ),
              ),
              nowSecs,
            )
          : null;
      const oldestScheduledAge =
        scheduledItems.length > 0
          ? formatRelativeAge(
              Math.min(...scheduledItems.map((x) => x.createSecs || nowSecs)),
              nowSecs,
            )
          : null;
      return {
        project: q.project || "r8",
        bucket: q.bucket || "try",
        builder: q.builder,
        displayName: q.builder,
        subtitle: cqModesLabel(q.modes),
        runningCount: runningItems.length,
        scheduledCount: scheduledItems.length,
        oldestRunningAge,
        oldestScheduledAge,
        runningItems,
        scheduledItems,
      };
    });

    const bucketsHeader =
      [...new Set(rows.map((r) => `${r.project}/${r.bucket}`))].join(", ") ||
      "try";

    return {
      id: "presubmit",
      title: "Presubmit",
      subtitle: bucketsHeader,
      totalRunning: rows.reduce((acc, r) => acc + r.runningCount, 0),
      totalScheduled: rows.reduce((acc, r) => acc + r.scheduledCount, 0),
      rows,
      allItems: rows.flatMap((r) => [...r.runningItems, ...r.scheduledItems]),
    };
  }

  async function fetchKokoroStatus() {
    const items = [];
    let pageToken = "";
    do {
      const tokenParam = pageToken
        ? `&pageToken=${encodeURIComponent(pageToken)}`
        : "";
      const listUrl = `https://storage.googleapis.com/storage/v1/b/${KOKORO_BUCKET}/o?prefix=${encodeURIComponent(KOKORO_PREFIX)}&fields=nextPageToken,items(name,generation,updated)${tokenParam}`;
      const resp = await fetch(listUrl, { cache: "no-store" });
      if (!resp.ok) {
        throw new Error(`GCS Kokoro list HTTP ${resp.status}`);
      }
      const data = await resp.json();
      for (const it of data.items || []) {
        if (it && it.name && it.name.endsWith(".json")) {
          items.push(it);
        }
      }
      pageToken = data.nextPageToken || "";
    } while (pageToken);

    const activeKeys = new Set(
      items.map((it) => `${it.name}@${it.generation}`),
    );
    for (const cachedKey of state.kokoroObjectCache.keys()) {
      if (!activeKeys.has(cachedKey)) {
        state.kokoroObjectCache.delete(cachedKey);
      }
    }

    const missing = items.filter(
      (it) => !state.kokoroObjectCache.has(`${it.name}@${it.generation}`),
    );
    const CONCURRENCY = 16;
    for (let i = 0; i < missing.length; i += CONCURRENCY) {
      const chunk = missing.slice(i, i + CONCURRENCY);
      await Promise.all(
        chunk.map(async (it) => {
          const mediaUrl = `https://storage.googleapis.com/storage/v1/b/${KOKORO_BUCKET}/o/${encodeURIComponent(it.name)}?alt=media&generation=${encodeURIComponent(it.generation)}`;
          let rec;
          try {
            const r = await fetch(mediaUrl);
            if (!r.ok) return;
            rec = await r.json();
          } catch (_) {
            return;
          }
          if (rec && rec.job && rec.build_number !== undefined) {
            state.kokoroObjectCache.set(`${it.name}@${it.generation}`, rec);
          }
        }),
      );
    }

    const records = [];
    for (const it of items) {
      const rec = state.kokoroObjectCache.get(`${it.name}@${it.generation}`);
      if (rec) records.push(rec);
    }
    return records;
  }

  function normalizeKokoroRecords(records, nowSecs) {
    const mainJobsMap = new Map();
    const releaseJobsMap = new Map();

    for (const r of records || []) {
      if (!r || !r.job || !r.commit || r.job_type === "RELEASE") continue;
      const startSecs = parseIsoSecs(r.start_time);
      let status;
      if (r.state === "RUNNING") {
        if (startSecs && nowSecs - startSecs > 16 * 3600) {
          status = "INFRA_FAILURE";
        } else {
          status = "STARTED";
        }
      } else if (r.exit_code === 0) {
        status = "SUCCESS";
      } else {
        status = "FAILURE";
      }

      const job = r.job;
      const branch = r.branch || "main";
      const ref =
        branch === "main" ? "refs/heads/main" : `refs/heads/${branch}`;
      const buildNumber = Number(r.build_number) || 0;
      const buildObj = {
        id: r.build_id || `${job}/${buildNumber}`,
        number: buildNumber,
        status,
        createTime: r.start_time,
        startTime: r.start_time,
        endTime: status === "STARTED" ? undefined : r.end_time || r.start_time,
        buildUrl: kokoroBuildUrl(r.build_id, job),
        input: {
          gitilesCommit: {
            host: "r8.googlesource.com",
            project: "r8",
            id: r.commit,
            ref,
          },
        },
      };

      if (branch === "main") {
        if (!mainJobsMap.has(job)) {
          mainJobsMap.set(job, []);
        }
        mainJobsMap.get(job).push(buildObj);
      } else {
        if (!releaseJobsMap.has(job)) {
          releaseJobsMap.set(job, []);
        }
        releaseJobsMap.get(job).push(buildObj);
      }
    }

    const kokoroMainEntries = [...mainJobsMap.keys()].sort().map((job) => ({
      project: "kokoro",
      bucket: "kokoro",
      builder: job,
      category: "Kokoro",
      short_name: kokoroShortName(job),
      auto_triggered: true,
      hasCompleteHistory: true,
      builderUrl: kokoroJobUrl(job),
    }));

    const kokoroReleaseEntries = [...releaseJobsMap.keys()]
      .sort()
      .map((job) => ({
        project: "kokoro",
        bucket: "kokoro",
        builder: job,
        category: "Kokoro",
        short_name: kokoroShortName(job),
        auto_triggered: true,
        hasCompleteHistory: true,
        builderUrl: kokoroJobUrl(job),
      }));

    const sortBuildsDesc = (a, b) =>
      (parseIsoSecs(b.startTime || b.createTime) || 0) -
        (parseIsoSecs(a.startTime || a.createTime) || 0) ||
      (b.number || 0) - (a.number || 0);

    const kokoroBuildsByBuilder = {};
    for (const [job, list] of mainJobsMap.entries()) {
      list.sort(sortBuildsDesc);
      kokoroBuildsByBuilder[job] = list;
    }
    for (const [job, list] of releaseJobsMap.entries()) {
      list.sort(sortBuildsDesc);
      kokoroBuildsByBuilder[job] = list;
    }

    return {
      kokoroMainEntries,
      kokoroReleaseEntries,
      kokoroBuildsByBuilder,
    };
  }

  function buildModelFromBuildbucket(
    luciCfg,
    buildsByBuilder,
    nowSecs = Date.now() / 1000,
    presubmitQueues = [],
    kokoroRecords = [],
  ) {
    const kokoroNorm = normalizeKokoroRecords(kokoroRecords, nowSecs);
    const mergedBuildsByBuilder = {
      ...buildsByBuilder,
      ...kokoroNorm.kokoroBuildsByBuilder,
    };
    const mainEntries = [
      ...(luciCfg.main_console || []),
      ...kokoroNorm.kokoroMainEntries,
    ];
    const releaseEntries = [
      ...(luciCfg.release_console || []),
      ...kokoroNorm.kokoroReleaseEntries,
    ];

    const mainCommits = resolveBranchCommits(
      "main",
      mainEntries.map((e) => mergedBuildsByBuilder[e.builder] || []),
    );
    const mainIdxMap = new Map();
    mainCommits.forEach((c, idx) => mainIdxMap.set(c.sha, idx));

    const visibleMainCommits = mainCommits.slice(0, MAIN_CONSOLE_ROWS);
    // Preserves the exact column order defined in luci-milo.cfg followed by Kokoro columns.
    const mainColumns = mainEntries.map((entry) =>
      analyzeBuilderColumn(
        entry,
        mergedBuildsByBuilder[entry.builder] || [],
        "main",
        mainCommits,
        mainIdxMap,
        visibleMainCommits.length,
        nowSecs,
      ),
    );

    const activeMainCols = mainColumns.filter((c) => c.kind !== "idle");
    const mainKind = worstKind(activeMainCols.map((c) => c.kind));
    const mainRed = activeMainCols.filter((c) => c.kind === "red").length;
    const mainInfra = activeMainCols.filter((c) => c.kind === "infra").length;
    const mainStale = activeMainCols.filter((c) => c.kind === "stale").length;
    const mainBuilding = mainColumns.filter((c) => c.isBuilding).length;

    const tipCommitObj = mainCommits[0] || null;
    const tipCommit = tipCommitObj
      ? {
          sha: tipCommitObj.sha,
          shortSha: tipCommitObj.sha.slice(0, 8),
          author:
            tipCommitObj.author && tipCommitObj.author !== "…"
              ? tipCommitObj.author
              : null,
          subject: tipCommitObj.subject || "",
          ageText: formatRelativeAge(tipCommitObj.time, nowSecs),
        }
      : null;

    // Release Branches (preserving exact release_console column order from luci-milo.cfg + Kokoro)
    const cutoffSecs = nowSecs - RELEASE_ACTIVE_DAYS * 86400;
    const rawBranchMap = {};
    for (const entry of releaseEntries) {
      const bName = entry.builder;
      for (const b of mergedBuildsByBuilder[bName] || []) {
        const ref =
          b.input && b.input.gitilesCommit && b.input.gitilesCommit.ref;
        if (!ref || !ref.startsWith("refs/heads/")) continue;
        const br = ref.slice("refs/heads/".length);
        if (!rawBranchMap[br]) rawBranchMap[br] = {};
        if (!rawBranchMap[br][bName]) rawBranchMap[br][bName] = [];
        rawBranchMap[br][bName].push(b);
      }
    }

    const branchSummaries = [];
    for (const br of Object.keys(rawBranchMap).sort(compareSemverDesc)) {
      const builderMap = rawBranchMap[br];
      let newestBranchBuildSecs = 0;
      for (const bList of Object.values(builderMap)) {
        const t = parseIsoSecs(bList[0] && bList[0].createTime) || 0;
        if (t > newestBranchBuildSecs) newestBranchBuildSecs = t;
      }
      if (newestBranchBuildSecs < cutoffSecs) continue;

      const brCommits = resolveBranchCommits(br, Object.values(builderMap));
      const brIdxMap = new Map();
      brCommits.forEach((c, idx) => brIdxMap.set(c.sha, idx));
      const visBrCommits = brCommits.slice(0, BRANCH_MODAL_ROWS);

      const retiredCutoffSecs =
        newestBranchBuildSecs - RETIRED_BRANCH_BUILDER_DAYS * 86400;
      const brColumns = [];
      for (const entry of releaseEntries) {
        const bList = builderMap[entry.builder];
        if (!bList || bList.length === 0) continue;
        const latestSecs = parseIsoSecs(bList[0] && bList[0].createTime) || 0;
        if (latestSecs < retiredCutoffSecs) continue;
        brColumns.push(
          analyzeBuilderColumn(
            entry,
            bList,
            br,
            brCommits,
            brIdxMap,
            visBrCommits.length,
            nowSecs,
          ),
        );
      }

      const brKind = worstKind(brColumns.map((i) => i.kind));

      // The card strip shows each builder's status on one commit: the newest
      // branch commit that has any build (release builders only trigger on
      // Version.java changes, so the branch tip may have none).
      const buildShaOf = (b) =>
        b && b.input && b.input.gitilesCommit && b.input.gitilesCommit.id;
      const builtShas = new Set();
      for (const col of brColumns) {
        for (const b of builderMap[col.builder] || []) {
          if (b.status !== "CANCELED" && buildShaOf(b)) {
            builtShas.add(buildShaOf(b));
          }
        }
      }
      const stripCommit = brCommits.find((c) => builtShas.has(c.sha));
      const stripSha = stripCommit ? stripCommit.sha : null;
      for (const col of brColumns) {
        const b = (builderMap[col.builder] || []).find(
          (x) => x.status !== "CANCELED" && buildShaOf(x) === stripSha,
        );
        col.stripKind = !b
          ? "none"
          : b.status === "SCHEDULED" || b.status === "STARTED"
            ? "running"
            : statusToCellKind(b.status);
        col.stripBuildUrl = b
          ? b.buildUrl || `${MILO_BUILD_BASE}${encodeURIComponent(b.id)}`
          : col.builderUrl;
      }
      const brFailing = brColumns.filter(
        (i) => i.kind === "red" || i.kind === "infra",
      );
      const brRed = brColumns.filter((i) => i.kind === "red");
      const brInfra = brColumns.filter((i) => i.kind === "infra");
      const brGreen = brColumns.filter((i) => i.kind === "green");
      const brBuilding = brColumns.filter((i) => i.isBuilding);

      const tipSha =
        (brCommits[0] && brCommits[0].sha) ||
        (brColumns[0] && brColumns[0].latestSha) ||
        null;
      const primaryFail =
        brFailing.find((i) => i.latestSha === tipSha) || brFailing[0] || null;

      let branchBlame = primaryFail ? primaryFail.blame : null;
      if (!branchBlame && tipSha) {
        const resolved = resolveBlamelist(brCommits, tipSha, null);
        branchBlame = {
          ...resolved,
          ageText: formatRelativeAge(
            resolved.commitTime || newestBranchBuildSecs,
            nowSecs,
          ),
        };
      }

      branchSummaries.push({
        branch: br,
        kind: brKind,
        totalCount: brColumns.length,
        greenCount: brGreen.length,
        redCount: brRed.length,
        infraCount: brInfra.length,
        buildingCount: brBuilding.length,
        failingItems: brFailing,
        columns: brColumns,
        commits: visBrCommits,
        tipSha: tipSha ? tipSha.slice(0, 8) : "?",
        stripSha,
        cutSha: state.releaseCuts[br] || null,
        blame: branchBlame,
      });
    }

    const releaseKind = worstKind(branchSummaries.map((b) => b.kind));

    return {
      nowSecs,
      tipCommit,
      totalBuildersCount: mainEntries.length + releaseEntries.length,
      mainConsole: {
        kind: mainKind,
        totalCount: mainColumns.length,
        activeCount: activeMainCols.length,
        greenCount: activeMainCols.filter((c) => c.kind === "green").length,
        redCount: mainRed,
        infraCount: mainInfra,
        staleCount: mainStale,
        buildingCount: mainBuilding,
        commits: visibleMainCommits,
        columns: mainColumns,
      },
      presubmit: buildPresubmitTile(presubmitQueues, nowSecs),
      releases: {
        id: "release_branches",
        title: "Release Branches",
        kind: releaseKind,
        branchSummaries,
        totalCount: branchSummaries.length,
        greenCount: branchSummaries.filter((b) => b.kind === "green").length,
        redCount: branchSummaries.filter((b) => b.kind === "red").length,
        infraCount: branchSummaries.filter((b) => b.kind === "infra").length,
        buildingCount: branchSummaries.reduce(
          (acc, b) => acc + b.buildingCount,
          0,
        ),
      },
    };
  }

  async function fetchBuildbucketPayload() {
    if (!state.luciConfig) {
      throw new Error("commits.json (LUCI console config) not loaded");
    }
    const luciCfg = state.luciConfig;

    const ciTargets = [];
    const seenCi = new Set();
    for (const entry of [
      ...(luciCfg.main_console || []),
      ...(luciCfg.release_console || []),
    ]) {
      const key = `${entry.project || "r8"}/${entry.bucket || "ci"}/${entry.builder}`;
      if (seenCi.has(key)) continue;
      seenCi.add(key);
      ciTargets.push(entry);
    }

    const ciFields =
      "builds.*.id,builds.*.number,builds.*.status,builds.*.createTime,builds.*.startTime,builds.*.endTime,builds.*.input.gitilesCommit";
    const ciRequests = ciTargets.map((entry) => ({
      searchBuilds: {
        predicate: {
          builder: {
            project: entry.project || "r8",
            bucket: entry.bucket || "ci",
            builder: entry.builder,
          },
          includeExperimental: false,
        },
        pageSize: 60,
        fields: ciFields,
      },
    }));

    const cqTargets = luciCfg.cq_builders || [];
    const tryFields =
      "builds.*.id,builds.*.number,builds.*.status,builds.*.createTime,builds.*.startTime,builds.*.tags,builds.*.input.gerritChanges";
    const tryQueries = [];
    for (const cqEntry of cqTargets) {
      tryQueries.push({ cqEntry, status: "STARTED" });
      tryQueries.push({ cqEntry, status: "SCHEDULED" });
    }
    const tryRequests = tryQueries.map((q) => ({
      searchBuilds: {
        predicate: {
          builder: {
            project: q.cqEntry.project || "r8",
            bucket: q.cqEntry.bucket || "try",
            builder: q.cqEntry.builder,
          },
          status: q.status,
          includeExperimental: false,
        },
        pageSize: 200,
        fields: tryFields,
      },
    }));

    const batchResp = await prpcCall("buildbucket.v2.Builds", "Batch", {
      requests: [...ciRequests, ...tryRequests],
    });
    const responses = batchResp.responses || [];
    const buildsByBuilder = {};

    for (let i = 0; i < ciTargets.length; i++) {
      const r = responses[i] || {};
      buildsByBuilder[ciTargets[i].builder] =
        (r.searchBuilds && r.searchBuilds.builds) || [];
    }

    // Deep streak lookup if any main builder has 60+ builds with zero SUCCESS in its initial page.
    const deepEntries = (luciCfg.main_console || []).filter((entry) => {
      const list = buildsByBuilder[entry.builder] || [];
      return list.length >= 60 && !list.some((b) => b.status === "SUCCESS");
    });
    if (deepEntries.length > 0) {
      const deepResp = await prpcCall("buildbucket.v2.Builds", "Batch", {
        requests: deepEntries.map((entry) => ({
          searchBuilds: {
            predicate: {
              builder: {
                project: entry.project || "r8",
                bucket: entry.bucket || "ci",
                builder: entry.builder,
              },
              includeExperimental: false,
            },
            pageSize: 200,
            fields: ciFields,
          },
        })),
      });
      const deepList = deepResp.responses || [];
      deepEntries.forEach((entry, idx) => {
        const r = deepList[idx] || {};
        if (r.searchBuilds && r.searchBuilds.builds) {
          buildsByBuilder[entry.builder] = r.searchBuilds.builds;
        }
      });
    }

    const presubmitQueues = cqTargets.map((cqEntry, idx) => {
      const rRun = responses[ciTargets.length + idx * 2] || {};
      const rSched = responses[ciTargets.length + idx * 2 + 1] || {};
      return {
        ...cqEntry,
        runningBuilds: (rRun.searchBuilds && rRun.searchBuilds.builds) || [],
        scheduledBuilds:
          (rSched.searchBuilds && rSched.searchBuilds.builds) || [],
      };
    });

    return { luciCfg, buildsByBuilder, presubmitQueues };
  }

  async function fetchLiveBuildbucketModel() {
    const t0 = performance.now();
    const [bbRes, kokoroRes] = await Promise.allSettled([
      fetchBuildbucketPayload(),
      fetchKokoroStatus(),
    ]);

    const errors = [];
    if (bbRes.status === "fulfilled") {
      state.lastBbPayload = bbRes.value;
    } else {
      errors.push(
        bbRes.reason && bbRes.reason.message
          ? bbRes.reason.message
          : String(bbRes.reason),
      );
    }

    if (kokoroRes.status === "fulfilled") {
      state.lastKokoroRecords = kokoroRes.value;
    } else {
      errors.push(
        kokoroRes.reason && kokoroRes.reason.message
          ? kokoroRes.reason.message
          : String(kokoroRes.reason),
      );
    }

    if (!state.lastBbPayload) {
      throw new Error(errors.join("; ") || "Live CI poll failed");
    }

    state.lastPollDurationMs = Math.round(performance.now() - t0);
    const model = buildModelFromBuildbucket(
      state.lastBbPayload.luciCfg,
      state.lastBbPayload.buildsByBuilder,
      Date.now() / 1000,
      state.lastBbPayload.presubmitQueues,
      state.lastKokoroRecords,
    );
    return {
      model,
      partialError: errors.length > 0 ? errors.join("; ") : null,
    };
  }

  function statusGlyphForKind(kind) {
    switch (kind) {
      case "red":
        return "✗";
      case "infra":
        return "⚠";
      case "stale":
        return "◌";
      default:
        return "✓";
    }
  }

  function renderConsoleTableHtml(commits, allColumns, options = {}) {
    const hidePassing = Boolean(options.hidePassing);
    const cols = hidePassing
      ? allColumns.filter(
          (c) => c.kind === "red" || c.kind === "infra" || c.kind === "stale",
        )
      : allColumns;

    if (cols.length === 0) {
      return `<div style="display:flex;align-items:center;justify-content:center;height:100%;font-size:1rem;font-weight:700;color:var(--green-head-fg);">✓ All ${allColumns.length} builders on this console have 0 failures</div>`;
    }

    // Group contiguous categories for <th colspan="..."> directly from luci-milo.cfg categories
    const catSpans = [];
    for (let i = 0; i < cols.length; i++) {
      const col = cols[i];
      const prev = cols[i - 1];
      const isCatStart = !prev || prev.category !== col.category;
      col._borderClass = isCatStart ? "cat-start" : "";

      if (isCatStart) {
        catSpans.push({ label: col.category, span: 1, startIdx: i });
      } else {
        catSpans[catSpans.length - 1].span += 1;
      }
    }

    const colWeights = computeColumnWeights(cols, catSpans);
    const totalWeight = colWeights.reduce((acc, w) => acc + w, 0) || 1;
    const colgroupHtml =
      `<col class="commit-col">` +
      colWeights
        .map(
          (w) =>
            `<col style="width: ${((w / totalWeight) * 100).toFixed(3)}%">`,
        )
        .join("");

    const catRowHtml =
      `<th class="commit-col-head">Revision · ${commits.length}</th>` +
      catSpans
        .map(
          (cs) =>
            `<th class="cat-start" colspan="${cs.span}">${escapeHtml(cs.label)}</th>`,
        )
        .join("");

    const builderRowHtml =
      `<th class="commit-col-sub">SHA · Author</th>` +
      cols
        .map((c) => {
          const url =
            c.builderUrl || miloBuilderUrl(c.project, c.bucket, c.builder);
          const tip = `${c.builder} — ${c.kind.toUpperCase()}${c.streak > 1 ? ` (×${c.streak})` : ""}${c.latestAgeText ? ` · ${c.latestAgeText}` : ""}`;
          return `<th class="${c._borderClass}">
            <a class="builder-col-chip head-${escapeHtml(c.kind)}" href="${escapeHtml(url)}" target="_blank" rel="noopener" title="${escapeHtml(tip)}" data-hover-info="${escapeHtml(tip)}">${escapeHtml(c.label)}</a>
          </th>`;
        })
        .join("");

    const bodyRowsHtml = commits
      .map((commit, rowIdx) => {
        const shortSha = commit.sha.slice(0, 8);
        const commitUrl = `${GITILES_COMMIT_BASE}${commit.sha}`;
        const commitAge = commit.time ? formatRelativeAge(commit.time) : "";
        const compactAge = commit.time ? formatCompactAge(commit.time) : "";
        const commitTip = `${shortSha} · ${commit.author || ""}${commitAge ? ` (${commitAge})` : ""}${commit.subject ? ` — ${commit.subject}` : ""}`;

        const cellsHtml = cols
          .map((col) => {
            const slot = col.slots[rowIdx] || { type: "empty" };
            if (slot.type === "spanned") {
              return "";
            }
            if (slot.type === "empty") {
              return `<td class="console-td cell-empty ${col._borderClass}"></td>`;
            }
            const buildUrl =
              slot.buildUrl ||
              (slot.buildId
                ? `${MILO_BUILD_BASE}${encodeURIComponent(slot.buildId)}`
                : col.builderUrl ||
                  miloBuilderUrl(col.project, col.bucket, col.builder));
            const batchNote =
              slot.commitCount > 1
                ? ` (${slot.commitCount} commits in batch)`
                : "";
            const hoverText = `${col.builder} · ${slot.status}${slot.buildNumber ? ` #${slot.buildNumber}` : ""} · ${shortSha}${batchNote}${commit.author ? ` · ${commit.author}` : ""}${slot.ageText ? ` · ${slot.ageText}` : ""}`;
            const rowspanAttr = slot.span > 1 ? ` rowspan="${slot.span}"` : "";

            return `<td class="console-td ${col._borderClass}"${rowspanAttr}>
              <a class="cell-pill pill-${escapeHtml(slot.kind)}" href="${escapeHtml(buildUrl)}" target="_blank" rel="noopener" title="${escapeHtml(hoverText)}" data-hover-info="${escapeHtml(hoverText)}"></a>
            </td>`;
          })
          .join("");

        return `<tr class="commit-row">
          <td class="commit-cell" title="${escapeHtml(commitTip)}" data-hover-info="${escapeHtml(commitTip)}">
            <div class="commit-cell-inner">
              <div class="commit-cell-main">
                <a class="commit-row-sha" href="${escapeHtml(commitUrl)}" target="_blank" rel="noopener">${escapeHtml(shortSha)}</a>
                <span class="commit-row-author">${escapeHtml(commit.author || "")}</span>
              </div>
              ${compactAge ? `<span class="commit-row-age">${escapeHtml(compactAge)}</span>` : ""}
            </div>
          </td>
          ${cellsHtml}
        </tr>`;
      })
      .join("");

    return `
      <table class="console-table">
        <colgroup>${colgroupHtml}</colgroup>
        <thead>
          <tr class="cat-row">${catRowHtml}</tr>
          <tr class="builder-row">${builderRowHtml}</tr>
        </thead>
        <tbody>${bodyRowsHtml}</tbody>
      </table>
    `;
  }

  function renderMainConsolePanel(mc) {
    const panelStateClass =
      mc.kind === "red" ? "is-red" : mc.kind === "infra" ? "is-infra" : "";
    return `
      <section class="panel fill-height ${panelStateClass}" id="tile-main-console">
        <div class="console-table-wrap">
          ${renderConsoleTableHtml(mc.commits, mc.columns, { hidePassing: state.hidePassingBuilders })}
        </div>
      </section>
    `;
  }

  function renderPresubmitPanel(ps) {
    const summaryText = `${ps.totalRunning} running · ${ps.totalScheduled} queued`;
    const cardsHtml = ps.rows
      .map((r) => {
        const builderUrl = miloBuilderUrl(r.project, r.bucket, r.builder);
        const runClass = r.runningCount > 0 ? "stat-running" : "";
        const schedClass = r.scheduledCount > 0 ? "stat-scheduled" : "";
        const cleanAge = (s) => (s ? s.replace(/\s*ago$/, "") : "");
        const ageNote = r.oldestRunningAge
          ? `${r.subtitle} · ${cleanAge(r.oldestRunningAge)}`
          : r.oldestScheduledAge
            ? `${r.subtitle} · wait ${cleanAge(r.oldestScheduledAge)}`
            : r.subtitle;

        return `
          <div class="presubmit-card" data-open-presubmit="1" role="button" tabindex="0">
            <div class="presubmit-info">
              <a class="presubmit-name" href="${escapeHtml(builderUrl)}" target="_blank" rel="noopener" onclick="event.stopPropagation()">${escapeHtml(r.displayName)}</a>
              <span class="presubmit-sub">${escapeHtml(ageNote)}</span>
            </div>
            <div class="presubmit-stats">
              <div class="presubmit-stat ${runClass}">
                <span class="presubmit-stat-num">${r.runningCount}</span>
                <span class="presubmit-stat-label">Run</span>
              </div>
              <div class="presubmit-stat ${schedClass}">
                <span class="presubmit-stat-num">${r.scheduledCount}</span>
                <span class="presubmit-stat-label">Queue</span>
              </div>
            </div>
          </div>
        `;
      })
      .join("");

    return `
      <section class="panel hug-height" id="tile-presubmit">
        <header class="panel-header clickable" data-open-presubmit="1">
          <div class="panel-title-group">
            <span class="panel-indicator ind-presubmit" aria-hidden="true">⟳</span>
            <h2 class="panel-title">${escapeHtml(ps.title)}</h2>
            <span class="panel-header-sub">${escapeHtml(ps.subtitle || "try")}</span>
          </div>
          <div class="panel-badges">
            <span class="summary-pill pill-presubmit">${escapeHtml(summaryText)}</span>
          </div>
        </header>
        <div class="presubmit-grid">
          ${cardsHtml}
        </div>
      </section>
    `;
  }

  function renderReleaseBranchesPanel(rel) {
    const glyph = statusGlyphForKind(rel.kind);
    const panelStateClass =
      rel.kind === "red" ? "is-red" : rel.kind === "infra" ? "is-infra" : "";
    const buildingBadge =
      rel.buildingCount > 0
        ? `<span class="building-pill">⟳ ${rel.buildingCount}</span>`
        : "";

    const parts = [];
    if (rel.redCount > 0) parts.push(`${rel.redCount} fail`);
    if (rel.infraCount > 0) parts.push(`${rel.infraCount} infra`);
    if (parts.length === 0)
      parts.push(`${rel.greenCount}/${rel.totalCount} OK`);

    const rowsHtml = rel.branchSummaries
      .map((br) => {
        const tagText =
          br.kind === "red"
            ? br.infraCount > 0
              ? `${br.redCount}F · ${br.infraCount}I`
              : `${br.redCount} FAIL`
            : br.kind === "infra"
              ? `${br.infraCount} INFRA`
              : "ALL OK";

        let failSummaryHtml = "";
        if (br.failingItems.length > 0) {
          const shown = br.failingItems.slice(0, 2).map((i) => i.label);
          const extra = br.failingItems.length - shown.length;
          failSummaryHtml = `<div class="branch-fail-names is-failing" title="${escapeHtml(br.failingItems.map((i) => i.label).join(", "))}">${escapeHtml(shown.join(", "))}${extra > 0 ? ` <span class="branch-fail-more">+${extra}</span>` : ""}</div>`;
        } else {
          failSummaryHtml = `<div class="branch-fail-names is-ok">✓ All ${br.totalCount} passing</div>`;
        }

        const miniStripParts = [];
        let prevCat = null;
        for (const col of br.columns) {
          if (prevCat && prevCat !== col.category) {
            miniStripParts.push('<span class="mini-sep"></span>');
          }
          prevCat = col.category;
          const cellKind = col.stripKind || "none";
          const buildUrl =
            col.stripBuildUrl ||
            col.builderUrl ||
            miloBuilderUrl(col.project, col.bucket, col.builder);
          const tip = `${br.branch} @ ${(br.stripSha || "").slice(0, 8)} · ${col.builder} (${cellKind.toUpperCase()})`;
          miniStripParts.push(
            `<a class="mini-cell mc-${cellKind}" href="${escapeHtml(buildUrl)}" target="_blank" rel="noopener" title="${escapeHtml(tip)}" onclick="event.stopPropagation()"></a>`,
          );
        }

        const b = br.blame;
        const author = b && b.authors && b.authors[0] ? b.authors[0] : "";
        const sha = (b && b.shortSha) || br.tipSha || "?";
        const fullSha = (b && b.fullSha) || sha;
        const ageRaw =
          b && b.failTimeSecs
            ? formatCompactAge(b.failTimeSecs)
            : ((b && b.ageText) || "").replace(/\s*ago$/, "");

        return `
          <div class="branch-card branch-${escapeHtml(br.kind)}" data-open-branch="${escapeHtml(br.branch)}" role="button" tabindex="0" title="Click to open console waterfall for branch ${escapeHtml(br.branch)} (${sha})">
            <div class="branch-card-top">
              <div style="display:flex;align-items:baseline;gap:0.38rem;min-width:0;">
                <span class="branch-version">${escapeHtml(br.branch)}</span>
                <span class="branch-ratio-age">${br.greenCount}/${br.totalCount}</span>
                ${br.cutSha ? `<a class="branch-cut-pill" href="${GITILES_COMMIT_BASE}${escapeHtml(br.cutSha)}" target="_blank" rel="noopener" title="Cut from main at ${escapeHtml(br.cutSha)}" onclick="event.stopPropagation()">${escapeHtml(br.cutSha.slice(0, 6))}</a>` : ""}
              </div>
              <span class="status-tag tag-${escapeHtml(br.kind === "green" ? "green" : br.kind)}">${escapeHtml(tagText)}</span>
            </div>
            ${failSummaryHtml}
            <div class="branch-card-meta">
              <a class="blame-author-link" href="${GITILES_COMMIT_BASE}${escapeHtml(fullSha)}" target="_blank" rel="noopener" onclick="event.stopPropagation()">${escapeHtml(author || sha)}</a>
              ${ageRaw ? `<span class="branch-ratio-age">${escapeHtml(ageRaw)}</span>` : ""}
            </div>
            <div class="branch-mini-console">
              ${miniStripParts.join("")}
            </div>
          </div>
        `;
      })
      .join("");

    return `
      <section class="panel hug-height ${panelStateClass}" id="tile-release-branches">
        <header class="panel-header">
          <div class="panel-title-group">
            <span class="panel-indicator ind-${escapeHtml(rel.kind)}" aria-hidden="true">${glyph}</span>
            <h2 class="panel-title">${escapeHtml(rel.title)}</h2>
            <span class="panel-header-sub">${rel.branchSummaries.length} active (30d)</span>
          </div>
          <div class="panel-badges">
            ${buildingBadge}
            <span class="summary-pill pill-${escapeHtml(rel.kind)}">${escapeHtml(parts.join(" · "))}</span>
          </div>
        </header>
        <div class="branch-cards-grid">
          ${rowsHtml}
        </div>
      </section>
    `;
  }

  function renderDashboard(model) {
    state.currentModel = model;

    const mc = model.mainConsole;
    const rel = model.releases;
    const mainIssues = mc.redCount + mc.infraCount;
    const mainPillKind =
      mc.redCount > 0 ? "red" : mc.infraCount > 0 ? "infra" : "green";
    const overallEl = document.getElementById("overall-status-pill");
    overallEl.className = `overall-pill status-${mainPillKind}`;

    if (mainIssues === 0) {
      overallEl.textContent = "✓ Main: OK";
    } else {
      overallEl.textContent = `${statusGlyphForKind(mainPillKind)} Main: ${mainIssues} issue${mainIssues === 1 ? "" : "s"}`;
    }

    const runPill = document.getElementById("top-running-pill");
    if (runPill) {
      if (mc.buildingCount > 0) {
        runPill.textContent = `⟳ ${mc.buildingCount} running`;
        runPill.style.display = "inline-flex";
      } else {
        runPill.style.display = "none";
      }
    }

    const toggleBtn = document.getElementById("btn-toggle-failing");
    if (toggleBtn) {
      toggleBtn.classList.toggle("is-active", state.hidePassingBuilders);
      toggleBtn.textContent = state.hidePassingBuilders
        ? "✓ Failing only"
        : "Hide passing";
    }

    const failingNames = mc.columns
      .filter((c) => c.kind === "red" || c.kind === "infra")
      .map((c) => `${c.builder}${c.streak > 1 ? ` (×${c.streak})` : ""}`);

    const tipEl = document.getElementById("tip-commit-chip");
    if (tipEl) {
      const tc = model.tipCommit;
      const defaultReadout =
        failingNames.length > 0
          ? `Attention on main: ${failingNames.join(" · ")}`
          : tc
            ? `main ${tc.shortSha} · ${tc.author || ""} — ${tc.subject || ""} (${tc.ageText})`
            : `All ${mc.activeCount} active main builders passing`;
      if (tc) {
        tipEl.href = `${GITILES_COMMIT_BASE}${tc.sha}`;
      }
      tipEl.innerHTML = `<span id="console-hover-readout" class="console-hover-text" data-default-text="${escapeHtml(defaultReadout)}">${escapeHtml(defaultReadout)}</span>`;
    }

    const gridEl = document.getElementById("radiator-grid");
    gridEl.innerHTML = `
      ${renderMainConsolePanel(mc)}
      <div class="bottom-deck">
        ${renderPresubmitPanel(model.presubmit)}
        ${renderReleaseBranchesPanel(rel)}
      </div>
    `;

    updateFreshnessUi();
  }

  function updateFreshnessUi() {
    const dotEl = document.getElementById("poll-pulse-dot");
    const textEl = document.getElementById("poll-status-text");
    const bannerEl = document.getElementById("alert-banner");
    const bannerTextEl = document.getElementById("alert-banner-text");

    if (state.isFetching) {
      dotEl.className = "pulse-dot fetching";
      textEl.textContent = "Polling…";
      return;
    }

    const nowMs = Date.now();
    const ageSecs = state.lastSuccessPollMs
      ? Math.max(0, Math.round((nowMs - state.lastSuccessPollMs) / 1000))
      : null;

    if (state.lastErrorMsg) {
      dotEl.className = "pulse-dot error";
      textEl.textContent =
        ageSecs !== null ? `Error · ${ageSecs}s ago` : "Fetch error";
      if (
        !state.lastSuccessPollMs ||
        nowMs - state.lastSuccessPollMs >= ERROR_BANNER_THRESHOLD_MS
      ) {
        bannerTextEl.textContent = `⚠ Live CI poll degraded (${state.lastErrorMsg}). Showing last known build state.`;
        bannerEl.classList.add("visible");
      }
    } else {
      dotEl.className = "pulse-dot";
      textEl.textContent = ageSecs !== null ? `${ageSecs}s ago` : "Live";
      bannerEl.classList.remove("visible");
    }
  }

  function openBranchConsoleModal(brObj) {
    const backdrop = document.getElementById("detail-modal-backdrop");
    const titleEl = document.getElementById("detail-modal-title");
    const bodyEl = document.getElementById("detail-modal-body");

    titleEl.textContent = `Release Branch ${brObj.branch} Console (${brObj.greenCount}/${brObj.totalCount} builders passing)`;
    bodyEl.innerHTML = `
      <div class="modal-console-box">
        ${renderConsoleTableHtml(brObj.commits, brObj.columns, { hidePassing: false })}
      </div>
    `;
    backdrop.classList.add("open");
  }

  function openPresubmitModal(ps) {
    const backdrop = document.getElementById("detail-modal-backdrop");
    const titleEl = document.getElementById("detail-modal-title");
    const bodyEl = document.getElementById("detail-modal-body");

    titleEl.textContent = `${ps.title} (${ps.totalRunning} running · ${ps.totalScheduled} queued)`;
    const rowsHtml = ps.allItems
      .map((item) => {
        const buildUrl = `${MILO_BUILD_BASE}${encodeURIComponent(item.id)}`;
        const builderUrl = miloBuilderUrl(
          item.project,
          item.bucket,
          item.builder,
        );
        const statusBadge =
          item.status === "STARTED"
            ? `<span class="building-pill">⟳ RUNNING</span>`
            : `<span class="summary-pill pill-presubmit">QUEUED</span>`;
        const clCell = item.clUrl
          ? `<a class="blame-sha" href="${escapeHtml(item.clUrl)}" target="_blank" rel="noopener">${escapeHtml(item.clLabel)}</a>`
          : escapeHtml(item.clLabel);
        return `
          <tr>
            <td>${statusBadge}</td>
            <td><a href="${escapeHtml(builderUrl)}" target="_blank" rel="noopener" style="font-weight:700;text-decoration:underline">${escapeHtml(item.builder)}</a></td>
            <td>${clCell}</td>
            <td class="blame-author">${escapeHtml(item.owner)}</td>
            <td><a href="${escapeHtml(buildUrl)}" target="_blank" rel="noopener" style="text-decoration:underline">${escapeHtml(item.ageText)}</a></td>
          </tr>
        `;
      })
      .join("");

    bodyEl.innerHTML = `
      <table class="modal-table">
        <thead>
          <tr>
            <th>Status</th>
            <th>Builder</th>
            <th>Gerrit CL</th>
            <th>Owner</th>
            <th>Duration / Queued</th>
          </tr>
        </thead>
        <tbody>${rowsHtml || '<tr><td colspan="5">No active presubmit jobs.</td></tr>'}</tbody>
      </table>
    `;
    backdrop.classList.add("open");
  }

  async function refreshData() {
    if (state.isFetching) return;
    state.isFetching = true;
    state.lastAttemptPollMs = Date.now();
    updateFreshnessUi();

    try {
      await loadCommitsAndVersion();
      const { model, partialError } = await fetchLiveBuildbucketModel();
      if (!partialError) {
        state.lastSuccessPollMs = Date.now();
        state.lastErrorMsg = null;
      } else {
        state.lastErrorMsg = partialError;
      }
      state.isFetching = false;
      renderDashboard(model);
    } catch (err) {
      state.isFetching = false;
      state.lastErrorMsg = err && err.message ? err.message : String(err);
      updateFreshnessUi();
    }
  }

  function bindEvents() {
    const refreshBtn = document.getElementById("btn-refresh-now");
    if (refreshBtn) {
      refreshBtn.addEventListener("click", () => refreshData());
    }

    const toggleFailingBtn = document.getElementById("btn-toggle-failing");
    if (toggleFailingBtn) {
      toggleFailingBtn.addEventListener("click", () => {
        state.hidePassingBuilders = !state.hidePassingBuilders;
        if (state.currentModel) renderDashboard(state.currentModel);
      });
    }

    const gridEl = document.getElementById("radiator-grid");
    gridEl.addEventListener("click", (e) => {
      const branchRow = e.target.closest("[data-open-branch]");
      if (branchRow && state.currentModel) {
        const brName = branchRow.getAttribute("data-open-branch");
        const brObj = state.currentModel.releases.branchSummaries.find(
          (b) => b.branch === brName,
        );
        if (brObj) {
          openBranchConsoleModal(brObj);
          return;
        }
      }

      if (e.target.closest("[data-open-presubmit]") && state.currentModel) {
        openPresubmitModal(state.currentModel.presubmit);
      }
    });

    gridEl.addEventListener("mouseover", (e) => {
      const hoverEl = e.target.closest("[data-hover-info]");
      const readout = document.getElementById("console-hover-readout");
      if (hoverEl && readout) {
        readout.textContent = hoverEl.getAttribute("data-hover-info");
      }
    });

    gridEl.addEventListener("mouseleave", () => {
      const readout = document.getElementById("console-hover-readout");
      if (readout && readout.dataset.defaultText) {
        readout.textContent = readout.dataset.defaultText;
      }
    });

    const backdrop = document.getElementById("detail-modal-backdrop");
    const closeBtn = document.getElementById("detail-modal-close");
    if (closeBtn) {
      closeBtn.addEventListener("click", () =>
        backdrop.classList.remove("open"),
      );
    }
    if (backdrop) {
      backdrop.addEventListener("click", (e) => {
        if (e.target === backdrop) backdrop.classList.remove("open");
      });
    }
    window.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && backdrop) {
        backdrop.classList.remove("open");
      }
    });
  }

  async function init() {
    bindEvents();
    await loadCommitsAndVersion();
    await refreshData();

    setInterval(refreshData, POLL_INTERVAL_MS);
    setInterval(loadCommitsAndVersion, VERSION_CHECK_MS);
    setInterval(updateFreshnessUi, 1000);
    setTimeout(() => window.location.reload(), SAFETY_RELOAD_MS);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
