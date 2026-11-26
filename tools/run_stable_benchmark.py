# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

try:
    import fcntl
except ImportError:
    fcntl = None
import os
import shutil
import subprocess
import sys
import tempfile

import git_utils
import utils

BASELINE_JARS = (
    'r8lib.jar',
    'r8libtestdeps-cf.jar',
    'r8libtestbase-cf.jar',
    'test_deps_all.jar',
)
BASELINE_GRADLE_TASKS = (
    ':test:assembleR8LibWithRelocatedDeps',
    ':test:rewriteTestsForR8LibWithRelocatedDeps',
    ':test:rewriteTestBaseForR8LibWithRelocatedDeps',
    ':test:packageTestDeps',
)
STABLE_RUNNER_MARKER = ('src/test/java/com/android/tools/r8/benchmarks/stable/'
                        'StableIterationRunner.java')
MAX_CACHED_COMMITS = 3


def _cache_root():
    return os.path.join(utils.BUILD, 'stable-cache')


def resolve_commit(ref):
    try:
        return git_utils.resolve_revision(f'{ref}^{{commit}}')
    except subprocess.CalledProcessError:
        # Fetch on miss so unfetched commit hashes (e.g. try commits) work.
        # Use utils.REPO_SOURCE rather than 'origin' (Kokoro uses rpc://).
        subprocess.check_call(
            ['git', 'fetch', '--no-tags', utils.REPO_SOURCE, ref],
            cwd=utils.REPO_ROOT)
        return git_utils.resolve_revision(f'{ref}^{{commit}}')


def _is_valid_worktree(wt):
    if not os.path.exists(os.path.join(wt, '.git')):
        return False
    try:
        toplevel = subprocess.check_output(
            ['git', '-C', wt, 'rev-parse', '--show-toplevel'],
            stderr=subprocess.DEVNULL,
            text=True).strip()
        return os.path.realpath(toplevel) == os.path.realpath(wt)
    except subprocess.CalledProcessError:
        return False


def _prepare_worktree(sha, cache_root):
    wt = os.path.join(cache_root, 'worktree')
    if _is_valid_worktree(wt):
        subprocess.check_call(
            ['git', '-C', wt, 'checkout', '--detach', '--force', sha])
    else:
        if os.path.exists(wt):
            shutil.rmtree(wt)
        subprocess.call(['git', 'worktree', 'prune'], cwd=utils.REPO_ROOT)
        subprocess.check_call(
            ['git', 'worktree', 'add', '--detach', '--force', wt, sha],
            cwd=utils.REPO_ROOT)
    return wt


def _evict_old_caches(cache_root, keep_sha):
    entries = []
    for entry in os.scandir(cache_root):
        if entry.is_dir() and len(entry.name) == 40 and entry.name != keep_sha:
            entries.append((entry.stat().st_mtime, entry.path))
    entries.sort(reverse=True)
    for _, path in entries[MAX_CACHED_COMMITS - 1:]:
        shutil.rmtree(path, ignore_errors=True)


def ensure_commit_classpath(sha, quiet=False):
    root = _cache_root()
    os.makedirs(root, exist_ok=True)
    cache_dir = os.path.join(root, sha)
    cached_jars = [os.path.join(cache_dir, jar) for jar in BASELINE_JARS]
    with open(os.path.join(root, '.lock'), 'w') as lock_file:
        if fcntl is not None:
            fcntl.flock(lock_file, fcntl.LOCK_EX)
        if all(os.path.isfile(p) for p in cached_jars):
            os.utime(cache_dir)
            return os.pathsep.join(cached_jars)

        wt = _prepare_worktree(sha, root)
        # -Pno_internal skips internal dump downloads while still compiling all
        # internal benchmark classes into the rewritten test JARs.
        cmd = [
            sys.executable,
            os.path.join(wt, 'tools', 'gradle.py'),
            *BASELINE_GRADLE_TASKS,
            '-Pno_internal',
            '--no-daemon',
        ]
        if quiet:
            cmd.append('-q')
        subprocess.check_call(cmd, cwd=wt)

        staging_dir = tempfile.mkdtemp(prefix=f'{sha}-', dir=root)
        try:
            for jar in BASELINE_JARS:
                shutil.copyfile(os.path.join(wt, 'build', 'libs', jar),
                                os.path.join(staging_dir, jar))
            if os.path.exists(cache_dir):
                shutil.rmtree(cache_dir)
            os.replace(staging_dir, cache_dir)
        finally:
            if os.path.exists(staging_dir):
                shutil.rmtree(staging_dir)
        _evict_old_caches(root, sha)
    return os.pathsep.join(cached_jars)


def resolve_stable_gradle_args(options):
    head_ref = options.version or 'HEAD'
    head_sha = resolve_commit(head_ref)
    parent_sha = git_utils.resolve_revision(f'{head_sha}^1^{{commit}}')
    if subprocess.call(
        ['git', 'cat-file', '-e', f'{parent_sha}:{STABLE_RUNNER_MARKER}'],
            cwd=utils.REPO_ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL) != 0:
        raise RuntimeError(
            f'Baseline commit {parent_sha} predates {STABLE_RUNNER_MARKER}')
    baseline_cp = ensure_commit_classpath(parent_sha, quiet=options.quiet)
    args = [f'-Pbaseline_classpath={baseline_cp}']
    if options.version:
        head_cp = ensure_commit_classpath(head_sha, quiet=options.quiet)
        args.append(f'-Phead_classpath={head_cp}')
    return args
