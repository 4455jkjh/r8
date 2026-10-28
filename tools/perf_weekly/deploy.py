#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
"""Deploys the weekly perf page and its main history to GCS.

The page loads the weekly result bundles that tools/perf.py writes directly
from the bucket, and uses commits.json to order the main commits.
"""

import argparse
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
TOOLS_DIR = SCRIPT_DIR.parent
REPO_ROOT = TOOLS_DIR.parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import r8_release
import utils

DEFAULT_BUCKET = 'r8-test-results'
DEFAULT_PREFIX = 'perf'
STATIC_SUFFIXES = ('.html', '.js', '.css')
# Enough main history for all backfilled results and release cuts.
MAIN_HISTORY_COMMITS = 5000
OLDEST_RELEASE_CUT = (8, 4)
RELEASE_BRANCH_RE = re.compile(r'^origin/(\d+)\.(\d+)$')
CACHE_HEADER = 'Cache-Control: no-cache, max-age=0'


def git_output(args):
    return subprocess.check_output(
        ['git', '-C', str(REPO_ROOT), *args], text=True)


def read_main_history():
    output = git_output([
        'log', '--first-parent', f'-n{MAIN_HISTORY_COMMITS}',
        '--format=%H%x09%an%x09%ct%x09%s', 'origin/main'
    ])
    commits = []
    for line in output.splitlines():
        parts = line.split('\t', 3)
        if len(parts) != 4:
            continue
        commit_hash, author, timestamp, title = parts
        commits.append({
            'author': author,
            'hash': commit_hash,
            'submitted': int(timestamp),
            'title': title,
        })
    return commits


def read_release_cuts():
    dev_branch_tuple = tuple(
        int(p) for p in r8_release.R8_DEV_BRANCH.split('.'))
    refs_output = git_output(
        ['for-each-ref', '--format=%(refname:short)', 'refs/remotes/origin'])
    versions = []
    for ref in refs_output.splitlines():
        m = RELEASE_BRANCH_RE.match(ref)
        if not m:
            continue
        version = (int(m.group(1)), int(m.group(2)))
        if OLDEST_RELEASE_CUT <= version < dev_branch_tuple:
            versions.append(version)
    versions.sort(reverse=True)
    cuts = []
    for major, minor in versions:
        branch = f'{major}.{minor}'
        cut_hash = git_output(['merge-base', f'origin/{branch}',
                               'origin/main']).strip()
        if not cut_hash:
            continue
        meta_lines = git_output(
            ['show', '-s', '--format=%an%x09%ct%x09%s', cut_hash]).splitlines()
        if not meta_lines:
            continue
        parts = meta_lines[0].split('\t', 2)
        if len(parts) != 3:
            continue
        author, timestamp, title = parts
        cuts.append({
            'branch': branch,
            'hash': cut_hash,
            'author': author,
            'submitted': int(timestamp),
            'title': title,
        })
    return cuts


def build_commits_data():
    return {
        'generated_at':
            datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'dev_branch':
            r8_release.R8_DEV_BRANCH,
        'release_cuts':
            read_release_cuts(),
        'main':
            read_main_history(),
    }


def deploy_perf_page(bucket=DEFAULT_BUCKET, prefix=DEFAULT_PREFIX):
    gcs_root = f'gs://{bucket}/{prefix.strip("/")}'
    with utils.TempDir(prefix='r8_perf_weekly_') as tmp_dir:
        commits_file = os.path.join(tmp_dir, 'commits.json')
        with open(commits_file, 'w', encoding='utf-8') as f:
            json.dump(build_commits_data(), f)
        files = [
            str(path)
            for path in sorted(SCRIPT_DIR.iterdir())
            if path.suffix in STATIC_SUFFIXES
        ] + [commits_file]
        for src in files:
            utils.upload_file_to_cloud_storage(
                src, f'{gcs_root}/{os.path.basename(src)}', header=CACHE_HEADER)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bucket', default=DEFAULT_BUCKET)
    parser.add_argument('--prefix', default=DEFAULT_PREFIX)
    args = parser.parse_args()
    deploy_perf_page(bucket=args.bucket, prefix=args.prefix)
    return 0


if __name__ == '__main__':
    sys.exit(main())
