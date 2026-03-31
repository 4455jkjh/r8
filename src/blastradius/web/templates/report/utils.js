// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

/**
 * Utility functions for analyzing Blast Radius data.
 */

function getDisallowObfuscationCount(data) {
  return getScore(data, 'DONT_OBFUSCATE');
}

function getDisallowOptimizationCount(data) {
  return getScore(data, 'DONT_OPTIMIZE');
}

function getDisallowShrinkingCount(data) {
  return getScore(data, 'DONT_SHRINK');
}

function getLiveItemCount(data) {
  if (!data || !data.buildInfo) return 0;
  return (data.buildInfo.liveClassCount || 0) +
         (data.buildInfo.liveFieldCount || 0) +
         (data.buildInfo.liveMethodCount || 0);
}

/**
 * Returns detailed counts for the stats table.
 */
function getDetailedStats(data) {
  if (!data) return null;

  const build = data.buildInfo || {};
  const stats = {
    classes: { total: build.liveClassCount || 0, obfuscation: 0, optimization: 0, shrinking: 0 },
    fields: { total: build.liveFieldCount || 0, obfuscation: 0, optimization: 0, shrinking: 0 },
    methods: { total: build.liveMethodCount || 0, obfuscation: 0, optimization: 0, shrinking: 0 },
    overall: { total: getLiveItemCount(data), obfuscation: 0, optimization: 0, shrinking: 0 }
  };

  const constraintsMap = getConstraintsMap(data);
  const rulesMap = getRulesConstraintsMap(data);

  const processTable = (table, key) => {
    if (!table) return;
    table.forEach(item => {
      const keptBy = item.keptBy || [];
      const constraints = keptBy.flatMap(ruleId => constraintsMap.get(rulesMap.get(ruleId)) || []);
      if (constraints.includes('DONT_OBFUSCATE')) stats[key].obfuscation++;
      if (constraints.includes('DONT_OPTIMIZE')) stats[key].optimization++;
      if (constraints.includes('DONT_SHRINK')) stats[key].shrinking++;
    });
  };

  processTable(data.keptClassInfoTable, 'classes');
  processTable(data.keptFieldInfoTable, 'fields');
  processTable(data.keptMethodInfoTable, 'methods');

  stats.overall.obfuscation = stats.classes.obfuscation + stats.fields.obfuscation + stats.methods.obfuscation;
  stats.overall.optimization = stats.classes.optimization + stats.fields.optimization + stats.methods.optimization;
  stats.overall.shrinking = stats.classes.shrinking + stats.fields.shrinking + stats.methods.shrinking;

  return stats;
}

function getConstraintsMap(data) {
  const constraintsMap = new Map();
  if (data.keepConstraintsTable) {
    data.keepConstraintsTable.forEach(c => {
      constraintsMap.set(c.id, c.constraints || []);
    });
  }
  return constraintsMap;
}

function getRulesConstraintsMap(data) {
  const rulesMap = new Map();
  if (data.keepRuleBlastRadiusTable) {
    data.keepRuleBlastRadiusTable.forEach(r => {
      rulesMap.set(r.id, r.constraintsId);
    });
  }
  return rulesMap;
}

function getImpactArray(constraints) {
  if (!constraints) return [];
  return constraints;
}

/**
 * Returns formatted rules for the table.
 */
function getRules(data) {
  if (!data || !data.keepRuleBlastRadiusTable) return [];
  const constraintsMap = getConstraintsMap(data);
  return data.keepRuleBlastRadiusTable.map(rule => {
    const constraints = constraintsMap.get(rule.constraintsId);
    const br = rule.blastRadius || {};
    const classes = (br.classBlastRadius || []).length;
    const fields = (br.fieldBlastRadius || []).length;
    const methods = (br.methodBlastRadius || []).length;
    return {
      id: rule.id,
      name: rule.source,
      impact: getImpactArray(constraints),
      matches: {
        classes,
        fields,
        methods,
        total: classes + fields + methods
      },
      subsumedBy: br.subsumedBy || []
    };
  });
}

/**
 * Returns formatted files (origins) for the table.
 */
