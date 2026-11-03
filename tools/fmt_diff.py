#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
from concurrent.futures import ThreadPoolExecutor
import os
import re
from subprocess import DEVNULL, PIPE, Popen
import sys

# Support running both directly as a script (where tools/ is in sys.path)
# and when imported as a module from the repository root (e.g. PRESUBMIT.py).
try:
    from tools import defines, google_java_format_diff, jdk, utils
except ImportError:
    import defines
    import google_java_format_diff
    import jdk
    import utils

GOOGLE_JAVA_FORMAT_DIR = os.path.join(defines.THIRD_PARTY, 'google',
                                      'google-java-format', '1.24.0')
GOOGLE_JAVA_FORMAT_JAR = os.path.join(GOOGLE_JAVA_FORMAT_DIR,
                                      'google-java-format-1.24.0-all-deps.jar')

GOOGLE_KOTLIN_FORMAT_DIR = os.path.join(defines.THIRD_PARTY, 'google',
                                        'google-kotlin-format', '0.54')
GOOGLE_KOTLIN_FORMAT_JAR = os.path.join(GOOGLE_KOTLIN_FORMAT_DIR,
                                        'ktfmt-0.54-jar-with-dependencies.jar')

GOOGLE_YAPF_DIR = os.path.join(defines.THIRD_PARTY, 'google', 'yapf',
                               '20231013')
GOOGLE_YAPF = os.path.join(GOOGLE_YAPF_DIR, 'yapf')
YAPF_PYTHON_PATH = [
    GOOGLE_YAPF_DIR,
    os.path.join(GOOGLE_YAPF_DIR, 'third_party')
]

NODE_DIR = os.path.join(defines.THIRD_PARTY, 'node', '24.16.0', 'linux')
NODE_BIN = os.path.join(NODE_DIR, 'bin', 'node')

PRETTIER_DIR = os.path.join(defines.THIRD_PARTY, 'prettier', '3.8.3')
PRETTIER_BIN = os.path.join(PRETTIER_DIR, 'node_modules', 'prettier', 'bin',
                            'prettier.cjs')

PLATFORMDIRS_ERROR_MSG = (
    "Error: Could not find a Python interpreter with `platformdirs` installed.\n"
    "Please ensure it is installed in your environment:\n"
    "  $ python3 -m venv .venv\n"
    "  $ source .venv/bin/activate\n"
    "  $ pip3 install platformdirs")

INCLUDED_FILES = {
    'PRESUBMIT.py',
    'build.gradle.kts',
    'settings.gradle.kts',
}

INCLUDED_DIRS = (
    'd8_r8',
    'scripts',
    'src',
    'tools',
)

EXCLUDED_SUBDIRS = {
    '__pycache__',
    'build',
    'out',
}

EXCLUDED_DIRS = (
    'tools/linux',
    'tools/mac',
    'tools/windows',
)

REPO_ROOT_ABS = os.path.abspath(defines.REPO_ROOT)


def is_java_file(file_path):
    return file_path.endswith('.java')


def is_kotlin_file(file_path):
    return file_path.endswith('.kt') or file_path.endswith('.kts')


def is_python_file(file_path):
    return file_path.endswith('.py')


def is_web_file(file_path):
    return file_path.endswith(('.js', '.html', '.css'))


def is_formattable_file(file_path):
    return (is_java_file(file_path) or is_kotlin_file(file_path) or
            is_python_file(file_path) or is_web_file(file_path))


def get_repo_rel_path(path):
    abs_path = os.path.abspath(path)
    rel = os.path.relpath(abs_path, REPO_ROOT_ABS).replace('\\', '/')
    if rel.startswith('..'):
        rel = os.path.relpath(os.path.realpath(path),
                              defines.REPO_ROOT).replace('\\', '/')
    return rel


def is_within_included_dirs(rel_dir):
    return any(
        rel_dir == d or rel_dir.startswith(d + '/') for d in INCLUDED_DIRS)


def has_excluded_component(rel_path):
    if any(rel_path == d or rel_path.startswith(d + '/')
           for d in EXCLUDED_DIRS):
        return True
    return any(
        p.startswith('.') or p in EXCLUDED_SUBDIRS for p in rel_path.split('/'))


