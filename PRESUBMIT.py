# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path
import datetime
from subprocess import check_output, check_call, CalledProcessError, Popen, PIPE, STDOUT, DEVNULL
from concurrent.futures import ThreadPoolExecutor
import inspect
import os
import sys
# Add both current path to allow us to package import utils and the tools
# dir to allow transitive (for utils) dependencies to be loaded.
sys.path.append(path.dirname(inspect.getfile(lambda: None)))
sys.path.append(
    os.path.join(path.dirname(inspect.getfile(lambda: None)), 'tools'))
from tools.utils import ensure_google_download
from tools.jdk import GetJavaExecutable, GetDefaultJdkHome

KOTLIN_FMT_DIR = path.join('third_party', 'google', 'google-kotlin-format',
                           '0.54')
KOTLIN_FMT_JAR = path.join(KOTLIN_FMT_DIR,
                           'ktfmt-0.54-jar-with-dependencies.jar')

KOTLIN_FMT_IGNORE = {
    'src/test/java/com/android/tools/r8/kotlin/metadata/inline_class_fun_descriptor_classes_app/main.kt'
}
KOTLIN_FMT_BATCH_SIZE = 100
JAVA_FMT_BATCH_SIZE = 100
PYTHON_FMT_BATCH_SIZE = 100
WEB_FMT_BATCH_SIZE = 100

FMT_CMD_JDK17 = path.join('tools', 'google-java-format-diff.py')
FMT_DIR = path.join('third_party', 'google', 'google-java-format', '1.24.0')
FMT_CMD = path.join(FMT_DIR, 'google-java-format-1.24.0', 'scripts',
                    'google-java-format-diff.py')

NODE_DIR = path.join('third_party', 'node', '24.16.0', 'linux')
NODE_EXEC = path.join(NODE_DIR, 'bin', 'node')

PRETTIER_DIR = path.join('third_party', 'prettier', '3.8.3')
PRETTIER_EXEC = path.join(PRETTIER_DIR, 'node_modules', 'prettier', 'bin',
                          'prettier.cjs')

PYTHON_FMT_DIR = path.join('third_party', 'google', 'yapf', '20231013')
PYTHON_FMT_EXEC = path.join(PYTHON_FMT_DIR, 'yapf')

YAPF_PYTHON_PATH = [PYTHON_FMT_DIR, os.path.join(PYTHON_FMT_DIR, 'third_party')]


def CheckDoNotMerge(input_api, output_api):
    for l in input_api.change.FullDescriptionText().splitlines():
        if l.lower().startswith('do not merge'):
            msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
            return [output_api.PresubmitPromptWarning(msg, [])]
    return []


def is_java_extension(file_path):
    return file_path.endswith('.java')


def is_kotlin_extension(file_path):
    return file_path.endswith('.kt') or file_path.endswith('.kts')


def is_python_extension(file_path):
    return file_path.endswith('.py')


def is_web_extension(file_path):
    return file_path.endswith(('.js', '.html', '.css'))


