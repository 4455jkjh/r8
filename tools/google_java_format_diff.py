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
import os
import re
import subprocess


def parse_diff(diff_lines, prefix_strip=0):
    filename = None
    lines_by_file = {}

    for line in diff_lines:
        if line.startswith('diff --git') or line.startswith('--- '):
            filename = None
        match = re.search(r'^\+\+\+\ (.*?/){%s}(\S*)' % prefix_strip, line)
        if match:
            filename = match.group(2)
        if filename is None or not filename.endswith('.java'):
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
                ['-lines', f'{start_line}:{end_line}'])

    return lines_by_file


def get_base_command(java_binary, google_java_format_jar):
    return [
        java_binary,
        '--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED',
        '-jar',
        google_java_format_jar,
    ]


def format_file(filename, base_command, lines=None, fix_formatting=False):
    if not os.path.exists(filename):
        return []
    command = base_command[:]
    if fix_formatting:
        command.append('-i')
    if lines:
        command.extend(lines)
    command.append(filename)
    p = subprocess.Popen(command,
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    if p.returncode != 0:
        err_msg = stderr.decode('utf-8') if stderr else "Unknown error"
        return [f"Java formatting error on {filename}:\n{err_msg}"]

    if not fix_formatting:
        with open(filename) as f:
            code = f.readlines()
        formatted_code = io.StringIO(stdout.decode('utf-8')).readlines()
        diff = list(
            difflib.unified_diff(code, formatted_code, filename, filename,
                                 '(before formatting)', '(after formatting)'))
        if diff:
            return [''.join(diff)]
    return []


def format_files(lines_by_file, base_command, fix_formatting=False):
    errors = []
    for filename, lines in lines_by_file.items():
        errors.extend(
            format_file(filename,
                        base_command,
                        lines=lines,
                        fix_formatting=fix_formatting))
    return errors


def format_diff(diff_text,
                google_java_format_jar,
                java_binary,
                fix_formatting=False,
                prefix_strip=0):
    lines_by_file = parse_diff(diff_text.splitlines(),
                               prefix_strip=prefix_strip)
    if not lines_by_file:
        return []

    base_command = get_base_command(java_binary, google_java_format_jar)
    return format_files(lines_by_file,
                        base_command,
                        fix_formatting=fix_formatting)
