// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

/* ==========================================================================
   APPLICATION LOGIC
   ==========================================================================
   This script handles the interactive features of the Report.
   It is divided into:
   1. Constants & Configuration
   2. UI Utilities (DOM helpers)
   3. Main App Controller (Router)
   4. ReportApp (The main table/grid view)
   ========================================================================== */

/**
 * Global Constants used throughout the application.
 */
const CONSTANTS = {
  VIEWS: {
    MODULES: "modules",
    PACKAGES: "packages",
    DETAILS: "details",
    FILE_DETAILS: "file-details"
  },
  DEFAULTS: {
    AGGREGATED: "Aggregated"
  },
};

/**
 * UI Utilities
 * Collection of helper functions for DOM manipulation and common UI patterns.
 */
const UIUtils = {
  /**
   * Returns the CSS class for score percentage coloring.
   * @param {number|string} percentage - The score percentage (0-100 or "--").
   * @returns {string} CSS class name.
   */
  getScoreClass(percentage) {
    if (percentage === "--") return "text-gray-500";
    if (percentage >= 80) return "text-green-600";
    if (percentage >= 60) return "text-yellow-600";
    return "text-red-600";
  },

  getMatchClass(percentage) {
    if (percentage === "--") return "text-gray-500";
    if (percentage < 10) return "text-green-600";
    if (percentage < 20) return "text-orange-600";
    return "text-red-600";
  },

  /**
   * Toggles the visibility of a DOM element.
   * @param {HTMLElement} element - The element to toggle.
   * @param {boolean} show - Whether to show or hide.
   */
  toggleVisibility(element, show) {
    if (!element) return;
    if (show) {
      element.classList.remove("hidden");
      // Restore appropriate display type
      if (element.id === "ts-selected-state") {
        element.style.display = "flex";
      } else {
        element.style.display = "";
      }
    } else {
      element.classList.add("hidden");
      element.style.display = "none";
    }
  },

  /**
   * Builds a multi-select dropdown with "Select All" / "Clear" actions (Zone A)
   * and a scrollable list of options (Zone B).
   *
   * @param {string} containerId - ID of the dropdown container.
   * @param {Array} options - List of options {name, value}.
   * @param {Array} selectedStateArr - Array storing currently selected values.
   * @param {Function} onSelectionChange - Callback when selection changes.
   * @param {boolean} searchable - Whether to include a search box.
   * @param {boolean} multiSelect - Whether to allow multiple selections.
   */
  buildActionDropdown(containerId, options, selectedStateArr,
    onSelectionChange, searchable = true, multiSelect = true, totalCount =
    null, itemName = "items", searchCallback = null) {
    const container = document.getElementById(containerId);
    if (!container) return;

    // Container config
    container.innerHTML = "";
    container.style.padding = "0";
    container.style.overflow = "hidden"; // Clip corners

    // --- ZONE 0: Search (Top Level) ---
    let searchInput = null;
    if (searchable) {
      const searchContainer = document.createElement("div");
      searchContainer.className = "dropdown-search-zone";
      searchContainer.style.padding = "0.5rem";
      searchContainer.style.borderBottom = "1px solid var(--border-color)";
      searchContainer.style.background = "var(--bg-surface)";

      searchInput = document.createElement("input");
      searchInput.type = "text";
      searchInput.className = "popover-search";
      searchInput.style.width = "100%";
      searchInput.placeholder = "Search...";

      searchContainer.appendChild(searchInput);
      container.appendChild(searchContainer);
    }

    // --- ZONE B: Option List ---
    const listZone = document.createElement("div");
    listZone.className = "dropdown-scroll-zone";
    container.appendChild(listZone);

    // Render List Logic
    const renderList = (optionsToRender) => {
      listZone.innerHTML = "";

      if (optionsToRender.length === 0) {
        listZone.innerHTML =
          `<div class="p-4 text-xs text-gray-400 text-center">No options available</div>`;
        return;
      }

      optionsToRender.forEach(opt => {
        // SKIP "All" options if they exist in the passed options list.
        if (opt.value === "all") return;

        const isChecked = selectedStateArr.includes(opt.value);

        const item = document.createElement(multiSelect ? "label" :
          "div");
        item.className = "popover-item" + (isChecked && !multiSelect ?
          " active-item" : "");

        let checkbox = null;
        if (multiSelect) {
          checkbox = document.createElement("input");
          checkbox.type = "checkbox";
          checkbox.className = "popover-checkbox";
          checkbox.checked = isChecked;
          item.appendChild(checkbox);
        }

        const label = document.createElement("span");
        label.innerHTML = opt.name;
        item.appendChild(label);

        // Interaction: Toggle Individual
        const handleSelect = (e) => {
          if (multiSelect) {
            if (checkbox.checked) {
              if (!selectedStateArr.includes(opt.value))
                selectedStateArr.push(opt.value);
            } else {
              const idx = selectedStateArr.indexOf(opt.value);
              if (idx > -1) selectedStateArr.splice(idx, 1);
            }
          } else {
            selectedStateArr.length = 0;
            selectedStateArr.push(opt.value);
          }

          if (!multiSelect) {
            renderList(optionsToRender);
          }
          onSelectionChange();
        };

        if (multiSelect) {
          item.addEventListener("change", (e) => {
            e.stopPropagation();
            handleSelect(e);
          });
        } else {
          item.addEventListener("click", (e) => {
            e.stopPropagation();
            handleSelect(e);
          });
        }

        listZone.appendChild(item);
      });
    };

    renderList(options);

    // --- ZONE C: Footer ---
    let footer = null;
    if (totalCount !== null) {
      footer = document.createElement("div");
      footer.className = "dropdown-footer";
      footer.style.padding = "0.5rem 1rem";
      footer.style.fontSize = "0.75rem";
      footer.style.color = "var(--text-gray-400)";
      footer.style.borderTop = "1px solid var(--border-color)";
      footer.style.background = "var(--bg-subtle)";
      footer.textContent =
        `Showing ${options.length} out of ${totalCount} ${itemName}`;
      container.appendChild(footer);
    }

    // --- Search Logic ---
    if (searchInput) {
      searchInput.addEventListener("input", (e) => {
        const term = e.target.value.toLowerCase();

        if (searchCallback) {
          const {
            options: filteredOptions,
            total: mCount
          } = searchCallback(term);
          renderList(filteredOptions);
          if (footer) {
            footer.textContent =
              `Showing ${filteredOptions.length} out of ${mCount} ${itemName}`;
          }
        } else {
          const items = listZone.querySelectorAll(".popover-item");
          let visibleCount = 0;
          items.forEach(item => {
            const label = item.querySelector("span").textContent
              .toLowerCase();
            const isVisible = label.includes(term);
            item.style.display = isVisible ? "flex" : "none";
            if (isVisible) visibleCount++;
          });

          if (footer && totalCount !== null) {
            footer.textContent =
              `Showing ${visibleCount} out of ${totalCount} ${itemName}`;
          }
        }
      });
    }
  },

  /**
   * Renders the text on the filter chip (e.g., "Module: All" or "Module: :core:network (+2)").
   */
  renderChipText(element, label, type, isMulti = false) {
    if (!element) return;
    // Logic for displaying close button: ALWAYS show if 'type' is present (meaning removable)
    // The user specifically requested option to remove filter when all items are selected.
    const showClose = !!type;

    if (!showClose) {
      element.innerHTML = `<span class="filter-text">${label}</span>`;
    } else {
      element.innerHTML = `
                <span class="filter-text">${label}</span>
                <span class="chip-close" data-clear="${type}">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" width="14" height="14">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                  </svg>
                </span>`;
    }
  },

  getFilterLabel(prefix, selectedArr, totalCount, options = []) {
    if (selectedArr.length === 0 || (totalCount > 0 && selectedArr.length ===
        totalCount)) return `${prefix}: All`;
    if (selectedArr.length === 1) {
      const val = selectedArr[0];
      const opt = options.find(o => String(o.value) === String(val));
      const name = opt ? opt.name : val;
      // Strip HTML tags if any (like in keepRuleOptions)
      const cleanName = name.replace(/<[^>]*>/g, "");
      return `${prefix}: ${cleanName}`;
    }
    return `${prefix}: ${selectedArr.length} Selected`;
  },

  /**
   * Finds the common prefix among an array of strings.
   * @param {string[]} strings - Array of strings to analyze.
   * @returns {string} The common prefix.
   */
  findCommonPrefix(strings) {
    if (!strings || strings.length === 0) return "";
    let prefix = strings[0];
    for (let i = 1; i < strings.length; i++) {
      if (strings[i].startsWith("Android Gradle plugin")) {
        continue;
      }
      while (strings[i].indexOf(prefix) !== 0) {
        prefix = prefix.substring(0, prefix.length - 1);
        if (prefix === "") return "";
      }
    }
    // Ensure we only strip up to the last slash to keep the filename.
    const lastSlash = prefix.lastIndexOf('/');
    if (lastSlash !== -1) {
      return prefix.substring(0, lastSlash + 1);
    }
    return "";
  }
};

