# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path
import datetime
from subprocess import check_output, check_call, CalledProcessError, STDOUT, DEVNULL
import inspect
import os
import re
import sys
# Add both current path to allow us to package import utils and the tools
# dir to allow transitive (for utils) dependencies to be loaded.
repo_root = path.dirname(inspect.getfile(lambda: None))
tools_dir = os.path.join(repo_root, 'tools')
if repo_root not in sys.path:
    sys.path.insert(0, repo_root)
if tools_dir not in sys.path:
    sys.path.insert(0, tools_dir)

from tools import fmt_diff


def CheckDoNotMerge(input_api, output_api):
    for l in input_api.change.FullDescriptionText().splitlines():
        if l.lower().startswith('do not merge'):
            msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
            return [output_api.PresubmitPromptWarning(msg, [])]
    return []


def FormatPresubmitMessage(language):
    cmd = 'tools/fmt_diff.py --format-precise-diff'
    return f"""Please fix the {language} formatting by running:

  {cmd}

or fix formatting, commit and upload:

  {cmd} && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
    """


def CheckFormatting(input_api, output_api, branch=None):
    files = [f.LocalPath() for f in input_api.AffectedFiles()]
    check_results = fmt_diff.check(files, upstream_for_diff_formatting=branch)
    results = []
    for lang, errors in check_results.items():
        if errors:
            for err in errors:
                results.append(output_api.PresubmitError(err))
            results.append(
                output_api.PresubmitError(FormatPresubmitMessage(lang)))
    return results


def CheckDeterministicDebuggingChanged(input_api, output_api, branch):
    for f in input_api.AffectedFiles():
        local_path = f.LocalPath()
        if not local_path.endswith('InternalOptions.java'):
            continue
        diff = check_output(
            ['git', 'diff', '--no-prefix', '-U0', branch, '--',
             local_path]).decode('utf-8')
        if 'DETERMINISTIC_DEBUGGING' in diff:
            return [output_api.PresubmitError(diff)]
    return []


def IsTestFile(file):
    localPath = file.LocalPath()
    return fmt_diff.is_java_file(localPath) and '/test/' in localPath


def CheckForAddedDisassemble(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if IsTestFile(file) and '.disassemble()' in line:
            results.append(
                output_api.PresubmitError('Test call to disassemble\n%s:%s %s' %
                                          (file.LocalPath(), line_nr, line)))
    return results


def CheckForAddedAllowXxxxxxMessages(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if (IsTestFile(file) and ('.allowStdoutMessages()' in line or
                                  '.allowStderrMessages()' in line)):
            results.append(
                output_api.PresubmitError(
                    'Test call to allowStdoutMessages or allowStderrMessages\n%s:%s %s'
                    % (file.LocalPath(), line_nr, line)))
    return results


def CheckForAddedPartialDebug(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if not fmt_diff.is_java_file(file.LocalPath()):
            continue
        if '.enablePrintPartialCompilationPartitioning(' in line:
            results.append(
                output_api.PresubmitError(
                    'Test call to enablePrintPartialCompilationPartitioning\n%s:%s %s'
                    % (file.LocalPath(), line_nr, line)))
        if '.setPartialCompilationSeed(' in line:
            results.append(
                output_api.PresubmitError(
                    'Test call to setPartialCompilationSeed\n%s:%s %s' %
                    (file.LocalPath(), line_nr, line)))
    return results


def CheckForAddedHeadful(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if IsTestFile(file) and '.enableHeadful()' in line:
            results.append(
                output_api.PresubmitError(
                    'Test call to enableHeadful\n%s:%s %s' %
                    (file.LocalPath(), line_nr, line)))
    return results


def is_allow_old_copyright_set(input_api=None):
    val = os.environ.get('ALLOW_OLD_COPYRIGHT')
    if val is None and input_api and hasattr(input_api, 'environ'):
        val = input_api.environ.get('ALLOW_OLD_COPYRIGHT')
    if val is None:
        return False
    val_lower = val.lower()
    if val_lower == 'true':
        return True
    if val_lower == 'false':
        return False
    raise ValueError(
        f"Invalid value for ALLOW_OLD_COPYRIGHT: '{val}'. Expected 'true' or 'false'."
    )


def CheckForCopyright(input_api, output_api, branch):
    allow_old_copyright = is_allow_old_copyright_set(input_api)
    results = []
    # Include .gradle and .kts files in the copyright check.
    files_to_check = input_api.DEFAULT_FILES_TO_CHECK + (
        r'.*\.gradle$',
        r'.*\.kts$',
    )
    file_filter = lambda file: input_api.FilterSourceFile(
        file, files_to_check=files_to_check)
    for f in input_api.AffectedSourceFiles(file_filter):
        # Check if it is a new file.
        if f.OldContents():
            continue
        contents = f.NewContents()
        if (not contents) or (len(contents) == 0):
            continue
        error = CheckCopyrightInContents(
            f, contents, output_api, allow_old_copyright=allow_old_copyright)
        if error:
            results.append(error)
    return results


def CheckCopyrightInContents(f, contents, output_api, allow_old_copyright=None):
    if allow_old_copyright is None:
        allow_old_copyright = is_allow_old_copyright_set()
    prefix = '#' if (fmt_diff.is_python_file(f.LocalPath()) or
                     f.LocalPath().endswith('.sh')) else '//'
    pattern = re.escape(prefix) + r' Copyright \(c\) (\d+)'
    current_year = datetime.datetime.now().year
    for content_line in contents:
        match = re.search(pattern, content_line)
        if match:
            year = int(match.group(1))
            if year == current_year:
                return None
            elif year < current_year:
                if allow_old_copyright:
                    return None
                else:
                    return output_api.PresubmitError(
                        'Copyright found with old year in file: %s\n'
                        'To allow old copyright years, run:\n'
                        '  ALLOW_OLD_COPYRIGHT=true git cl ..' % f)
            else:
                return output_api.PresubmitError(
                    'Copyright found with future year in file: %s' % f)
    return output_api.PresubmitError('No copyright found in file: %s' % f)


def CheckLucicfg(input_api, output_api):
    for f in input_api.AffectedFiles():
        if f.LocalPath() == 'infra/config/global/main.star':
            try:
                check_call(
                    ['lucicfg', 'validate', 'infra/config/global/main.star'],
                    stdout=DEVNULL,
                    stderr=STDOUT)
            except CalledProcessError as e:
                return [
                    output_api.PresubmitError(
                        'lucicfg validate infra/config/global/main.star failed')
                ]
            except FileNotFoundError:
                return [output_api.PresubmitError('lucicfg not found in PATH')]
    return []


def CheckChange(input_api, output_api):
    branch = (check_output(['git', 'cl',
                            'upstream']).decode('utf-8').strip().replace(
                                'refs/heads/', ''))
    results = []
    results.extend(CheckDoNotMerge(input_api, output_api))
    results.extend(CheckFormatting(input_api, output_api, branch))
    results.extend(
        CheckDeterministicDebuggingChanged(input_api, output_api, branch))
    results.extend(CheckForAddedDisassemble(input_api, output_api))
    results.extend(CheckForAddedAllowXxxxxxMessages(input_api, output_api))
    results.extend(CheckForAddedPartialDebug(input_api, output_api))
    results.extend(CheckForAddedHeadful(input_api, output_api))
    results.extend(CheckForCopyright(input_api, output_api, branch))
    results.extend(CheckLucicfg(input_api, output_api))
    return results


def CheckChangeOnCommit(input_api, output_api):
    return CheckChange(input_api, output_api)


def CheckChangeOnUpload(input_api, output_api):
    return CheckChange(input_api, output_api)
