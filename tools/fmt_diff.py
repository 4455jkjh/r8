#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
from concurrent.futures import ThreadPoolExecutor
import os
from subprocess import CalledProcessError, DEVNULL, PIPE, Popen, STDOUT, check_call, check_output
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

FMT_IGNORE = {
    'src/test/java/com/android/tools/r8/kotlin/metadata/inline_class_fun_descriptor_classes_app/main.kt'
}

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


def get_changed_files(upstream, file_predicate=None):
    changed_files = check_output(['git', 'diff', '--name-only',
                                  upstream]).decode('utf-8').splitlines()
    return [
        f for f in changed_files if f not in FMT_IGNORE and
        (file_predicate is None or file_predicate(f)) and os.path.exists(f)
    ]


def get_dir_files(directory, file_predicate=None):
    predicate = file_predicate or is_formattable_file
    if not os.path.exists(directory):
        print(f"Error: Directory or file not found: {directory}",
              file=sys.stderr)
        return []
    if os.path.isfile(directory):
        if directory not in FMT_IGNORE and predicate(directory):
            return [directory]
        return []
    files = []
    for root, _, filenames in os.walk(directory):
        for filename in filenames:
            file_path = os.path.join(root, filename)
            if file_path not in FMT_IGNORE and predicate(file_path):
                files.append(file_path)
    return files


def chunk_list(items, chunk_size):
    for i in range(0, len(items), chunk_size):
        yield items[i:i + chunk_size]


def partition_kotlin_files(files):
    return {
        '--kotlinlang-style': [
            p for p in files if p.replace('\\', '/').startswith('src/keepanno/')
        ],
        '--google-style': [
            p for p in files
            if not p.replace('\\', '/').startswith('src/keepanno/')
        ],
    }


def get_env_with_python_path():
    new_env = os.environ.copy()
    new_env['PYTHONPATH'] = os.pathsep.join(YAPF_PYTHON_PATH)
    return new_env


class PythonRuntime:

    def __init__(self):
        self.interpreter = None
        self.has_failed = False

    def initialize_runtime(self):
        python_env = get_env_with_python_path()
        for candidate in [
                sys.executable, 'python3',
                os.path.join('.venv', 'bin', 'python3'),
                os.path.join('.venv', 'bin', 'python')
        ]:
            try:
                check_call([candidate, '-c', 'import platformdirs'],
                           stdout=DEVNULL,
                           stderr=DEVNULL,
                           env=python_env)
                self.interpreter = candidate
                return None
            except (CalledProcessError, FileNotFoundError):
                continue

        self.has_failed = True
        return PLATFORMDIRS_ERROR_MSG


def get_java_format_base_command():
    java_bin = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    return google_java_format_diff.get_base_command(java_bin,
                                                    GOOGLE_JAVA_FORMAT_JAR)


def get_kotlin_format_base_command():
    java_exec = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    return [java_exec, '-jar', GOOGLE_KOTLIN_FORMAT_JAR]


def get_python_format_base_command(python_runtime):
    return [python_runtime.interpreter, GOOGLE_YAPF, '--style', 'google']


def get_prettier_base_command():
    return [NODE_BIN, PRETTIER_BIN]


def get_git_upstream():
    return check_output(['git', 'cl', 'upstream']).decode('utf-8').strip()


def prepare_check_java_files():
    utils.ensure_google_download(GOOGLE_JAVA_FORMAT_DIR)


# This function must be threadsafe.
# prepare_check_java_files() must be called first.
# upstream_for_diff_formatting: If set, only changed lines are checked/formatted (best effort).
def check_java_files_precise_diff(files, upstream, fix_formatting=False):
    if not upstream:
        return [
            "Error: Upstream branch/commit must be specified for per-line Java formatting."
        ]
    if not files:
        return []
    try:
        git_diff_cmd = ['git', 'diff', '-U0', '--no-color', upstream, '--'
                       ] + files
        diff_text = check_output(git_diff_cmd).decode('utf-8')
    except CalledProcessError as e:
        return [
            f"Git diff error:\n{e.output.decode('utf-8') if e.output else str(e)}"
        ]

    if not diff_text.strip():
        return []

    java_bin = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    return google_java_format_diff.format_diff(diff_text,
                                               GOOGLE_JAVA_FORMAT_JAR,
                                               java_bin,
                                               fix_formatting=fix_formatting,
                                               prefix_strip=1)


