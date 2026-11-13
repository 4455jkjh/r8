#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
import subprocess


def git_clone(url, checkout_dir):
    cmd = ['git', 'clone', url, checkout_dir]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def git_checkout(revision, checkout_dir):
    with utils.ChangedWorkingDirectory(checkout_dir):
        cmd = ['git', 'checkout', revision]
        utils.PrintCmd(cmd)
        return subprocess.check_call(cmd)


def get_head_revision(checkout_dir, use_main=False):
    revision_from = 'origin/main' if use_main else 'HEAD'
    cmd = ['git', 'rev-parse', revision_from]
    utils.PrintCmd(cmd)
    with utils.ChangedWorkingDirectory(checkout_dir):
        return subprocess.check_output(cmd).strip().decode('utf-8')


def _reviewer_arg(reviewer):
    if reviewer.find('@') == -1:
        reviewer = reviewer + "@google.com"
    return '--reviewer=' + reviewer


def git_cl_append_reviewers(cmd, reviewer, send_mail):
    if reviewer:
        cmd.extend(map(_reviewer_arg, reviewer))
        if send_mail:
            cmd.append('--send-mail')


def git_commit(message):
    cmd = ['git', 'commit', '-a', '-m', message]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def version_commit_message(version, description=None, bugs=None):
    lines = ['Version %s' % version]
    if description:
        lines.append('')
        lines.append(description)
    if bugs:
        lines.append('')
        for bug in sorted(bugs):
            bug_str = str(bug).strip()
            if not bug_str.startswith('b/'):
                bug_str = 'b/%s' % bug_str
            lines.append('Bug: %s' % bug_str)
    return '\n'.join(lines)


def get_head_ref(checkout_dir=utils.REPO_ROOT):
    try:
        branch = subprocess.check_output(
            ['git', 'symbolic-ref', '-q', '--short', 'HEAD'],
            cwd=checkout_dir,
            stderr=subprocess.PIPE,
            text=True).strip()
        if branch:
            return branch
    except subprocess.CalledProcessError:
        pass
    return get_head_revision(checkout_dir)


def resolve_revision(revision, checkout_dir=utils.REPO_ROOT):
    return subprocess.check_output(['git', 'rev-parse', '--verify', revision],
                                   cwd=checkout_dir,
                                   stderr=subprocess.PIPE,
                                   text=True).strip()


def is_working_tree_clean(checkout_dir=utils.REPO_ROOT):
    out = subprocess.check_output(
        ['git', 'status', '--porcelain', '--untracked-files=no'],
        cwd=checkout_dir,
        stderr=subprocess.PIPE,
        text=True).strip()
    return not out


def is_unmerged_commit(revision,
                       base_ref='origin/main',
                       checkout_dir=utils.REPO_ROOT):
    return subprocess.call(
        ['git', 'merge-base', '--is-ancestor', revision, base_ref],
        cwd=checkout_dir,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL) == 1


def _get_change_id(revision, checkout_dir=utils.REPO_ROOT):
    out = subprocess.check_output([
        'git', 'log', '-1', '--format=%(trailers:key=Change-Id,valueonly=true)',
        revision
    ],
                                  cwd=checkout_dir,
                                  stderr=subprocess.PIPE,
                                  text=True).strip()
    lines = [line.strip() for line in out.splitlines() if line.strip()]
    return lines[-1] if lines else None


def get_previous_cl_commit(checkout_dir=utils.REPO_ROOT):
    try:
        rev_list = subprocess.check_output(
            ['git', 'rev-list', '--parents', '-n', '1', 'HEAD'],
            cwd=checkout_dir,
            stderr=subprocess.PIPE,
            text=True).strip().split()
        if len(rev_list) != 2:
            return None
        head_sha, parent_sha = rev_list
        if not is_unmerged_commit(parent_sha, checkout_dir=checkout_dir):
            return None
        try:
            cherry_out = subprocess.check_output(
                ['git', 'cherry', 'origin/main', parent_sha, f'{parent_sha}~1'],
                cwd=checkout_dir,
                stderr=subprocess.PIPE,
                text=True).strip()
            if cherry_out.startswith('-'):
                return None
        except subprocess.CalledProcessError:
            pass
        head_change_id = _get_change_id(head_sha, checkout_dir)
        parent_change_id = _get_change_id(parent_sha, checkout_dir)
        if head_change_id and parent_change_id and (head_change_id
                                                    == parent_change_id):
            return None
        return parent_sha
    except subprocess.CalledProcessError:
        return None


def get_candidate_main_commits(explicit_base_hash=None,
                               exclude_sha=None,
                               max_count=25):
    if explicit_base_hash:
        start_ref = explicit_base_hash
    else:
        try:
            merge_base_cmd = ['git', 'merge-base', 'HEAD', 'origin/main']
            start_ref = subprocess.check_output(merge_base_cmd,
                                                cwd=utils.REPO_ROOT,
                                                stderr=subprocess.PIPE,
                                                text=True).strip()
        except subprocess.CalledProcessError:
            start_ref = utils.get_HEAD_sha1()
    try:
        log_cmd = [
            'git',
            'log',
            '--first-parent',
            start_ref,
            f'--max-count={max_count}',
            '--pretty=format:%H',
        ]
        commits = subprocess.check_output(log_cmd,
                                          cwd=utils.REPO_ROOT,
                                          text=True).strip().splitlines()
        res = [
            c.strip()
            for c in commits
            if c.strip() and (explicit_base_hash or c.strip() != exclude_sha)
        ]
        return res or [start_ref]
    except subprocess.CalledProcessError:
        return [start_ref]


def ensure_origin_main(checkout_dir=utils.REPO_ROOT,
                       repo_source=utils.REPO_SOURCE):
    if subprocess.call(
        ['git', 'show-ref', '--verify', '--quiet', 'refs/remotes/origin/main'],
            cwd=checkout_dir) != 0:
        # Fetch from repo_source rather than 'origin', as Kokoro's GoB checkout
        # uses an rpc:// origin URL without git-remote-rpc inside the build
        # container and omits origin/main when rebase = false.
        subprocess.run([
            'git', 'fetch', '--no-tags', repo_source,
            '+refs/heads/main:refs/remotes/origin/main'
        ],
                       cwd=checkout_dir,
                       check=True,
                       capture_output=True,
                       text=True)


def get_merge_base_with_main(revision,
                             checkout_dir=utils.REPO_ROOT,
                             repo_source=utils.REPO_SOURCE):
    ensure_origin_main(checkout_dir=checkout_dir, repo_source=repo_source)
    return subprocess.run(['git', 'merge-base', revision, 'origin/main'],
                          cwd=checkout_dir,
                          check=True,
                          capture_output=True,
                          text=True).stdout.strip()


# Returns the repo-relative paths modified by commit_hash (or across the CL
# chain since origin/main when is_try is True).
def get_changed_files(commit_hash,
                      is_try=False,
                      checkout_dir=utils.REPO_ROOT,
                      repo_source=utils.REPO_SOURCE):
    if is_try:
        base_hash = get_merge_base_with_main(f'{commit_hash}^1',
                                             checkout_dir=checkout_dir,
                                             repo_source=repo_source)
    else:
        base_hash = f'{commit_hash}^1'
    # Pass -z to avoid core.quotePath escaping and --no-renames so moving a file
    # still reports both the old and new paths.
    output = subprocess.run([
        'git', 'diff', '-z', '--no-renames', '--name-only', base_hash,
        commit_hash
    ],
                            cwd=checkout_dir,
                            check=True,
                            capture_output=True,
                            text=True).stdout
    return [path for path in output.split('\0') if path]
