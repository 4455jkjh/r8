# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import datetime
from subprocess import check_output, check_call, CalledProcessError, STDOUT, DEVNULL
import inspect
import os
import re
import sys
# Add both current path to allow us to package import utils and the tools
# dir to allow transitive (for utils) dependencies to be loaded.
repo_root = os.path.dirname(inspect.getfile(lambda: None))
tools_dir = os.path.join(repo_root, 'tools')
if repo_root not in sys.path:
    sys.path.insert(0, repo_root)
if tools_dir not in sys.path:
    sys.path.insert(0, tools_dir)

from tools import utils

VERSION_FILE = os.path.join(repo_root, 'src', 'main', 'java', 'com', 'android',
                            'tools', 'r8', 'Version.java')


def get_version():
    with open(VERSION_FILE) as f:
        content = f.read()
    match = re.search(r'\bLABEL\s*=\s*"([^"]+)"', content)
    if not match:
        raise RuntimeError(f'Unable to find LABEL in {VERSION_FILE}')
    label = match.group(1)
    if label == 'main':
        return None
    return utils.check_basic_semver_version(label,
                                            'in Version.java',
                                            components=3,
                                            allow_prerelease=True)


def is_release_before(major, minor, patch, version=None):
    if version is None:
        version = get_version()
    if version is None:
        return False
    return utils.SemanticVersion(major, minor, patch, None).larger_than(version)


def check_do_not_merge(input_api, output_api):
    for line in input_api.change.FullDescriptionText().splitlines():
        if line.lower().startswith('do not merge'):
            msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
            return [output_api.PresubmitPromptWarning(msg, [])]
    return []


def format_presubmit_message(language):
    cmd = 'tools/fmt_diff.py'
    return f"""Please fix the {language} formatting by running:

  {cmd}

or fix formatting, commit and upload:

  {cmd} && git commit -a --amend --no-edit && git cl upload

To disable formatting for specific Java blocks, use `// @formatter:off` and `// @formatter:on` comments.
To disable formatting for a Kotlin file, add a `// ktfmt:disable-file` comment.

To skip formatting checks, for new CLs add footer to commit message:

  git commit --amend --no-edit --trailer "Skip-Formatting: true"

For existing CLs, add footer to CL description:

  git cl description -d | git interpret-trailers --trailer "Skip-Formatting: true" | git cl description -n -
    """


def ensure_default_jdk():
    from tools import jdk
    with open(jdk.GRADLE_DAEMON_JVM_PROPERTIES) as f:
        for line in f:
            line = line.strip()
            if line.startswith('toolchainVersion='):
                version = line.split('=', 1)[1].strip()
                jdk_dir = jdk.GetOSJavaHome(
                    os.path.join(jdk.JDK_DIRS, f'jdk-{version}'))
                if os.path.exists(jdk_dir + '.tar.gz.sha1'):
                    utils.ensure_google_download(jdk_dir)
                return


def check_formatting(input_api, output_api):
    if is_skip_formatting_set(input_api) or is_release_before(9, 6, -1):
        return []
    # Imported locally to avoid crashing on older 9.0-9.5 branches.
    from tools import fmt_diff
    ensure_default_jdk()
    files = [f.LocalPath() for f in input_api.AffectedFiles()]
    check_results = fmt_diff.check(files)
    results = []
    for lang, errors in check_results.items():
        if errors:
            for err in errors:
                results.append(output_api.PresubmitError(err))
            results.append(
                output_api.PresubmitError(format_presubmit_message(lang)))
    return results


def check_deterministic_debugging_changed(input_api, output_api, branch):
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


# File extension checks are kept local to avoid importing fmt_diff on older
# 9.0-9.5 branches.
def is_test_file(file):
    local_path = file.LocalPath()
    return local_path.endswith('.java') and '/test/' in local_path