# This function must be threadsafe.
# prepare_check_java_files() must be called first.
def check_java_files_full(files, fix_formatting=False):
    if not files:
        return []
    cmd = get_java_format_base_command()
    if fix_formatting:
        cmd.extend(['-i'] + files)
        proc = Popen(cmd, stdout=PIPE, stderr=PIPE, stdin=DEVNULL)
        _, stderr = proc.communicate()
        if proc.returncode != 0:
            err_msg = stderr.decode('utf-8') if stderr else "Unknown error"
            return [f"Java formatting error:\n{err_msg}"]
        return []
    else:
        cmd.extend(['--dry-run', '--set-exit-if-changed'] + files)
        proc = Popen(cmd, stdout=PIPE, stderr=PIPE, stdin=DEVNULL)
        stdout, stderr = proc.communicate()
        errors = []
        if proc.returncode != 0:
            if stdout:
                base_command = get_java_format_base_command()
                for file_path in stdout.decode('utf-8').splitlines():
                    errors.extend(
                        google_java_format_diff.format_file(
                            file_path, base_command, fix_formatting=False))
            if stderr:
                errors.append(
                    f"Java formatting error:\n{stderr.decode('utf-8')}")
            elif not errors and not stdout:
                errors.append("Java formatting error:\nUnknown error")
        return errors


# This function must be threadsafe.
# prepare_check_java_files() must be called first.
# upstream_for_diff_formatting: If set, only changed lines are checked/formatted (best effort).
def check_java_files(files,
                     fix_formatting=False,
                     upstream_for_diff_formatting=None):
    if upstream_for_diff_formatting is not None:
        return check_java_files_precise_diff(files,
                                             upstream_for_diff_formatting,
                                             fix_formatting=fix_formatting)
    else:
        return check_java_files_full(files, fix_formatting=fix_formatting)


def prepare_check_kotlin_files():
    utils.ensure_google_download(GOOGLE_KOTLIN_FORMAT_DIR)


# This function must be threadsafe.
# prepare_check_kotlin_files() must be called first.
def check_kotlin_files(files, format_style, fix_formatting=False):
    cmd = get_kotlin_format_base_command() + [format_style]
    if not fix_formatting:
        cmd.append('-n')
    cmd.extend(files)
    if fix_formatting:
        check_call(cmd)
        return []
    else:
        result = check_output(cmd)
        errors = []
        if len(result) > 0:
            for file_path in result.splitlines():
                errors.append(
                    f"File {file_path.decode('utf-8')} needs formatting")
        return errors


def prepare_check_python_files(python_runtime: PythonRuntime = None):
    utils.ensure_google_download(GOOGLE_YAPF_DIR)
    if python_runtime:
        return python_runtime.initialize_runtime()
    return None


# This function must be threadsafe.
# prepare_check_python_files() must be called first.
def check_python_files(files,
                       python_runtime: PythonRuntime,
                       fix_formatting=False):
    mode_flag = '--in-place' if fix_formatting else '--diff'
    format_cmd = get_python_format_base_command(python_runtime) + [mode_flag
                                                                  ] + files
    python_env = get_env_with_python_path()
    try:
        check_output(format_cmd, stderr=STDOUT, env=python_env)
        return []
    except CalledProcessError as e:
        output_str = (e.output.decode('utf-8')
                      if isinstance(e.output, bytes) else str(e.output))
        if fix_formatting:
            return [f"Python formatting error:\n{output_str}"]
        return [output_str]


def prepare_check_web_files():
    utils.ensure_google_download(NODE_DIR)
    utils.ensure_google_download(PRETTIER_DIR)


# This function must be threadsafe.
# prepare_check_web_files() must be called first.
def check_web_files(files, fix_formatting=False):
    mode_flag = '--write' if fix_formatting else '--check'
    format_cmd = get_prettier_base_command() + [mode_flag] + files
    if fix_formatting:
        check_call(format_cmd)
        return []
    else:
        try:
            check_output(format_cmd, stderr=STDOUT)
            return []
        except CalledProcessError as e:
            output_str = (e.output.decode('utf-8') if isinstance(
                e.output, bytes) else str(e.output))
            return [f"Web formatting error:\n{output_str}"]


