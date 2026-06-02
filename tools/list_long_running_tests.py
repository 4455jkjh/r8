#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
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


def main():
    parser = argparse.ArgumentParser(
        description="Generate a Markdown table of test times from RDB.")
    parser.add_argument("-i",
                        "--input",
                        help="Path to an existing RDB JSON file.")
    parser.add_argument(
        "-b",
        "--build",
        help="The RDB build ID to query (e.g., build-8680470238573953425).")
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

    parser.add_argument(
        "--per-class",
        dest="per_class",
        action="store_true",
        default=True,
        help="Aggregate the test times per test class (default).")
    parser.add_argument("--no-per-class",
                        dest="per_class",
                        action="store_false",
                        help="Do not aggregate. Show individual test cases.")

    args = parser.parse_args()

    if not args.input and not args.build:
        print(
            "Error: You must provide either --input <file> or --build <build_id>.",
            file=sys.stderr)
        parser.print_help()
        sys.exit(1)

    lines_source = []

    if args.input:
        try:
            with open(args.input, "r") as f:
                lines_source = f.readlines()
        except FileNotFoundError:
            print(f"Error: Input file '{args.input}' not found.",
                  file=sys.stderr)
            sys.exit(1)
    else:
        # Before running query, check if rdb is in PATH
        if not shutil.which("rdb"):
            print("Error: 'rdb' command not found in PATH.", file=sys.stderr)
            sys.exit(1)

        # Check if rdb is authenticated
        if not check_rdb_auth():
            print("Error: You do not appear to be logged into ResultDB.",
                  file=sys.stderr)
            print(
                "Please run 'rdb auth-login' in your terminal to authenticate first.",
                file=sys.stderr)
            sys.exit(1)

        cmd = ["rdb", "query", args.build, "-json"]
        print(f"Running: {' '.join(cmd)} ...", file=sys.stderr)
        try:
            process = subprocess.run(cmd,
                                     capture_output=True,
                                     text=True,
                                     check=True)
            lines_source = process.stdout.splitlines()
        except subprocess.CalledProcessError as e:
            print(f"Error running rdb: {e.stderr}", file=sys.stderr)
            sys.exit(e.returncode)

    # Process results based on mode
    if args.per_class:
        class_times = defaultdict(float)
        class_counts = defaultdict(int)

        for line in lines_source:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                test_result = data.get("testResult", {})
                class_name = extract_class_name(test_result)
                duration_str = test_result.get("duration")
                if duration_str:
                    duration = parse_duration(duration_str)
                    class_times[class_name] += duration
                    class_counts[class_name] += 1
            except Exception:
                pass

        sorted_results = sorted(class_times.items(),
                                key=lambda x: x[1],
                                reverse=True)

        markdown_lines = []
        markdown_lines.append(
            "| Class Name | Total Duration | Test Count | Avg Duration |")
        markdown_lines.append("| :--- | :---: | :---: | :---: |")
        for class_name, total_dur in sorted_results[:args.limit]:
            count = class_counts[class_name]
            avg_dur = total_dur / count if count > 0 else 0.0
            total_dur_str = f"**{total_dur:.3f}s**" if total_dur > 100 else f"{total_dur:.3f}s"
            display_name = shorten_name(class_name, args.remove_prefix)
            markdown_lines.append(
                f"| `{display_name}` | {total_dur_str} | {count} | {avg_dur:.3f}s |"
            )
    else:
        individual_tests = []
        for line in lines_source:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                test_result = data.get("testResult", {})
                test_name = extract_test_name(test_result)
                duration_str = test_result.get("duration")
                if duration_str:
                    duration = parse_duration(duration_str)
                    individual_tests.append((test_name, duration))
            except Exception:
                pass

        sorted_results = sorted(individual_tests,
                                key=lambda x: x[1],
                                reverse=True)

        markdown_lines = []
        markdown_lines.append("| Test Name | Duration |")
        markdown_lines.append("| :--- | :---: |")
        for test_name, dur in sorted_results[:args.limit]:
            dur_str = f"**{dur:.3f}s**" if dur > 100 else f"{dur:.3f}s"
            display_name = shorten_name(test_name, args.remove_prefix)
            markdown_lines.append(f"| `{display_name}` | {dur_str} |")

    if not sorted_results:
        print("No tests found or processed.", file=sys.stderr)
        sys.exit(0)

    markdown_content = "\n".join(markdown_lines) + "\n"

    glow_path = shutil.which("glow")
    if glow_path and sys.stdout.isatty():
        try:
            process = subprocess.Popen([glow_path, "--width", "0", "-"],
                                       stdin=subprocess.PIPE,
                                       text=True)
            process.communicate(input=markdown_content)
        except Exception:
            print(markdown_content, end="")
    else:
        print(markdown_content, end="")


if __name__ == "__main__":
    main()