def check_for_added_disassemble(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if is_test_file(file) and '.disassemble()' in line:
            results.append(
                output_api.PresubmitError('Test call to disassemble\n%s:%s %s' %
                                          (file.LocalPath(), line_nr, line)))
    return results


def check_for_added_allow_xxxxxx_messages(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if (is_test_file(file) and ('.allowStdoutMessages()' in line or
                                    '.allowStderrMessages()' in line)):
            results.append(
                output_api.PresubmitError(
                    'Test call to allowStdoutMessages or allowStderrMessages\n%s:%s %s'
                    % (file.LocalPath(), line_nr, line)))
    return results


def check_for_added_partial_debug(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if not file.LocalPath().endswith('.java'):
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


def check_for_added_headful(input_api, output_api):
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if is_test_file(file) and '.enableHeadful()' in line:
            results.append(
                output_api.PresubmitError(
                    'Test call to enableHeadful\n%s:%s %s' %
                    (file.LocalPath(), line_nr, line)))
    return results


def check_for_added_without_collapsed_runtimes(input_api, output_api):
    version = get_version()
    if version is None:
        return []
    checks = [(method, major, minor) for method, major, minor in (
        ('withoutCollapsedDexRuntimes', 9, 2),
        ('withoutCollapsedCfRuntimes', 9, 6),
    ) if is_release_before(major, minor, -1, version)]
    if not checks:
        return []
    results = []
    for (file, line_nr, line) in input_api.RightHandSideLines():
        if not is_test_file(file):
            continue
        for method, major, minor in checks:
            if f'.{method}()' in line:
                results.append(
                    output_api.PresubmitError(
                        'Test call to %s is not allowed before branch %d.%d (current version %s)\n%s:%s %s'
                        % (method, major, minor, version, file.LocalPath(),
                           line_nr, line)))
    return results


def parse_bool_option(name, val):
    val_lower = val.lower()
    if val_lower == 'true':
        return True
    if val_lower == 'false':
        return False
    raise ValueError(
        f"Invalid value for {name}: '{val}'. Expected 'true' or 'false'.")


def get_footer_flag(input_api, footer_key):
    if not input_api:
        return None
    values = input_api.change.GitFootersFromDescription().get(footer_key)
    if values:
        return parse_bool_option(footer_key, values[0])
    return None


def is_no_presubmit_set(input_api=None):
    return bool(get_footer_flag(input_api, 'No-Presubmit'))


def is_skip_formatting_set(input_api=None):
    return bool(get_footer_flag(input_api, 'Skip-Formatting'))


def is_allow_old_copyright_set(input_api=None):
    return bool(get_footer_flag(input_api, 'Allow-Old-Copyright'))


def check_for_copyright(input_api, output_api):
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
        if not contents:
            continue
        error = check_copyright_in_contents(
            f, contents, output_api, allow_old_copyright=allow_old_copyright)
        if error:
            results.append(error)
    return results


def check_copyright_in_contents(f,
                                contents,
                                output_api,
                                allow_old_copyright=False):
    prefix = '#' if (f.LocalPath().endswith('.py') or
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
                        'Copyright found with old year (%s) in file: %s\n\n'
                        'For new CLs, add footer to commit message:\n\n'
                        '  git commit --amend --no-edit --trailer "Allow-Old-Copyright: true"\n\n'
                        'For existing CLs, add footer to CL description:\n\n'
                        '  git cl description -d | git interpret-trailers --trailer "Allow-Old-Copyright: true" | git cl description -n -'
                        % (year, f))
            else:
                return output_api.PresubmitError(
                    'Copyright found with future year in file: %s' % f)
    return output_api.PresubmitError('No copyright found in file: %s' % f)


def check_lucicfg(input_api, output_api):
    for f in input_api.AffectedFiles():
        if f.LocalPath() == 'infra/config/global/main.star':
            try:
                check_call(
                    ['lucicfg', 'validate', 'infra/config/global/main.star'],
                    stdout=DEVNULL,
                    stderr=STDOUT)
            except CalledProcessError:
                return [
                    output_api.PresubmitError(
                        'lucicfg validate infra/config/global/main.star failed')
                ]
            except FileNotFoundError:
                return [output_api.PresubmitError('lucicfg not found in PATH')]
    return []


def get_upstream_branch(input_api):
    upstream = input_api.change.UpstreamBranch()
    if upstream:
        return upstream.replace('refs/heads/', '')
    try:
        branch = check_output(['git', 'cl', 'upstream'],
                              stderr=DEVNULL).decode('utf-8').strip()
        if branch:
            return branch.replace('refs/heads/', '')
    except CalledProcessError:
        pass
    raise Exception('Unable to determine upstream branch')


def check_change(input_api, output_api):
    if is_no_presubmit_set(input_api):
        return []
    branch = get_upstream_branch(input_api)
    results = []
    results.extend(check_do_not_merge(input_api, output_api))
    results.extend(check_formatting(input_api, output_api))
    results.extend(
        check_deterministic_debugging_changed(input_api, output_api, branch))
    results.extend(check_for_added_disassemble(input_api, output_api))
    results.extend(check_for_added_allow_xxxxxx_messages(input_api, output_api))
    results.extend(check_for_added_partial_debug(input_api, output_api))
    results.extend(check_for_added_headful(input_api, output_api))
    results.extend(
        check_for_added_without_collapsed_runtimes(input_api, output_api))
    results.extend(check_for_copyright(input_api, output_api))
    results.extend(check_lucicfg(input_api, output_api))
    return results


def CheckChangeOnCommit(input_api, output_api):
    return check_change(input_api, output_api)


def CheckChangeOnUpload(input_api, output_api):
    return check_change(input_api, output_api)