/* ==========================================================================
   MAIN APP CONTROLLER
   ========================================================================== */

const App = {
  blastRadiusData: null,

  init() {
    // Initialize Report (Main Grid)
    ReportApp.init();

    // Load Protobuf Data in the Background
    this.loadProtoData();
  },

  showDetailsView(ruleId) {
    ReportApp.state.currentView = CONSTANTS.VIEWS.DETAILS;
    document.getElementById("report-view").style.display = "none";
    document.getElementById("file-details-view").style.display = "none";
    document.getElementById("details-view").style.display = "flex";

    const ruleBody = document.getElementById("details-rule-body");
    const impactBody = document.getElementById("details-impact-body");
    const identicalRulesBody = document.getElementById(
      "details-identical-rules-body");
    const identicalRulesHeader = document.getElementById(
      "details-identical-rules-header");
    const identicalRulesTitle = document.getElementById(
      "details-identical-rules-title");
    const subsumedByBody = document.getElementById(
    "details-subsumed-by-body");
    const subsumedByHeader = document.getElementById(
      "details-subsumed-by-header");
    const subsumedByTitle = document.getElementById(
      "details-subsumed-by-title");
    const impactHeader = document.getElementById(
    "details-rule-impact-header");
    const classesContent = document.getElementById("details-classes-content");
    const methodsContent = document.getElementById("details-methods-content");
    const fieldsContent = document.getElementById("details-fields-content");

    const rule = this.blastRadiusData?.keepRuleBlastRadiusTable.find(r => r
      .id === parseInt(ruleId));
    if (rule) {
      const fileOriginId = rule?.origin?.fileOriginId;
      const fileOrigin = this.blastRadiusData?.fileOriginTable.find(f => f
        .id === fileOriginId);

      let originStr = "";
      if (fileOrigin) {
        const mavenName = formatMavenCoordinate(fileOrigin.mavenCoordinate);
        originStr =
          `${mavenName || fileOrigin.filename}:${rule.origin?.lineNumber || 1}`;
        if (mavenName) {
          originStr += ` (${fileOrigin.filename})`;
        }
      }

      const constraintsMap = getConstraintsMap(this.blastRadiusData);
      const constraints = constraintsMap.get(rule.constraintsId) || [];

      const getTag = (c, label) => {
        const isRestricted = constraints.includes(c);
        if (!isRestricted) return "";
        const color = "var(--text-red-600)";
        const bgColor = "var(--bg-red-light)";
        return `<span class="impact-tag" style="color: ${color}; background-color: ${bgColor}; border-color: ${color}; opacity: 0.8;">${label}</span>`;
      };

      const impactTagsHtml = `
            <div class="impact-container">
              ${getTag('DONT_OBFUSCATE', 'OBFUSCATE')}
              ${getTag('DONT_OPTIMIZE', 'OPTIMIZE')}
              ${getTag('DONT_SHRINK', 'SHRINK')}
            </div>
          `;

      ruleBody.innerHTML = `
            <tr class="border-t border-gray-200">
              <td style="padding: 1rem; vertical-align: top;">
                ${originStr ? `<div style="font-size: 0.75rem; color: var(--text-gray-900); margin-bottom: 0.5rem; font-family: var(--font-family-mono);">${escapeHTML(originStr)}</div>` : ""}
                <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; color: var(--text-blue-700);">${escapeHTML(rule.source)}</pre>
              </td>
            </tr>
          `;

      const br = rule.blastRadius || {};
      const classIds = br.classBlastRadius || [];
      const fieldIds = br.fieldBlastRadius || [];
      const methodIds = br.methodBlastRadius || [];
      const matchedTotal = classIds.length + fieldIds.length + methodIds
        .length;
      const totalLive = getLiveItemCount(this.blastRadiusData);
      const liveClasses = this.blastRadiusData?.buildInfo?.liveClassCount ||
      0;
      const liveFields = this.blastRadiusData?.buildInfo?.liveFieldCount || 0;
      const liveMethods = this.blastRadiusData?.buildInfo?.liveMethodCount ||
        0;

      const renderMatchCell = (count, total, borderLeft = true) => {
        const perc = total > 0 ? (count / total * 100) : 0;
        const colorClass = UIUtils.getMatchClass(perc);
        const bl = borderLeft ? "border-l border-gray-200" : "";
        return `
              <td class="text-center ${bl}" style="padding: 1rem; width: 100px; min-width: 100px;">
                <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                  <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                  <span class="text-xs text-gray-500 mt-1">${count}</span>
                </div>
              </td>
            `;
      };

      impactBody.innerHTML = `
            <tr class="border-t border-gray-200">
              ${renderMatchCell(classIds.length, liveClasses, false)}
              ${renderMatchCell(fieldIds.length, liveFields)}
              ${renderMatchCell(methodIds.length, liveMethods)}
              <td class="border-l border-gray-200" style="padding: 1rem; vertical-align: middle;">
                ${impactTagsHtml}
              </td>
            </tr>
          `;

      const allRules = this.blastRadiusData?.keepRuleBlastRadiusTable || [];
      const subsumingIds = br.subsumedBy || [];

      const identicalRules = [];
      const subsumedByRules = [];

      subsumingIds.forEach(id => {
        const otherRule = allRules.find(r => r.id === id);
        if (otherRule) {
          const otherSubsumedBy = otherRule.blastRadius?.subsumedBy || [];
          if (otherSubsumedBy.includes(rule.id)) {
            identicalRules.push(otherRule);
          } else {
            subsumedByRules.push(otherRule);
          }
        }
      });

      const renderRuleRow = (r) => {
        const rBr = r.blastRadius || {};
        const rClassIds = rBr.classBlastRadius || [];
        const rFieldIds = rBr.fieldBlastRadius || [];
        const rMethodIds = rBr.methodBlastRadius || [];

        const rConstraints = constraintsMap.get(r.constraintsId) || [];
        const getTag = (c, label) => {
          const isRestricted = rConstraints.includes(c);
          if (!isRestricted) return "";
          const color = "var(--text-red-600)";
          const bgColor = "var(--bg-red-light)";
          return `<span class="impact-tag" style="color: ${color}; background-color: ${bgColor}; border-color: ${color}; opacity: 0.8;">${label}</span>`;
        };

        const impactCell = `
              <td class="text-center border-l border-gray-200" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OBFUSCATE', 'OBFUSCATE')}</td>
              <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OPTIMIZE', 'OPTIMIZE')}</td>
              <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_SHRINK', 'SHRINK')}</td>
            `;

        return `
              <tr class="table-row border-t border-gray-200 hover:bg-gray-50 cursor-pointer" onclick="App.showDetailsView('${r.id}')">
                <td class="sticky-name font-medium text-blue-600 hover:underline" title="${escapeHTML(r.source)}" style="padding: 1rem; width: 600px; min-width: 600px;">
                  <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapeHTML(r.source)}</pre>
                </td>
                ${renderMatchCell(rClassIds.length + rFieldIds.length + rMethodIds.length, totalLive, true)}
                ${renderMatchCell(rClassIds.length, liveClasses)}
                ${renderMatchCell(rFieldIds.length, liveFields)}
                ${renderMatchCell(rMethodIds.length, liveMethods)}
                ${impactCell}
              </tr>
            `;
      };

      if (identicalRules.length === 0) {
        identicalRulesTitle.textContent = "Identical Rules (None)";
        identicalRulesHeader.style.display = "none";
        identicalRulesBody.innerHTML = "";
      } else {
        identicalRulesTitle.textContent = "Identical Rules";
        identicalRulesHeader.style.display = "";
        identicalRulesBody.innerHTML = identicalRules.map(renderRuleRow).join(
          "");
      }

      if (subsumedByRules.length === 0) {
        subsumedByTitle.textContent = "Subsumed By (None)";
        subsumedByHeader.style.display = "none";
        subsumedByBody.innerHTML = "";
      } else {
        subsumedByTitle.textContent = "Subsumed By";
        subsumedByHeader.style.display = "";
        subsumedByBody.innerHTML = subsumedByRules.map(renderRuleRow).join(
        "");
      }

      const typeRefMap = new Map();
      if (this.blastRadiusData.typeReferenceTable) {
        this.blastRadiusData.typeReferenceTable.forEach(t => typeRefMap.set(t
          .id, t.javaDescriptor));
      }

      const renderList = (ids, getLabel) => {
        if (ids.length === 0)
        return '<div style="padding: 0.5rem; color: var(--text-gray-500); font-size: 0.8125rem;">None</div>';
        const limit = 1000;
        const toRender = ids.slice(0, limit);
        const listHtml = toRender.map(id => `
              <div style="padding: 0.375rem 0.5rem; border-radius: 0.375rem; margin-bottom: 0.125rem; font-family: var(--font-family-mono); font-size: 0.8125rem;" class="hover-bg-gray-100">
                ${getLabel(id)}
              </div>
            `).join("");
        if (ids.length > limit) {
          return listHtml + `
                <div style="padding: 0.5rem; color: var(--text-gray-500); font-size: 0.8125rem; font-style: italic;">
                  ... and ${ids.length - limit} more items
                </div>`;
        }
        return listHtml;
      };

      classesContent.innerHTML = renderList(classIds, (id) => {
        const info = this.blastRadiusData.keptClassInfoTable.find(c => c
          .id === id);
        return escapeHTML(formatDescriptor(typeRefMap.get(info
          ?.classReferenceId)));
      });

      fieldsContent.innerHTML = renderList(fieldIds, (id) => {
        const info = this.blastRadiusData.keptFieldInfoTable.find(f => f
          .id === id);
        const ref = this.blastRadiusData.fieldReferenceTable.find(r => r
          .id === info?.fieldReferenceId);
        return escapeHTML(formatFieldName(ref, this.blastRadiusData,
          typeRefMap));
      });

      methodsContent.innerHTML = renderList(methodIds, (id) => {
        const info = this.blastRadiusData.keptMethodInfoTable.find(m => m
          .id === id);
        const ref = this.blastRadiusData.methodReferenceTable.find(r => r
          .id === info?.methodReferenceId);
        return escapeHTML(formatMethodName(ref, this.blastRadiusData,
          typeRefMap));
      });

    } else {
      ruleBody.innerHTML =
        '<tr><td colspan="2" style="padding: 1rem;">Rule not found.</td></tr>';
      classesContent.innerHTML = "";
      methodsContent.innerHTML = "";
      fieldsContent.innerHTML = "";
    }

    const fileOrigin = this.blastRadiusData?.fileOriginTable.find(f => f
      .id === rule?.origin?.fileOriginId);
    const fileOriginName = formatMavenCoordinate(fileOrigin
      ?.mavenCoordinate) || fileOrigin?.filename;
    this.updateDetailsBreadcrumbs(fileOriginName, fileOrigin?.id);
  },

  showReportView() {
    ReportApp.state.currentView = CONSTANTS.VIEWS.MODULES;
    document.getElementById("details-view").style.display = "none";
    document.getElementById("file-details-view").style.display = "none";
    document.getElementById("report-view").style.display = "flex";
    ReportApp.render();
  },

  showFileDetailsView(fileOriginId) {
    ReportApp.state.currentView = CONSTANTS.VIEWS.FILE_DETAILS;
    ReportApp.state.drillContext.fileOriginId = fileOriginId;
    this.renderFileDetailsView(fileOriginId);
  },

  renderFileDetailsView(fileOriginId) {
    document.getElementById("report-view").style.display = "none";
    document.getElementById("details-view").style.display = "none";
    document.getElementById("file-details-view").style.display = "flex";

    const impactBody = document.getElementById("file-details-impact-body");
    const rulesBody = document.getElementById("file-details-rules-body");
    const rulesHeader = document.getElementById("file-details-rules-header");

    const fileOrigin = this.blastRadiusData?.fileOriginTable.find(f => f
      .id === parseInt(fileOriginId));
    if (!fileOrigin) return;

    const allRulesForFile = this.blastRadiusData.keepRuleBlastRadiusTable
      .filter(r => r.origin?.fileOriginId === fileOrigin.id);
    let rules = allRulesForFile;

    const lens = ReportApp.state.filters.keepRules[0];
    if (lens) {
      rules = ReportApp.applyKeepRuleLens(rules, lens);
    }

    const isIdenticalLens = lens === "Identical";
    const isSubsumedLens = lens === "Subsumed";

    if (isIdenticalLens || isSubsumedLens) {
      rulesHeader.innerHTML = `
            <tr>
              <th class="text-left bg-gray-50 z-30" style="padding: 1rem; width: 600px; min-width: 600px; border-bottom: 1px solid var(--border-color);">Rule</th>
              <th class="text-left bg-gray-50 border-l border-gray-200" style="padding: 1rem; width: 100%; border-bottom: 1px solid var(--border-color);">${isIdenticalLens ? 'Identical Rules' : 'Subsumed By'}</th>
            </tr>
          `;
    } else {
      rulesHeader.innerHTML = `
            <tr>
              <th rowspan="2" class="text-left bg-gray-50 z-30" style="padding: 1rem; width: 600px; min-width: 600px; border-bottom: 1px solid var(--border-color);">Rule</th>
              <th colspan="4" class="text-center border-l border-gray-200 bg-gray-50" style="padding: 0.5rem; border-bottom: 1px solid var(--border-color);">Kept Items (higher is worse)</th>
              <th rowspan="2" colspan="3" class="text-center border-l border-gray-200 bg-gray-50" style="padding: 0.5rem; width: 150px; min-width: 150px; border-bottom: 1px solid var(--border-color);">Steps blocked by rule</th>
            </tr>
            <tr>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200" style="padding: 0.5rem; width: 100px; min-width: 100px; border-bottom: 1px solid var(--border-color);">Total</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200" style="padding: 0.5rem; width: 100px; min-width: 100px; border-bottom: 1px solid var(--border-color);">Classes</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200" style="padding: 0.5rem; width: 100px; min-width: 100px; border-bottom: 1px solid var(--border-color);">Fields</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200" style="padding: 0.5rem; width: 100px; min-width: 100px; border-bottom: 1px solid var(--border-color);">Methods</th>
            </tr>
          `;
    }

    const liveClasses = this.blastRadiusData?.buildInfo?.liveClassCount || 0;
    const liveFields = this.blastRadiusData?.buildInfo?.liveFieldCount || 0;
    const liveMethods = this.blastRadiusData?.buildInfo?.liveMethodCount || 0;

    const matchedClasses = new Set();
    const matchedFields = new Set();
    const matchedMethods = new Set();

    allRulesForFile.forEach(rule => {
      const br = rule.blastRadius || {};
      (br.classBlastRadius || []).forEach(id => matchedClasses.add(id));
      (br.fieldBlastRadius || []).forEach(id => matchedFields.add(id));
      (br.methodBlastRadius || []).forEach(id => matchedMethods.add(id));
    });

    const renderMatchCell = (count, total, borderLeft = true) => {
      const perc = total > 0 ? (count / total * 100) : 0;
      const colorClass = UIUtils.getMatchClass(perc);
      const bl = borderLeft ? "border-l border-gray-200" : "";
      return `
            <td class="text-center ${bl}" style="padding: 1rem;">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500 mt-1">${count}</span>
              </div>
            </td>
          `;
    };

    impactBody.innerHTML = `
          <tr class="border-t border-gray-200">
            ${renderMatchCell(matchedClasses.size, liveClasses, false)}
            ${renderMatchCell(matchedFields.size, liveFields)}
            ${renderMatchCell(matchedMethods.size, liveMethods)}
          </tr>
        `;

    rulesBody.innerHTML = rules.map(rule => {
      if (isIdenticalLens || isSubsumedLens) {
        const list = isIdenticalLens ? rule.identicalRules : rule
          .subsumedByRules;
        const otherRulesHtml = (list || []).map(other => `
              <div class="text-xs text-blue-600 hover:underline cursor-pointer mb-1" onclick="event.stopPropagation(); App.showDetailsView('${other.id}')">
                <pre style="white-space: pre-wrap; margin: 0; font-family: var(--font-family-mono);">${escapeHTML(other.source)}</pre>
              </div>
            `).join("");

        return `
              <tr class="border-t border-gray-200 hover:bg-gray-50 cursor-pointer" onclick="App.showDetailsView('${rule.id}')">
                <td class="sticky-name font-medium text-blue-600 hover:underline" title="${escapeHTML(rule.source)}" style="padding: 1rem; width: 600px; min-width: 600px;">
                  <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapeHTML(rule.source)}</pre>
                </td>
                <td class="border-l border-gray-200" style="padding: 1rem; width: 100%;">
                  ${otherRulesHtml || '<span class="text-gray-400 italic">None</span>'}
                </td>
              </tr>
            `;
      }

      const br = rule.blastRadius || {};
      const c = (br.classBlastRadius || []).length;
      const f = (br.fieldBlastRadius || []).length;
      const m = (br.methodBlastRadius || []).length;

      const constraintsMap = getConstraintsMap(this.blastRadiusData);
      const constraints = constraintsMap.get(rule.constraintsId) || [];
      const getTag = (c, label) => {
        const isRestricted = constraints.includes(c);
        if (!isRestricted) return "";
        const color = "var(--text-red-600)";
        const bgColor = "var(--bg-red-light)";
        return `<span class="impact-tag" style="color: ${color}; background-color: ${bgColor}; border-color: ${color}; opacity: 0.8;">${label}</span>`;
      };

      const impactCell = `
            <td class="text-center border-l border-gray-200" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OBFUSCATE', 'OBFUSCATE')}</td>
            <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OPTIMIZE', 'OPTIMIZE')}</td>
            <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_SHRINK', 'SHRINK')}</td>
          `;

      const renderMatchCell = (count, total, borderLeft = true) => {
        const perc = total > 0 ? (count / total * 100) : 0;
        const colorClass = UIUtils.getMatchClass(perc);
        const bl = borderLeft ? "border-l border-gray-200" : "";
        return `
              <td class="text-center ${bl}" style="padding: 1rem; width: 100px; min-width: 100px;">
                <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                  <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                  <span class="text-xs text-gray-500 mt-1">${count}</span>
                </div>
              </td>
            `;
      };

      const totalLive = getLiveItemCount(this.blastRadiusData);
      const liveClasses = this.blastRadiusData?.buildInfo
        ?.liveClassCount || 0;
      const liveFields = this.blastRadiusData?.buildInfo
        ?.liveFieldCount || 0;
      const liveMethods = this.blastRadiusData?.buildInfo
        ?.liveMethodCount || 0;

      return `
            <tr class="border-t border-gray-200 hover:bg-gray-50 cursor-pointer" onclick="App.showDetailsView('${rule.id}')">
              <td class="sticky-name font-medium text-blue-600 hover:underline" title="${escapeHTML(rule.source)}" style="padding: 1rem; width: 600px; min-width: 600px;">
                <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapeHTML(rule.source)}</pre>
              </td>
              ${renderMatchCell(c + f + m, totalLive, true)}
              ${renderMatchCell(c, liveClasses)}
              ${renderMatchCell(f, liveFields)}
              ${renderMatchCell(m, liveMethods)}
              ${impactCell}
            </tr>
          `;
    }).join("");

    const fileOriginName = formatMavenCoordinate(fileOrigin
      .mavenCoordinate) || fileOrigin.filename;
    this.updateFileDetailsBreadcrumbs(fileOriginName);
  },

  updateFileDetailsBreadcrumbs(filename) {
    const bc = document.getElementById("file-details-breadcrumbs");
    if (!bc) return;

    const linkClass = "breadcrumb-pill";
    const textClass = "breadcrumb-text";
    const sep = '<span class="text-gray-300 mx-1">/</span>';

    bc.innerHTML = `
          <span class="${linkClass}" id="file-details-back-to-summary">Project</span>
          ${sep}
          <span class="${textClass}">${escapeHTML(filename)}</span>
        `;

    document.getElementById("file-details-back-to-summary").addEventListener(
      "click", () => {
        this.showReportView();
      });
  },

  updateDetailsBreadcrumbs(filename, fileOriginId) {
    const bc = document.getElementById("details-breadcrumbs");
    if (!bc) return;

    const linkClass = "breadcrumb-pill";
    const textClass = "breadcrumb-text";
    const sep = '<span class="text-gray-300 mx-1">/</span>';

    let html = `
          <span class="${linkClass}" id="details-back-to-summary">Project</span>
          ${sep}`;

    if (filename && fileOriginId !== undefined) {
      html += `
            <span class="${linkClass}" id="details-back-to-file">${escapeHTML(filename)}</span>
            ${sep}`;
    }

    html += `<span class="${textClass}">Keep Rule Details</span>`;
    bc.innerHTML = html;

    document.getElementById("details-back-to-summary").addEventListener(
      "click", () => {
        this.showReportView();
      });

    if (filename && fileOriginId !== undefined) {
      document.getElementById("details-back-to-file").addEventListener(
        "click", () => {
          this.showFileDetailsView(fileOriginId);
        });
    }
  },

  async loadProtoData() {
    const embeddedProtoSchemaSource = document.getElementById('blastradius-proto');
    const embeddedProtoDataSource = document.getElementById('blastradius-data');

    try {
      const root = protobuf.parse(embeddedProtoSchemaSource.textContent).root;
      const data = embeddedProtoDataSource.textContent.trim();
      const bytes = Uint8Array.from(atob(data), c => c.charCodeAt(0));
      const BlastRadiusContainer = root.lookupType(
        "com.android.tools.r8.blastradius.proto.BlastRadiusContainer");
      const message = BlastRadiusContainer.decode(bytes);
      this.blastRadiusData = BlastRadiusContainer.toObject(message, {
        longs: String,
        enums: String,
        bytes: String,
        defaults: true,
        arrays: true,
        objects: true,
        oneofs: true
      });

      // Extract and strip common prefix from file names
      if (this.blastRadiusData.fileOriginTable) {
        const filenames = this.blastRadiusData.fileOriginTable.map(f => f
          .filename).filter(Boolean);
        const commonPrefix = UIUtils.findCommonPrefix(filenames);
        if (commonPrefix) {
          this.blastRadiusData.fileOriginTable.forEach(f => {
            if (f.filename && f.filename.startsWith(commonPrefix)) {
              f.filename = f.filename.substring(commonPrefix.length);
            }
          });
        }
      }

      console.log("Protobuf data loaded successfully:", this
        .blastRadiusData);

      // Trigger re-render now that data is available
      ReportApp.render();
    } catch (err) {
      console.error("Failed to load protobuf data:", err);
    }
  },
};