def is_included_rel_file(rel_file):
    if rel_file == '.' or rel_file.startswith('..'):
        return False
    if rel_file in INCLUDED_FILES:
        return True
    if not any(rel_file.startswith(d + '/') for d in INCLUDED_DIRS):
        return False
    return not has_excluded_component(rel_file)


def is_included_file(file_path):
    return is_included_rel_file(get_repo_rel_path(file_path))


def is_included_dir(rel_dir):
    if rel_dir == '.':
        return True
    if rel_dir.startswith('..') or has_excluded_component(rel_dir):
        return False
    return (is_within_included_dirs(rel_dir) or
            any(d.startswith(rel_dir + '/') for d in INCLUDED_DIRS))


def get_changed_files(upstream, file_predicate=None):
    returncode, stdout, stderr = run_command(
        ['git', 'diff', '--name-only', upstream], cwd=defines.REPO_ROOT)
    if returncode != 0:
        err_msg = stderr or stdout or f"Failed to diff against {upstream}."
        print(f"Git error:\n{err_msg}", file=sys.stderr)
        return None
    return [
        f for f in stdout.splitlines() if is_included_rel_file(f) and
        (file_predicate is None or file_predicate(f)) and
        os.path.exists(os.path.join(defines.REPO_ROOT, f))
    ]


def get_path_files(path, file_predicate=None):
    predicate = file_predicate or is_formattable_file
    if not os.path.exists(path):
        print(f"Error: Directory or file not found: {path}", file=sys.stderr)
        return [], False
    if os.path.isfile(path):
        if not is_included_file(path):
            print(f"Error: File is not in included paths: {path}",
                  file=sys.stderr)
            return [], False
        norm_path = os.path.normpath(path)
        if predicate(norm_path):
            return [norm_path], True
        return [], True
    start_rel = get_repo_rel_path(path)
    if not is_included_dir(start_rel):
        print(f"Error: Directory is not in included paths: {path}",
              file=sys.stderr)
        return [], False
    files = []
    for root, dirs, filenames in os.walk(path):
        rel_from_start = os.path.relpath(root, path).replace('\\', '/')
        if rel_from_start == '.':
            root_rel = start_rel
        elif start_rel == '.':
            root_rel = rel_from_start
        else:
            root_rel = f'{start_rel}/{rel_from_start}'
        dirs[:] = [
            d for d in dirs
            if is_included_dir(d if root_rel == '.' else f'{root_rel}/{d}')
        ]
        for filename in filenames:
            rel_file = (filename
                        if root_rel == '.' else f'{root_rel}/{filename}')
            if not is_included_rel_file(rel_file):
                continue
            file_path = os.path.normpath(os.path.join(root, filename))
            if predicate(file_path):
                files.append(file_path)
    return files, True


def chunk_list(items, chunk_size):
    for i in range(0, len(items), chunk_size):
        yield items[i:i + chunk_size]


def partition_kotlin_files(files):
    kotlin_files = []
    google_files = []
    for p in files:
        if get_repo_rel_path(p).startswith('src/keepanno/'):
            kotlin_files.append(p)
        else:
            google_files.append(p)
    return {
        '--kotlinlang-style': kotlin_files,
        '--google-style': google_files,
    }


def get_env_with_python_path():
    new_env = os.environ.copy()
    new_env['PYTHONPATH'] = os.pathsep.join(YAPF_PYTHON_PATH)
    return new_env


class PythonRuntime:

    def __init__(self):
        self.interpreter = None

    def initialize_runtime(self):
        python_env = get_env_with_python_path()
        for candidate in [
                sys.executable,
                'python3',
                os.path.join(defines.REPO_ROOT, '.venv', 'bin', 'python3'),
                os.path.join(defines.REPO_ROOT, '.venv', 'bin', 'python'),
        ]:
            try:
                returncode, _, _ = run_command(
                    [candidate, '-c', 'import platformdirs'], env=python_env)
                if returncode == 0:
                    self.interpreter = candidate
                    return None
            except FileNotFoundError:
                continue

        return PLATFORMDIRS_ERROR_MSG


def get_java_format_base_command():
    java_exec = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    return [
        java_exec,
        # Disable hsperfdata to prevent lock contention on /tmp/hsperfdata_<user> during parallel execution.
        '-XX:+PerfDisableSharedMem',
        '--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED',
        '--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED',
        '-jar',
        GOOGLE_JAVA_FORMAT_JAR,
    ]


