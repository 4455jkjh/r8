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
    FILE_DETAILS: "file-details",
  },
  DEFAULTS: {
    AGGREGATED: "Aggregated",
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
  buildActionDropdown(
    containerId,
    options,
    selectedStateArr,
    onSelectionChange,
    searchable = true,
    multiSelect = true,
    totalCount = null,
    itemName = "items",
    searchCallback = null,
  ) {
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
        listZone.innerHTML = `<div class="p-4 text-xs text-gray-400 text-center">No options available</div>`;
        return;
      }
      optionsToRender.forEach((opt) => {
        // SKIP "All" options if they exist in the passed options list.
        if (opt.value === "all") return;
        const isChecked = selectedStateArr.includes(opt.value);
        const item = document.createElement(multiSelect ? "label" : "div");
        item.className =
          "popover-item" + (isChecked && !multiSelect ? " active-item" : "");
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
      footer.textContent = `Showing ${options.length} out of ${totalCount} ${itemName}`;
      container.appendChild(footer);
    }
    // --- Search Logic ---
    if (searchInput) {
      searchInput.addEventListener("input", (e) => {
        const term = e.target.value.toLowerCase();
        if (searchCallback) {
          const { options: filteredOptions, total: mCount } =
            searchCallback(term);
          renderList(filteredOptions);
          if (footer) {
            footer.textContent = `Showing ${filteredOptions.length} out of ${mCount} ${itemName}`;
          }
        } else {
          const items = listZone.querySelectorAll(".popover-item");
          let visibleCount = 0;
          items.forEach((item) => {
            const label = item.querySelector("span").textContent.toLowerCase();
            const isVisible = label.includes(term);
            item.style.display = isVisible ? "flex" : "none";
            if (isVisible) visibleCount++;
          });
          if (footer && totalCount !== null) {
            footer.textContent = `Showing ${visibleCount} out of ${totalCount} ${itemName}`;
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
    if (
      selectedArr.length === 0 ||
      (totalCount > 0 && selectedArr.length === totalCount)
    )
      return `${prefix}: All`;
    if (selectedArr.length === 1) {
      const val = selectedArr[0];
      const opt = options.find((o) => String(o.value) === String(val));
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
    const lastSlash = prefix.lastIndexOf("/");
    if (lastSlash !== -1) {
      return prefix.substring(0, lastSlash + 1);
    }
    return "";
  },
};
/* ==========================================================================
   MAIN APP CONTROLLER
   ========================================================================== */
const App = {
  keepRadiusData: null,
  lookups: {},
  detailsState: {
    ruleId: null,
    classQuery: "",
    fieldQuery: "",
    methodQuery: "",
  },
  init() {
    // Initialize Report (Main Grid)
    ReportApp.init();
    // Load Protobuf Data in the Background
    this.loadProtoData();
    const headerLink = document.getElementById("header-link");
    if (headerLink) {
      headerLink.addEventListener("click", (e) => {
        e.preventDefault();
        this.showReportView();
      });
    }
    // Setup Kept Lists Search listeners
    const setupListSearch = (type, stateKey) => {
      const toggleBtn = document.querySelector(
        `.search-toggle-btn[data-target="${type}"]`,
      );
      const container = document.getElementById(`${type}-search-container`);
      const input = document.getElementById(`${type}-search-input`);
      if (toggleBtn && container && input) {
        toggleBtn.addEventListener("click", () => {
          const isHidden = container.style.display === "none";
          container.style.display = isHidden ? "block" : "none";
          toggleBtn.classList.toggle("active", isHidden);
          if (isHidden) {
            input.focus();
          } else {
            // Clear query when closing search
            input.value = "";
            App.detailsState[stateKey] = "";
            const rule = App.keepRadiusData?.keepRuleKeepRadiusTable.find(
              (r) => r.id === parseInt(App.detailsState.ruleId),
            );
            if (rule) App.renderKeptLists(rule, type);
          }
        });
        input.addEventListener("input", (e) => {
          App.detailsState[stateKey] = e.target.value;
          const rule = App.keepRadiusData?.keepRuleKeepRadiusTable.find(
            (r) => r.id === parseInt(App.detailsState.ruleId),
          );
          if (rule) App.renderKeptLists(rule, type);
        });
      }
    };
    setupListSearch("classes", "classQuery");
    setupListSearch("fields", "fieldQuery");
    setupListSearch("methods", "methodQuery");
  },
  showDetailsView(ruleId) {
    this.detailsState.ruleId = ruleId;
    this.detailsState.classQuery = "";
    this.detailsState.fieldQuery = "";
    this.detailsState.methodQuery = "";
    ReportApp.state.currentView = CONSTANTS.VIEWS.DETAILS;
    document.getElementById("report-view").style.display = "none";
    document.getElementById("file-details-view").style.display = "none";
    document.getElementById("report-view-controls").style.display = "none";
    document.getElementById("details-view").style.display = "flex";
    const ruleContainer = document.getElementById("details-rule-container");
    const impactContainer = document.getElementById("details-impact-container");
    const identicalRulesBody = document.getElementById(
      "details-identical-rules-body",
    );
    const identicalRulesHeader = document.getElementById(
      "details-identical-rules-header",
    );
    const identicalRulesTitle = document.getElementById(
      "details-identical-rules-title",
    );
    const subsumedByBody = document.getElementById("details-subsumed-by-body");
    const subsumedByHeader = document.getElementById(
      "details-subsumed-by-header",
    );
    const subsumedByTitle = document.getElementById(
      "details-subsumed-by-title",
    );
    const impactHeader = document.getElementById("details-rule-impact-header");
    const classesContent = document.getElementById("details-classes-content");
    const methodsContent = document.getElementById("details-methods-content");
    const fieldsContent = document.getElementById("details-fields-content");
    let rule = this.keepRadiusData?.keepRuleKeepRadiusTable.find(
      (r) => r.id === parseInt(ruleId),
    );
    let isGlobal = false;
    if (!rule) {
      rule = this.keepRadiusData?.globalKeepRuleKeepRadiusTable.find(
        (r) => r.id === parseInt(ruleId),
      );
      isGlobal = true;
    }
    if (rule) {
      const fileOriginId = rule?.origin?.fileOriginId;
      const fileOrigin = this.keepRadiusData?.fileOriginTable.find(
        (f) => f.id === fileOriginId,
      );
      let originStr = "";
      if (fileOrigin) {
        const mavenName = formatMavenCoordinate(fileOrigin.mavenCoordinate);
        originStr = `${mavenName || fileOrigin.filename}:${rule.origin?.lineNumber || 1}`;
        if (mavenName) {
          originStr += ` (${fileOrigin.filename})`;
        }
      }

      if (ruleContainer) {
        const highlightedSource = highlightRule(rule.source);
        ruleContainer.innerHTML = `
      ${originStr ? `<div style="font-size: 0.75rem; color: var(--text-gray-500); margin-bottom: 0.5rem; font-family: var(--font-family-mono);">${escapeHTML(originStr)}</div>` : ""}
      <div style="background-color: var(--bg-body); border: 1px solid var(--border-color); border-radius: 4px; padding: 0.75rem;">
        <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; color: var(--text-main);">${highlightedSource}</pre>
      </div>
    `;
      }
      const impactContainer = document.getElementById(
        "details-impact-container",
      );
      const relatedRulesContainer = document.getElementById(
        "related-rules-container",
      );
      const gridContainer = document.getElementById("details-classes-content")
        ?.parentElement?.parentElement;
      if (isGlobal) {
        if (impactContainer)
          impactContainer.parentElement.style.display = "none";
        if (relatedRulesContainer) relatedRulesContainer.style.display = "none";
        if (gridContainer) gridContainer.style.display = "none";
      } else {
        if (impactContainer) impactContainer.parentElement.style.display = "";
        if (relatedRulesContainer) relatedRulesContainer.style.display = "";
        if (gridContainer) gridContainer.style.display = "grid";
        const constraintsMap = getConstraintsMap(this.keepRadiusData);
        const constraints = constraintsMap.get(rule.constraintsId) || [];
        const getTag = (c, label) => {
          const isRestricted = constraints.includes(c);
          if (!isRestricted) return "";
          const color = "oklch(0.446 0.043 257.281)";
          const bgColor = "oklch(0.984 0.003 247.858)";
          const borderColor = "oklch(0.929 0.013 255.508)";
          return `<span class="impact-tag" style="display: inline-block; color: ${color}; background-color: ${bgColor}; border: 1px solid ${borderColor}; border-radius: 4px; padding: 2px 8px; font-size: 10px; font-weight: 400; height: 21px; line-height: 15px; text-transform: uppercase; letter-spacing: 0.25px; box-sizing: border-box; text-align: center;">${label}</span>`;
        };
        const impactTagsHtml = `
          <div class="impact-container">
            ${getTag("DONT_OBFUSCATE", "OBFUSCATE")}
            ${getTag("DONT_OPTIMIZE", "OPTIMIZE")}
            ${getTag("DONT_SHRINK", "SHRINK")}
          </div>
        `;
        const br = rule.keepRadius || {};
        const classIds = br.classKeepRadius || [];
        const fieldIds = br.fieldKeepRadius || [];
        const methodIds = br.methodKeepRadius || [];
        const matchedTotal =
          classIds.length + fieldIds.length + methodIds.length;
        const totalLive = getLiveItemCount(this.keepRadiusData);
        const liveClasses = this.keepRadiusData?.buildInfo?.liveClassCount || 0;
        const liveFields = this.keepRadiusData?.buildInfo?.liveFieldCount || 0;
        const liveMethods =
          this.keepRadiusData?.buildInfo?.liveMethodCount || 0;
        const renderMatchCell = (count, total, borderLeft = true) => {
          const perc = total > 0 ? (count / total) * 100 : 0;
          const colorClass = UIUtils.getMatchClass(perc);
          const bl = borderLeft ? "border-l border-gray-200" : "";
          return `
            <td class="text-center ${bl}" style="padding: 1rem; width: 100px; min-width: 100px;">
              <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
                <span class="font-medium ${colorClass}">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500 mt-1">${count}</span>
              </div>
            </td>
          `;
        };
        if (impactContainer) {
          const getPerc = (count, total) =>
            total > 0 ? (count / total) * 100 : 0;
          const classPerc = getPerc(classIds.length, liveClasses);
          const fieldPerc = getPerc(fieldIds.length, liveFields);
          const methodPerc = getPerc(methodIds.length, liveMethods);
          impactContainer.innerHTML = `
        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 2rem; padding: 0; align-items: start;">
          <!-- Kept Classes -->
          <div>
            <div style="color: var(--text-gray-500); font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">Kept Classes</div>
            <div style="display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 0.5rem;">
              <span style="color: oklch(0.505 0.213 27.518); font-size: 1.25rem; font-weight: 500;">${classPerc.toFixed(1)}%</span>
              <span style="color: var(--text-gray-500); font-size: 0.75rem;">${classIds.length} / ${liveClasses}</span>
            </div>
            <div style="height: 4px; background-color: var(--bg-hover); border-radius: 2px; width: 100%;">
              <div style="height: 100%; background-color: oklch(0.505 0.213 27.518); border-radius: 2px; width: ${classPerc}%;"></div>
            </div>
          </div>
          <!-- Kept Fields -->
          <div>
            <div style="color: var(--text-gray-500); font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">Kept Fields</div>
            <div style="display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 0.5rem;">
              <span style="color: oklch(0.505 0.213 27.518); font-size: 1.25rem; font-weight: 500;">${fieldPerc.toFixed(1)}%</span>
              <span style="color: var(--text-gray-500); font-size: 0.75rem;">${fieldIds.length} / ${liveFields}</span>
            </div>
            <div style="height: 4px; background-color: var(--bg-hover); border-radius: 2px; width: 100%;">
              <div style="height: 100%; background-color: oklch(0.505 0.213 27.518); border-radius: 2px; width: ${fieldPerc}%;"></div>
            </div>
          </div>
          <!-- Kept Methods -->
          <div>
            <div style="color: var(--text-gray-500); font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">Kept Methods</div>
            <div style="display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 0.5rem;">
              <span style="color: oklch(0.505 0.213 27.518); font-size: 1.25rem; font-weight: 500;">${methodPerc.toFixed(1)}%</span>
              <span style="color: var(--text-gray-500); font-size: 0.75rem;">${methodIds.length} / ${liveMethods}</span>
            </div>
            <div style="height: 4px; background-color: var(--bg-hover); border-radius: 2px; width: 100%;">
              <div style="height: 100%; background-color: oklch(0.505 0.213 27.518); border-radius: 2px; width: ${methodPerc}%;"></div>
            </div>
          </div>
          <!-- Blocked by Rule -->
          <div>
            <div style="color: var(--text-gray-500); font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: .80rem;">Blocked by Rule</div>
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              ${impactTagsHtml}
            </div>
          </div>
        </div>
      `;
        }

        const allRules = this.keepRadiusData?.keepRuleKeepRadiusTable || [];
        const subsumingIds = br.subsumedBy || [];
        const identicalRules = [];
        const subsumedByRules = [];
        subsumingIds.forEach((id) => {
          const otherRule = allRules.find((r) => r.id === id);
          if (otherRule) {
            const otherSubsumedBy = otherRule.keepRadius?.subsumedBy || [];
            if (otherSubsumedBy.includes(rule.id)) {
              identicalRules.push(otherRule);
            } else {
              subsumedByRules.push(otherRule);
            }
          }
        });
        if (relatedRulesContainer) {
          const hasIdentical = identicalRules.length > 0;
          const hasSubsumed = subsumedByRules.length > 0;
          const renderDetailRuleRow = (r) => {
            const rBr = r.keepRadius || {};
            const rClassIds = rBr.classKeepRadius || [];
            const rFieldIds = rBr.fieldKeepRadius || [];
            const rMethodIds = rBr.methodKeepRadius || [];
            const totalLive = getLiveItemCount(this.keepRadiusData);
            const liveClasses =
              this.keepRadiusData?.buildInfo?.liveClassCount || 0;
            const liveFields =
              this.keepRadiusData?.buildInfo?.liveFieldCount || 0;
            const liveMethods =
              this.keepRadiusData?.buildInfo?.liveMethodCount || 0;
            const renderMatchCell = (count, total) => {
              const perc = total > 0 ? (count / total) * 100 : 0;
              return `
            <td class="text-center" style="padding: 0.5rem; border-left: 1px solid var(--border-color);">
              <div style="display: flex; flex-direction: column; align-items: center;">
                <span style="color: var(--text-red-600); font-weight: 500;">${perc.toFixed(1)}%</span>
                <span class="text-xs text-gray-500">${count}</span>
              </div>
            </td>
          `;
            };
            const constraintsMap = getConstraintsMap(this.keepRadiusData);
            const rConstraints = constraintsMap.get(r.constraintsId) || [];
            const getTag = (c, label) => {
              const isRestricted = rConstraints.includes(c);
              const color = isRestricted
                ? "oklch(0.446 0.043 257.281)"
                : "#cbd5e1";
              const bgColor = isRestricted
                ? "oklch(0.984 0.003 247.858)"
                : "#f8fafc";
              const borderColor = isRestricted
                ? "oklch(0.929 0.013 255.508)"
                : "#e2e8f0";
              return `<span class="impact-tag" style="display: inline-block; color: ${color}; background-color: ${bgColor}; border: 1px solid ${borderColor}; border-radius: 4px; padding: 2px 8px; font-size: 10px; font-weight: 400; height: 21px; line-height: 15px; text-transform: uppercase; letter-spacing: 0.25px; box-sizing: border-box; text-align: center;">${label}</span>`;
            };
            const impactCell = `
          <td style="padding: 1rem; border-left: 1px solid var(--border-color);">
            <div class="flex justify-start" style="gap: 0.5rem;">
              ${getTag("DONT_OBFUSCATE", "OBFUSCATE")}
              ${getTag("DONT_OPTIMIZE", "OPTIMIZE")}
              ${getTag("DONT_SHRINK", "SHRINK")}
            </div>
          </td>
        `;
            return `
          <tr class="table-row border-t border-gray-200 hover:bg-gray-50 cursor-pointer" onclick="App.showDetailsView('${r.id}')">
            <td style="padding: 0.5rem; width: 40%;">
              <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0;">${highlightRule(r.source)}</pre>
            </td>
            ${renderMatchCell(rClassIds.length + rFieldIds.length + rMethodIds.length, totalLive)}
            ${renderMatchCell(rClassIds.length, liveClasses)}
            ${renderMatchCell(rFieldIds.length, liveFields)}
            ${renderMatchCell(rMethodIds.length, liveMethods)}
            ${impactCell}
          </tr>
        `;
          };
          const renderSection = (title, rules, explainer) => {
            const hasRules = rules.length > 0;
            let sectionHtml = `
          <div class="table-container" style="display: flex; flex-direction: column;">
            <div style="padding: 0.75rem 1rem; border-bottom: 1px solid var(--border-color); background-color: var(--bg-subtle); display: flex; justify-content: space-between; align-items: center;">
              <div>
                <span style="font-size: 0.875rem; font-weight: 600;">${title}</span>
                <span style="color: var(--text-gray-500); font-size: 0.75rem; margin-left: 0.25rem;">· ${hasRules ? rules.length : "None"}</span>
              </div>
              ${hasRules ? `<span style="color: var(--text-gray-500); font-size: 0.75rem;">${explainer}</span>` : ""}
            </div>
        `;
            if (hasRules) {
              sectionHtml += `
            <table style="width: 100%; border-collapse: collapse;">
              <thead>
                <tr>
                  <th rowspan="2" class="text-left bg-gray-50" style="padding: 0 1rem; width: 40%;">RULE</th>
                  <th colspan="4" class="text-center bg-gray-50" style="padding: .25rem 1rem; width: 40%; border-left: 1px solid var(--border-color);">KEPT ITEMS <span style="color: var(--text-muted);text-transform: none;font-weight: 500;">Higher is worse</span> <button type="button" class="tooltip-icon" data-tooltip="Items retained in the app due to this rule" aria-label="Items retained in the app due to this rule">?</button></th>
                  <th rowspan="2" class="text-left bg-gray-50" style="padding: 0 1rem; width: 20%; border-left: 1px solid var(--border-color);">BLOCKED BY RULE <button type="button" class="tooltip-icon" data-tooltip="Specific actions blocked by this rule" aria-label="Specific actions blocked by this rule">?</button></th>
                </tr>
                <tr>
                  <th class="text-center text-xs font-medium text-gray-500" style="padding: .15rem 1rem; width: 10%; border-left: 1px solid var(--border-color);">Total</th>
                  <th class="text-center text-xs font-medium text-gray-500" style="padding: .15rem 1rem; width: 10%; border-left: 1px solid var(--border-color);">Classes</th>
                  <th class="text-center text-xs font-medium text-gray-500" style="padding: .15rem 1rem; width: 10%; border-left: 1px solid var(--border-color);">Fields</th>
                  <th class="text-center text-xs font-medium text-gray-500" style="padding: .15rem 1rem; width: 10%; border-left: 1px solid var(--border-color);">Methods</th>
                </tr>
              </thead>
              <tbody>
                ${rules.map((r) => renderDetailRuleRow(r)).join("")}
              </tbody>
            </table>
          `;
            }
            sectionHtml += `</div>`;
            return sectionHtml;
          };
          if (!hasIdentical && !hasSubsumed) {
            relatedRulesContainer.innerHTML = `
          <div class="table-container" style="padding: 0.75rem 1rem; font-size: 0.875rem; display: flex; gap: 2rem; align-items: center;">
            <span style="font-weight: 600; color: var(--text-gray-900);">Related rules:</span>
            <span style="color: var(--text-gray-500);">Identical · None</span>
            <span style="color: var(--text-gray-500);">Subsumed by · None</span>
          </div>
        `;
          } else {
            let html =
              '<div style="display: flex; flex-direction: column; gap: 1.5rem;">';
            html += renderSection(
              "Identical Rules",
              identicalRules,
              "Same matchers, can be deduplicated",
            );
            html += renderSection(
              "Subsumed By",
              subsumedByRules,
              "Already covered by a broader rule",
            );
            html += "</div>";
            relatedRulesContainer.innerHTML = html;
          }
        }

        // Reset search inputs UI
        const resetSearchUI = (type) => {
          const container = document.getElementById(`${type}-search-container`);
          const input = document.getElementById(`${type}-search-input`);
          const toggleBtn = document.querySelector(
            `.search-toggle-btn[data-target="${type}"]`,
          );
          if (container && input && toggleBtn) {
            container.style.display = "none";
            input.value = "";
            toggleBtn.classList.remove("active");
          }
        };
        resetSearchUI("classes");
        resetSearchUI("fields");
        resetSearchUI("methods");
        // Initial render of kept lists
        this.renderKeptLists(rule);
      }
    } else {
      ruleBody.innerHTML =
        '<tr><td colspan="2" style="padding: 1rem;">Rule not found.</td></tr>';
      classesContent.innerHTML = "";
      methodsContent.innerHTML = "";
      fieldsContent.innerHTML = "";
    }
    const fileOrigin = this.keepRadiusData?.fileOriginTable.find(
      (f) => f.id === rule?.origin?.fileOriginId,
    );
    const fileOriginName =
      formatMavenCoordinate(fileOrigin?.mavenCoordinate) ||
      fileOrigin?.filename;
    this.updateDetailsBreadcrumbs(fileOriginName, fileOrigin?.id);
  },
  renderKeptLists(rule, targetType = null) {
    const br = rule.keepRadius || {};

    const typeRefMap = App.lookups.typeReference;
    const renderList = (ids, getLabel) => {
      if (ids.length === 0)
        return '<div style="padding: 0.5rem; color: var(--text-gray-500); font-size: 0.8125rem;">None</div>';
      const formatSleekItem = (fullName) => {
        const firstParen = fullName.indexOf("(");
        const searchString =
          firstParen === -1 ? fullName : fullName.substring(0, firstParen);
        const lastDot = searchString.lastIndexOf(".");
        if (lastDot === -1) {
          return `<span style="color: var(--text-gray-900); font-weight: 500;">${escapeHTML(fullName)}</span>`;
        }
        const pkg = fullName.substring(0, lastDot + 1);
        const name = fullName.substring(lastDot + 1);
        return `<span style="color: var(--text-gray-500);">${escapeHTML(pkg)}</span><span style="color: var(--text-gray-900); font-weight: 500;">${escapeHTML(name)}</span>`;
      };
      const limit = 1000;
      const toRender = ids.slice(0, limit);
      const listHtml = toRender
        .map(
          (id) => `
        <div style="padding: 0.375rem 0.5rem; border-bottom: 1px solid #f1f5f9; font-family: var(--font-family-mono); font-size: 0.8125rem;" class="hover-bg-gray-100">
          ${formatSleekItem(getLabel(id))}
        </div>
      `,
        )
        .join("");
      if (ids.length > limit) {
        return (
          listHtml +
          `
          <div style="padding: 0.5rem; color: var(--text-gray-500); font-size: 0.8125rem; font-style: italic;">
            ... and ${ids.length - limit} more items
          </div>`
        );
      }
      return listHtml;
    };
    // 1. CLASSES COLUMN
    if (!targetType || targetType === "classes") {
      const classIds = br.classKeepRadius || [];
      const classesContent = document.getElementById("details-classes-content");
      const filteredClassIds = classIds.filter((id) => {
        const info = App.lookups.keptClassInfo.get(id);
        const name = formatDescriptor(typeRefMap.get(info?.classReferenceId));
        return name
          .toLowerCase()
          .includes(this.detailsState.classQuery.toLowerCase());
      });
      document.getElementById("details-classes-count").textContent =
        `· ${filteredClassIds.length}`;
      classesContent.innerHTML = renderList(filteredClassIds, (id) => {
        const info = App.lookups.keptClassInfo.get(id);
        return formatDescriptor(typeRefMap.get(info?.classReferenceId));
      });
    }
    // 2. FIELDS COLUMN
    if (!targetType || targetType === "fields") {
      const fieldIds = br.fieldKeepRadius || [];
      const fieldsContent = document.getElementById("details-fields-content");
      const filteredFieldIds = fieldIds.filter((id) => {
        const info = App.lookups.keptFieldInfo.get(id);
        const ref = App.lookups.fieldReference.get(info?.fieldReferenceId);
        const name = formatFieldName(ref, App.lookups);
        return name
          .toLowerCase()
          .includes(this.detailsState.fieldQuery.toLowerCase());
      });
      document.getElementById("details-fields-count").textContent =
        `· ${filteredFieldIds.length}`;
      fieldsContent.innerHTML = renderList(filteredFieldIds, (id) => {
        const info = App.lookups.keptFieldInfo.get(id);
        const ref = App.lookups.fieldReference.get(info?.fieldReferenceId);
        return formatFieldName(ref, App.lookups);
      });
    }
    // 3. METHODS COLUMN
    if (!targetType || targetType === "methods") {
      const methodIds = br.methodKeepRadius || [];
      const methodsContent = document.getElementById("details-methods-content");
      const filteredMethodIds = methodIds.filter((id) => {
        const info = App.lookups.keptMethodInfo.get(id);
        const ref = App.lookups.methodReference.get(info?.methodReferenceId);
        const name = formatMethodName(ref, App.lookups);
        return name
          .toLowerCase()
          .includes(this.detailsState.methodQuery.toLowerCase());
      });
      document.getElementById("details-methods-count").textContent =
        `· ${filteredMethodIds.length}`;
      methodsContent.innerHTML = renderList(filteredMethodIds, (id) => {
        const info = App.lookups.keptMethodInfo.get(id);
        const ref = App.lookups.methodReference.get(info?.methodReferenceId);
        return formatMethodName(ref, App.lookups);
      });
    }
  },
  showReportView() {
    ReportApp.state.currentView = CONSTANTS.VIEWS.MODULES;
    document.getElementById("details-view").style.display = "none";
    document.getElementById("file-details-view").style.display = "none";
    document.getElementById("report-view-controls").style.display = "flex";
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
    document.getElementById("report-view-controls").style.display = "none";
    document.getElementById("file-details-view").style.display = "flex";
    const impactBody = document.getElementById("file-details-impact-body");
    const rulesBody = document.getElementById("file-details-rules-body");
    const rulesHeader = document.getElementById("file-details-rules-header");
    const fileOrigin = this.keepRadiusData?.fileOriginTable.find(
      (f) => f.id === parseInt(fileOriginId),
    );
    if (!fileOrigin) return;
    const fileRules = this.keepRadiusData.keepRuleKeepRadiusTable.filter(
      (r) => r.origin?.fileOriginId === fileOrigin.id,
    );

    const globalRules = [];
    if (this.keepRadiusData.globalKeepRuleKeepRadiusTable) {
      const totalClasses = this.keepRadiusData?.buildInfo?.liveClassCount || 0;
      const totalFields = this.keepRadiusData?.buildInfo?.liveFieldCount || 0;
      const totalMethods = this.keepRadiusData?.buildInfo?.liveMethodCount || 0;
      this.keepRadiusData.globalKeepRuleKeepRadiusTable.forEach((rule) => {
        if (
          rule.source === "-dontoptimize" ||
          rule.source === "-dontshrink" ||
          rule.source === "-dontobfuscate"
        ) {
          if (rule.origin?.fileOriginId === fileOrigin.id) {
            globalRules.push({
              id: rule.id,
              source: rule.source,
              constraintsId: rule.constraintsId,
              isGlobal: true,
              keepRadius: {
                classKeepRadius: new Array(totalClasses),
                fieldKeepRadius: new Array(totalFields),
                methodKeepRadius: new Array(totalMethods),
              },
            });
          }
        }
      });
    }

    const allRulesForFile = [...globalRules, ...fileRules];
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
          <th class="text-left bg-gray-50 border-l border-gray-200" style="padding: 1rem; width: 100%; border-bottom: 1px solid var(--border-color);">${isIdenticalLens ? "Identical Rules" : "Subsumed By"}</th>
        </tr>
      `;
    } else {
      rulesHeader.innerHTML = `
        <tr>
          <th rowspan="2" class="text-left bg-gray-50" style="padding: 1rem; width: 40%; border-bottom: 1px solid var(--border-color);">Rule</th>
          <th colspan="4" class="text-center bg-gray-50" style="padding: 0.5rem; width: 40%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Kept Items <span style="color: var(--text-muted);text-transform: none;font-weight: 500;">Higher is worse</span></th>
          <th rowspan="2" class="text-left bg-gray-50" style="padding: 0.5rem; width: 20%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Blocked by Rule</th>
        </tr>
        <tr>
          <th class="text-center text-xs font-medium text-gray-500" style="padding: 0.5rem; width: 10%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Total</th>
          <th class="text-center text-xs font-medium text-gray-500" style="padding: 0.5rem; width: 10%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Classes</th>
          <th class="text-center text-xs font-medium text-gray-500" style="padding: 0.5rem; width: 10%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Fields</th>
          <th class="text-center text-xs font-medium text-gray-500" style="padding: 0.5rem; width: 10%; border-left: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color);">Methods</th>
        </tr>
      `;
    }
    const liveClasses = this.keepRadiusData?.buildInfo?.liveClassCount || 0;
    const liveFields = this.keepRadiusData?.buildInfo?.liveFieldCount || 0;
    const liveMethods = this.keepRadiusData?.buildInfo?.liveMethodCount || 0;
    const matchedClasses = new Set();
    const matchedFields = new Set();
    const matchedMethods = new Set();
    allRulesForFile.forEach((rule) => {
      const br = rule.keepRadius || {};
      (br.classKeepRadius || []).forEach((id) => matchedClasses.add(id));
      (br.fieldKeepRadius || []).forEach((id) => matchedFields.add(id));
      (br.methodKeepRadius || []).forEach((id) => matchedMethods.add(id));
    });
    const renderMatchCell = (count, total, borderLeft = true) => {
      const perc = total > 0 ? (count / total) * 100 : 0;
      const colorClass = UIUtils.getMatchClass(perc);
      const bl = borderLeft ? "border-l border-gray-200" : "";
      return `
        <td class="text-center ${bl}" style="padding: 1rem;">
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
            <span class="font-medium ${colorClass}">${perc.toFixed(1)}%</span>
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
    rulesBody.innerHTML = rules
      .map((rule) => {
        if (isIdenticalLens || isSubsumedLens) {
          const list = isIdenticalLens
            ? rule.identicalRules
            : rule.subsumedByRules;
          const otherRulesHtml = (list || [])
            .map(
              (other) => `
          <div class="text-xs text-blue-600 hover:underline cursor-pointer mb-1" onclick="event.stopPropagation(); App.showDetailsView('${other.id}')">
            <pre style="white-space: pre-wrap; margin: 0; font-family: var(--font-family-mono);">${escapeHTML(other.source)}</pre>
          </div>
        `,
            )
            .join("");
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
        const br = rule.keepRadius || {};
        const c = (br.classKeepRadius || []).length;
        const f = (br.fieldKeepRadius || []).length;
        const m = (br.methodKeepRadius || []).length;
        const constraintsMap = getConstraintsMap(this.keepRadiusData);
        let constraints = [...(constraintsMap.get(rule.constraintsId) || [])];
        if (rule.isGlobal) {
          if (
            rule.source === "-dontobfuscate" &&
            !constraints.includes("DONT_OBFUSCATE")
          ) {
            constraints.push("DONT_OBFUSCATE");
          }
          if (
            rule.source === "-dontoptimize" &&
            !constraints.includes("DONT_OPTIMIZE")
          ) {
            constraints.push("DONT_OPTIMIZE");
          }
          if (
            rule.source === "-dontshrink" &&
            !constraints.includes("DONT_SHRINK")
          ) {
            constraints.push("DONT_SHRINK");
          }
        }
        const getTag = (c, label) => {
          const isRestricted = constraints.includes(c);
          const color = isRestricted ? "oklch(0.446 0.043 257.281)" : "#cbd5e1";
          const bgColor = isRestricted
            ? "oklch(0.984 0.003 247.858)"
            : "#f8fafc";
          const borderColor = isRestricted
            ? "oklch(0.929 0.013 255.508)"
            : "#e2e8f0";
          return `<span class="impact-tag" style="display: inline-block; color: ${color}; background-color: ${bgColor}; border: 1px solid ${borderColor}; border-radius: 4px; padding: 2px 8px; font-size: 10px; font-weight: 400; height: 21px; line-height: 15px; text-transform: uppercase; letter-spacing: 0.25px; box-sizing: border-box; text-align: center;">${label}</span>`;
        };
        const impactCell = `
    <td style="padding: 0.5rem; border-left: 1px solid var(--border-color);">
      <div class="flex justify-start" style="gap: 0.5rem;">
        ${getTag("DONT_OBFUSCATE", "OBFUSCATE")}
        ${getTag("DONT_OPTIMIZE", "OPTIMIZE")}
        ${getTag("DONT_SHRINK", "SHRINK")}
      </div>
    </td>
  `;
        const renderMatchCell = (count, total, borderLeft = true) => {
          const perc = total > 0 ? (count / total) * 100 : 0;
          const colorClass = UIUtils.getMatchClass(perc);
          const bl = borderLeft
            ? "border-left: 1px solid var(--border-color);"
            : "";
          return `
          <td class="text-center" style="padding: 1rem; ${bl}">
            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
              <span class="font-medium ${colorClass}">${perc.toFixed(1)}%</span>
              <span class="text-xs text-gray-500 mt-1">${count}</span>
            </div>
          </td>
        `;
        };
        const totalLive = getLiveItemCount(this.keepRadiusData);
        const liveClasses = this.keepRadiusData?.buildInfo?.liveClassCount || 0;
        const liveFields = this.keepRadiusData?.buildInfo?.liveFieldCount || 0;
        const liveMethods =
          this.keepRadiusData?.buildInfo?.liveMethodCount || 0;
        const highlightedSource = highlightRule(rule.source);
        return `
        <tr class="border-t border-gray-200 hover:bg-gray-50 cursor-pointer" onclick="App.showDetailsView('${rule.id}')">
          <td style="padding: 0.5rem; width: 40%;">
            <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0;">${highlightedSource}</pre>
          </td>
          ${renderMatchCell(c + f + m, totalLive, true)}
          ${renderMatchCell(c, liveClasses)}
          ${renderMatchCell(f, liveFields)}
          ${renderMatchCell(m, liveMethods)}
          ${impactCell}
        </tr>
      `;
      })
      .join("");
    const fileOriginName =
      formatMavenCoordinate(fileOrigin.mavenCoordinate) || fileOrigin.filename;
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
    document
      .getElementById("file-details-back-to-summary")
      .addEventListener("click", () => {
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
    document
      .getElementById("details-back-to-summary")
      .addEventListener("click", () => {
        this.showReportView();
      });
    if (filename && fileOriginId !== undefined) {
      document
        .getElementById("details-back-to-file")
        .addEventListener("click", () => {
          this.showFileDetailsView(fileOriginId);
        });
    }
  },
  async loadProtoData() {
    const embeddedProtoSchemaSource =
      document.getElementById("keepradius-proto");
    const embeddedProtoDataSource = document.getElementById("keepradius-data");
    try {
      const root = protobuf.parse(embeddedProtoSchemaSource.textContent).root;
      const data = embeddedProtoDataSource.textContent.trim();
      const bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
      const KeepRadiusContainer = root.lookupType(
        "com.android.tools.r8.keepradius.proto.KeepRadiusContainer",
      );
      const message = KeepRadiusContainer.decode(bytes);
      this.keepRadiusData = KeepRadiusContainer.toObject(message, {
        longs: String,
        enums: String,
        bytes: String,
        defaults: true,
        arrays: true,
        objects: true,
        oneofs: true,
      });
      // Extract and strip common prefix from file names
      if (this.keepRadiusData.fileOriginTable) {
        const filenames = this.keepRadiusData.fileOriginTable
          .map((f) => f.filename)
          .filter(Boolean);
        const commonPrefix = UIUtils.findCommonPrefix(filenames);
        if (commonPrefix) {
          this.keepRadiusData.fileOriginTable.forEach((f) => {
            if (f.filename && f.filename.startsWith(commonPrefix)) {
              f.filename = f.filename.substring(commonPrefix.length);
            }
          });
        }
      }
      // Build highly optimized O(1) lookup Maps for details and filtering views
      App.lookups = {
        keptClassInfo: new Map(
          (this.keepRadiusData.keptClassInfoTable || []).map((c) => [c.id, c]),
        ),
        keptFieldInfo: new Map(
          (this.keepRadiusData.keptFieldInfoTable || []).map((f) => [f.id, f]),
        ),
        keptMethodInfo: new Map(
          (this.keepRadiusData.keptMethodInfoTable || []).map((m) => [m.id, m]),
        ),
        fieldReference: new Map(
          (this.keepRadiusData.fieldReferenceTable || []).map((r) => [r.id, r]),
        ),
        methodReference: new Map(
          (this.keepRadiusData.methodReferenceTable || []).map((r) => [
            r.id,
            r,
          ]),
        ),
        protoReference: new Map(
          (this.keepRadiusData.protoReferenceTable || []).map((p) => [p.id, p]),
        ),
        typeReferenceList: new Map(
          (this.keepRadiusData.typeReferenceListTable || []).map((l) => [
            l.id,
            l,
          ]),
        ),
        typeReference: new Map(
          (this.keepRadiusData.typeReferenceTable || []).map((t) => [
            t.id,
            t.javaDescriptor,
          ]),
        ),
      };
      console.log("Protobuf data loaded successfully:", this.keepRadiusData);
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
      methods: [],
    },
    activeFilterChips: [],
    sort: {
      by: "matches.total",
      order: "desc",
    },
    drillContext: {
      module: null,
      pkg: null,
      fileOriginId: null,
    }, // Navigation path
    statsVisible: true,
    searchTerm: "",
    showBlockedByRule: true,
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
      searchInput: getById("search-input"),
      clearSearchBtn: getById("clear-search-btn"),
      searchContainer: getById("search-container"),
      searchIconBtn: getById("search-icon-btn"),
      toggleColumnsBtn: getById("toggle-columns-btn"),
      columnsDropdown: getById("columns-dropdown"),
      toggleBlockedByRuleCb: getById("toggle-blocked-by-rule-cb"),
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
    const { keepRules, classes, fields, methods } = this.state.filters;
    const chips = this.state.activeFilterChips;
    // --- 1. Keep Rules Lens ---
    const keepRuleOptions = [
      {
        name: "<b>Identical:</b> Show rules that match the same items as other rules",
        value: "Identical",
      },
      {
        name: "<b>Subsumed:</b> Show rules that match a subset of the items matched by another rule",
        value: "Subsumed",
      },
      {
        name: "<b>Unused:</b> Show rules that don't match anything",
        value: "Unused",
      },
    ];
    const lens = this.state.filters.keepRules[0] || "All";
    const tabsContainer = document.getElementById("lens-tabs");
    if (tabsContainer) {
      tabsContainer.querySelectorAll(".segment-btn").forEach((btn) => {
        if (btn.dataset.lens === lens) {
          btn.classList.add("active");
        } else {
          btn.classList.remove("active");
        }
      });
    }
    // --- 2. Classes Filter ---
    const { options: classOptions, total: totalClasses } =
      this.getKeptClasses();
    UIUtils.buildActionDropdown(
      "cls-dropdown",
      classOptions,
      classes,
      () => {
        this.updateDynamicFilters();
        this.render();
      },
      true,
      true,
      totalClasses,
      "classes",
      (term) => {
        const brData = App.keepRadiusData;
        if (!brData || !brData.keptClassInfoTable)
          return {
            options: [],
            total: 0,
          };
        const typeRefMap = new Map();
        brData.typeReferenceTable.forEach((t) =>
          typeRefMap.set(t.id, t.javaDescriptor),
        );
        const filtered = brData.keptClassInfoTable.filter((c) => {
          const name = formatDescriptor(typeRefMap.get(c.classReferenceId));
          return name.toLowerCase().includes(term);
        });
        const results = filtered.slice(0, 1000).map((c) => {
          const name = escapeHTML(
            formatDescriptor(typeRefMap.get(c.classReferenceId)),
          );
          return {
            name,
            value: c.id,
          };
        });
        return {
          options: results,
          total: filtered.length,
        };
      },
    );
    UIUtils.renderChipText(
      this.elements.clsText,
      UIUtils.getFilterLabel("Classes", classes, totalClasses, classOptions),
      "class",
      true,
    );
    UIUtils.toggleVisibility(this.elements.clsChip, chips.includes("class"));
    // --- 3. Fields Filter ---
    const { options: fieldOptions, total: totalFields } = this.getKeptFields();
    UIUtils.buildActionDropdown(
      "field-dropdown",
      fieldOptions,
      fields,
      () => {
        this.updateDynamicFilters();
        this.render();
      },
      true,
      true,
      totalFields,
      "fields",
      (term) => {
        const brData = App.keepRadiusData;
        if (!brData || !brData.keptFieldInfoTable)
          return {
            options: [],
            total: 0,
          };
        const typeRefMap = new Map();
        brData.typeReferenceTable.forEach((t) =>
          typeRefMap.set(t.id, t.javaDescriptor),
        );
        const fieldRefMap = new Map();
        brData.fieldReferenceTable.forEach((f) => fieldRefMap.set(f.id, f));
        const filtered = brData.keptFieldInfoTable.filter((f) => {
          const fieldRef = fieldRefMap.get(f.fieldReferenceId);
          const name = formatFieldName(fieldRef, brData, typeRefMap);
          return name.toLowerCase().includes(term);
        });
        const results = filtered.slice(0, 1000).map((f) => {
          const fieldRef = fieldRefMap.get(f.fieldReferenceId);
          const name = escapeHTML(
            formatFieldName(fieldRef, brData, typeRefMap),
          );
          return {
            name,
            value: f.id,
          };
        });
        return {
          options: results,
          total: filtered.length,
        };
      },
    );
    UIUtils.renderChipText(
      this.elements.fieldText,
      UIUtils.getFilterLabel("Fields", fields, totalFields, fieldOptions),
      "field",
      true,
    );
    UIUtils.toggleVisibility(this.elements.fieldChip, chips.includes("field"));
    // --- 4. Methods Filter ---
    const { options: methodOptions, total: totalMethods } =
      this.getKeptMethods();
    UIUtils.buildActionDropdown(
      "method-dropdown",
      methodOptions,
      methods,
      () => {
        this.updateDynamicFilters();
        this.render();
      },
      true,
      true,
      totalMethods,
      "methods",
      (term) => {
        const brData = App.keepRadiusData;
        if (!brData || !brData.keptMethodInfoTable)
          return {
            options: [],
            total: 0,
          };
        const typeRefMap = new Map();
        brData.typeReferenceTable.forEach((t) =>
          typeRefMap.set(t.id, t.javaDescriptor),
        );
        const methodRefMap = new Map();
        brData.methodReferenceTable.forEach((m) => methodRefMap.set(m.id, m));
        const filtered = brData.keptMethodInfoTable.filter((m) => {
          const methodRef = methodRefMap.get(m.methodReferenceId);
          const name = formatMethodName(methodRef, brData, typeRefMap);
          return name.toLowerCase().includes(term);
        });
        const results = filtered.slice(0, 1000).map((m) => {
          const methodRef = methodRefMap.get(m.methodReferenceId);
          const name = escapeHTML(
            formatMethodName(methodRef, brData, typeRefMap),
          );
          return {
            name,
            value: m.id,
          };
        });
        return {
          options: results,
          total: filtered.length,
        };
      },
    );
    UIUtils.renderChipText(
      this.elements.methodText,
      UIUtils.getFilterLabel("Methods", methods, totalMethods, methodOptions),
      "method",
      true,
    );
    UIUtils.toggleVisibility(
      this.elements.methodChip,
      chips.includes("method"),
    );
    // --- Add Filter Button Logic ---
    const availableFilters = [];
    if (!chips.includes("class"))
      availableFilters.push({
        name: "Classes",
        value: "class",
        options: classOptions,
        total: totalClasses,
        state: classes,
      });
    if (!chips.includes("field"))
      availableFilters.push({
        name: "Fields",
        value: "field",
        options: fieldOptions,
        total: totalFields,
        state: fields,
      });
    if (!chips.includes("method"))
      availableFilters.push({
        name: "Methods",
        value: "method",
        options: methodOptions,
        total: totalMethods,
        state: methods,
      });
    if (availableFilters.length > 0) {
      this.elements.addFilterContainer.classList.remove("hidden");
      this.elements.addFilterList.innerHTML = availableFilters
        .map(
          (f) =>
            `<a href="#" class="dropdown-item add-filter-option" data-value="${f.value}">${f.name}</a>`,
        )
        .join("");
      this.elements.addFilterList
        .querySelectorAll(".add-filter-option")
        .forEach((item) => {
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
              if (val === "class")
                this.toggleDropdown(
                  this.elements.clsDropdown,
                  this.elements.clsBtn,
                );
              if (val === "field")
                this.toggleDropdown(
                  this.elements.fieldDropdown,
                  this.elements.fieldBtn,
                );
              if (val === "method")
                this.toggleDropdown(
                  this.elements.methodDropdown,
                  this.elements.methodBtn,
                );
            }, 0);
          });
        });
    } else {
      this.elements.addFilterContainer.classList.add("hidden");
    }
  },
  getKeptClasses() {
    const brData = App.keepRadiusData;
    if (!brData || !brData.keptClassInfoTable)
      return {
        options: [],
        total: 0,
      };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach((t) =>
      typeRefMap.set(t.id, t.javaDescriptor),
    );
    const options = brData.keptClassInfoTable.slice(0, 1000).map((c) => {
      const name = escapeHTML(
        formatDescriptor(typeRefMap.get(c.classReferenceId)),
      );
      return {
        name,
        value: c.id,
      };
    });
    return {
      options,
      total: brData.keptClassInfoTable.length,
    };
  },
  getKeptFields() {
    const brData = App.keepRadiusData;
    if (!brData || !brData.keptFieldInfoTable)
      return {
        options: [],
        total: 0,
      };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach((t) =>
      typeRefMap.set(t.id, t.javaDescriptor),
    );
    const fieldRefMap = new Map();
    brData.fieldReferenceTable.forEach((f) => fieldRefMap.set(f.id, f));
    const options = brData.keptFieldInfoTable.slice(0, 1000).map((f) => {
      const fieldRef = fieldRefMap.get(f.fieldReferenceId);
      const name = escapeHTML(formatFieldName(fieldRef, brData, typeRefMap));
      return {
        name,
        value: f.id,
      };
    });
    return {
      options,
      total: brData.keptFieldInfoTable.length,
    };
  },
  getKeptMethods() {
    const brData = App.keepRadiusData;
    if (!brData || !brData.keptMethodInfoTable)
      return {
        options: [],
        total: 0,
      };
    const typeRefMap = new Map();
    brData.typeReferenceTable.forEach((t) =>
      typeRefMap.set(t.id, t.javaDescriptor),
    );
    const methodRefMap = new Map();
    brData.methodReferenceTable.forEach((m) => methodRefMap.set(m.id, m));
    const options = brData.keptMethodInfoTable.slice(0, 1000).map((m) => {
      const methodRef = methodRefMap.get(m.methodReferenceId);
      const name = escapeHTML(formatMethodName(methodRef, brData, typeRefMap));
      return {
        name,
        value: m.id,
      };
    });
    return {
      options,
      total: brData.keptMethodInfoTable.length,
    };
  },
  toggleDropdown(dropdown, triggerBtn = null) {
    const allHelpers = [
      {
        dd: this.elements.grpDropdown,
        btn: this.elements.grpBtn,
      },
      {
        dd: this.elements.clsDropdown,
        btn: this.elements.clsBtn,
      },
      {
        dd: this.elements.fieldDropdown,
        btn: this.elements.fieldBtn,
      },
      {
        dd: this.elements.methodDropdown,
        btn: this.elements.methodBtn,
      },
      {
        dd: this.elements.addFilterDropdown,
        btn: this.elements.addFilterBtn,
      },
      {
        dd: this.elements.columnsDropdown,
        btn: this.elements.toggleColumnsBtn,
      },
    ];
    const isOpening = dropdown.classList.contains("hidden");
    // Close all others first
    allHelpers.forEach(({ dd, btn }) => {
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
    const map = [
      {
        btn: this.elements.grpBtn,
        dd: this.elements.grpDropdown,
      },
      {
        btn: this.elements.clsBtn,
        dd: this.elements.clsDropdown,
      },
      {
        btn: this.elements.fieldBtn,
        dd: this.elements.fieldDropdown,
      },
      {
        btn: this.elements.methodBtn,
        dd: this.elements.methodDropdown,
      },
      {
        btn: this.elements.addFilterBtn,
        dd: this.elements.addFilterDropdown,
      },
      {
        btn: this.elements.toggleColumnsBtn,
        dd: this.elements.columnsDropdown,
      },
    ];
    map.forEach(({ btn, dd }) => {
      if (btn && dd) {
        btn.addEventListener("click", (e) => {
          if (e.target.closest(".chip-close")) return;
          e.stopPropagation();
          this.toggleDropdown(dd, btn);
          this.updateDynamicFilters();
        });
      }
    });
    // --- Column Toggle ---
    if (this.elements.toggleBlockedByRuleCb) {
      this.elements.toggleBlockedByRuleCb.addEventListener("change", (e) => {
        this.state.showBlockedByRule = e.target.checked;
        this.render();
      });
    }
    const tabsContainer = document.getElementById("lens-tabs");
    if (tabsContainer) {
      tabsContainer.addEventListener("click", (e) => {
        const btn = e.target.closest(".segment-btn");
        if (!btn) return;
        const lens = btn.dataset.lens;
        this.state.filters.keepRules = lens === "All" ? [] : [lens];
        this.updateDynamicFilters();
        this.render();
      });
    }
    // Global click listener to close dropdowns
    document.addEventListener("click", (e) => {
      let anyClosed = false;
      map.forEach(({ dd, btn }) => {
        if (dd && !dd.contains(e.target) && (!btn || !btn.contains(e.target))) {
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
      // Close search bar if clicked outside
      if (
        this.elements.searchContainer &&
        !this.elements.searchContainer.contains(e.target)
      ) {
        this.elements.searchContainer.classList.remove("active");
      }
    });
    // Chip removals
    document
      .getElementById("filter-chips-container")
      .addEventListener("click", (e) => {
        const closeBtn = e.target.closest(".chip-close");
        if (closeBtn) {
          e.stopPropagation();
          const type = closeBtn.dataset.clear;
          if (type === "module") {
            this.state.filters.keepRules = [];
            this.state.activeFilterChips = this.state.activeFilterChips.filter(
              (c) => c !== "module",
            );
          } else if (type === "class") {
            this.state.filters.classes = [];
            this.state.activeFilterChips = this.state.activeFilterChips.filter(
              (c) => c !== "class",
            );
          } else if (type === "field") {
            this.state.filters.fields = [];
            this.state.activeFilterChips = this.state.activeFilterChips.filter(
              (c) => c !== "field",
            );
          } else if (type === "method") {
            this.state.filters.methods = [];
            this.state.activeFilterChips = this.state.activeFilterChips.filter(
              (c) => c !== "method",
            );
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
        pkg: null,
      }; // Reset Drill-Down
      // Reset default sort based on view
      if (
        this.state.currentView === CONSTANTS.VIEWS.CLASSES ||
        this.state.currentView === CONSTANTS.VIEWS.FIELDS ||
        this.state.currentView === CONSTANTS.VIEWS.METHODS
      ) {
        this.state.sort = {
          by: "name",
          order: "asc",
        };
      } else {
        this.state.sort = {
          by: "matches.total",
          order: "desc",
        };
      }
      this.toggleDropdown(this.elements.grpDropdown, this.elements.grpBtn);
      this.render();
    });
    // --- Table Sorting ---
    this.elements.tableHeaders.addEventListener("click", (e) => {
      const th = e.target.closest("[data-sort-by]");
      if (!th) return;
      const newSortBy = th.dataset.sortBy;
      if (this.state.sort.by === newSortBy)
        this.state.sort.order =
          this.state.sort.order === "asc" ? "desc" : "asc";
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
      const { name, type, moduleName } = td.dataset;
      // Update Drill-Context (Silent)
      if (type === "module") {
        this.state.drillContext.module = name;
        this.state.drillContext.pkg = null;
      } else if (type === "package") {
        this.state.drillContext.module =
          moduleName || this.state.drillContext.module;
        this.state.drillContext.pkg = name;
      }
      this.updateBreadcrumbs();
      this.render();
    });
    // --- Search Icon Toggle ---
    if (this.elements.searchIconBtn) {
      this.elements.searchIconBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.elements.searchContainer.classList.toggle("active");
        if (this.elements.searchContainer.classList.contains("active")) {
          this.elements.searchInput.focus();
        }
      });
    }
    // --- Search Input ---
    if (this.elements.searchInput) {
      this.elements.searchInput.addEventListener("input", (e) => {
        this.state.searchTerm = e.target.value;
        if (this.state.searchTerm) {
          this.elements.clearSearchBtn.classList.remove("hidden");
        } else {
          this.elements.clearSearchBtn.classList.add("hidden");
        }
        this.render();
      });
    }
    if (this.elements.clearSearchBtn) {
      this.elements.clearSearchBtn.addEventListener("click", () => {
        this.elements.searchInput.value = "";
        this.state.searchTerm = "";
        this.elements.clearSearchBtn.classList.add("hidden");
        this.render();
      });
    }
    // --- Help Hub Events ---
    const helpHubFab = document.getElementById("help-hub-fab");
    const helpHubPanel = document.getElementById("help-hub-panel");
    const closeHelpHubBtn = document.getElementById("close-help-hub");
    const helpHubSearchInput = document.querySelector(
      "#help-hub-panel .search-input",
    );
    if (helpHubFab && helpHubPanel) {
      helpHubFab.addEventListener("click", (e) => {
        e.stopPropagation();
        helpHubPanel.classList.toggle("open");
      });
    }
    if (closeHelpHubBtn && helpHubPanel) {
      closeHelpHubBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        helpHubPanel.classList.remove("open");
      });
    }
    // Close help hub when clicking outside
    document.addEventListener("click", (e) => {
      if (
        helpHubPanel &&
        !helpHubPanel.contains(e.target) &&
        !helpHubFab.contains(e.target)
      ) {
        helpHubPanel.classList.remove("open");
      }
    });
    // Help Hub Accordion
    if (helpHubPanel) {
      const legendItems = helpHubPanel.querySelectorAll(".legend-item");
      legendItems.forEach((item) => {
        const header = item.querySelector(".legend-item-header");
        if (header) {
          header.addEventListener("click", () => {
            // Close all other items
            legendItems.forEach((other) => {
              if (other !== item) {
                other.classList.remove("open");
              }
            });
            // Toggle current item
            item.classList.toggle("open");
          });
        }
      });
    }
  },
  /**
   * Core Data Pipeline: Filters and Flattens data based on current View Mode and Filters.
   */
  applyKeepRuleLens(rules, lens) {
    const brData = App.keepRadiusData;
    if (!lens || !brData) return rules;
    const ruleMap = new Map();
    brData.keepRuleKeepRadiusTable.forEach((r) => {
      ruleMap.set(r.id, r);
    });
    return rules.filter((r) => {
      const getSubsumedBy = (rule) =>
        rule.subsumedBy ||
        (rule.keepRadius && rule.keepRadius.subsumedBy) ||
        [];
      const getMatchesTotal = (rule) => {
        if (rule.matches && rule.matches.total !== undefined)
          return rule.matches.total;
        const b = rule.keepRadius || {};
        return (
          (b.classKeepRadius || []).length +
          (b.fieldKeepRadius || []).length +
          (b.methodKeepRadius || []).length
        );
      };
      const subsumedBy = getSubsumedBy(r);
      if (lens === "Identical") {
        const identical = subsumedBy
          .filter((otherId) => {
            const other = ruleMap.get(otherId);
            return other && (other.keepRadius?.subsumedBy || []).includes(r.id);
          })
          .map((otherId) => ruleMap.get(otherId));
        if (identical.length > 0) {
          r.identicalRules = identical;
          return true;
        }
        return false;
      } else if (lens === "Subsumed") {
        if (subsumedBy.length > 0) {
          r.subsumedByRules = subsumedBy.map((id) => ruleMap.get(id));
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
    const { currentView, filters } = this.state;
    const brData = App.keepRadiusData;
    if (!brData) return [];
    let data = [];
    if (currentView === CONSTANTS.VIEWS.PACKAGES) {
      data = getRuleFiles(brData).filter((f) => f.matches.total > 0);
    } else {
      data = getRules(brData);
      // If lens is 'Residual', 'All', or default, show only rules with matches > 0.
      // If lens is 'Unused', show only rules with matches === 0.
      const lens = filters.keepRules[0];
      if (lens === "Residual" || lens === "All" || !lens) {
        data = data.filter((r) => r.matches.total > 0);
      } else if (lens === "Unused") {
        data = data.filter((r) => r.matches.total === 0);
      }
    }
    // Apply Keep Rules Lens (Filtering)
    if (
      currentView === CONSTANTS.VIEWS.MODULES &&
      filters.keepRules.length > 0
    ) {
      data = this.applyKeepRuleLens(data, filters.keepRules[0]);
    }
    // Apply Class/Field/Method filters
    if (
      filters.classes.length > 0 ||
      filters.fields.length > 0 ||
      filters.methods.length > 0
    ) {
      const matchedRuleIds = new Set();
      if (filters.classes.length > 0) {
        brData.keptClassInfoTable.forEach((c) => {
          if (filters.classes.includes(c.id)) {
            (c.keptBy || []).forEach((rid) => matchedRuleIds.add(rid));
          }
        });
      }
      if (filters.fields.length > 0) {
        brData.keptFieldInfoTable.forEach((f) => {
          if (filters.fields.includes(f.id)) {
            (f.keptBy || []).forEach((rid) => matchedRuleIds.add(rid));
          }
        });
      }
      if (filters.methods.length > 0) {
        brData.keptMethodInfoTable.forEach((m) => {
          if (filters.methods.includes(m.id)) {
            (m.keptBy || []).forEach((rid) => matchedRuleIds.add(rid));
          }
        });
      }
      data = data.filter((r) => matchedRuleIds.has(r.id));
    }
    if (this.state.searchTerm) {
      const term = this.state.searchTerm.toLowerCase();
      data = data.filter((item) => item.name.toLowerCase().includes(term));
    }
    return data;
  },
  getSortedData(data) {
    const { by, order } = this.state.sort;
    if (!by) return data;
    const getVal = (obj, path) => {
      return path
        .split(".")
        .reduce((o, key) => (o && o[key] !== undefined ? o[key] : 0), obj);
    };
    return [...data].sort((a, b) => {
      const vA = getVal(a, by);
      const vB = getVal(b, by);
      if (typeof vA === "string")
        return order === "asc" ? vA.localeCompare(vB) : vB.localeCompare(vA);
      return order === "asc" ? (vA || 0) - (vB || 0) : (vB || 0) - (vA || 0);
    });
  },
  updateBreadcrumbs() {
    const bc = document.getElementById("flat-breadcrumbs");
    if (!bc) return;
    const linkClass = "breadcrumb-pill";
    const textClass = "breadcrumb-text";
    const sep = '<span class="text-gray-300 mx-1">/</span>';
    let html = "";
    const { module, pkg: pkg } = this.state.drillContext;
    const toggleText = this.state.statsVisible ? "Hide" : "Show";
    const toggleHtml = `
      <button class="dropdown-btn" data-action="toggle-stats" style="border: none; background: transparent; padding: 0.25rem 0.5rem; display: flex; align-items: center; border-radius: 4px; margin-left: auto; cursor: pointer;">
        <span style="font-weight: 500; color: var(--text-gray-400);">${toggleText}</span>
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"
          stroke="currentColor" style="margin-left: 0.25rem; color: var(--text-gray-500); transform: ${this.state.statsVisible ? "rotate(180deg)" : "rotate(0deg)"}; transition: transform 0.2s;">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    `;
    if (module) {
      // Drilled Down Path
      html += `<span class="${linkClass}" data-action="reset">R8 Optimization Levels</span>`;
      html += sep;
      if (pkg) {
        html += `<span class="${linkClass}" data-action="module" data-val="${escapeHTML(module)}">${escapeHTML(module)}</span>`;
        html += sep;
        html += `<span class="${textClass}">${escapeHTML(pkg)}</span>`;
      } else {
        html += `<span class="${textClass}">${escapeHTML(module)}</span>`;
      }
    } else {
      // Global View
      html = `<span class="${textClass}">R8 Optimization Levels</span>`;
    }

    const subtextHtml = `
      <div style="font-size: 0.75rem; color: var(--text-gray-500); font-weight: 400; margin-top: 0.25rem; text-transform: none; letter-spacing: normal; padding: 0rem 0.5rem;">
        The percentage of your app’s codebase that R8 is allowed to shrink, optimize, and obfuscate. Achieving a higher percentage indicates a leaner, more performant application. The percentage here reflect the initial evaluation of the keep rules before optimizations. Although this percentage will change after further optimization, it is a very good proxy for analyzing the impact of keep rules. For the final percentage after all optimizations, build your app bundle, and see r8.json.
      </div>
    `;
    bc.style.display = "flex";
    bc.style.alignItems = "center";
    bc.style.width = "100%";
    bc.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: start;">
        <div style="display: flex; align-items: center;">
          ${html}
        </div>
        ${this.state.statsVisible ? subtextHtml : ""}
      </div>
      ${toggleHtml}
    `;
    // Breadcrumb Click Handlers
    bc.querySelectorAll("[data-action]").forEach((el) => {
      el.addEventListener("click", (e) => {
        const action = el.dataset.action;
        if (action === "reset") {
          this.state.drillContext = {
            module: null,
            pkg: null,
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
      packages: "Keep Rule Files",
    };
    this.elements.grpText.textContent =
      viewLabels[currentView] ||
      currentView.charAt(0).toUpperCase() + currentView.slice(1);
    if (this.elements.grpList) {
      this.elements.grpList.querySelectorAll(".dropdown-item").forEach((el) => {
        if (el.dataset.value === currentView)
          el.classList.add("bg-gray-100", "text-gray-900", "font-semibold");
        else
          el.classList.remove("bg-gray-100", "text-gray-900", "font-semibold");
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
      relevantClasses = data.flatMap((m) =>
        (m.packages || []).flatMap((p) => p.classes || []),
      );
      modCount = data.length;
    }
    // Stats table visibility
    if (this.elements.statsContainer) {
      this.elements.statsContainer.style.display = this.state.statsVisible
        ? "flex"
        : "none";
      this.elements.statsContainer.style.marginBottom = this.state.statsVisible
        ? "2rem"
        : "0";
    }
    const brData = App.keepRadiusData;
    const totalLive = getLiveItemCount(brData);
    const formatPerc = (disallowCount) =>
      totalLive > 0
        ? (100 - (disallowCount / totalLive) * 100).toFixed(1) + "%"
        : "--";
    const getPerc = (disallowCount) =>
      totalLive > 0 ? 100 - (disallowCount / totalLive) * 100 : "--";
    const setStatValue = (element, disallowCount) => {
      if (!brData) {
        element.textContent = "--";
        element.className = "stat-value " + UIUtils.getScoreClass("--");
        return;
      }
      const perc = getPerc(disallowCount);
      element.textContent = formatPerc(disallowCount);
      element.className = "stat-value " + UIUtils.getScoreClass(perc);
    };
    setStatValue(
      this.elements.totalObfuscation,
      getDisallowObfuscationCount(brData),
    );
    setStatValue(
      this.elements.totalOptimization,
      getDisallowOptimizationCount(brData),
    );
    setStatValue(
      this.elements.totalShrinking,
      getDisallowShrinkingCount(brData),
    );
    // Render Tables
    this.renderHeaders();
    this.renderFlatRows(data);
    this.renderStatsTable();
  },
  renderStatsTable() {
    const brData = App.keepRadiusData;
    if (!brData) return;
    const stats = getDetailedStats(brData);
    if (!stats) return;
    const updateCard = (key) => {
      const setStat = (id, disallowCount, total) => {
        const el = document.getElementById(id);
        const perc = total > 0 ? 100 - (disallowCount / total) * 100 : 100;
        if (el) {
          el.textContent = perc.toFixed(1) + "%";
        }
        return perc;
      };
      const totalPerc = setStat(
        `card-total-${key}`,
        stats.overall[key],
        stats.overall.total,
      );
      const updateItem = (type) => {
        const bar = document.getElementById(`card-${key}-${type}-bar`);
        const val = document.getElementById(`card-${key}-${type}-val`);
        if (bar && val) {
          const disallow = stats[type][key];
          const total = stats[type].total;
          const perc = total > 0 ? 100 - (disallow / total) * 100 : 100;
          bar.style.width = `${perc}%`;
          val.textContent = `${perc.toFixed(1)}%`;
          const colorClass = UIUtils.getScoreClass(perc);
          if (colorClass.includes("green")) {
            bar.style.background = "var(--text-green-600)";
          } else if (colorClass.includes("yellow")) {
            bar.style.background = "var(--text-yellow-600)";
          } else if (colorClass.includes("red")) {
            bar.style.background = "var(--text-red-600)";
          }
        }
      };
      updateItem("classes");
      updateItem("fields");
      updateItem("methods");
    };
    updateCard("obfuscation");
    updateCard("optimization");
    updateCard("shrinking");
  },
  renderHeaders() {
    const { currentView, sort } = this.state;
    const topHeader = document.createElement("tr");
    const subHeader = document.createElement("tr");
    const viewLabels = {
      modules: "RULE",
      packages: "KEEP RULE FILES",
    };
    const title = viewLabels[currentView];
    const ind = (key) => {
      if (sort.by !== key) return "";
      return sort.order === "asc"
        ? `<span class="sort-icon text-blue-600 ml-1">▲</span>`
        : `<span class="sort-icon text-blue-600 ml-1">▼</span>`;
    };
    if (currentView === CONSTANTS.VIEWS.PACKAGES) {
      topHeader.innerHTML = `
        <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 40%; min-width: 300px;">
          <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
              ${title}${ind("name")}
          </div>
        </th>
        <th rowspan="2" class="text-center bg-gray-50 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="keepRules" style="padding: 1rem; width: 12%;">Keep Rules${ind("keepRules")}</th>
        <th rowspan="2" class="text-center border-l border-gray-200 bg-gray-50 cursor-pointer hover:bg-gray-100" data-sort-by="matches.total" style="padding: 0.5rem; width: 12%;">Kept Items${ind("matches.total")}</th>
        <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.obfuscation" style="padding: 0.5rem; width: 12%;">Obfuscation Score${ind("impact.obfuscation")}</th>
        <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.optimization" style="padding: 0.5rem; width: 12%;">Optimization Score${ind("impact.optimization")}</th>
        <th rowspan="2" class="text-center text-xs font-medium text-gray-500 border-l border-gray-200 cursor-pointer hover:bg-gray-100" data-sort-by="impact.shrinking" style="padding: 0.5rem; width: 12%;">Shrinking Score${ind("impact.shrinking")}</th>
      `;
      subHeader.innerHTML = ``;
    } else {
      const lens = this.state.filters.keepRules[0];
      const isIdenticalLens =
        currentView === CONSTANTS.VIEWS.MODULES && lens === "Identical";
      const isSubsumedLens =
        currentView === CONSTANTS.VIEWS.MODULES && lens === "Subsumed";
      const isUnusedLens =
        currentView === CONSTANTS.VIEWS.MODULES && lens === "Unused";
      if (this.elements.mainTable) {
        if (isIdenticalLens || isSubsumedLens || isUnusedLens) {
          this.elements.mainTable.style.tableLayout = "fixed";
        } else {
          this.elements.mainTable.style.tableLayout = "";
        }
      }
      if (isIdenticalLens || isSubsumedLens) {
        topHeader.innerHTML = `
          <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 50%; min-width: 300px;">
            <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                ${title}${ind("name")}
            </div>
          </th>
          <th rowspan="2" class="text-left bg-gray-50 border-l border-gray-200" style="padding: 1rem; width: 50%;">${isIdenticalLens ? "Identical Rules" : "Subsumed By"}</th>
        `;
        subHeader.innerHTML = "";
      } else if (isUnusedLens) {
        topHeader.innerHTML = `
          <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 50%; min-width: 300px;">
            <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                ${title}${ind("name")}
            </div>
          </th>
          <th rowspan="2" class="text-left bg-gray-50 border-l border-gray-200" style="padding: 1rem; width: 50%;">Origin</th>
        `;
        subHeader.innerHTML = "";
      } else {
        topHeader.innerHTML = `
          <th rowspan="2" class="text-left sticky-name bg-gray-50 z-30" data-sort-by="name" style="width: 40%; min-width: 300px;">
            <div class="flex items-center cursor-pointer hover:text-blue-600" style="padding: 1rem;">
                ${title}${ind("name")}
            </div>
          </th>
          <th colspan="4" class="text-center border-l border-gray-200 bg-gray-50" style="padding: 0.5rem; width: 40%;">KEPT ITEMS <span style="color: var(--text-muted);text-transform: none;font-weight: 500;">Higher is worse</span> <button type="button" class="tooltip-icon" data-tooltip="Items retained in the app due to this rule" aria-label="Items retained in the app due to this rule">?</button></th>
          ${
            this.state.showBlockedByRule
              ? `
          <th rowspan="2" class="text-left bg-gray-50" style="padding: 1rem; width: 20%; border-left: 1px solid var(--border-color);">BLOCKED BY RULE <button type="button" class="tooltip-icon" data-tooltip="Specific actions blocked by this rule" aria-label="Specific actions blocked by this rule">?</button></th>
          `
              : ""
          }
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
    const { currentView } = this.state;
    if (!data.length) {
      this.elements.tableData.innerHTML = `<tr><td colspan="10" class="text-center py-8 text-gray-500">No results found.</td></tr>`;
      return;
    }
    const brData = App.keepRadiusData;
    const build = brData?.buildInfo || {};
    const totalLive = getLiveItemCount(brData);
    const renderMatchCell = (count, total) => {
      const perc = total > 0 ? (count / total) * 100 : 0;
      const colorClass = UIUtils.getMatchClass(perc);
      return `
        <td class="text-center border-l border-gray-200" style="padding: 1rem; width: 12%;">
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
            <span class="font-medium ${colorClass}">${perc.toFixed(1)}%</span>
            <span class="text-xs text-gray-500 mt-1">${count}</span>
          </div>
        </td>
      `;
    };
    const renderScoreCell = (disallowCount, total) => {
      const perc = total > 0 ? 100 - (disallowCount / total) * 100 : 100;
      const colorClass = UIUtils.getScoreClass(perc);
      return `
        <td class="text-center border-l border-gray-200" style="padding: 1rem; width: 12%;">
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
            <span class="font-medium ${colorClass}">${perc.toFixed(1)}%</span>
            <span class="text-xs text-gray-500 mt-1">${total - disallowCount}/${total}</span>
          </div>
        </td>
      `;
    };
    this.elements.tableData.innerHTML = data
      .map((item) => {
        const cleanedName = item.name.trim();
        const escapedName = escapeHTML(cleanedName);
        let nameCell = "";
        if (currentView === CONSTANTS.VIEWS.MODULES) {
          const highlightedName = highlightRule(cleanedName);
          nameCell = `<td class="sticky-name font-medium hover:underline cursor-pointer" title="${escapedName}" style="padding: 1rem; width: 40%; min-width: 300px; font-family: var(--font-family-mono);" data-rule-id="${item.id}">
            <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${highlightedName}</pre>
          </td>`;
        } else if (currentView === CONSTANTS.VIEWS.PACKAGES) {
          nameCell = `<td class="sticky-name font-medium text-blue-600 hover:underline cursor-pointer" title="${escapedName}" style="padding: 1rem; width: 40%; min-width: 300px;" data-file-origin-id="${item.id}">
            <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${escapedName}</pre>
          </td>`;
        } else {
          nameCell = `<td class="sticky-name font-medium text-gray-900" title="${escapedName}" style="padding: 1rem; width: 40%; min-width: 300px;"><pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0;">${escapedName}</pre></td>`;
        }
        if (currentView === CONSTANTS.VIEWS.PACKAGES) {
          const keepRulesCell = `<td class="text-center border-l border-gray-200 text-sm font-semibold" style="padding: 1rem; width: 12%;">${item.keepRules}</td>`;
          const totalKeptCell = renderMatchCell(item.matches.total, totalLive);
          const impactCells = `
          ${renderScoreCell(item.impact.obfuscation, totalLive)}
          ${renderScoreCell(item.impact.optimization, totalLive)}
          ${renderScoreCell(item.impact.shrinking, totalLive)}
        `;
          return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${keepRulesCell}${totalKeptCell}${impactCells}</tr>`;
        } else {
          const lens = this.state.filters.keepRules[0];
          const isIdenticalLens =
            currentView === CONSTANTS.VIEWS.MODULES && lens === "Identical";
          const isSubsumedLens =
            currentView === CONSTANTS.VIEWS.MODULES && lens === "Subsumed";
          const isUnusedLens =
            currentView === CONSTANTS.VIEWS.MODULES && lens === "Unused";
          const customNameCell = `<td class="sticky-name font-medium hover:underline cursor-pointer" title="${escapedName}" style="padding: 1rem; width: 50%; min-width: 300px; font-family: var(--font-family-mono);" data-rule-id="${item.id}">
            <pre style="white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.8125rem; margin: 0; pointer-events: none;">${highlightRule(cleanedName)}</pre>
          </td>`;
          if (isIdenticalLens || isSubsumedLens) {
            const rules = isIdenticalLens
              ? item.identicalRules
              : item.subsumedByRules;
            const rulesHtml = (rules || [])
              .map(
                (r) => `
            <div style="font-family: var(--font-family-mono); font-size: 0.8125rem; margin-bottom: 0.25rem;">
              <pre style="white-space: pre-wrap; margin: 0; color: var(--text-main);">${highlightRule(r.source)}</pre>
            </div>
          `,
              )
              .join("");

            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${customNameCell}<td class="border-l border-gray-200" style="padding: 1rem; vertical-align: top; width: 50%; font-family: var(--font-family-mono);">${rulesHtml}</td></tr>`;
          } else if (isUnusedLens) {
            const rule =
              brData.keepRuleKeepRadiusTable.find((r) => r.id === item.id) ||
              brData.globalKeepRuleKeepRadiusTable.find(
                (r) => r.id === item.id,
              );
            const fileOrigin = brData.fileOriginTable.find(
              (f) => f.id === rule?.origin?.fileOriginId,
            );
            let originStr = "Unknown";
            if (fileOrigin) {
              const mavenName = formatMavenCoordinate(
                fileOrigin.mavenCoordinate,
              );
              originStr = `${mavenName || fileOrigin.filename}:${rule.origin?.lineNumber || 1}`;
            }
            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${customNameCell}<td class="border-l border-gray-200" style="padding: 1rem; width: 50%; font-family: var(--font-family-mono);">${escapeHTML(originStr)}</td></tr>`;
          }
          const impact = item.impact || [];
          const getTag = (c, label) => {
            const isRestricted = impact.includes(c);
            const color = isRestricted
              ? "oklch(0.446 0.043 257.281)"
              : "#cbd5e1";
            const bgColor = isRestricted
              ? "oklch(0.984 0.003 247.858)"
              : "#f8fafc";
            const borderColor = isRestricted
              ? "oklch(0.929 0.013 255.508)"
              : "#e2e8f0";
            return `<span class="impact-tag" style="display: inline-block; color: ${color}; background-color: ${bgColor}; border: 1px solid ${borderColor}; border-radius: 4px; padding: 2px 8px; font-size: 10px; font-weight: 400; height: 21px; line-height: 15px; text-transform: uppercase; letter-spacing: 0.25px; box-sizing: border-box; text-align: center;">${label}</span>`;
          };
          const impactCell = `
          <td style="padding: 1rem; border-left: 1px solid var(--border-color);">
            <div class="flex justify-start" style="gap: 0.5rem;">
              ${getTag("DONT_OBFUSCATE", "OBFUSCATE")}
              ${getTag("DONT_OPTIMIZE", "OPTIMIZE")}
              ${getTag("DONT_SHRINK", "SHRINK")}
            </div>
          </td>
        `;
          const matchesCells = `
          ${renderMatchCell(item.matches.total, totalLive)}
          ${renderMatchCell(item.matches.classes, build.liveClassCount || 0)}
          ${renderMatchCell(item.matches.fields, build.liveFieldCount || 0)}
          ${renderMatchCell(item.matches.methods, build.liveMethodCount || 0)}
        `;
          return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${matchesCells}${this.state.showBlockedByRule ? impactCell : ""}</tr>`;
        }
      })
      .join("");
  },
};
document.addEventListener("DOMContentLoaded", () => {
  App.init();
});