def CheckFormatting(input_api, output_api, branch):
    ensure_google_download(KOTLIN_FMT_DIR)
    ensure_google_download(FMT_DIR)
    ensure_google_download(NODE_DIR)
    ensure_google_download(PRETTIER_DIR)
    ensure_google_download(PYTHON_FMT_DIR)

    java_files = []
    kotlin_files = []
    python_files = []
    web_files = []

    for f in input_api.AffectedFiles():
        file_path = f.LocalPath()
        if is_kotlin_extension(file_path):
            if file_path in KOTLIN_FMT_IGNORE:
                continue
            kotlin_files.append(file_path)
        elif is_java_extension(file_path):
            java_files.append(file_path)
        elif is_python_extension(file_path):
            python_files.append(file_path)
        elif is_web_extension(file_path):
            web_files.append(file_path)

    results = []
    seen_errors = {
        'java': False,
        'kotlin': False,
        'python': False,
        'web': False,
    }
    python_runtime = PythonRuntime()

    futures = []
    with ThreadPoolExecutor() as executor:
        # Schedule Java batches
        for i in range(0, len(java_files), JAVA_FMT_BATCH_SIZE):
            batch = java_files[i:i + JAVA_FMT_BATCH_SIZE]
            futures.append(('java',
                            executor.submit(CheckJavaBatch, batch, branch,
                                            output_api)))

        # Schedule Kotlin batches per style
        kotlin_paths_to_format = {
            '--kotlinlang-style': [
                p for p in kotlin_files if p.startswith('src/keepanno/')
            ],
            '--google-style': [
                p for p in kotlin_files if not p.startswith('src/keepanno/')
            ]
        }
        for format_style in ['--kotlinlang-style', '--google-style']:
            style_paths = kotlin_paths_to_format[format_style]
            for i in range(0, len(style_paths), KOTLIN_FMT_BATCH_SIZE):
                batch = style_paths[i:i + KOTLIN_FMT_BATCH_SIZE]
                futures.append(('kotlin',
                                executor.submit(CheckKotlinBatch, batch,
                                                format_style, output_api)))

        # Schedule Python batches
        if python_files:
            init_error = python_runtime.initialize_runtime()
            if init_error:
                seen_errors['python'] = True
                results.append(output_api.PresubmitError(init_error))
            else:
                for i in range(0, len(python_files), PYTHON_FMT_BATCH_SIZE):
                    batch = python_files[i:i + PYTHON_FMT_BATCH_SIZE]
                    futures.append(('python',
                                    executor.submit(python_runtime.check_batch,
                                                    batch, output_api)))

        # Schedule Web batches
        for i in range(0, len(web_files), WEB_FMT_BATCH_SIZE):
            batch = web_files[i:i + WEB_FMT_BATCH_SIZE]
            futures.append(
                ('web', executor.submit(CheckWebBatch, batch, output_api)))

        for lang, future in futures:
            errors = future.result()
            if errors:
                seen_errors[lang] = True
                results.extend(errors)

    # Provide the reformatting commands if needed.
    if seen_errors['kotlin']:
        results.append(output_api.PresubmitError(
            KotlinFormatPresubmitMessage()))
    if seen_errors['java']:
        results.append(output_api.PresubmitError(JavaFormatPresubmitMessage()))
    if seen_errors['python']:
        results.append(output_api.PresubmitError(
            PythonFormatPresubmitMessage()))
    if seen_errors['web']:
        results.append(output_api.PresubmitError(WebFormatPresubmitMessage()))

    # Comment this out to easily fail presubmit changes
    # results.append(output_api.PresubmitError("TESTING"))
    return results


def CheckKotlinBatch(batch, format_style, output_api):
    cmd = [
        GetJavaExecutable(GetDefaultJdkHome()), '-jar', KOTLIN_FMT_JAR,
        format_style, '-n'
    ] + batch
    result = check_output(cmd)
    errors = []
    if len(result) > 0:
        for file_path in result.splitlines():
            errors.append(
                output_api.PresubmitError(
                    "File {file_path} needs formatting".format(
                        file_path=file_path.decode('utf-8'))))
    return errors


def KotlinFormatPresubmitMessage():
    return """Please fix the Kotlin formatting by running:

  git diff $(git cl upstream) --name-only "*.kt" "*.kts" | grep -v "^src/keepanno/" | xargs {java} -jar {fmt_jar} --google-style
  git diff $(git cl upstream) --name-only "*.kt" "*.kts" | grep "^src/keepanno/" | xargs {java} -jar {fmt_jar} --kotlinlang-style

or fix formatting, commit and upload:

  git diff $(git cl upstream) --name-only "*.kt" "*.kts" | grep -v "^src/keepanno/" | xargs {java} -jar {fmt_jar} --google-style && git commit -a --amend --no-edit && git cl upload
  git diff $(git cl upstream) --name-only "*.kt" "*.kts" | grep "^src/keepanno/" | xargs {java} -jar {fmt_jar} --kotlinlang-style && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
    """.format(java=GetJavaExecutable(GetDefaultJdkHome()),
               fmt_jar=KOTLIN_FMT_JAR)