def get_kotlin_format_base_command():
    java_exec = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    # Disable hsperfdata to prevent lock contention on /tmp/hsperfdata_<user> during parallel execution.
    return [
        java_exec, '-XX:+PerfDisableSharedMem', '-jar', GOOGLE_KOTLIN_FORMAT_JAR
    ]


def get_python_format_base_command(python_runtime):
    return [python_runtime.interpreter, GOOGLE_YAPF, '--style', 'google']


def get_prettier_base_command():
    return [NODE_BIN, PRETTIER_BIN]


def get_git_upstream():
    returncode, stdout, stderr = run_command(['git', 'cl', 'upstream'],
                                             cwd=defines.REPO_ROOT)
    if returncode != 0:
        err_msg = stderr or stdout or "Failed to determine git upstream."
        print(f"Git error:\n{err_msg}", file=sys.stderr)
        return None
    return stdout.strip()


JAVA_FORMATTER_TAG_RE = re.compile(r'(?://|/\*)\s*@formatter:(off|on)\b')
KTFMT_DISABLE_TAG_RE = re.compile(r'(?://|/\*)\s*ktfmt:disable-file\b')


def is_kotlin_file_disabled(file_path):
    if not os.path.exists(file_path):
        return False
    with open(file_path, 'rb') as f:
        content = f.read()
    if b'ktfmt:disable-file' not in content:
        return False
    return bool(
        KTFMT_DISABLE_TAG_RE.search(content.decode('utf-8', errors='ignore')))


# Scans `filename` for `@formatter:off` and `@formatter:on` comments.
# Returns:
#   - None: if `@formatter:off` never appears in the file (the entire file is enabled).
#   - list of (start_line, end_line) 1-based inclusive tuples: the enabled line ranges
#     when `@formatter:off` is used (empty list `[]` if the entire file is disabled).
def get_java_enabled_ranges(filename):
    if not os.path.exists(filename):
        return []

    with open(filename, 'rb') as f:
        raw_content = f.read()
    if b'@formatter:off' not in raw_content:
        return None

    enabled_ranges = []
    enabled = True
    start_line = 1
    saw_formatter_off = False
    line_num = 0

    for line_num, line in enumerate(raw_content.decode('utf-8').splitlines(),
                                    start=1):
        if '@formatter:' in line:
            match = JAVA_FORMATTER_TAG_RE.search(line)
            if match:
                state = match.group(1)
                if state == 'off':
                    saw_formatter_off = True
                    if enabled:
                        if start_line < line_num:
                            enabled_ranges.append((start_line, line_num - 1))
                        enabled = False
                elif state == 'on' and not enabled:
                    enabled = True
                    start_line = line_num + 1

    if not saw_formatter_off:
        return None

    if enabled and start_line <= line_num:
        enabled_ranges.append((start_line, line_num))

    return enabled_ranges


def ranges_to_cli_args(line_ranges):
    args = []
    for start, end in line_ranges:
        args.extend(['-lines', f'{start}:{end}'])
    return args


def run_command(command, env=None, cwd=None):
    p = Popen(command,
              stdout=PIPE,
              stderr=PIPE,
              stdin=DEVNULL,
              env=env,
              cwd=cwd)
    stdout, stderr = p.communicate()
    return p.returncode, stdout.decode('utf-8'), stderr.decode('utf-8')


def format_java_file(filename, base_command, line_ranges, fix_formatting):
    if not os.path.exists(filename):
        return []
    command = base_command[:]
    if fix_formatting:
        command.append('-i')
    if line_ranges:
        command.extend(ranges_to_cli_args(line_ranges))
    command.append(filename)
    returncode, stdout, stderr = run_command(command)
    if returncode != 0:
        err_msg = stderr if stderr else "Unknown error"
        return [f"Java formatting error on {filename}:\n{err_msg}"]

    if not fix_formatting:
        diff_string = google_java_format_diff.create_diff(filename, stdout)
        if diff_string:
            return [diff_string]
    return []


def segment_java_files(files):
    full_files = []
    partial_files = []
    for file_path in files:
        enabled_ranges = get_java_enabled_ranges(file_path)
        if enabled_ranges is None:
            full_files.append(file_path)
        elif enabled_ranges:
            partial_files.append((file_path, enabled_ranges))
    return full_files, partial_files