# upstream_for_diff_formatting: If set, only changed lines are checked/formatted (best effort).
def check(files,
          fix_formatting=False,
          batch_size=100,
          upstream_for_diff_formatting=None):
    java_files = []
    kotlin_files = []
    python_files = []
    web_files = []

    for file_path in files:
        if file_path in FMT_IGNORE or not os.path.exists(file_path):
            pass
        elif is_kotlin_file(file_path):
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

    if java_files:
        prepare_check_java_files()

    if kotlin_files:
        prepare_check_kotlin_files()

    python_runtime = PythonRuntime()
    if python_files:
        init_error = prepare_check_python_files(python_runtime)
        if init_error:
            results['Python'].append(init_error)
            return results

    if web_files:
        prepare_check_web_files()

    futures = []
    with ThreadPoolExecutor() as executor:
        for batch in chunk_list(java_files, batch_size):
            futures.append((
                'Java',
                executor.submit(
                    check_java_files,
                    batch,
                    fix_formatting=fix_formatting,
                    upstream_for_diff_formatting=upstream_for_diff_formatting)))

        for format_style, style_paths in partition_kotlin_files(
                kotlin_files).items():
            for batch in chunk_list(style_paths, batch_size):
                futures.append(('Kotlin',
                                executor.submit(check_kotlin_files,
                                                batch,
                                                format_style,
                                                fix_formatting=fix_formatting)))

        for batch in chunk_list(python_files, batch_size):
            futures.append(('Python',
                            executor.submit(check_python_files,
                                            batch,
                                            python_runtime,
                                            fix_formatting=fix_formatting)))

        for batch in chunk_list(web_files, batch_size):
            futures.append(('Web (JS, HTML, CSS)',
                            executor.submit(check_web_files,
                                            batch,
                                            fix_formatting=fix_formatting)))

        for lang, future in futures:
            errors = future.result()
            if errors:
                results[lang].extend(errors)

    return results


def parse_options():
    result = argparse.ArgumentParser()
    result.add_argument('--format-dir',
                        help='Format all files in the given directory.',
                        default=None)
    result.add_argument(
        '--format-precise-diff',
        help=
        'Only format changed lines (Java only, other formats are still formatted per file).',
        action='store_true',
        default=False)
    result.add_argument(
        '--upstream',
        help=
        'Upstream branch/commit to diff against for --format-precise-diff (default: git cl upstream).',
        default=None)
    result.add_argument('--only-java',
                        help='Only run google-java-format.',
                        action='store_true',
                        default=False)
    result.add_argument('--only-kotlin',
                        help='Only run google-kotlin-format.',
                        action='store_true',
                        default=False)
    result.add_argument('--only-python',
                        help='Only run YAPF.',
                        action='store_true',
                        default=False)
    result.add_argument('--only-web',
                        help='Only run Prettier.',
                        action='store_true',
                        default=False)
    return result.parse_known_args()


def format_files(files, upstream_for_diff_formatting=None):
    results = check(files,
                    fix_formatting=True,
                    upstream_for_diff_formatting=upstream_for_diff_formatting)
    for errors in results.values():
        for err in errors:
            print(err, file=sys.stderr)


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
    upstream_for_diff_formatting = upstream if options.format_precise_diff else None
    format_files(get_changed_files(upstream, predicate),
                 upstream_for_diff_formatting=upstream_for_diff_formatting)


def format_dir(directory, options):
    predicate = get_predicate(options)
    upstream_for_diff_formatting = options.upstream if options.format_precise_diff else None
    format_files(get_dir_files(directory, predicate),
                 upstream_for_diff_formatting=upstream_for_diff_formatting)


def main():
    (options, args) = parse_options()
    only_options_count = sum([
        options.only_java, options.only_kotlin, options.only_python,
        options.only_web
    ])
    if only_options_count > 1:
        print("Error: Cannot specify multiple --only options.", file=sys.stderr)
        return 1
    if options.format_dir and options.format_precise_diff:
        print("Error: Cannot both --format-dir and --format-precise-diff",
              file=sys.stderr)
        return 1
    if options.format_dir:
        format_dir(options.format_dir, options)
    else:
        upstream = options.upstream or get_git_upstream()
        format_all_changed_files(upstream, options)
    return 0


if __name__ == '__main__':
    sys.exit(main())
