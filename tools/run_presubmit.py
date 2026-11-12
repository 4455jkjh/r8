#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
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


def resolve_upstream(options):
    if options.upstream:
        return options.upstream

    upstream = subprocess.run(
        ['git', 'rev-parse', '--symbolic-full-name', '@{upstream}'],
        cwd=utils.REPO_ROOT,
        capture_output=True,
        text=True,
    )
    if upstream.returncode == 0 and upstream.stdout.strip():
        return upstream.stdout.strip()

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

    utils.PrintCmd(cmd)
    return subprocess.call(cmd, cwd=utils.REPO_ROOT)


if __name__ == '__main__':
    sys.exit(main())