def apply_batch_java_formatting(files, base_command):
    returncode, _, stderr = run_command(base_command + ['-i'] + files)
    if returncode != 0:
        err_msg = stderr if stderr else "Unknown error"
        return [f"Java formatting error:\n{err_msg}"]
    return []


def check_batch_java_formatting(files, base_command):
    returncode, stdout, stderr = run_command(
        base_command + ['--dry-run', '--set-exit-if-changed'] + files)
    errors = []
    if returncode != 0:
        if stdout:
            for file_path in stdout.splitlines():
                errors.extend(
                    format_java_file(file_path, base_command, None, False))
        if stderr:
            errors.append(f"Java formatting error:\n{stderr}")
        elif not errors and not stdout:
            errors.append("Java formatting error:\nUnknown error")
    return errors


# This function must be threadsafe.
# prepare_java_files() must be called first.
def format_batch_java_files(files, base_command, fix_formatting):
    if not files:
        return []
    if fix_formatting:
        return apply_batch_java_formatting(files, base_command)
    else:
        return check_batch_java_formatting(files, base_command)


def prepare_java_files():
    utils.ensure_google_download(GOOGLE_JAVA_FORMAT_DIR)


def prepare_kotlin_files():
    utils.ensure_google_download(GOOGLE_KOTLIN_FORMAT_DIR)


# This function must be threadsafe.
# prepare_kotlin_files() must be called first.
def format_kotlin_files(files, format_style, fix_formatting=False):
    if not files:
        return []
    cmd = get_kotlin_format_base_command() + [format_style]
    if not fix_formatting:
        cmd.append('-n')
    cmd.extend(files)
    returncode, stdout, stderr = run_command(cmd)
    if returncode != 0:
        err_msg = stderr if stderr else "Unknown error"
        return [f"Kotlin formatting error:\n{err_msg}"]
    if not fix_formatting and stdout:
        return [
            f"File {file_path} needs formatting"
            for file_path in stdout.splitlines()
        ]
    return []


def prepare_python_files(python_runtime: PythonRuntime = None):
    utils.ensure_google_download(GOOGLE_YAPF_DIR)
    if python_runtime:
        return python_runtime.initialize_runtime()
    return None


# This function must be threadsafe.
# prepare_python_files() must be called first.
def format_python_files(files,
                        python_runtime: PythonRuntime,
                        fix_formatting=False):
    if not files:
        return []
    mode_flag = '--in-place' if fix_formatting else '--diff'
    format_cmd = get_python_format_base_command(python_runtime) + [mode_flag
                                                                  ] + files
    returncode, stdout, stderr = run_command(format_cmd,
                                             env=get_env_with_python_path())
    if returncode != 0:
        errors = []
        if stdout:
            errors.append(stdout)
        if stderr:
            errors.append(f"Python formatting error:\n{stderr}")
        if not errors:
            errors.append("Python formatting error:\nUnknown error")
        return errors
    return []


def prepare_web_files():
    utils.ensure_google_download(NODE_DIR)
    utils.ensure_google_download(PRETTIER_DIR)


# This function must be threadsafe.
# prepare_web_files() must be called first.
def format_web_files(files, fix_formatting=False):
    if not files:
        return []
    mode_flag = '--write' if fix_formatting else '--check'
    format_cmd = get_prettier_base_command() + [mode_flag] + files
    returncode, stdout, stderr = run_command(format_cmd)
    if returncode != 0:
        err_msg = stderr or stdout or "Unknown error"
        return [f"Web formatting error:\n{err_msg}"]
    return []