/* ==========================================================================
   REPORT APP (Grid/List View)
   ========================================================================== */

const ReportApp = {
  state: {
    currentView: CONSTANTS.VIEWS.MODULES,
    filters: {
      keepRules: [],
      classes: [],
      fields: [],
      methods: []
    },
    activeFilterChips: [],
    sort: {
      by: "matches.total",
      order: "desc"
    },
    drillContext: {
      module: null,
      pkg: null,
      fileOriginId: null
    }, // Navigation path
    statsVisible: true
  },
  elements: {},

  init() {
    this.cacheDOMElements();
    this.populateHeaderInfo();
    this.populateFilters();
    this.bindEvents();
    this.render();
  },

  cacheDOMElements() {
    const getById = (id) => document.getElementById(id);
    this.elements = {
      modChip: getById("mod-chip-container"),
      modBtn: getById("mod-filter-btn"),
      modDropdown: getById("mod-dropdown"),
      modText: getById("mod-filter-text"),
      clsChip: getById("cls-chip-container"),
      clsBtn: getById("cls-filter-btn"),
      clsDropdown: getById("cls-dropdown"),
      clsText: getById("cls-filter-text"),
      fieldChip: getById("field-chip-container"),
      fieldBtn: getById("field-filter-btn"),
      fieldDropdown: getById("field-dropdown"),
      fieldText: getById("field-filter-text"),
      methodChip: getById("method-chip-container"),
      methodBtn: getById("method-filter-btn"),
      methodDropdown: getById("method-dropdown"),
      methodText: getById("method-filter-text"),
      addFilterContainer: getById("add-filter-container"),
      addFilterBtn: getById("add-filter-btn"),
      addFilterDropdown: getById("add-filter-dropdown"),
      addFilterList: getById("add-filter-list"),
      lensFilterContainer: getById("lens-filter-container"),
      lensFilterBtn: getById("lens-filter-btn"),
      lensFilterDropdown: getById("lens-filter-dropdown"),
      lensFilterList: getById("lens-filter-list"),
      grpBtn: getById("group-by-btn"),
      grpDropdown: getById("group-by-dropdown"),
      grpText: getById("group-by-text"),
      grpContainer: getById("group-by-container"),
      grpList: getById("group-by-list"),
      tableHeaders: getById("table-headers"),
      tableData: getById("table-data"),
      statsTableHeaders: getById("stats-table-headers"),
      statsTableData: getById("stats-table-data"),
      statsContainer: getById("stats-container"),
      mainTable: getById("main-table"),
      totalObfuscation: getById("total-obfuscation"),
      totalOptimization: getById("total-optimization"),
      totalShrinking: getById("total-shrinking"),
    };
  },

  populateHeaderInfo() {},

  /**
   * Initializes all filters (Variants, Modules, etc.)
   */
  populateFilters() {
    // --- 1. Dynamic Filters (Modules/Packages/Classes) ---
    this.updateDynamicFilters();
  },

  /**
   * Updates the available options in Module/Package/Class dropdowns based on dependencies.
   * e.g., Selecting "Module A" filters the Package options to only those in Module A.
   */
  updateDynamicFilters() {
    const {
      keepRules,
      classes,
      fields,
      methods
    } = this.state.filters;
    const chips = this.state.activeFilterChips;

    // --- 1. Keep Rules Lens ---
    const keepRuleOptions = [{
        name: "<b>Identical:</b> Show rules that match the same items as other rules",
        value: "Identical"
      },
      {
        name: "<b>Subsumed:</b> Show rules that match a subset of the items matched by another rule",
        value: "Subsumed"
      },
      {
        name: "<b>Unused:</b> Show rules that don't match anything",
        value: "Unused"
      },
    ];

    this.elements.modDropdown.style.minWidth = "550px";
    this.elements.lensFilterDropdown.style.minWidth = "550px";
    UIUtils.buildActionDropdown("mod-dropdown", keepRuleOptions, keepRules,
    () => {
        this.updateDynamicFilters();
        this.render();
      }, false, false);

    UIUtils.buildActionDropdown("lens-filter-list", keepRuleOptions,
      keepRules, () => {
        if (!this.state.activeFilterChips.includes("module")) {
          this.state.activeFilterChips.push("module");
        }
        this.toggleDropdown(this.elements.lensFilterDropdown, this.elements
          .lensFilterBtn);
        this.updateDynamicFilters();
        this.render();
      }, false, false);

    UIUtils.renderChipText(this.elements.modText, UIUtils.getFilterLabel(
        "Keep Rules", keepRules, keepRuleOptions.length, keepRuleOptions),
      "module", true);
    UIUtils.toggleVisibility(this.elements.modChip, chips.includes("module"));

    // --- 2. Classes Filter ---
    const {
      options: classOptions,
      total: totalClasses
    } = this.getKeptClasses();
    UIUtils.buildActionDropdown("cls-dropdown", classOptions, classes, () => {
      this.updateDynamicFilters();
      this.render();
    }, true, true, totalClasses, "classes", (term) => {
      const brData = App.blastRadiusData;
      if (!brData || !brData.keptClassInfoTable) return {
        options: [],
        total: 0
      };
      const typeRefMap = new Map();
      brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
        .javaDescriptor));

      const filtered = brData.keptClassInfoTable.filter(c => {
        const name = formatDescriptor(typeRefMap.get(c
          .classReferenceId));
        return name.toLowerCase().includes(term);
      });
      const results = filtered.slice(0, 1000).map(c => {
        const name = escapeHTML(formatDescriptor(typeRefMap.get(c
          .classReferenceId)));
        return {
          name,
          value: c.id
        };
      });
      return {
        options: results,
        total: filtered.length
      };
    });
    UIUtils.renderChipText(this.elements.clsText, UIUtils.getFilterLabel(
      "Classes", classes, totalClasses, classOptions), "class", true);
    UIUtils.toggleVisibility(this.elements.clsChip, chips.includes("class"));

    // --- 3. Fields Filter ---
    const {
      options: fieldOptions,
      total: totalFields
    } = this.getKeptFields();
    UIUtils.buildActionDropdown("field-dropdown", fieldOptions, fields,
  () => {
      this.updateDynamicFilters();
      this.render();
    }, true, true, totalFields, "fields", (term) => {
      const brData = App.blastRadiusData;
      if (!brData || !brData.keptFieldInfoTable) return {
        options: [],
        total: 0
      };
      const typeRefMap = new Map();
      brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
        .javaDescriptor));
      const fieldRefMap = new Map();
      brData.fieldReferenceTable.forEach(f => fieldRefMap.set(f.id, f));

      const filtered = brData.keptFieldInfoTable.filter(f => {
        const fieldRef = fieldRefMap.get(f.fieldReferenceId);
        const name = formatFieldName(fieldRef, brData, typeRefMap);
        return name.toLowerCase().includes(term);
      });
      const results = filtered.slice(0, 1000).map(f => {
        const fieldRef = fieldRefMap.get(f.fieldReferenceId);
        const name = escapeHTML(formatFieldName(fieldRef, brData,
          typeRefMap));
        return {
          name,
          value: f.id
        };
      });
      return {
        options: results,
        total: filtered.length
      };
    });
    UIUtils.renderChipText(this.elements.fieldText, UIUtils.getFilterLabel(
      "Fields", fields, totalFields, fieldOptions), "field", true);
    UIUtils.toggleVisibility(this.elements.fieldChip, chips.includes(
    "field"));

    // --- 4. Methods Filter ---
    const {
      options: methodOptions,
      total: totalMethods
    } = this.getKeptMethods();
    UIUtils.buildActionDropdown("method-dropdown", methodOptions, methods,
    () => {
        this.updateDynamicFilters();
        this.render();
      }, true, true, totalMethods, "methods", (term) => {
        const brData = App.blastRadiusData;
        if (!brData || !brData.keptMethodInfoTable) return {
          options: [],
          total: 0
        };
        const typeRefMap = new Map();
        brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
          .javaDescriptor));
        const methodRefMap = new Map();
        brData.methodReferenceTable.forEach(m => methodRefMap.set(m.id, m));

        const filtered = brData.keptMethodInfoTable.filter(m => {
          const methodRef = methodRefMap.get(m.methodReferenceId);
          const name = formatMethodName(methodRef, brData, typeRefMap);
          return name.toLowerCase().includes(term);
        });
        const results = filtered.slice(0, 1000).map(m => {
          const methodRef = methodRefMap.get(m.methodReferenceId);
          const name = escapeHTML(formatMethodName(methodRef, brData,
            typeRefMap));
          return {
            name,
            value: m.id
          };
        });
        return {
          options: results,
          total: filtered.length
        };
      });
    UIUtils.renderChipText(this.elements.methodText, UIUtils.getFilterLabel(
      "Methods", methods, totalMethods, methodOptions), "method", true);
    UIUtils.toggleVisibility(this.elements.methodChip, chips.includes(
      "method"));

    // --- Add Filter Button Logic ---
    const availableFilters = [];
    if (!chips.includes("class")) availableFilters.push({
      name: "Classes",
      value: "class",
      options: classOptions,
      total: totalClasses,
      state: classes
    });
    if (!chips.includes("field")) availableFilters.push({
      name: "Fields",
      value: "field",
      options: fieldOptions,
      total: totalFields,
      state: fields
    });
    if (!chips.includes("method")) availableFilters.push({
      name: "Methods",
      value: "method",
      options: methodOptions,
      total: totalMethods,
      state: methods
    });

    if (availableFilters.length > 0) {
      this.elements.addFilterContainer.classList.remove("hidden");
      this.elements.addFilterList.innerHTML = availableFilters
        .map(f =>
          `<a href="#" class="dropdown-item add-filter-option" data-value="${f.value}">${f.name}</a>`
          )
        .join("");

      this.elements.addFilterList.querySelectorAll(".add-filter-option")
        .forEach(item => {
          item.addEventListener("click", (e) => {
            e.preventDefault();
            e.stopPropagation();
            const val = item.dataset.value;
            if (!this.state.activeFilterChips.includes(val)) {
              this.state.activeFilterChips.push(val);
            }

            // Close the "Add filter" dropdown
            this.elements.addFilterDropdown.classList.add("hidden");
            this.elements.addFilterBtn.classList.remove("bg-gray-200");

            this.updateDynamicFilters();
            this.render();

            // Auto-open newly added dropdown
            setTimeout(() => {
              if (val === "class") this.toggleDropdown(this.elements
                .clsDropdown, this.elements.clsBtn);
              if (val === "field") this.toggleDropdown(this.elements
                .fieldDropdown, this.elements.fieldBtn);
              if (val === "method") this.toggleDropdown(this
                .elements.methodDropdown, this.elements.methodBtn);
            }, 0);
          });
        });
    } else {
      this.elements.addFilterContainer.classList.add("hidden");
    }
  },

  getKeptClasses() {
    const brData = App.blastRadiusData;
    if (!brData || !brData.keptClassInfoTable) return {
      options: [],
      total: 0
    };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
      .javaDescriptor));

    const options = brData.keptClassInfoTable.slice(0, 1000).map(c => {
      const name = escapeHTML(formatDescriptor(typeRefMap.get(c
        .classReferenceId)));
      return {
        name,
        value: c.id
      };
    });
    return {
      options,
      total: brData.keptClassInfoTable.length
    };
  },

  getKeptFields() {
    const brData = App.blastRadiusData;
    if (!brData || !brData.keptFieldInfoTable) return {
      options: [],
      total: 0
    };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
      .javaDescriptor));
    const fieldRefMap = new Map();
    brData.fieldReferenceTable.forEach(f => fieldRefMap.set(f.id, f));

    const options = brData.keptFieldInfoTable.slice(0, 1000).map(f => {
      const fieldRef = fieldRefMap.get(f.fieldReferenceId);
      const name = escapeHTML(formatFieldName(fieldRef, brData,
        typeRefMap));
      return {
        name,
        value: f.id
      };
    });
    return {
      options,
      total: brData.keptFieldInfoTable.length
    };
  },

  getKeptMethods() {
    const brData = App.blastRadiusData;
    if (!brData || !brData.keptMethodInfoTable) return {
      options: [],
      total: 0
    };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach(t => typeRefMap.set(t.id, t
      .javaDescriptor));
    const methodRefMap = new Map();
    brData.methodReferenceTable.forEach(m => methodRefMap.set(m.id, m));

    const options = brData.keptMethodInfoTable.slice(0, 1000).map(m => {
      const methodRef = methodRefMap.get(m.methodReferenceId);
      const name = escapeHTML(formatMethodName(methodRef, brData,
        typeRefMap));
      return {
        name,
        value: m.id
      };
    });
    return {
      options,
      total: brData.keptMethodInfoTable.length
    };
  },

  toggleDropdown(dropdown, triggerBtn = null) {
    const allHelpers = [{
        dd: this.elements.grpDropdown,
        btn: this.elements.grpBtn
      },
      {
        dd: this.elements.modDropdown,
        btn: this.elements.modBtn
      },
      {
        dd: this.elements.clsDropdown,
        btn: this.elements.clsBtn
      },
      {
        dd: this.elements.fieldDropdown,
        btn: this.elements.fieldBtn
      },
      {
        dd: this.elements.methodDropdown,
        btn: this.elements.methodBtn
      },
      {
        dd: this.elements.addFilterDropdown,
        btn: this.elements.addFilterBtn
      },
      {
        dd: this.elements.lensFilterDropdown,
        btn: this.elements.lensFilterBtn
      },
    ];

    const isOpening = dropdown.classList.contains("hidden");

    // Close all others first
    allHelpers.forEach(({
      dd,
      btn
    }) => {
      if (dd !== dropdown) {
        if (dd) dd.classList.add("hidden");
        if (btn) btn.classList.remove("bg-gray-200");
      }
    });

    if (isOpening) {
      dropdown.classList.remove("hidden");
      if (triggerBtn) triggerBtn.classList.add("bg-gray-200");
    } else {
      dropdown.classList.add("hidden");
      if (triggerBtn) triggerBtn.classList.remove("bg-gray-200");
    }
  },

  bindEvents() {
    // --- Dropdown Management ---
    const map = [{
        btn: this.elements.grpBtn,
        dd: this.elements.grpDropdown
      },
      {
        btn: this.elements.modBtn,
        dd: this.elements.modDropdown
      },
      {
        btn: this.elements.clsBtn,
        dd: this.elements.clsDropdown
      },
      {
        btn: this.elements.fieldBtn,
        dd: this.elements.fieldDropdown
      },
      {
        btn: this.elements.methodBtn,
        dd: this.elements.methodDropdown
      },
      {
        btn: this.elements.addFilterBtn,
        dd: this.elements.addFilterDropdown
      },
      {
        btn: this.elements.lensFilterBtn,
        dd: this.elements.lensFilterDropdown
      },
    ];

    map.forEach(({
      btn,
      dd
    }) => {
      if (btn && dd) {
        btn.addEventListener("click", (e) => {
          if (e.target.closest(".chip-close")) return;
          e.stopPropagation();
          this.toggleDropdown(dd, btn);
          this.updateDynamicFilters();
        });
      }
    });

    // Global click listener to close dropdowns
    document.addEventListener("click", (e) => {
      let anyClosed = false;
      map.forEach(({
        dd,
        btn
      }) => {
        if (dd && !dd.contains(e.target) && (!btn || !btn.contains(e
            .target))) {
          if (!dd.classList.contains("hidden")) {
            dd.classList.add("hidden");
            if (btn) btn.classList.remove("bg-gray-200");
            anyClosed = true;
          }
        }
      });
      if (anyClosed) {
        this.updateDynamicFilters();
      }
    });

    // Chip removals
    document.getElementById("filter-chips-container").addEventListener(
      "click", (e) => {
        const closeBtn = e.target.closest(".chip-close");
        if (closeBtn) {
          e.stopPropagation();
          const type = closeBtn.dataset.clear;
          if (type === "module") {
            this.state.filters.keepRules = [];
            this.state.activeFilterChips = this.state.activeFilterChips
              .filter(c => c !== "module");
          } else if (type === "class") {
            this.state.filters.classes = [];
            this.state.activeFilterChips = this.state.activeFilterChips
              .filter(c => c !== "class");
          } else if (type === "field") {
            this.state.filters.fields = [];
            this.state.activeFilterChips = this.state.activeFilterChips
              .filter(c => c !== "field");
          } else if (type === "method") {
            this.state.filters.methods = [];
            this.state.activeFilterChips = this.state.activeFilterChips
              .filter(c => c !== "method");
          }
          this.updateDynamicFilters();
          this.render();
        }
      });


    // --- Group By Dropdown ---
    this.elements.grpDropdown.addEventListener("click", (e) => {
      const item = e.target.closest(".dropdown-item");
      if (!item) return;
      e.preventDefault();
      this.state.currentView = item.dataset.value;
      this.state.drillContext = {
        module: null,
        pkg: null
      }; // Reset Drill-Down

      // Reset default sort based on view
      if (this.state.currentView === CONSTANTS.VIEWS.CLASSES ||
        this.state.currentView === CONSTANTS.VIEWS.FIELDS ||
        this.state.currentView === CONSTANTS.VIEWS.METHODS) {
        this.state.sort = {
          by: "name",
          order: "asc"
        };
      } else {
        this.state.sort = {
          by: "matches.total",
          order: "desc"
        };
      }

      this.toggleDropdown(this.elements.grpDropdown, this.elements
      .grpBtn);
      this.render();
    });

    // --- Table Sorting ---
    this.elements.tableHeaders.addEventListener("click", (e) => {
      const th = e.target.closest("[data-sort-by]");
      if (!th) return;
      const newSortBy = th.dataset.sortBy;
      if (this.state.sort.by === newSortBy)
        this.state.sort.order = this.state.sort.order === "asc" ? "desc" :
        "asc";
      else {
        this.state.sort.by = newSortBy;
        this.state.sort.order = "desc";
      }
      this.render();
    });

    // --- Table Row Interaction (Manual Drill-Down) ---
    this.elements.tableData.addEventListener("click", (e) => {
      const ruleTd = e.target.closest("td[data-rule-id]");
      if (ruleTd) {
        e.preventDefault();
        App.showDetailsView(ruleTd.dataset.ruleId);
        return;
      }

      const fileTd = e.target.closest("td[data-file-origin-id]");
      if (fileTd) {
        e.preventDefault();
        App.showFileDetailsView(fileTd.dataset.fileOriginId);
        return;
      }

      const td = e.target.closest("td[data-name]");
      if (!td) return;

      const {
        name,
        type,
        moduleName
      } = td.dataset;

      // Update Drill-Context (Silent)
      if (type === "module") {
        this.state.drillContext.module = name;
        this.state.drillContext.pkg = null;
      } else if (type === "package") {
        this.state.drillContext.module = moduleName || this.state
          .drillContext.module;
        this.state.drillContext.pkg = name;
      }

      this.updateBreadcrumbs();
      this.render();
    });
  },

  /**
   * Core Data Pipeline: Filters and Flattens data based on current View Mode and Filters.
   */
  applyKeepRuleLens(rules, lens) {
    const brData = App.blastRadiusData;
    if (!lens || !brData) return rules;

    const ruleMap = new Map();
    brData.keepRuleBlastRadiusTable.forEach(r => {
      ruleMap.set(r.id, r);
    });

    return rules.filter(r => {
      const getSubsumedBy = (rule) => rule.subsumedBy || (rule
        .blastRadius && rule.blastRadius.subsumedBy) || [];
      const getMatchesTotal = (rule) => {
        if (rule.matches && rule.matches.total !== undefined)
        return rule.matches.total;
        const b = rule.blastRadius || {};
        return (b.classBlastRadius || []).length + (b
          .fieldBlastRadius || []).length + (b.methodBlastRadius ||
        []).length;
      };

      const subsumedBy = getSubsumedBy(r);

      if (lens === "Identical") {
        const identical = subsumedBy.filter(otherId => {
          const other = ruleMap.get(otherId);
          return other && (other.blastRadius?.subsumedBy || [])
            .includes(r.id);
        }).map(otherId => ruleMap.get(otherId));

        if (identical.length > 0) {
          r.identicalRules = identical;
          return true;
        }
        return false;
      } else if (lens === "Subsumed") {
        if (subsumedBy.length > 0) {
          r.subsumedByRules = subsumedBy.map(id => ruleMap.get(id));
          return true;
        }
        return false;
      } else if (lens === "Unused") {
        return getMatchesTotal(r) === 0;
      }
      return true;
    });
  },

  getFilteredData() {
    const {
      currentView,
      filters
    } = this.state;
    const brData = App.blastRadiusData;
    if (!brData) return [];

    let data = [];
    if (currentView === CONSTANTS.VIEWS.PACKAGES) {
      data = getRuleFiles(brData).filter(f => f.keepRules > 0);
    } else {
      data = getRules(brData);
      // Default: hide rules with zero matches, unless the Unused lens is applied.
      if (filters.keepRules[0] !== "Unused") {
        data = data.filter(r => r.matches.total > 0);
      }
    }

    // Apply Keep Rules Lens (Filtering)
    if (currentView === CONSTANTS.VIEWS.MODULES && filters.keepRules.length >
      0) {
      data = this.applyKeepRuleLens(data, filters.keepRules[0]);
    }

    // Apply Class/Field/Method filters
    if (filters.classes.length > 0 || filters.fields.length > 0 || filters
      .methods.length > 0) {
      const matchedRuleIds = new Set();

      if (filters.classes.length > 0) {
        brData.keptClassInfoTable.forEach(c => {
          if (filters.classes.includes(c.id)) {
            (c.keptBy || []).forEach(rid => matchedRuleIds.add(rid));
          }
        });
      }
      if (filters.fields.length > 0) {
        brData.keptFieldInfoTable.forEach(f => {
          if (filters.fields.includes(f.id)) {
            (f.keptBy || []).forEach(rid => matchedRuleIds.add(rid));
          }
        });
      }
      if (filters.methods.length > 0) {
        brData.keptMethodInfoTable.forEach(m => {
          if (filters.methods.includes(m.id)) {
            (m.keptBy || []).forEach(rid => matchedRuleIds.add(rid));
          }
        });
      }

      data = data.filter(r => matchedRuleIds.has(r.id));
    }

    return data;
  },

  getSortedData(data) {
    const {
      by,
      order
    } = this.state.sort;
    if (!by) return data;

    const getVal = (obj, path) => {
      return path.split('.').reduce((o, key) => (o && o[key] !==
        undefined) ? o[key] : 0, obj);
    };

    return [...data].sort((a, b) => {
      const vA = getVal(a, by);
      const vB = getVal(b, by);

      if (typeof vA === "string")
        return order === "asc" ? vA.localeCompare(vB) : vB.localeCompare(
          vA);
      return order === "asc" ? (vA || 0) - (vB || 0) : (vB || 0) - (vA ||
        0);
    });
  },

  updateBreadcrumbs() {
    const bc = document.getElementById("flat-breadcrumbs");
    if (!bc) return;

    const linkClass = "breadcrumb-pill";
    const textClass = "breadcrumb-text";
    const sep = '<span class="text-gray-300 mx-1">/</span>';

    let html = "";
    const {
      module,
      pkg: pkg
    } = this.state.drillContext;

    const toggleText = this.state.statsVisible ? "Hide Summary" :
      "Show Summary";
    const toggleHtml = `
          <button class="dropdown-btn" data-action="toggle-stats" style="border: none; background: transparent; padding: 0.25rem 0.5rem; display: flex; align-items: center; border-radius: 4px; margin-left: auto; cursor: pointer;">
            <span style="font-weight: 500; color: var(--text-gray-400);">${toggleText}</span>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"
              stroke="currentColor" style="margin-left: 0.25rem; color: var(--text-gray-500); transform: ${this.state.statsVisible ? 'rotate(180deg)' : 'rotate(0deg)'}; transition: transform 0.2s;">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
        `;

    if (module) {
      // Drilled Down Path
      html += `<span class="${linkClass}" data-action="reset">Summary</span>`;
      html += sep;
      if (pkg) {
        html +=
          `<span class="${linkClass}" data-action="module" data-val="${escapeHTML(module)}">${escapeHTML(module)}</span>`;
        html += sep;
        html += `<span class="${textClass}">${escapeHTML(pkg)}</span>`;
      } else {
        html += `<span class="${textClass}">${escapeHTML(module)}</span>`;
      }
    } else {
      // Global View
      html = `<span class="${textClass}">Summary</span>`;
    }

    bc.style.display = "flex";
    bc.style.alignItems = "center";
    bc.style.width = "100%";
    bc.innerHTML = html + toggleHtml;

    // Breadcrumb Click Handlers
    bc.querySelectorAll("[data-action]").forEach((el) => {
      el.addEventListener("click", (e) => {
        const action = el.dataset.action;
        if (action === "reset") {
          this.state.drillContext = {
            module: null,
            pkg: null
          };
          this.render();
        } else if (action === "module") {
          this.state.drillContext.pkg = null;
          this.render();
        } else if (action === "toggle-stats") {
          this.state.statsVisible = !this.state.statsVisible;
          this.render();
        }
      });
    });
  },

  render() {
    this.updateBreadcrumbs();

    if (this.state.currentView === CONSTANTS.VIEWS.FILE_DETAILS) {
      App.renderFileDetailsView(this.state.drillContext.fileOriginId);
      return;
    }

    if (this.state.currentView === CONSTANTS.VIEWS.DETAILS) {
      return;
    }

    // Update Label of Group Dropdown
    const currentView = this.state.currentView;
    const viewLabels = {
      modules: "Keep Rules",
      packages: "Keep Rule Files"
    };
    this.elements.grpText.textContent = viewLabels[currentView] || (
      currentView.charAt(0).toUpperCase() + currentView.slice(1));

    if (this.elements.grpList) {
      this.elements.grpList.querySelectorAll(".dropdown-item").forEach((
      el) => {
        if (el.dataset.value === currentView)
          el.classList.add("bg-gray-100", "text-gray-900",
            "font-semibold");
        else el.classList.remove("bg-gray-100", "text-gray-900",
          "font-semibold");
      });
    }

    // Process Data
    let data = this.getFilteredData();
    data = this.getSortedData(data);

    // Update Summary Counts
    let relevantClasses = [],
      modCount = 0;
    if (currentView === CONSTANTS.VIEWS.PACKAGES) {
      relevantClasses = data.flatMap((p) => p.classes || []);
      modCount = new Set(data.map((p) => p.moduleName)).size;
    } else {
      relevantClasses = data.flatMap((m) => (m.packages || []).flatMap((p) =>
        p.classes || []));
      modCount = data.length;
    }

    // Stats table visibility
    if (this.elements.statsContainer) {
      this.elements.statsContainer.style.display = this.state.statsVisible ?
        "block" : "none";
      this.elements.statsContainer.style.marginBottom = this.state
        .statsVisible ? "2rem" : "0";
    }

    const brData = App.blastRadiusData;
    const totalLive = getLiveItemCount(brData);
    const formatPerc = (disallowCount) => totalLive > 0 ? (100 - (
      disallowCount / totalLive * 100)).toFixed(1) + '%' : '--';
    const getPerc = (disallowCount) => totalLive > 0 ? (100 - (disallowCount /
      totalLive * 100)) : '--';

    const setStatValue = (element, disallowCount) => {
      if (!brData) {
        element.textContent = '--';
        element.className = 'stat-value ' + UIUtils.getScoreClass('--');
        return;
      }
      const perc = getPerc(disallowCount);
      element.textContent = formatPerc(disallowCount);
      element.className = 'stat-value ' + UIUtils.getScoreClass(perc);
    };

    setStatValue(this.elements.totalObfuscation, getDisallowObfuscationCount(
      brData));
    setStatValue(this.elements.totalOptimization,
      getDisallowOptimizationCount(brData));
    setStatValue(this.elements.totalShrinking, getDisallowShrinkingCount(
      brData));

    // Render Tables
    this.renderHeaders();
    this.renderFlatRows(data);
    this.renderStatsTable();
  },

  renderStatsTable() {
    const brData = App.blastRadiusData;
    if (!brData) return;

    const stats = getDetailedStats(brData);
    if (!stats) return;

    const getHeaderCell = (label, disallowCount, total) => {
      const perc = total > 0 ? (100 - (disallowCount / total * 100)) : 100;
      const colorClass = UIUtils.getScoreClass(perc);
      return `
            <th class="text-center border-l border-gray-200" style="padding-bottom: 0.5rem; padding-top: 1rem; background-color: var(--bg-gray-50);">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span style="color: var(--text-gray-900);">${label}</span>
                <span class="text-sm font-bold ${colorClass} mt-1">${perc.toFixed(1)}%</span>
              </div>
            </th>
          `;
    };

    // Render Headers
    this.elements.statsTableHeaders.innerHTML = `
          <tr>
            <th class="text-left sticky-name bg-gray-50 z-30" style="padding: 1rem;">Category</th>
            ${getHeaderCell("Obfuscation Score", stats.overall.obfuscation, stats.overall.total)}
            ${getHeaderCell("Optimization Score", stats.overall.optimization, stats.overall.total)}
            ${getHeaderCell("Shrinking Score", stats.overall.shrinking, stats.overall.total)}
          </tr>
        `;

    const renderCell = (disallowCount, total) => {
      const perc = total > 0 ? (100 - (disallowCount / total * 100)) : 100;
      const colorClass = UIUtils.getScoreClass(perc);
      return `
            <td class="text-center border-l border-gray-200">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500">${total - disallowCount}/${total}</span>
              </div>
            </td>
          `;
    };

    const rows = [{
        label: "Classes",
        data: stats.classes
      },
      {
        label: "Fields",
        data: stats.fields
      },
      {
        label: "Methods",
        data: stats.methods
      }
    ];

    this.elements.statsTableData.innerHTML = rows.map(row => `
          <tr class="table-row border-b border-gray-200 hover:bg-gray-50">
            <td class="sticky-name font-medium text-gray-900" style="padding: 1rem;">${row.label}</td>
            ${renderCell(row.data.obfuscation, row.data.total)}
            ${renderCell(row.data.optimization, row.data.total)}
            ${renderCell(row.data.shrinking, row.data.total)}
          </tr>
        `).join("");
  },

  renderHeaders() {
    const {
      currentView,
      sort
    } = this.state;
    const topHeader = document.createElement("tr");
    const subHeader = document.createElement("tr");

    const viewLabels = {
      modules: "Rule",
      packages: "Keep Rule Files"
    };
    const title = viewLabels[currentView];

    const ind = (key) => {
      if (sort.by !== key) return "";
      return sort.order === "asc" ?
        `<span class="sort-icon text-blue-600 ml-1">▲</span>` :
        `<span class="sort-icon text-blue-600 ml-1">▼</span>`;
    };

    if (currentView === CONSTANTS.VIEWS.PACKAGES) {
      topHeader.innerHTML = `
            <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 400px; min-width: 400px;">
              <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                  ${title}${ind("name")}
              </div>
            </th>
            <th rowspan="2" class="text-center bg-gray-50 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="keepRules" style="padding: 1rem; width: 100px; min-width: 100px;">Keep Rules${ind("keepRules")}</th>
            <th rowspan="2" class="text-center border-l border-gray-200 bg-gray-50 cursor-pointer hover:bg-gray-100" data-sort-by="matches.total" style="padding: 0.5rem; width: 80px; min-width: 80px;">Kept Items${ind("matches.total")}</th>
            <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.obfuscation" style="padding: 0.5rem; width: 80px; min-width: 80px;">Obfuscation Score${ind("impact.obfuscation")}</th>
            <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.optimization" style="padding: 0.5rem; width: 80px; min-width: 80px;">Optimization Score${ind("impact.optimization")}</th>
            <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.shrinking" style="padding: 0.5rem; width: 80px; min-width: 80px;">Shrinking Score${ind("impact.shrinking")}</th>
            <th rowspan="2" class="bg-gray-50" style="width: 100%;"></th>
          `;
      subHeader.innerHTML = ``;
    } else {
      const lens = this.state.filters.keepRules[0];
      const isIdenticalLens = currentView === CONSTANTS.VIEWS.MODULES &&
        lens === "Identical";
      const isSubsumedLens = currentView === CONSTANTS.VIEWS.MODULES &&
        lens === "Subsumed";

      if (isIdenticalLens || isSubsumedLens) {
        topHeader.innerHTML = `
              <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 600px; min-width: 600px;">
                <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                    ${title}${ind("name")}
                </div>
              </th>
              <th rowspan="2" class="text-left bg-gray-50 border-l border-gray-200" style="padding: 1rem; width: 100%;">${isIdenticalLens ? 'Identical Rules' : 'Subsumed By'}</th>
            `;
        subHeader.innerHTML = '';
      } else {
        topHeader.innerHTML = `
              <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 600px; min-width: 600px;">
                <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                    ${title}${ind("name")}
                </div>
              </th>
              <th colspan="4" class="text-center border-l border-gray-200 bg-gray-50" style="padding: 0.5rem;">Kept Items (higher is worse)</th>
              <th rowspan="2" colspan="3" class="text-center border-l border-gray-200 bg-gray-50" style="padding: 0.5rem; width: 150px; min-width: 150px;">Steps blocked by rule</th>
              <th rowspan="2" class="bg-gray-50" style="width: 100%;"></th>
            `;

        subHeader.innerHTML = `
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="matches.total" style="padding: 0.5rem; width: 100px; min-width: 100px;">Total${ind("matches.total")}</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="matches.classes" style="padding: 0.5rem; width: 100px; min-width: 100px;">Classes${ind("matches.classes")}</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="matches.fields" style="padding: 0.5rem; width: 100px; min-width: 100px;">Fields${ind("matches.fields")}</th>
              <th class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="matches.methods" style="padding: 0.5rem; width: 100px; min-width: 100px;">Methods${ind("matches.methods")}</th>
            `;
      }
    }

    this.elements.tableHeaders.innerHTML = "";
    this.elements.tableHeaders.append(topHeader, subHeader);
  },

  renderFlatRows(data) {
    const {
      currentView
    } = this.state;
    if (!data.length) {
      this.elements.tableData.innerHTML =
        `<tr><td colspan="10" class="text-center py-8 text-gray-500">No results found.</td></tr>`;
      return;
    }

    const brData = App.blastRadiusData;
    const build = brData?.buildInfo || {};
    const totalLive = getLiveItemCount(brData);

    const renderMatchCell = (count, total) => {
      const perc = total > 0 ? (count / total * 100) : 0;
      const colorClass = UIUtils.getMatchClass(perc);
      return `
            <td class="text-center border-l border-gray-200" style="padding: 1rem; width: 100px; min-width: 100px;">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500 mt-1">${count}</span>
              </div>
            </td>
          `;
    };

    const renderScoreCell = (disallowCount, total) => {
      const perc = total > 0 ? (100 - (disallowCount / total * 100)) : 100;
      const colorClass = UIUtils.getScoreClass(perc);
      return `
            <td class="text-center border-l border-gray-200" style="padding: 1rem; width: 80px; min-width: 80px;">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span class="font-bold ${colorClass}">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500 mt-1">${total - disallowCount}/${total}</span>
              </div>
            </td>
          `;
    };

    this.elements.tableData.innerHTML = data.map((item) => {
      const cleanedName = item.name.replace(/\s+/g, ' ').trim();
      const escapedName = escapeHTML(cleanedName);
      let nameCell = "";
      if (currentView === CONSTANTS.VIEWS.MODULES) {
        nameCell = `
              <td class="sticky-name font-medium text-blue-600 hover:underline cursor-pointer" title="${escapedName}" style="padding: 1rem; width: 600px; min-width: 600px; font-family: var(--font-family-mono);" data-rule-id="${item.id}">
                <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapedName}</pre>
              </td>`;
      } else if (currentView === CONSTANTS.VIEWS.PACKAGES) {
        nameCell = `
              <td class="sticky-name font-medium text-blue-600 hover:underline cursor-pointer" title="${escapedName}" style="padding: 1rem; width: 400px; min-width: 400px;" data-file-origin-id="${item.id}">
                <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapedName}</pre>
              </td>`;
      } else {
        nameCell =
          `<td class="sticky-name font-medium text-gray-900" title="${escapedName}" style="padding: 1rem; width: 400px; min-width: 400px;"><pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0;">${escapedName}</pre></td>`;
      }

      if (currentView === CONSTANTS.VIEWS.PACKAGES) {
        const keepRulesCell =
          `<td class="text-center border-l border-gray-200 text-sm font-semibold" style="padding: 1rem; width: 100px; min-width: 100px;">${item.keepRules}</td>`;
        const totalKeptCell = renderMatchCell(item.matches.total,
          totalLive);
        const impactCells = `
              ${renderScoreCell(item.impact.obfuscation, totalLive)}
              ${renderScoreCell(item.impact.optimization, totalLive)}
              ${renderScoreCell(item.impact.shrinking, totalLive)}
            `;
        return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${keepRulesCell}${totalKeptCell}${impactCells}<td></td></tr>`;
      } else {
        const lens = this.state.filters.keepRules[0];
        const isIdenticalLens = currentView === CONSTANTS.VIEWS.MODULES &&
          lens === "Identical";
        const isSubsumedLens = currentView === CONSTANTS.VIEWS.MODULES &&
          lens === "Subsumed";

        if (isIdenticalLens || isSubsumedLens) {
          const rules = isIdenticalLens ? item.identicalRules : item
            .subsumedByRules;
          const rulesHtml = (rules || []).map(r => `
                <div style="font-family: var(--font-family-mono); font-size: 0.8125rem; margin-bottom: 0.25rem;">
                  <pre style="white-space: pre-wrap; margin: 0; color: var(--text-main);">${escapeHTML(r.source)}</pre>
                </div>
              `).join("");
          return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}<td class="border-l border-gray-200" style="padding: 1rem; vertical-align: top; width: 100%; font-family: var(--font-family-mono);">${rulesHtml}</td></tr>`;
        }

        const impact = item.impact || [];
        const getTag = (c, label) => {
          const isRestricted = impact.includes(c);
          if (!isRestricted) return "";
          const color = "var(--text-red-600)";
          const bgColor = "var(--bg-red-light)";
          return `<span class="impact-tag" style="color: ${color}; background-color: ${bgColor}; border-color: ${color}; opacity: 0.8;">${label}</span>`;
        };

        const impactCell = `
              <td class="text-center border-l border-gray-200" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OBFUSCATE', 'OBFUSCATE')}</td>
              <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_OPTIMIZE', 'OPTIMIZE')}</td>
              <td class="text-center border-l border-gray-100" style="padding: 0.5rem; width: 50px; min-width: 50px;">${getTag('DONT_SHRINK', 'SHRINK')}</td>
            `;

        const matchesCells = `
              ${renderMatchCell(item.matches.total, totalLive)}
              ${renderMatchCell(item.matches.classes, build.liveClassCount || 0)}
              ${renderMatchCell(item.matches.fields, build.liveFieldCount || 0)}
              ${renderMatchCell(item.matches.methods, build.liveMethodCount || 0)}
            `;
        return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${matchesCells}${impactCell}<td></td></tr>`;
      }
    }).join("");
  },
};

document.addEventListener("DOMContentLoaded", () => App.init());