#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
from contextlib import ExitStack
import os
import shutil
import subprocess
import sys

import utils


def parse_options(argv=None):
    parser = argparse.ArgumentParser(
        description='Run depot_tools PRESUBMIT.py checks.')
    parser.add_argument(
        '--upstream',
        help=
        'Upstream ref or commit to diff against (auto-detected if omitted).',
    )
    options, _ = parser.parse_known_args(argv)
    return options


def has_platformdirs(python_cmd):
    try:
        return subprocess.call(
            [python_cmd, '-c', 'import platformdirs'],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ) == 0
    except FileNotFoundError:
        return False


def ensure_platformdirs_env(stack, env):
    candidates = [
        sys.executable,
        'python3',
        os.path.join(utils.REPO_ROOT, '.venv', 'bin', 'python3'),
        os.path.join(utils.REPO_ROOT, '.venv', 'bin', 'python'),
    ]
    if any(has_platformdirs(c) for c in candidates):
        return

    temp_dir = stack.enter_context(utils.TempDir(prefix='r8-presubmit-venv-'))
    venv_dir = os.path.join(temp_dir, 'venv')
    subprocess.check_call([sys.executable, '-m', 'venv', venv_dir])
    bin_dir = os.path.join(venv_dir, 'Scripts' if utils.IsWindows() else 'bin')
    pip_exe = os.path.join(bin_dir, 'pip.exe' if utils.IsWindows() else 'pip')

    installed = False
    ar_url = os.environ.get('VPYTHON_AR_URL')
    if ar_url:
        installed = subprocess.call([
            pip_exe, 'install', '--quiet', '--index-url', ar_url, 'platformdirs'
        ]) == 0
    if not installed:
        subprocess.check_call([pip_exe, 'install', '--quiet', 'platformdirs'])

    env['PATH'] = bin_dir + os.pathsep + env.get('PATH', '')


def resolve_upstream(options):
    if options.upstream:
        return options.upstream

    cl_upstream = subprocess.run(
        ['git', 'cl', 'upstream'],
        cwd=utils.REPO_ROOT,
        capture_output=True,
        text=True,
    )
    if cl_upstream.returncode == 0 and cl_upstream.stdout.strip():
        return cl_upstream.stdout.strip()

    revs = subprocess.check_output(
        ['git', 'rev-list', '--parents', '-n', '1', 'HEAD'],
        cwd=utils.REPO_ROOT,
        text=True,
    ).strip().split()
    if len(revs) >= 2:
        return revs[1]
    return 'origin/main'


def get_head_description():
    return subprocess.check_output(
        ['git', 'log', '-1', '--format=%B', 'HEAD'],
        cwd=utils.REPO_ROOT,
        text=True,
    )


def main(argv=None):
    options = parse_options(argv)
    presubmit_support = shutil.which('presubmit_support.py')
    if not presubmit_support:
        print(
            'Error: presubmit_support.py not found in PATH '
            '(ensure depot_tools is in PATH).',
            file=sys.stderr,
        )
        return 1

    upstream = resolve_upstream(options)
    description = get_head_description()

    env = os.environ.copy()
    vpython3 = shutil.which('vpython3')
    runner = [vpython3] if vpython3 else [sys.executable]
    cmd = runner + [
        presubmit_support,
        '--root',
        utils.REPO_ROOT,
        '--commit',
        '--verbose',
        '--upstream',
        upstream,
        '--description',
        description,
    ]

    with ExitStack() as stack:
        if utils.is_bot():
            ensure_platformdirs_env(stack, env)
        utils.PrintCmd(cmd)
        return subprocess.call(cmd, cwd=utils.REPO_ROOT, env=env)


if __name__ == '__main__':
    sys.exit(main())