def check(files, fix_formatting=False, batch_size=50):
    java_files = []
    kotlin_files = []
    python_files = []
    web_files = []

    for file_path in files:
        if not os.path.exists(file_path) or not is_included_file(file_path):
            pass
        elif is_kotlin_file(file_path):
            if not is_kotlin_file_disabled(file_path):
                kotlin_files.append(file_path)
        elif is_java_file(file_path):
            java_files.append(file_path)
        elif is_python_file(file_path):
            python_files.append(file_path)
        elif is_web_file(file_path):
            web_files.append(file_path)

    results = {
        'Java': [],
        'Kotlin': [],
        'Python': [],
        'Web (JS, HTML, CSS)': [],
    }

    full_java_files = []
    partial_java_files = []
    java_base_command = None
    if java_files:
        prepare_java_files()
        java_base_command = get_java_format_base_command()
        full_java_files, partial_java_files = segment_java_files(java_files)

    if kotlin_files:
        prepare_kotlin_files()

    python_runtime = PythonRuntime()
    if python_files:
        init_error = prepare_python_files(python_runtime)
        if init_error:
            results['Python'].append(init_error)
            return results

    if web_files:
        prepare_web_files()

    futures = []
    with ThreadPoolExecutor() as executor:
        for batch in chunk_list(full_java_files, batch_size):
            futures.append(('Java',
                            executor.submit(format_batch_java_files, batch,
                                            java_base_command, fix_formatting)))
        for file_path, line_ranges in partial_java_files:
            futures.append(
                ('Java',
                 executor.submit(format_java_file, file_path, java_base_command,
                                 line_ranges, fix_formatting)))

        for format_style, style_paths in partition_kotlin_files(
                kotlin_files).items():
            for batch in chunk_list(style_paths, batch_size):
                futures.append(('Kotlin',
                                executor.submit(format_kotlin_files,
                                                batch,
                                                format_style,
                                                fix_formatting=fix_formatting)))

        for batch in chunk_list(python_files, batch_size):
            futures.append(('Python',
                            executor.submit(format_python_files,
                                            batch,
                                            python_runtime,
                                            fix_formatting=fix_formatting)))

        for batch in chunk_list(web_files, batch_size):
            futures.append(('Web (JS, HTML, CSS)',
                            executor.submit(format_web_files,
                                            batch,
                                            fix_formatting=fix_formatting)))

        for lang, future in futures:
            errors = future.result()
            if errors:
                results[lang].extend(errors)

    return results


def parse_options():
    result = argparse.ArgumentParser()
    result.add_argument('paths',
                        nargs='*',
                        help='Files or directories to format.')
    result.add_argument('--check',
                        help='Check formatting without modifying files.',
                        action='store_true',
                        default=False)
    result.add_argument(
        '--upstream',
        help=
        'Upstream branch/commit to find changed files against (default: git cl upstream).',
        default=None)
    only_group = result.add_mutually_exclusive_group()
    only_group.add_argument('--only-java',
                            help='Only run google-java-format.',
                            action='store_true',
                            default=False)
    only_group.add_argument('--only-kotlin',
                            help='Only run google-kotlin-format.',
                            action='store_true',
                            default=False)
    only_group.add_argument('--only-python',
                            help='Only run YAPF.',
                            action='store_true',
                            default=False)
    only_group.add_argument('--only-web',
                            help='Only run Prettier.',
                            action='store_true',
                            default=False)
    return result.parse_args()


def format_files(files, fix_formatting=True):
    results = check(files, fix_formatting=fix_formatting)
    has_errors = False
    for errors in results.values():
        for err in errors:
            has_errors = True
            print(err, file=sys.stderr)
    return not has_errors


def get_predicate(options):
    if options.only_java:
        return is_java_file
    elif options.only_kotlin:
        return is_kotlin_file
    elif options.only_python:
        return is_python_file
    elif options.only_web:
        return is_web_file
    return None


def format_all_changed_files(upstream, options):
    predicate = get_predicate(options)
    changed_files = get_changed_files(upstream, predicate)
    if changed_files is None:
        return False
    return format_files(changed_files, fix_formatting=not options.check)


def format_paths(paths, options):
    predicate = get_predicate(options)
    files = []
    all_paths_ok = True
    for path in paths:
        path_files, ok = get_path_files(path, predicate)
        if not ok:
            all_paths_ok = False
        files.extend(path_files)
    formatted_ok = format_files(list(dict.fromkeys(files)),
                                fix_formatting=not options.check)
    return all_paths_ok and formatted_ok


def resolve_cli_path(path):
    rel = get_repo_rel_path(path)
    if rel.startswith('..'):
        return os.path.abspath(path)
    return rel


def main():
    options = parse_options()
    if options.paths and options.upstream:
        print("Error: Cannot specify both paths and --upstream.",
              file=sys.stderr)
        return 1
    resolved_paths = [resolve_cli_path(p) for p in options.paths]
    os.chdir(defines.REPO_ROOT)
    if resolved_paths:
        ok = format_paths(resolved_paths, options)
    else:
        upstream = options.upstream or get_git_upstream()
        if not upstream:
            return 1
        ok = format_all_changed_files(upstream, options)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