def CheckJavaBatch(batch, branch, output_api):
    diff = check_output(['git', 'diff', '--no-prefix', '-U0', branch, '--'] +
                        batch)
    if not diff:
        return []
    proc = Popen(FMT_CMD, stdin=PIPE, stdout=PIPE, stderr=STDOUT)
    (stdout, stderr) = proc.communicate(input=diff)
    errors = []
    if len(stdout) > 0:
        errors.append(output_api.PresubmitError(stdout.decode('utf-8')))
    return errors


def JavaFormatPresubmitMessage():
    return """Please fix the Java formatting by running:

  git diff -U0 $(git cl upstream) | %s -p1 -i

or fix formatting, commit and upload:

  git diff -U0 $(git cl upstream) | %s -p1 -i && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks

If formatting fails with 'No enum constant javax.lang.model.element.Modifier.SEALED' try

  git diff -U0 $(git cl upstream) | %s %s %s -p1 -i && git commit -a --amend --no-edit && git cl upload
  """ % (
        FMT_CMD, FMT_CMD, FMT_CMD_JDK17, '--google-java-format-jar',
        'third_party/google/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar'
    )


def get_env_with_python_path():
    new_env = os.environ.copy()
    new_env['PYTHONPATH'] = ':'.join(YAPF_PYTHON_PATH)
    return new_env


class PythonRuntime:

    def __init__(self):
        self.interpreter = None
        self.has_failed = False

    def initialize_runtime(self):
        # Ensure a python interpreter with platformdirs.
        # This search allows manual setup of .venv.
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
        return (
            "Error: Could not find a Python interpreter with `platformdirs` installed.\n"
            "Please ensure it is installed in your environment:\n"
            "  $ python3 -m venv .venv\n"
            "  $ source .venv/bin/activate\n"
            "  $ pip3 install platformdirs")

    def check_batch(self, batch, output_api):
        format_cmd = [
            self.interpreter, PYTHON_FMT_EXEC, '--diff', '--style', 'google'
        ] + batch
        python_env = get_env_with_python_path()
        try:
            check_output(format_cmd, env=python_env)
            return []
        except CalledProcessError as e:
            # --diff returns non-zero if there is a diff
            output_str = (e.output.decode('utf-8') if isinstance(
                e.output, bytes) else str(e.output))
            return [output_api.PresubmitError(output_str)]


def PythonFormatPresubmitMessage():
    return """Please fix the Python formatting by running:

  tools/fmt-diff.py --no-java --no-kotlin --python

or fix formatting, commit and upload:

  tools/fmt-diff.py --no-java --no-kotlin --python && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
    """


def CheckWebBatch(batch, output_api):
    format_cmd = [NODE_EXEC, PRETTIER_EXEC, '--check'] + batch
    try:
        check_output(format_cmd, stderr=STDOUT)
        return []
    except CalledProcessError as e:
        output_str = (e.output.decode('utf-8')
                      if isinstance(e.output, bytes) else str(e.output))
        return [
            output_api.PresubmitError(f"Web formatting error:\n{output_str}")
        ]


def WebFormatPresubmitMessage():
    return """Please fix the Web formatting (JS, HTML, CSS) by running:

  tools/fmt-diff.py --web

or fix formatting, commit and upload:

  tools/fmt-diff.py --web && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
    """


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
    return is_java_extension(localPath) and '/test/' in localPath


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
        if not is_java_extension(file.LocalPath()):
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


def CheckForCopyright(input_api, output_api, branch):
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
        if not CopyrightInContents(f, contents):
            results.append(
                output_api.PresubmitError('Could not find correctly formatted '
                                          'copyright in file: %s' % f))
    return results


def CopyrightInContents(f, contents):
    expected = '//'
    if is_python_extension(f.LocalPath()) or f.LocalPath().endswith('.sh'):
        expected = '#'
    expected = expected + ' Copyright (c) ' + str(datetime.datetime.now().year)
    for content_line in contents:
        if expected in content_line:
            return True
    return False


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
