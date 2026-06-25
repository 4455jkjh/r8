#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import concurrent.futures
import json
import re
import shutil
import subprocess
import sys
from collections import defaultdict


def parse_duration(duration_str):
    if not duration_str:
        return 0.0
    match = re.match(r"([\d\.]+)(ms|m|s)?", duration_str)
    if not match:
        return 0.0
    val = float(match.group(1))
    unit = match.group(2)
    if unit == "m":
        return val * 60.0
    elif unit == "ms":
        return val / 1000.0
    return val


def shorten_name(name, remove_prefix):
    if not remove_prefix:
        return name
    prefix = "com.android.tools.r8."
    if name.startswith(prefix):
        return "..." + name[len(prefix):]
    return name


def extract_class_name(test_result):
    test_id_struct = test_result.get("testIdStructured", {})
    coarse = test_id_struct.get("coarseName", "")
    fine = test_id_struct.get("fineName", "")

    if coarse and fine:
        return f"{coarse}.{fine}"

    test_id = test_result.get("testId", "")
    match = re.match(r":r8!junit:([^#:]+)", test_id)
    if match:
        return match.group(1)
    return "Unknown"


def extract_test_name(test_result):
    test_id = test_result.get("testId", "")
    prefix = ":r8!junit:"
    if test_id.startswith(prefix):
        return test_id[len(prefix):]
    return test_id


def check_rdb_auth():
    """Returns True if rdb is authenticated, False otherwise."""
    try:
        result = subprocess.run(["rdb", "auth-info"],
                                capture_output=True,
                                text=True)
        if result.returncode != 0 or "Logged in as" not in result.stdout:
            return False
        return True
    except Exception:
        return False


def get_current_issue():
    try:
        result = subprocess.run(["git", "cl", "issue"],
                                capture_output=True,
                                text=True,
                                check=True)
        match = re.search(r"Issue number: (\d+)", result.stdout)
        if match:
            return match.group(1)
    except Exception:
        pass
    return None


def extract_patchset(tryjob):
    for tag in tryjob.get("tags", []):
        if tag.get("key") == "buildset":
            val = tag.get("value", "")
            match = re.search(r"/(\d+)/(\d+)$", val)
            if match:
                return int(match.group(2))
    return None


def get_tryjobs(issue_id):
    cmd = ["git", "cl", "try-results"]
    if issue_id:
        cmd.extend(["-i", str(issue_id)])
    cmd.extend(["--json", "-"])

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return json.loads(result.stdout)
    except subprocess.CalledProcessError as e:
        print(f"Error running git cl try-results: {e.stderr}", file=sys.stderr)
        sys.exit(e.returncode)
    except json.JSONDecodeError as e:
        print(f"Error parsing json from git cl try-results: {e}",
              file=sys.stderr)
        sys.exit(1)


def fetch_results_for_build(build):
    build_id = build.get("id")
    builder_name = build.get("builder", {}).get("builder", "unknown")

    cmd = ["rdb", "query", f"build-{build_id}", "-json"]
    try:
        process = subprocess.run(cmd,
                                 capture_output=True,
                                 text=True,
                                 check=True)
        return builder_name, process.stdout.splitlines()
    except Exception:
        return builder_name, []


def format_console_table(headers, alignments, rows):
    """Formats an aligned console table."""
    if not rows:
        return ""

    num_cols = len(headers)
    col_widths = [len(h) for h in headers]

    # Calculate max widths
    for row in rows:
        for i in range(num_cols):
            cell_str = str(row[i])
            if len(cell_str) > col_widths[i]:
                col_widths[i] = len(cell_str)

    # Generate header line
    header_line = "  ".join(
        headers[i].ljust(col_widths[i]) for i in range(num_cols))

    # Generate separator line
    separator_line = "  ".join("-" * col_widths[i] for i in range(num_cols))

    # Generate data lines
    data_lines = []
    for row in rows:
        cells = []
        for i in range(num_cols):
            cell_str = str(row[i])
            align = alignments[i]
            width = col_widths[i]
            if align == "left":
                cells.append(cell_str.ljust(width))
            elif align == "right":
                cells.append(cell_str.rjust(width))
            elif align == "center":
                cells.append(cell_str.center(width))
            else:
                cells.append(cell_str.ljust(width))
        data_lines.append("  ".join(cells))

    return "\n".join([header_line, separator_line] + data_lines)


