#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
"""Generates commits.json / version.json and deploys the CI radiator to GCS."""

import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import time

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
TOOLS_DIR = SCRIPT_DIR.parent
REPO_ROOT = TOOLS_DIR.parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import utils

LUCI_GENERATED_DIR = REPO_ROOT / 'infra' / 'config' / 'global' / 'generated'
RELEASE_BRANCH_RE = re.compile(r'^origin/([0-9]+\.[0-9]+(?:\.[0-9]+)?)$')
DEFAULT_BUCKET = 'r8-test-results'
DEFAULT_PREFIX = 'ci-dashboard'
STATIC_ASSETS = ('index.html', 'dashboard.css', 'dashboard.js')
GENERATED_ASSETS = ('commits.json', 'version.json')
CACHE_HEADER = 'Cache-Control: no-cache, max-age=0'


def git_output(args):
    return subprocess.check_output(
        ['git', '-C', str(REPO_ROOT)] + args,
        text=True,
    )


def parse_luci_configs():
    milo_text = (LUCI_GENERATED_DIR /
                 'luci-milo.cfg').read_text(encoding='utf-8')
    sched_text = (LUCI_GENERATED_DIR /
                  'luci-scheduler.cfg').read_text(encoding='utf-8')
    cq_text = (LUCI_GENERATED_DIR /
               'commit-queue.cfg').read_text(encoding='utf-8')

    trig_match = re.search(
        r'trigger \{\s*id: "main-gitiles-trigger"(.*?)\n\}',
        sched_text,
        re.S,
    )
    main_triggered = (set(
        re.findall(r'triggers: "([^"]+)"', trig_match.group(1)))
                      if trig_match else set())

    main_console = []
    release_console = []
    for project, bucket, builder, category, short_name in re.findall(
            r'builders \{\s*name: "buildbucket/luci\.([^.]+)\.([^/]+)/([^"]+)"\s*'
            r'category: "([^"]+)"\s*short_name: "([^"]+)"\s*\}',
            milo_text,
    ):
        if category == 'Release' or category.startswith('Release|'):
            release_console.append({
                'project': project,
                'bucket': bucket,
                'builder': builder,
                'category': category.removeprefix('Release|'),
                'short_name': short_name,
            })
        else:
            main_console.append({
                'project': project,
                'bucket': bucket,
                'builder': builder,
                'category': category,
                'short_name': short_name,
                'auto_triggered': builder in main_triggered,
            })

    cq_builders = []
    for project, bucket, builder, modes_block in re.findall(
            r'builders \{\s*name: "([^/]+)/([^/]+)/([^"]+)"'
            r'((?:\s*mode_allowlist: "[^"]+")*)\s*\}',
            cq_text,
    ):
        cq_builders.append({
            'project': project,
            'bucket': bucket,
            'builder': builder,
            'modes': re.findall(r'mode_allowlist: "([^"]+)"', modes_block),
        })

    return {
        'main_console': main_console,
        'release_console': release_console,
        'cq_builders': cq_builders,
    }


def read_branch_commits(ref, limit):
    out = git_output([
        'log',
        '--first-parent',
        f'-n{limit}',
        '--format=%H%x09%ae%x09%ct%x09%s',
        ref,
    ])
    commits = []
    for line in out.splitlines():
        parts = line.split('\t', 3)
        if len(parts) != 4:
            continue
        sha, email, ts_str, subject = parts
        commits.append({
            'sha': sha,
            'author': email.split('@', 1)[0],
            'time': int(ts_str),
            'subject': subject,
        })
    return commits


def discover_active_release_branches(days=90):
    cutoff = int(time.time()) - days * 86400
    out = git_output([
        'for-each-ref',
        '--format=%(refname:short)%09%(committerdate:unix)',
        'refs/remotes/origin',
    ])
    branches = []
    for line in out.splitlines():
        parts = line.split('\t')
        if len(parts) != 2:
            continue
        ref, ts_str = parts
        m = RELEASE_BRANCH_RE.match(ref)
        if m and int(ts_str) >= cutoff:
            branches.append(m.group(1))
    branches.sort(key=lambda b: [int(p) for p in b.split('.')], reverse=True)
    return branches


def read_release_cut(branch):
    # Release branches merge main into dev-release; the latest such merge's
    # second parent is the main commit the branch was cut from.
    parents = git_output([
        'log', '-1', '--first-parent', '--merges', '--format=%P',
        f'origin/{branch}', '^origin/main'
    ]).split()
    return parents[1] if len(parents) > 1 else None


def generate_metadata(out_dir):
    out_path = pathlib.Path(out_dir)
    now_iso = (datetime.datetime.now(datetime.timezone.utc).replace(
        microsecond=0).isoformat().replace('+00:00', 'Z'))

    branches_data = {'main': read_branch_commits('origin/main', 300)}
    release_cuts = {}
    for br in discover_active_release_branches():
        branches_data[br] = read_branch_commits(f'origin/{br}', 80)
        cut = read_release_cut(br)
        if cut:
            release_cuts[br] = cut

    h = hashlib.sha256()
    for asset in STATIC_ASSETS:
        h.update((SCRIPT_DIR / asset).read_bytes())

    commits_payload = {
        'generated_at': now_iso,
        'luci_config': parse_luci_configs(),
        'branches': branches_data,
        'release_cuts': release_cuts,
    }
    version_payload = {
        'assets_hash': h.hexdigest()[:12],
        'deployed_at': now_iso,
    }

    (out_path / 'commits.json').write_text(json.dumps(commits_payload,
                                                      separators=(',', ':')),
                                           encoding='utf-8')
    (out_path / 'version.json').write_text(json.dumps(version_payload,
                                                      indent=2),
                                           encoding='utf-8')


def deploy_dashboard(bucket=DEFAULT_BUCKET, prefix=DEFAULT_PREFIX):
    gcs_root = f'gs://{bucket}/{prefix.strip("/")}'
    with tempfile.TemporaryDirectory(prefix='r8_ci_dashboard_') as tmp_dir:
        generate_metadata(tmp_dir)
        for filename in STATIC_ASSETS:
            src = str(SCRIPT_DIR / filename)
            utils.upload_file_to_cloud_storage(src,
                                               f'{gcs_root}/{filename}',
                                               header=CACHE_HEADER)
        for filename in GENERATED_ASSETS:
            src = os.path.join(tmp_dir, filename)
            utils.upload_file_to_cloud_storage(src,
                                               f'{gcs_root}/{filename}',
                                               header=CACHE_HEADER)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bucket', default=DEFAULT_BUCKET)
    parser.add_argument('--prefix', default=DEFAULT_PREFIX)
    args = parser.parse_args()
    deploy_dashboard(bucket=args.bucket, prefix=args.prefix)


if __name__ == '__main__':
    main()
