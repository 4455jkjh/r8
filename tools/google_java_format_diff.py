#!/usr/bin/env python3
#
#===- google-java-format-diff.py - google-java-format Diff Reformatter -----===#
#
#                     The LLVM Compiler Infrastructure
#
# This file is distributed under the University of Illinois Open Source
# License. See LICENSE.TXT for details.
#
#===------------------------------------------------------------------------===#

import difflib
import io
import re


def parse_diff(lines, p=0, regex=None, iregex=r'.*\.java'):
    # Extract changed lines for each file.
    filename = None
    lines_by_file = {}

    for line in lines:
        match = re.search(r'^\+\+\+\ (.*?/){%s}(\S*)' % p, line)
        if match:
            filename = match.group(2)
        if filename == None:
            continue

        if regex is not None:
            if not re.match('^%s$' % regex, filename):
                continue
        else:
            if not re.match('^%s$' % iregex, filename, re.IGNORECASE):
                continue

        match = re.search(r'^@@.*\+(\d+)(,(\d+))?', line)
        if match:
            start_line = int(match.group(1))
            line_count = 1
            if match.group(3):
                line_count = int(match.group(3))
            if line_count == 0:
                continue
            end_line = start_line + line_count - 1
            lines_by_file.setdefault(filename, []).extend(
                ['-lines', str(start_line) + ':' + str(end_line)])

    return lines_by_file


def create_diff(filename, formatted_code_str):
    with open(filename) as f:
        code = f.readlines()
    formatted_code = io.StringIO(formatted_code_str).readlines()
    diff = difflib.unified_diff(code, formatted_code, filename, filename,
                                '(before formatting)', '(after formatting)')
    return ''.join(diff)