def main():
    parser = argparse.ArgumentParser(
        description="Fetch test results for the current CL/Tryjobs.")
    parser.add_argument(
        "-i",
        "--issue",
        help="Gerrit issue number (defaults to current branch's CL).")
    parser.add_argument(
        "-p",
        "--patchset",
        type=int,
        help=
        "Specific patchset to query (defaults to latest patchset with try jobs)."
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=25,
        help="Number of top classes or tests to display (default: 25)")
    parser.add_argument(
        "--remove-prefix",
        dest="remove_prefix",
        action="store_true",
        default=True,
        help=
        "Shorten the class/test name prefix 'com.android.tools.r8.' to '...' (default)."
    )
    parser.add_argument("--no-remove-prefix",
                        dest="remove_prefix",
                        action="store_false",
                        help="Do not shorten the prefix.")
    parser.add_argument("--per-class",
                        dest="per_class",
                        action="store_true",
                        default=False,
                        help="Aggregate the test times per test class.")
    parser.add_argument(
        "--failed-only",
        dest="failed_only",
        action="store_true",
        default=None,
        help=
        "Only display unexpected failures (default if not aggregating per-class)."
    )
    parser.add_argument(
        "--no-failed-only",
        dest="failed_only",
        action="store_false",
        help=
        "Display all test results matching status filter, not just unexpected failures."
    )
    parser.add_argument(
        "--status",
        choices=["PASS", "FAIL", "CRASH", "ABORT", "SKIP", "ALL"],
        default="ALL",
        help="Filter tests by status (default: ALL).")

    args = parser.parse_args()

    # Resolve defaults
    if args.failed_only is None:
        args.failed_only = not args.per_class

    # Find the issue to query
    issue_id = args.issue
    if not issue_id:
        issue_id = get_current_issue()
        if not issue_id:
            print(
                "Error: No current branch issue found. Please specify -i/--issue.",
                file=sys.stderr)
            sys.exit(1)

    # Check dependencies
    if not shutil.which("rdb"):
        print("Error: 'rdb' command not found in PATH.", file=sys.stderr)
        sys.exit(1)

    if not check_rdb_auth():
        print("Error: You do not appear to be logged into ResultDB.",
              file=sys.stderr)
        print(
            "Please run 'rdb auth-login' in your terminal to authenticate first.",
            file=sys.stderr)
        sys.exit(1)

    # Fetch tryjobs list
    tryjobs = get_tryjobs(issue_id)
    if not tryjobs:
        print(f"No tryjobs found for issue {issue_id}.", file=sys.stderr)
        sys.exit(0)

    # Group by patchset
    patchsets = defaultdict(list)
    for job in tryjobs:
        ps = extract_patchset(job)
        if ps is not None:
            patchsets[ps].append(job)

    if not patchsets:
        print("No tryjobs with valid patchset tags found.", file=sys.stderr)
        sys.exit(0)

    # Find selected patchset
    if args.patchset is not None:
        if args.patchset not in patchsets:
            print(f"Error: Patchset {args.patchset} has no try jobs.",
                  file=sys.stderr)
            sys.exit(1)
        selected_patchset = args.patchset
    else:
        selected_patchset = max(patchsets.keys())

    jobs = patchsets[selected_patchset]

    # Categorize jobs by status
    categorized = defaultdict(list)
    builder_statuses = {}
    for job in jobs:
        status = job.get("status", "UNKNOWN")
        categorized[status].append(job)
        builder = job.get("builder", {}).get("builder", "unknown")
        builder_statuses[builder] = status

    # Determine which builds to query ResultDB for
    jobs_to_query = []
    for job in jobs:
        status = job.get("status")
        if status in ("SUCCESS", "FAILURE", "INFRA_FAILURE"):
            if (args.failed_only and status == "SUCCESS" and
                    not args.per_class and args.status in ("ALL", "FAIL")):
                continue
            jobs_to_query.append(job)

    # Concurrently fetch results from ResultDB
    build_results = {}
    if jobs_to_query:
        print(
            f"Querying ResultDB for {len(jobs_to_query)} builds on patchset {selected_patchset}...",
            file=sys.stderr)
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            future_to_build = {
                executor.submit(fetch_results_for_build, job): job
                for job in jobs_to_query
            }
            for future in concurrent.futures.as_completed(future_to_build):
                builder_name, lines = future.result()
                build_results[builder_name] = lines

    # Parse and filter test results
    processed_results = defaultdict(list)
    for builder_name, lines in build_results.items():
        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                test_result = data.get("testResult")
                if not test_result:
                    continue

                status = test_result.get("status", "UNKNOWN")
                expected = test_result.get("expected", False)

                # Filter: Unexpected failures
                if args.failed_only and expected:
                    continue

                # Filter: Specific status
                if args.status != "ALL" and status != args.status:
                    continue

                processed_results[builder_name].append({
                    "test_name": extract_test_name(test_result),
                    "class_name": extract_class_name(test_result),
                    "status": status,
                    "expected": expected,
                    "duration": parse_duration(test_result.get("duration")),
                })
            except Exception:
                pass

    # Build Console Output
    output_lines = []
    separator_bar = "=" * 80
    section_bar = "-" * 80

    output_lines.append(separator_bar)
    output_lines.append(
        f"Tryjob Results for CL {issue_id} (Patchset {selected_patchset})")
    output_lines.append(separator_bar)
    output_lines.append("")

    # 1. Summary Section
    output_lines.append("SUMMARY")
    output_lines.append(section_bar)
    for status in [
            "SUCCESS", "FAILURE", "INFRA_FAILURE", "STARTED", "SCHEDULED",
            "CANCELED"
    ]:
        jobs_in_status = categorized.get(status, [])
        if not jobs_in_status:
            continue

        status_display = status
        if status in ("STARTED", "SCHEDULED"):
            status_display = "RUNNING"

        output_lines.append(f"  {status_display}:")
        for job in jobs_in_status:
            builder = job.get("builder", {}).get("builder", "unknown")
            build_id = job.get("id")
            link = f"https://ci.chromium.org/b/{build_id}" if build_id else ""

            num_failures = len([
                r for r in processed_results.get(builder, [])
                if not r["expected"]
            ])

            job_str = f"    - {builder}"
            if link:
                job_str += f" ({link})"

            if status == "FAILURE":
                if num_failures > 0:
                    job_str += f" [{num_failures} unexpected failure(s)]"
                else:
                    job_str += " [No test failures found (likely compile or setup error)]"
            output_lines.append(job_str)
    output_lines.append("")

    # 2. Detailed results
    if args.per_class:
        # Aggregated per-class
        class_times = defaultdict(float)
        class_counts = defaultdict(int)
        class_failures = defaultdict(int)

        for builder, results in processed_results.items():
            for r in results:
                cname = r["class_name"]
                class_times[cname] += r["duration"]
                class_counts[cname] += 1
                if not r["expected"]:
                    class_failures[cname] += 1

        sorted_results = sorted(class_times.items(),
                                key=lambda x: x[1],
                                reverse=True)

        rows = []
        for class_name, total_dur in sorted_results[:args.limit]:
            count = class_counts[class_name]
            fails = class_failures[class_name]
            avg_dur = total_dur / count if count > 0 else 0.0
            total_dur_str = f"{total_dur:.3f}s"
            display_name = shorten_name(class_name, args.remove_prefix)
            fails_str = str(fails)
            rows.append([
                display_name, total_dur_str,
                str(count), fails_str, f"{avg_dur:.3f}s"
            ])

        table_str = format_console_table(
            ["Class Name", "Duration", "Count", "Failures", "Avg"],
            ["left", "right", "right", "right", "right"], rows)
        if table_str:
            output_lines.append("AGGREGATED TEST RESULTS PER CLASS")
            output_lines.append(section_bar)
            output_lines.append(table_str)
            output_lines.append("")
    else:
        # Detailed list per builder
        has_details = False
        for builder, results in processed_results.items():
            if not results:
                continue

            if not has_details:
                output_lines.append("DETAILED RESULTS")
                output_lines.append(section_bar)
                has_details = True

            b_status = builder_statuses.get(builder, "UNKNOWN")
            status_suffix = " (RUNNING)" if b_status in ("STARTED",
                                                         "SCHEDULED") else ""
            output_lines.append(f"{builder}{status_suffix}:")

            # Group results by class name
            by_class = defaultdict(list)
            for r in results:
                by_class[r["class_name"]].append(r)

            for class_name, class_results in sorted(by_class.items()):
                display_class = shorten_name(class_name, args.remove_prefix)
                output_lines.append(f"  {display_class}:")
                # Sort tests by expected (unexpected first) and duration desc
                sorted_r = sorted(class_results,
                                  key=lambda x: (x["expected"], -x["duration"]))
                for r in sorted_r[:args.limit]:
                    exp_str = "" if r["expected"] else " (Unexpected)"
                    dur_str = f"{r['duration']:.3f}s"
                    output_lines.append(
                        f"    {r['test_name']} ({dur_str}) - {r['status']}{exp_str}"
                    )
            output_lines.append("")

    print("\n".join(output_lines))


if __name__ == "__main__":
    main()
