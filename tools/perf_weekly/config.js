// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

const COMPOSE_SAMPLE_BENCHMARKS = [
  "JetCasterApp",
  "JetCasterAppPartial",
  "JetChatApp",
  "JetChatAppPartial",
  "JetLaggedApp",
  "JetLaggedAppPartial",
  "JetNewsApp",
  "JetNewsAppPartial",
  "JetSnackApp",
  "JetSnackAppPartial",
  "ReplyApp",
  "ReplyAppPartial",
];

// Commits on main that replaced or altered benchmark compiler dumps.
// Benchmarks listed for a commit are not compared across that commit boundary.
const DUMP_ALTERING_COMMITS = [
  {
    // Update compose-sample apps to include resources.
    hash: "c4b8f50da2b18e6ca94948e73a2f2dab186e86a4",
    benchmarks: COMPOSE_SAMPLE_BENCHMARKS,
  },
  {
    // Revert "Update compose-sample apps to include resources".
    hash: "069c15175f3c70bf2ba64cce0ab86751bdb40d1a",
    benchmarks: COMPOSE_SAMPLE_BENCHMARKS,
  },
  {
    // Update compose-sample apps to include resources.
    hash: "3db76fafeab5c2e1e863849a6f9a8a6de91858c7",
    benchmarks: COMPOSE_SAMPLE_BENCHMARKS,
  },
  {
    // Fix agsa dump.
    hash: "32962a6c11206bce55bf00478eecfe03f60a20fd",
    benchmarks: ["AGSA"],
  },
];

export default {
  dumpAlteringCommits: DUMP_ALTERING_COMMITS,
};