function getRuleFiles(data) {
  if (!data || !data.fileOriginTable || !data.keepRuleBlastRadiusTable) return [];

  const fileMap = new Map();
  const constraintsMap = getConstraintsMap(data);
  data.fileOriginTable.forEach(f => {
    const mavenName = formatMavenCoordinate(f.mavenCoordinate);
    fileMap.set(f.id, {
      id: f.id,
      name: mavenName || f.filename,
      keepRules: 0,
      matches: { classes: new Set(), fields: new Set(), methods: new Set() },
      impact: {
        obfuscation: new Set(),
        optimization: new Set(),
        shrinking: new Set()
      }
    });
  });

  data.keepRuleBlastRadiusTable.forEach(rule => {
    const fileId = rule.origin?.fileOriginId;
    const fileEntry = fileMap.get(fileId);
    if (fileEntry) {
      fileEntry.keepRules++;
      const br = rule.blastRadius || {};
      const c = br.classBlastRadius || [];
      const f = br.fieldBlastRadius || [];
      const m = br.methodBlastRadius || [];

      c.forEach(id => fileEntry.matches.classes.add(id));
      f.forEach(id => fileEntry.matches.fields.add(id));
      m.forEach(id => fileEntry.matches.methods.add(id));

      const constraints = constraintsMap.get(rule.constraintsId) || [];
      if (constraints.includes('DONT_OBFUSCATE')) {
        c.forEach(id => fileEntry.impact.obfuscation.add('c' + id));
        f.forEach(id => fileEntry.impact.obfuscation.add('f' + id));
        m.forEach(id => fileEntry.impact.obfuscation.add('m' + id));
      }
      if (constraints.includes('DONT_OPTIMIZE')) {
        c.forEach(id => fileEntry.impact.optimization.add('c' + id));
        f.forEach(id => fileEntry.impact.optimization.add('f' + id));
        m.forEach(id => fileEntry.impact.optimization.add('m' + id));
      }
      if (constraints.includes('DONT_SHRINK')) {
        c.forEach(id => fileEntry.impact.shrinking.add('c' + id));
        f.forEach(id => fileEntry.impact.shrinking.add('f' + id));
        m.forEach(id => fileEntry.impact.shrinking.add('m' + id));
      }
    }
  });

  if (data.globalKeepRuleBlastRadiusTable) {
    data.globalKeepRuleBlastRadiusTable.forEach(rule => {
      const fileId = rule.origin?.fileOriginId;
      const fileEntry = fileMap.get(fileId);
      if (fileEntry) {
        fileEntry.keepRules++;
      }
    });
  }

  return Array.from(fileMap.values()).map(f => ({
    id: f.id,
    name: f.name,
    keepRules: f.keepRules,
    matches: {
      classes: f.matches.classes.size,
      fields: f.matches.fields.size,
      methods: f.matches.methods.size,
      total: f.matches.classes.size + f.matches.fields.size + f.matches.methods.size
    },
    impact: {
      obfuscation: f.impact.obfuscation.size,
      optimization: f.impact.optimization.size,
      shrinking: f.impact.shrinking.size
    }
  }));
}

function formatDescriptor(desc) {
  if (!desc) return "Unknown";
  let dimensions = 0;
  while (desc[dimensions] === '[') {
    dimensions++;
  }
  let base = desc.substring(dimensions);
  let res = "";
  if (base.startsWith('L') && base.endsWith(';')) {
    res = base.substring(1, base.length - 1).replace(/\//g, '.');
  } else if (base.length === 1) {
    switch (base[0]) {
      case 'V':
        res = "void";
        break;
      case 'Z':
        res = "boolean";
        break;
      case 'B':
        res = "byte";
        break;
      case 'S':
        res = "short";
        break;
      case 'C':
        res = "char";
        break;
      case 'I':
        res = "int";
        break;
      case 'J':
        res = "long";
        break;
      case 'F':
        res = "float";
        break;
      case 'D':
        res = "double";
        break;
      default:
        res = base;
    }
  } else {
    res = base;
  }
  for (let i = 0; i < dimensions; i++) {
    res += "[]";
  }
  return res;
}

function formatMethodName(methodRef, data, typeRefMap) {
  if (!methodRef) return "Unknown method";
  const className = formatDescriptor(typeRefMap.get(methodRef.classReferenceId));

  const proto = (data.protoReferenceTable || []).find(p => p.id === methodRef.protoReferenceId);
  let params = "";
  if (proto && proto.parametersId) {
    const list = (data.typeReferenceListTable || []).find(l => l.id === proto.parametersId);
    if (list && list.typeReferenceIds) {
      params = list.typeReferenceIds.map(id => formatDescriptor(typeRefMap.get(id))).join(', ');
    }
  }
  return `${className}.${methodRef.name}(${params})`;
}

function formatFieldName(fieldRef, data, typeRefMap) {
  if (!fieldRef) return "Unknown field";
  const className = formatDescriptor(typeRefMap.get(fieldRef.classReferenceId));
  return `${className}.${fieldRef.name}`;
}

function formatMavenCoordinate(m) {
  if (!m || !m.groupId) return null;
  return `${m.groupId}:${m.artifactId}:${m.version}`;
}

function escapeHTML(str) {
  if (!str) return "";
  return str.replace(/[&<>"']/g, function(m) {
    return {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[m];
  });
}

/**
 * Shared helper to count kept items that have a specific constraint.
 * An item is counted if ANY of the keep rules that keep it has the specified constraint.
 */
function getScore(data, constraintName) {
  if (!data) return 0;

  const constraintsMap = getConstraintsMap(data);
  const rulesMap = getRulesConstraintsMap(data);

  let count = 0;
  const tables = [
    data.keptClassInfoTable,
    data.keptFieldInfoTable,
    data.keptMethodInfoTable
  ];

  tables.forEach(table => {
    if (table) {
      table.forEach(item => {
        const hasRuleWithConstraint = (item.keptBy || []).some(ruleId => {
          const constraintsId = rulesMap.get(ruleId);
          const constraints = constraintsMap.get(constraintsId);
          return constraints && constraints.includes(constraintName);
        });
        if (hasRuleWithConstraint) {
          count++;
        }
      });
    }
  });
  return count;
}
