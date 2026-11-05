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


def create_diff(filename, formatted_code_str):
    with open(filename) as f:
        code = f.readlines()
    formatted_code = io.StringIO(formatted_code_str).readlines()
    diff = difflib.unified_diff(code, formatted_code, filename, filename,
                                '(before formatting)', '(after formatting)')
    return ''.join(diff)
