// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

/**
 * R8 Configuration Analyzer — platform-wide dashboard.
 *
 * Reads a JSON array of base64-encoded KeepRadiusSummary protos from
 * <script id="keepradius-data">, decodes them with a hand-rolled
 * wire-format reader, and renders three views: System Health, Builds, and
 * Global Keep Rules.
 *
 * Sections:
 *   1. Protobuf wire-format decoder (class Reader & decoders)
 *   2. Numeric + DOM helpers
 *   3. Data loading and platform aggregation
 *   4. Application state and router
 *   5. View renderers (header, system, builds, rules)
 *   6. Event delegation
 *   7. Boot
 */
(function () {
  "use strict";

  /* ==========================================================================
     1. PROTOBUF SCHEMA INITIALIZATION
     ========================================================================== */

  const protoSchema = document.getElementById("keepradius-proto").textContent;
  const root = protobuf.parse(protoSchema, { keepCase: true }).root;
  const KeepRadiusSummary = root.lookupType(
    "com.android.tools.r8.keepradius.proto.KeepRadiusSummary",
  );

  /**
   * Returns a default empty KeepInfo struct.
   * @returns {{item_count: number, no_obfuscation_count: number, no_optimization_count: number, no_shrinking_count: number}}
   */
  const emptyKeepInfo = () => ({
    item_count: 0,
    no_obfuscation_count: 0,
    no_optimization_count: 0,
    no_shrinking_count: 0,
  });

  /**
   * Decodes a KeepRadiusSummary proto payload using protobuf.js.
   * @param {Uint8Array} buf
   * @returns {object}
   */
  const decodeSummary = (buf) => {
    const message = KeepRadiusSummary.decode(buf);
    return KeepRadiusSummary.toObject(message, {
      defaults: true,
      arrays: true,
      objects: true,
      oneofs: true,
      keepCase: true,
    });
  };

  /**
   * Converts a base64-encoded string to a Uint8Array byte buffer.
   * @param {string} b64
   * @returns {Uint8Array}
   */
  const base64ToBytes = (b64) => {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  };

  /* ==========================================================================
     2. NUMERIC + DOM HELPERS
     ========================================================================== */

  /**
   * Locale-format an integer, treating null/undefined as 0.
   * @param {number} n
   * @returns {string}
   */
  const fmt = (n) => (n || 0).toLocaleString("en-US");

  /**
   * Calculate percentage (part / total) clamped to [0, 100].
   * @param {number} part
   * @param {number} total
   * @returns {number}
   */
  const pctNum = (part, total) => {
    if (!total) return 0;
    return Math.max(0, Math.min(100, (part / total) * 100));
  };

  /**
   * Render percentage as string (e.g. "82.3%").
   * @param {number} part
   * @param {number} total
   * @returns {string}
   */
  const pctStr = (part, total) => `${pctNum(part, total).toFixed(1)}%`;

  /**
   * Escape user-supplied strings before interpolating into HTML.
   * @param {string} value
   * @returns {string}
   */
  const escapeHtml = (value) =>
    String(value).replace(
      /[&<>"']/g,
      (ch) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[ch],
    );

  /**
   * Pick the CSS color token for a health percentage.
   * @param {number} percent
   * @returns {string}
   */
  const severityColor = (percent) => {
    if (percent >= 90) return "var(--green)";
    if (percent >= 75) return "var(--yellow)";
    if (percent >= 50) return "var(--orange)";
    return "var(--red)";
  };

  /**
   * Sum kept, un-optimized, un-shrunk, and un-obfuscated items across the three facets.
   * @param {object} summary
   * @returns {{kept: number, noOpt: number, noShr: number, noObf: number, total: number}}
   */
  const buildKeptInfo = (summary) => {
    const globalRules = summary.global_keep_rule_keep_radius || [];
    const hasGlobalDontOptimize = globalRules.some(
      (r) => r.source === "-dontoptimize",
    );
    const hasGlobalDontShrink = globalRules.some(
      (r) => r.source === "-dontshrink",
    );

    const getFacetInfo = (facetKey, facetSrc, totalCount) => {
      const src = facetSrc || emptyKeepInfo();
      const kept = hasGlobalDontShrink ? totalCount : src.item_count || 0;
      return {
        kept: kept,
        noOpt: hasGlobalDontOptimize ? kept : src.no_optimization_count || 0,
        noShr: hasGlobalDontShrink ? totalCount : src.no_shrinking_count || 0,
        noObf: src.no_obfuscation_count || 0,
        total: totalCount,
      };
    };

    const classes = getFacetInfo(
      "classes",
      summary.kept_classes,
      summary.live_class_count || 0,
    );
    const fields = getFacetInfo(
      "fields",
      summary.kept_fields,
      summary.live_field_count || 0,
    );
    const methods = getFacetInfo(
      "methods",
      summary.kept_methods,
      summary.live_method_count || 0,
    );

    const declaredTotal = classes.total + fields.total + methods.total;

    return {
      kept: classes.kept + fields.kept + methods.kept,
      noOpt: classes.noOpt + fields.noOpt + methods.noOpt,
      noShr: classes.noShr + fields.noShr + methods.noShr,
      noObf: classes.noObf + fields.noObf + methods.noObf,
      total: declaredTotal,
      facets: { classes, fields, methods },
    };
  };

  /**
   * Return target detail page link, replacing extension if needed.
   * @param {object} summary
   * @returns {string}
   */
  const buildLink = (summary) =>
    summary.link || summary.name.replace(".pb", ".html");

  const hasGlobalRule = (summary, ruleName) => {
    const globalRules = summary.global_keep_rule_keep_radius || [];
    return globalRules.some((r) => r.source === ruleName);
  };

  /* ==========================================================================
     3. DATA LOADING + PLATFORM AGGREGATION
     ========================================================================== */

  /**
   * Load and enrich summaries with pre-calculated metadata for performance.
   * @returns {object[]}
   */
  const loadSummaries = () => {
    const dataEl = document.getElementById("keepradius-data");
    if (!dataEl) return [];
    let encoded;
    try {
      encoded = JSON.parse(dataEl.textContent.trim());
    } catch (err) {
      console.error("Failed to parse summaries JSON payload:", err);
      return [];
    }
    if (!Array.isArray(encoded)) return [];
    const decodedSummaries = [];
    for (const item of encoded) {
      try {
        const decoded = decodeSummary(base64ToBytes(item));

        // Pre-calculate expensive details once at load time
        const info = buildKeptInfo(decoded);

        // Combine regular and global rules for sorting
        const rules = (decoded.keep_rule_keep_radius || []).map((r) => ({
          source: r.source,
          item_count: r.item_count,
        }));

        const globalRules = decoded.global_keep_rule_keep_radius || [];
        if (globalRules.length > 0) {
          const liveTotal =
            (decoded.live_class_count || 0) +
            (decoded.live_field_count || 0) +
            (decoded.live_method_count || 0);
          for (const gr of globalRules) {
            rules.push({
              source: gr.source,
              item_count: liveTotal,
            });
          }
        }

        const worstRules = rules
          .sort((a, b) => (b.item_count || 0) - (a.item_count || 0))
          .slice(0, 10);

        decodedSummaries.push({
          ...decoded,
          nameLower: decoded.name.toLowerCase(),
          calculatedInfo: info,
          worstRules: worstRules,
        });
      } catch (err) {
        console.error("Failed to decode protobuf entry:", err);
      }
    }
    return decodedSummaries;
  };

  const computePlatformTotals = (summaries) => {
    const totals = {
      total: 0,
      kept: 0,
      noOpt: 0,
      noShr: 0,
      noObf: 0,
      buildCount: summaries.length,
      facets: {
        classes: { total: 0, kept: 0, noOpt: 0, noShr: 0, noObf: 0 },
        fields: { total: 0, kept: 0, noOpt: 0, noShr: 0, noObf: 0 },
        methods: { total: 0, kept: 0, noOpt: 0, noShr: 0, noObf: 0 },
      },
    };
    for (const summary of summaries) {
      const info = summary.calculatedInfo;
      totals.total += info.total;
      totals.kept += info.kept;
      totals.noOpt += info.noOpt;
      totals.noShr += info.noShr;
      totals.noObf += info.noObf;

      for (const key of ["classes", "fields", "methods"]) {
        const bucket = totals.facets[key];
        const src = info.facets[key];
        bucket.total += src.total;
        bucket.kept += src.kept;
        bucket.noOpt += src.noOpt;
        bucket.noShr += src.noShr;
        bucket.noObf += src.noObf;
      }
    }
    return totals;
  };

  /** Aggregate keep rules by exact source string across all builds. */
  const computeGlobalRules = (summaries) => {
    const bySource = new Map();
    for (const summary of summaries) {
      const seenInBuild = new Set();

      const addRule = (source, itemCount, isGlobal) => {
        let entry = bySource.get(source);
        if (!entry) {
          entry = {
            source: source,
            sourceLower: source.toLowerCase(),
            total: 0,
            builds: [],
            buildsLower: [],
            isGlobal: isGlobal,
          };
          bySource.set(source, entry);
        }
        if (isGlobal) {
          entry.isGlobal = true;
        }
        entry.total += itemCount;
        if (!seenInBuild.has(source)) {
          entry.builds.push(summary.name);
          entry.buildsLower.push(
            summary.nameLower || summary.name.toLowerCase(),
          );
          seenInBuild.add(source);
        }
      };

      // 1. Regular keep rules
      const rules = summary.keep_rule_keep_radius || [];
      for (const rule of rules) {
        if (rule && rule.source) {
          addRule(rule.source, rule.item_count || 0, false);
        }
      }

      // 2. Global keep rules (implicitly apply to all live items)
      const globalRules = summary.global_keep_rule_keep_radius || [];
      if (globalRules.length > 0) {
        const liveTotal =
          (summary.live_class_count || 0) +
          (summary.live_field_count || 0) +
          (summary.live_method_count || 0);
        for (const rule of globalRules) {
          if (rule && rule.source) {
            addRule(rule.source, liveTotal, true);
          }
        }
      }
    }
    return Array.from(bySource.values());
  };

  /** Pre-count builds above each size threshold for the Builds sub-toolbar. */
  const computeSizeFloorCounts = (summaries) => {
    const counts = { 0: summaries.length, 1000: 0, 10000: 0, 100000: 0 };
    for (const summary of summaries) {
      const kept = summary.calculatedInfo.kept;
      if (kept >= 1000) counts[1000]++;
      if (kept >= 10000) counts[10000]++;
      if (kept >= 100000) counts[100000]++;
    }
    return counts;
  };

  const rawSummaries = loadSummaries();
  const testAppBuildsCount = rawSummaries.filter((s) =>
    s.name.includes("TestApp"),
  ).length;
  let summaries = [];
  let totals = { facets: { classes: {}, fields: {}, methods: {} } };
  let globalRules = [];
  let sizeFloorCounts = {};
  let ruleSumAll = 0;
  let multiBuildRuleCount = 0;
  let unusedRuleCount = 0;
  let rulesForRulesView = [];
  let rulesViewRuleSumAll = 0;
  let rulesViewMultiBuildRuleCount = 0;
  let rulesViewUnusedRuleCount = 0;
  let dontOptBuildsCount = 0;
  let dontShrBuildsCount = 0;
  let optimizationPct = 0;
  let shrinkingPct = 0;

  /* ==========================================================================
     4. APPLICATION STATE + ROUTER
     ========================================================================== */

  const VIEWS = { SYSTEM: "system", BUILDS: "builds", RULES: "rules" };
  const SIZE_FLOORS = [
    { id: 0, label: "All sizes" },
    { id: 1000, label: "≥ 1k kept" },
    { id: 10000, label: "≥ 10k kept" },
    { id: 100000, label: "≥ 100k kept" },
  ];
  const RULE_LENSES = [
    { id: "all", label: "All rules" },
    { id: "critical", label: "Critical" },
    { id: "warning", label: "Warning" },
    { id: "minor", label: "Minor" },
  ];

  const initialView = () => {
    const hash = location.hash.replace("#", "");
    return hash === VIEWS.BUILDS || hash === VIEWS.RULES ? hash : VIEWS.SYSTEM;
  };

  const state = {
    view: initialView(),
    query: "",
    sizeFloor: 0,
    buildSort: { key: "kept", desc: true },
    ruleLens: "all",
    multiBuildOnly: false,
    onlyUnused: false,
    excludeDontOptimize: false,
    excludeDontShrink: false,
    ruleSort: { key: "total", desc: true },
    expandedBuilds: new Set(),
    expandedRuleBuildLists: new Set(),
    excludeTestApp: false,
  };

  const updateDerivedState = () => {
    summaries = state.excludeTestApp
      ? rawSummaries.filter((s) => !s.name.includes("TestApp"))
      : rawSummaries;

    totals = computePlatformTotals(summaries);
    globalRules = computeGlobalRules(summaries);
    sizeFloorCounts = computeSizeFloorCounts(summaries);
    ruleSumAll = globalRules.reduce((acc, r) => acc + r.total, 0);
    multiBuildRuleCount = globalRules.filter(
      (r) => r.builds.length >= 2,
    ).length;
    unusedRuleCount = globalRules.filter((r) => r.total === 0).length;

    dontOptBuildsCount = summaries.filter((s) =>
      hasGlobalRule(s, "-dontoptimize"),
    ).length;
    dontShrBuildsCount = summaries.filter((s) =>
      hasGlobalRule(s, "-dontshrink"),
    ).length;

    optimizationPct = pctNum(totals.kept - totals.noOpt, totals.kept);
    shrinkingPct = pctNum(totals.total - totals.noShr, totals.total);

    updateRulesViewData();
  };

  const updateRulesViewData = () => {
    const activeSummaries = summaries.filter((s) => {
      if (state.excludeDontOptimize && hasGlobalRule(s, "-dontoptimize"))
        return false;
      if (state.excludeDontShrink && hasGlobalRule(s, "-dontshrink"))
        return false;
      return true;
    });
    rulesForRulesView = computeGlobalRules(activeSummaries);
    rulesViewRuleSumAll = rulesForRulesView.reduce(
      (acc, r) => acc + r.total,
      0,
    );
    rulesViewMultiBuildRuleCount = rulesForRulesView.filter(
      (r) => r.builds.length >= 2,
    ).length;
    rulesViewUnusedRuleCount = rulesForRulesView.filter(
      (r) => r.total === 0,
    ).length;
  };

  const setState = (patch) => {
    Object.assign(state, patch);
    if ("excludeTestApp" in patch) {
      updateDerivedState();
    } else if ("excludeDontOptimize" in patch || "excludeDontShrink" in patch) {
      updateRulesViewData();
    }
    render();
  };

  /**
   * Router endpoint: simply sets URL hash, allowing hashchange to drive state changes.
   * @param {string} view
   */
  const goToView = (view) => {
    location.hash = view;
  };

  /**
   * Unified route transition controller.
   */
  const handleRouting = () => {
    const hash = location.hash.replace("#", "");
    const nextView =
      hash === VIEWS.BUILDS || hash === VIEWS.RULES ? hash : VIEWS.SYSTEM;
    if (nextView !== state.view) {
      state.query = ""; // Reset search query on view changes
      state.view = nextView;
    }
    render();
  };

  window.addEventListener("hashchange", handleRouting);

  /* ==========================================================================
     5. VIEW RENDERERS
     ========================================================================== */

  // ---- Header (top stats + tabs + view-specific sub-toolbar) ----

  const renderHeader = () => {
    const tabs = [
      { id: VIEWS.SYSTEM, label: "System Health" },
      { id: VIEWS.BUILDS, label: "Builds" },
      { id: VIEWS.RULES, label: "Global Keep Rules" },
    ];
    const tabsHtml = tabs
      .map(
        (tab) => `
  <button class="segment-btn ${state.view === tab.id ? "active" : ""}" 
          data-action="view" 
          data-value="${tab.id}"
          role="tab"
          aria-selected="${state.view === tab.id}">
    ${tab.label}
  </button>
`,
      )
      .join("");

    return `
  <header class="report-header">
    <div class="header-top-row">
      <div class="header-branding">
        <h1 class="header-title">R8 Configuration Analyzer</h1>
        <span class="header-subtitle">${fmt(totals.buildCount)} builds across the platform</span>
      </div>
      <div class="header-stats">
        ${statCell("Optimization", optimizationPct)}
        ${statCell("Shrinking", shrinkingPct)}
      </div>
    </div>
    <div class="header-toolbar">
      <div class="header-toolbar-inner">
        <div class="segmented-control" role="tablist">${tabsHtml}</div>
        <label class="toggle-control ${state.excludeTestApp ? "active" : ""}">
          <input type="checkbox" data-action="exclude-testapp" ${state.excludeTestApp ? "checked" : ""} /> 
          Exclude TestApp builds <span class="count">(${fmt(testAppBuildsCount)})</span>
        </label>
      </div>
    </div>
    ${renderSubToolbar()}
  </header>
`;
  };

  const statCell = (label, percent) => `
<div class="stat-item">
  <span class="stat-label">${label}</span>
  <span class="stat-value" style="color: ${severityColor(percent)}">${percent.toFixed(1)}%</span>
</div>
`;

  const renderSubToolbar = () => {
    if (state.view === VIEWS.BUILDS) return renderBuildsSubToolbar();
    if (state.view === VIEWS.RULES) return renderRulesSubToolbar();
    return "";
  };

  const renderBuildsSubToolbar = () => {
    const floorsHtml = SIZE_FLOORS.map(
      (floor) => `
  <button class="segment-btn ${state.sizeFloor === floor.id ? "active" : ""}" 
          data-action="sizefloor" 
          data-value="${floor.id}"
          aria-pressed="${state.sizeFloor === floor.id}">
    ${floor.label}
    <span class="segment-count">${fmt(sizeFloorCounts[floor.id])}</span>
  </button>
`,
    ).join("");
    return `
  <div class="header-subtoolbar">
    <div class="header-subtoolbar-inner">
      <span class="subtoolbar-label">Size</span>
      <div class="segmented-control">${floorsHtml}</div>
      <div class="subtoolbar-spacer"></div>
      ${renderSearchInput("Search builds…")}
    </div>
  </div>
`;
  };

  const renderRulesSubToolbar = () => {
    const lensHtml = RULE_LENSES.map(
      (lens) => `
  <button class="segment-btn ${state.ruleLens === lens.id ? "active" : ""}" 
          data-action="rulelens" 
          data-value="${lens.id}"
          aria-pressed="${state.ruleLens === lens.id}">
    ${lens.label}
  </button>
`,
    ).join("");
    return `
  <div class="header-subtoolbar">
    <div class="header-subtoolbar-inner">
      <div class="segmented-control">${lensHtml}</div>
      <label class="toggle-control ${state.multiBuildOnly ? "active" : ""}">
        <input type="checkbox" data-action="multibuild" ${state.multiBuildOnly ? "checked" : ""} /> 
        Multi-build only 
        <span class="count">(${fmt(rulesViewMultiBuildRuleCount)})</span>
      </label>
      <label class="toggle-control ${state.onlyUnused ? "active" : ""}">
        <input type="checkbox" data-action="onlyunused" ${state.onlyUnused ? "checked" : ""} /> 
        Only unused 
        <span class="count">(${fmt(rulesViewUnusedRuleCount)})</span>
      </label>
      <label class="toggle-control ${state.excludeDontOptimize ? "active" : ""}">
        <input type="checkbox" data-action="exclude-dontoptimize" ${state.excludeDontOptimize ? "checked" : ""} /> 
        Exclude -dontoptimize <span class="count">(${fmt(dontOptBuildsCount)})</span>
      </label>
      <label class="toggle-control ${state.excludeDontShrink ? "active" : ""}">
        <input type="checkbox" data-action="exclude-dontshrink" ${state.excludeDontShrink ? "checked" : ""} /> 
        Exclude -dontshrink <span class="count">(${fmt(dontShrBuildsCount)})</span>
      </label>
      <div class="subtoolbar-spacer"></div>
      ${renderSearchInput("Search rules or builds…")}
    </div>
  </div>
`;
  };

  const renderSearchInput = (placeholder) => `
<div class="search-bar">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
  </svg>
  <input type="text" data-action="query" value="${escapeHtml(state.query)}" placeholder="${placeholder}" aria-label="${placeholder}" />
</div>
`;

  // ---- System view ----

  const renderSystemView = () => {
    const facets = totals.facets;
    const optOffenders = topOffenders(summaries, "noOpt", 5);
    const shrOffenders = topOffenders(summaries, "noShr", 5);
    const topGlobalRules = globalRules
      .filter((r) => r.isGlobal)
      .sort((a, b) => b.total - a.total)
      .slice(0, 5);
    const topRegularRules = globalRules
      .filter((r) => !r.isGlobal)
      .sort((a, b) => b.total - a.total)
      .slice(0, 5);

    return `
  <div class="stack">
    ${renderPlatformTotalsCard()}
    ${renderFacetBreakdownCard(facets)}
    ${renderOffendersSection(optOffenders, shrOffenders)}
    <section class="rules-grid">
      ${renderTopRulesCard(topGlobalRules, "Most expensive global keep rules")}
      ${renderTopRulesCard(topRegularRules, "Most expensive regular keep rules")}
    </section>
    ${renderScoringPolicyCard()}
  </div>
`;
  };

  const topOffenders = (summaries, key, limit) =>
    summaries
      .map((s) => ({ summary: s, value: s.calculatedInfo[key] }))
      .filter((entry) => entry.value > 0)
      .sort((a, b) => b.value - a.value)
      .slice(0, limit);

  const renderPlatformTotalsCard = () => `
<section class="card">
  <div class="card-row">
    <span class="section-label">Platform totals</span>
    <span class="muted">Aggregated across ${fmt(totals.buildCount)} builds</span>
  </div>
  <div class="totals-grid">
    ${renderTotalCell("Total items", fmt(totals.total), "classes + fields + methods")}
    ${renderTotalCell("Un-optimized items", fmt(totals.noOpt), pctStr(totals.noOpt, totals.kept) + " of kept", "var(--orange)")}
    ${renderTotalCell("Un-shrunk items", fmt(totals.noShr), pctStr(totals.noShr, totals.total) + " of total", "var(--red)")}
  </div>
</section>
`;

  const renderTotalCell = (label, value, sub, tone) => `
<div class="total-cell">
  <div class="total-label">${label}</div>
  <div class="total-value" ${tone ? `style="color: ${tone}"` : ""}>${value}</div>
  ${sub ? `<div class="total-sub">${sub}</div>` : ""}
</div>
`;

  const renderFacetBreakdownCard = (facets) => `
<section class="card">
  <div class="axis-card-header">
    <span class="section-label">R8 optimization levels by facet</span>
    <span class="tooltip" title="Health % per axis broken out by classes, fields, and methods.">?</span>
  </div>
  <div class="axis-grid">
    <div class="axis-cell">
      ${renderAxisPanel(
        "Optimization",
        optimizationPct,
        `${fmt(totals.kept - totals.noOpt)} / ${fmt(totals.kept)} kept items free of -dontoptimize`,
        pctNum(facets.classes.kept - facets.classes.noOpt, facets.classes.kept),
        pctNum(facets.fields.kept - facets.fields.noOpt, facets.fields.kept),
        pctNum(facets.methods.kept - facets.methods.noOpt, facets.methods.kept),
      )}
    </div>
    <div class="axis-cell">
      ${renderAxisPanel(
        "Shrinking",
        shrinkingPct,
        `${fmt(totals.total - totals.noShr)} / ${fmt(totals.total)} items free of -dontshrink`,
        pctNum(
          facets.classes.total - facets.classes.noShr,
          facets.classes.total,
        ),
        pctNum(facets.fields.total - facets.fields.noShr, facets.fields.total),
        pctNum(
          facets.methods.total - facets.methods.noShr,
          facets.methods.total,
        ),
      )}
    </div>
  </div>
</section>
`;

  const renderAxisPanel = (
    label,
    percent,
    detail,
    classesPct,
    fieldsPct,
    methodsPct,
  ) => `
<div class="axis-header">
  <div class="axis-title-block">
    <div class="axis-label">${label}</div>
    ${detail ? `<div class="axis-detail">${detail}</div>` : ""}
  </div>
  <div class="axis-pct" style="color: ${severityColor(percent)}">${percent.toFixed(1)}%</div>
</div>
<div class="facet-rows">
  ${renderFacetBar("Classes", classesPct)}
  ${renderFacetBar("Fields", fieldsPct)}
  ${renderFacetBar("Methods", methodsPct)}
</div>
`;

  const renderFacetBar = (name, percent) => `
<div class="facet-row">
  <div class="facet-name">${name}</div>
  <div class="facet-bar">
    <div class="facet-bar-fill" style="width: ${Math.min(100, Math.max(0, percent))}%; background: ${severityColor(percent)}"></div>
  </div>
  <div class="facet-pct">${percent.toFixed(1)}%</div>
</div>
`;

  const renderOffendersSection = (optOffenders, shrOffenders) => `
<section class="offenders-grid">
  ${renderOffenderCard("Dragging optimization down", "var(--orange)", optOffenders, "see-opt", "un-optimized", "See all un-optimized →")}
  ${renderOffenderCard("Dragging shrinking down", "var(--red)", shrOffenders, "see-shr", "un-shrunk", "See all un-shrunk →")}
</section>
`;

  const renderOffenderCard = (
    title,
    tone,
    rows,
    action,
    metricLabel,
    allLabel,
  ) => {
    const rowsHtml =
      rows.length === 0
        ? '<div class="empty-inline">None.</div>'
        : rows
            .map(
              (entry) => `
      <div class="offender-row">
        <a class="offender-link" href="${escapeHtml(buildLink(entry.summary))}" title="${escapeHtml(entry.summary.name)}">
          ${escapeHtml(entry.summary.name)}
        </a>
        <div class="offender-metric" style="color: ${tone}">
          ${fmt(entry.value)} <span class="label">${metricLabel}</span>
        </div>
      </div>
    `,
            )
            .join("");
    return `
  <div class="card">
    <div class="offender-head">
      <span class="section-label">${title}</span>
      <button class="link-btn" data-action="${action}">${allLabel}</button>
    </div>
    ${rowsHtml}
  </div>
`;
  };

  const renderTopRulesCard = (topRules, title) => {
    const rowsHtml =
      topRules.length === 0
        ? '<div class="empty-inline">No keep rules recorded.</div>'
        : topRules
            .map((rule) => {
              const share = ruleSumAll ? (rule.total / ruleSumAll) * 100 : 0;
              return `
        <div class="sys-rules-row">
          <code class="sys-rule-src" title="${escapeHtml(rule.source)}">${escapeHtml(rule.source)}</code>
          <div class="sys-rule-builds">${fmt(rule.builds.length)} builds</div>
          <div class="sys-rule-total">
            ${fmt(rule.total)} <span class="share">(${share.toFixed(1)}%)</span>
          </div>
        </div>
      `;
            })
            .join("");
    return `
  <section class="card">
    <div class="offender-head">
      <span class="section-label">${escapeHtml(title)}</span>
      <button class="link-btn" data-action="see-rules">See all rules →</button>
    </div>
    ${rowsHtml}
  </section>
`;
  };

  const renderScoringPolicyCard = () => `
<section class="scoring-policy">
  <span class="tooltip" title="Scoring policy">i</span>
  <p>
    Health, build ranking, and rule ranking are based strictly on 
    <strong>optimization</strong> and <strong>shrinking</strong>. 
    Obfuscation is intentionally excluded from platform scoring.
  </p>
</section>
`;

  // ---- Builds view ----

  const renderBuildsView = () => {
    const filtered = filterBuilds(summaries, state.query, state.sizeFloor);
    if (!filtered.length) return '<div class="empty">No builds match.</div>';

    const sorted = sortBuilds(filtered, state.buildSort);
    const top10Share = computeTop10KeptShare(sorted);
    const rowsHtml = sorted.map(renderBuildRow).join("");

    return `
  <div class="stack-sm">
    <div class="view-intro">
      <div class="primary">${fmt(filtered.length)} builds</div>
      <div class="secondary">
        Ranked by absolute kept items. Top 10 hold 
        <strong>${top10Share.toFixed(1)}%</strong> of all kept items across the platform.
      </div>
    </div>
    <div class="card" role="table" aria-label="Builds">
      <div class="builds-head" role="row">
        <div role="columnheader" aria-label="Expand"></div>
        ${renderSortHeader("Build", "name", state.buildSort, "left")}
        ${renderSortHeader("Kept", "kept", state.buildSort)}
        ${renderSortHeader("Classes", "classes", state.buildSort)}
        ${renderSortHeader("Fields", "fields", state.buildSort)}
        ${renderSortHeader("Methods", "methods", state.buildSort)}
        <div style="text-align: right" role="columnheader">Blocked</div>
      </div>
      ${rowsHtml}
    </div>
  </div>
`;
  };

  const filterBuilds = (summaries, query, sizeFloor) => {
    const needle = query.trim().toLowerCase();
    return summaries.filter((summary) => {
      if (needle && summary.nameLower.indexOf(needle) === -1) return false;
      if (sizeFloor > 0) {
        if (summary.calculatedInfo.kept < sizeFloor) return false;
      }
      return true;
    });
  };

  const sortBuilds = (builds, sort) => {
    const value = (summary) => {
      switch (sort.key) {
        case "name":
          return summary.nameLower;
        case "kept":
          return summary.calculatedInfo.kept;
        case "classes":
          return summary.kept_classes?.item_count || 0;
        case "fields":
          return summary.kept_fields?.item_count || 0;
        case "methods":
          return summary.kept_methods?.item_count || 0;
        case "noOpt":
          return summary.calculatedInfo.noOpt;
        case "noShr":
          return summary.calculatedInfo.noShr;
        default:
          return 0;
      }
    };
    return builds.slice().sort((a, b) => {
      const va = value(a);
      const vb = value(b);
      const cmp = typeof va === "string" ? va.localeCompare(vb) : va - vb;
      return sort.desc ? -cmp : cmp;
    });
  };

  const computeTop10KeptShare = (sortedBuilds) => {
    const totalVisible = sortedBuilds.reduce(
      (acc, summary) => acc + summary.calculatedInfo.kept,
      0,
    );
    if (!totalVisible) return 0;
    const top10 = sortedBuilds
      .slice(0, 10)
      .reduce((acc, summary) => acc + summary.calculatedInfo.kept, 0);
    return (top10 / totalVisible) * 100;
  };

  const renderBuildRow = (summary) => {
    const info = summary.calculatedInfo;
    const rules = summary.keep_rule_keep_radius || [];
    const worstRules = summary.worstRules;
    const canExpand = worstRules.length > 0;
    const isOpen = state.expandedBuilds.has(summary.name) && canExpand;

    const expansionHtml = isOpen ? renderBuildExpansion(worstRules) : "";

    return `
  <div class="builds-row-wrap" data-build="${escapeHtml(summary.name)}" role="rowgroup">
    <div class="builds-row" role="row">
      <div role="cell" style="display: flex; align-items: center; justify-content: center;">
        <button class="expand-btn ${isOpen ? "open" : ""}" 
                data-action="toggle-build" 
                aria-label="${isOpen ? "Collapse" : "Expand"} build details"
                aria-expanded="${isOpen}"
                ${canExpand ? "" : "disabled"}>▶</button>
      </div>
      <div class="build-name-cell" role="cell">
        <a class="build-name" href="${escapeHtml(buildLink(summary))}" title="${escapeHtml(summary.name)}">
          ${escapeHtml(summary.name)}
        </a>
        <div class="build-meta">${fmt(info.total)} items · ${fmt(summary.keep_rule_count || rules.length)} keep rules</div>
      </div>
      ${renderNumCell(info.kept, pctStr(info.kept, info.total))}
      ${renderNumCell(info.facets.classes.kept, pctStr(info.facets.classes.kept, info.facets.classes.total))}
      ${renderNumCell(info.facets.fields.kept, pctStr(info.facets.fields.kept, info.facets.fields.total))}
      ${renderNumCell(info.facets.methods.kept, pctStr(info.facets.methods.kept, info.facets.methods.total))}
      <div class="pills" role="cell">
        ${renderPill("OPT", info.noOpt, "opt")}
        ${renderPill("SHR", info.noShr, "shr")}
      </div>
    </div>
    ${expansionHtml}
  </div>
`;
  };

  const renderBuildExpansion = (worstRules) => {
    const linesHtml = worstRules
      .map(
        (rule) => `
  <div class="rule-line">
    <span class="count">${fmt(rule.item_count || 0)}</span>
    <code class="src">${escapeHtml(rule.source)}</code>
  </div>
`,
      )
      .join("");
    return `
  <div class="build-expand" role="row">
    <div role="cell" aria-colspan="7">
      <div class="build-expand-label">Worst keep rules</div>
      <div class="rule-lines">${linesHtml}</div>
    </div>
  </div>
`;
  };

  const renderNumCell = (value, sub) => `
<div class="num-cell" role="cell">
  <div class="value">${fmt(value)}</div>
  ${sub ? `<div class="sub">${sub}</div>` : ""}
</div>
`;

  const renderPill = (label, value, kind) => {
    const isActive = value > 0;
    return `
  <span class="pill ${kind} ${isActive ? "active" : ""}" title="${label}: ${fmt(value)} items">
    ${label} ${isActive ? `<span>${fmt(value)}</span>` : ""}
  </span>
`;
  };

  const renderSortHeader = (label, key, sort, align) => {
    const isActive = sort.key === key;
    const arrow = isActive ? (sort.desc ? "▼" : "▲") : "▾";
    const ariaSort = isActive
      ? sort.desc
        ? "descending"
        : "ascending"
      : "none";
    return `
  <button class="sort-header ${align === "left" ? "left" : ""} ${isActive ? "active" : ""}" 
          data-action="sort" 
          data-key="${key}"
          role="columnheader"
          aria-sort="${ariaSort}">
    ${label} <span class="sort-arrow" aria-hidden="true">${arrow}</span>
  </button>
`;
  };

  // ---- Rules view ----

  const renderRulesView = () => {
    const filtered = filterRules(
      rulesForRulesView,
      state.query,
      state.ruleLens,
      state.multiBuildOnly,
      state.onlyUnused,
      rulesViewRuleSumAll,
    );
    if (!filtered.length)
      return '<div class="empty">No keep rules match.</div>';

    const sorted = sortRules(filtered, state.ruleSort);
    const maxNonGlobalTotal = filtered.reduce(
      (acc, r) => (!r.isGlobal ? Math.max(acc, r.total) : acc),
      1,
    );
    const sumAll = filtered.reduce((acc, r) => acc + r.total, 0);
    const topShare = computeTop10RuleShare(sorted, sumAll);

    const rowsHtml = sorted
      .slice(0, 500)
      .map((rule, index) =>
        renderRuleRow(rule, index + 1, maxNonGlobalTotal, sumAll),
      )
      .join("");

    const overflowHtml =
      sorted.length > 500
        ? `<div class="rules-overflow">Showing the first 500 of ${fmt(sorted.length)} unique rules.</div>`
        : "";

    return `
  <div class="stack-sm">
    <div class="view-intro">
      <div class="primary">${fmt(filtered.length)} unique keep rules</div>
      <div class="secondary">
        Aggregated across ${fmt(totals.buildCount)} builds. 
        Top 10 rules drive <strong>${topShare.toFixed(1)}%</strong> of all kept items.
      </div>
    </div>
    <div class="card" role="table" aria-label="Keep Rules">
      <div class="rules-head" role="row">
        <div style="text-align: right" role="columnheader">#</div>
        ${renderSortHeader("Source", "source", state.ruleSort, "left")}
        ${renderSortHeader("Builds", "builds", state.ruleSort)}
        ${renderSortHeader("Total items", "total", state.ruleSort)}
      </div>
      ${rowsHtml}
      ${overflowHtml}
    </div>
  </div>
`;
  };

  const filterRules = (
    rules,
    query,
    lens,
    multiBuildOnly,
    onlyUnused,
    ruleSumAll,
  ) => {
    const needle = query.trim().toLowerCase();
    return rules.filter((rule) => {
      if (
        needle &&
        rule.sourceLower.indexOf(needle) === -1 &&
        !rule.buildsLower.some((b) => b.indexOf(needle) !== -1)
      ) {
        return false;
      }
      const share = ruleSumAll ? (rule.total / ruleSumAll) * 100 : 0;
      if (lens === "critical" && share < 5) return false;
      if (lens === "warning" && (share < 1 || share >= 5)) return false;
      if (lens === "minor" && share >= 1) return false;
      if (multiBuildOnly && rule.builds.length < 2) return false;
      if (onlyUnused && rule.total > 0) return false;
      return true;
    });
  };

  const sortRules = (rules, sort) => {
    const value = (rule) => {
      if (sort.key === "total") return rule.total;
      if (sort.key === "builds") return rule.builds.length;
      return rule.sourceLower;
    };
    return rules.slice().sort((a, b) => {
      const va = value(a);
      const vb = value(b);
      const cmp = typeof va === "string" ? va.localeCompare(vb) : va - vb;
      return sort.desc ? -cmp : cmp;
    });
  };

  const computeTop10RuleShare = (sortedRules, sumAll) => {
    if (!sumAll) return 0;
    const top10 = sortedRules
      .slice()
      .sort((a, b) => b.total - a.total)
      .slice(0, 10)
      .reduce((acc, rule) => acc + rule.total, 0);
    return (top10 / sumAll) * 100;
  };

  const renderRuleRow = (rule, rank, maxNonGlobalTotal, sumAll) => {
    const share = sumAll ? (rule.total / sumAll) * 100 : 0;
    const dotColor =
      share >= 5
        ? "var(--red)"
        : share >= 1
          ? "var(--orange)"
          : share >= 0.25
            ? "var(--yellow)"
            : "var(--border)";

    const isOpen = state.expandedRuleBuildLists.has(rule.source);
    const visibleBuilds = isOpen ? rule.builds : rule.builds.slice(0, 3);
    const hiddenCount = rule.builds.length - visibleBuilds.length;

    const chipsHtml = visibleBuilds
      .map((buildName) => {
        const label = buildName
          .split("/")
          .slice(-2)
          .join("/")
          .replace(".pb", "");
        return `
    <a class="rule-build-chip" href="${escapeHtml(buildName.replace(".pb", ".html"))}" title="${escapeHtml(buildName)}">
      ${escapeHtml(label)}
    </a>
  `;
      })
      .join("");

    let toggleHtml = "";
    if (hiddenCount > 0 && !isOpen) {
      toggleHtml = `
    <button class="rule-build-toggle" data-action="toggle-rule-builds" data-source="${escapeHtml(rule.source)}" aria-expanded="false">
      +${hiddenCount} more
    </button>
  `;
    } else if (isOpen && rule.builds.length > 3) {
      toggleHtml = `
    <button class="rule-build-toggle" data-action="toggle-rule-builds" data-source="${escapeHtml(rule.source)}" aria-expanded="true">
      Show fewer
    </button>
  `;
    }

    const barWidth = rule.isGlobal
      ? 100
      : pctNum(rule.total, maxNonGlobalTotal);

    return `
  <div class="rules-row" role="row">
    <div class="rank-cell" role="cell">
      <span class="rank-dot" style="background: ${dotColor}" title="${share.toFixed(2)}% of all kept items"></span>
      <span class="rank-num">${rank}</span>
    </div>
    <div style="min-width: 0" role="cell">
      <code class="rule-source">${escapeHtml(rule.source)}</code>
      <div class="rule-builds-list">${chipsHtml}${toggleHtml}</div>
    </div>
    <div class="rule-num-cell" role="cell">${fmt(rule.builds.length)}</div>
    <div class="rule-total-cell" role="cell">
      <div class="value">${fmt(rule.total)}</div>
      <div class="rule-bar">
        <div class="rule-bar-fill" style="width: ${barWidth}%"></div>
      </div>
    </div>
  </div>
`;
  };

  // ---- Top-level render ----

  let lastView = null;
  let lastSizeFloor = null;
  let lastRuleLens = null;
  let lastMultiBuildOnly = null;
  let lastExcludeTestApp = null;
  let lastOnlyUnused = null;
  let lastExcludeDontOptimize = null;
  let lastExcludeDontShrink = null;

  /**
   * Optimized rendering cycle.
   * Performs partial render of `<main>` on sort or text input changes to maintain focused elements naturally.
   */
  const render = () => {
    const mainEl = document.querySelector("main");
    const appEl = document.getElementById("app");

    const body =
      state.view === VIEWS.SYSTEM
        ? renderSystemView()
        : state.view === VIEWS.BUILDS
          ? renderBuildsView()
          : renderRulesView();

    const needsFullRender =
      state.view !== lastView ||
      state.sizeFloor !== lastSizeFloor ||
      state.ruleLens !== lastRuleLens ||
      state.multiBuildOnly !== lastMultiBuildOnly ||
      state.excludeTestApp !== lastExcludeTestApp ||
      state.onlyUnused !== lastOnlyUnused ||
      state.excludeDontOptimize !== lastExcludeDontOptimize ||
      state.excludeDontShrink !== lastExcludeDontShrink ||
      !mainEl;

    if (needsFullRender) {
      appEl.innerHTML = `${renderHeader()}<main>${body}</main>`;
      lastView = state.view;
      lastSizeFloor = state.sizeFloor;
      lastRuleLens = state.ruleLens;
      lastMultiBuildOnly = state.multiBuildOnly;
      lastExcludeTestApp = state.excludeTestApp;
      lastOnlyUnused = state.onlyUnused;
      lastExcludeDontOptimize = state.excludeDontOptimize;
      lastExcludeDontShrink = state.excludeDontShrink;
    } else {
      mainEl.innerHTML = body;
    }
  };

  /* ==========================================================================
     6. EVENT DELEGATION
     ========================================================================== */

  document.addEventListener("click", (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.getAttribute("data-action");

    if (action === "view") {
      goToView(target.getAttribute("data-value"));
    } else if (action === "sizefloor") {
      setState({ sizeFloor: Number(target.getAttribute("data-value")) });
    } else if (action === "rulelens") {
      setState({ ruleLens: target.getAttribute("data-value") });
    } else if (action === "sort") {
      const key = target.getAttribute("data-key");
      const current =
        state.view === VIEWS.BUILDS ? state.buildSort : state.ruleSort;
      const nextDesc =
        current.key === key
          ? !current.desc
          : key !== "name" && key !== "source";
      const next = { key: key, desc: nextDesc };
      setState(
        state.view === VIEWS.BUILDS ? { buildSort: next } : { ruleSort: next },
      );
    } else if (action === "toggle-build") {
      const buildName = target
        .closest("[data-build]")
        .getAttribute("data-build");
      toggleSetMember(state.expandedBuilds, buildName);
      render();
    } else if (action === "toggle-rule-builds") {
      const source = target.getAttribute("data-source");
      toggleSetMember(state.expandedRuleBuildLists, source);
      render();
    } else if (action === "see-opt") {
      state.buildSort = { key: "noOpt", desc: true };
      goToView(VIEWS.BUILDS);
    } else if (action === "see-shr") {
      state.buildSort = { key: "noShr", desc: true };
      goToView(VIEWS.BUILDS);
    } else if (action === "see-rules") {
      goToView(VIEWS.RULES);
    }
  });

  document.addEventListener("input", (event) => {
    const target = event.target;
    if (target.getAttribute && target.getAttribute("data-action") === "query") {
      state.query = target.value;
      render(); // Input maintains focus natively as only <main> is updated
    }
  });

  document.addEventListener("change", (event) => {
    const target = event.target;
    if (!target.getAttribute) return;
    const action = target.getAttribute("data-action");
    if (action === "multibuild") {
      setState({ multiBuildOnly: target.checked });
    } else if (action === "exclude-testapp") {
      setState({ excludeTestApp: target.checked });
    } else if (action === "onlyunused") {
      setState({ onlyUnused: target.checked });
    } else if (action === "exclude-dontoptimize") {
      setState({ excludeDontOptimize: target.checked });
    } else if (action === "exclude-dontshrink") {
      setState({ excludeDontShrink: target.checked });
    }
  });

  const toggleSetMember = (set, value) => {
    if (set.has(value)) set.delete(value);
    else set.add(value);
  };

  /* ==========================================================================
     7. BOOT
     ========================================================================== */

  updateDerivedState();
  handleRouting(); // Trigger initial route loading
})();
