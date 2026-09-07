#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
import subprocess


def GitClone(url, checkout_dir):
    cmd = ['git', 'clone', url, checkout_dir]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def GitCheckout(revision, checkout_dir):
    with utils.ChangedWorkingDirectory(checkout_dir):
        cmd = ['git', 'checkout', revision]
        utils.PrintCmd(cmd)
        return subprocess.check_call(cmd)


def GetHeadRevision(checkout_dir, use_main=False):
    revision_from = 'origin/main' if use_main else 'HEAD'
    cmd = ['git', 'rev-parse', revision_from]
    utils.PrintCmd(cmd)
    with utils.ChangedWorkingDirectory(checkout_dir):
        return subprocess.check_output(cmd).strip().decode('utf-8')


def _reviewer_arg(reviewer):
    if reviewer.find('@') == -1:
        reviewer = reviewer + "@google.com"
    return '--reviewer=' + reviewer


def GitClAppendReviewers(cmd, reviewer, send_mail):
    if reviewer:
        cmd.extend(map(_reviewer_arg, reviewer))
        if send_mail:
            cmd.append('--send-mail')


def GitCommit(message):
    cmd = ['git', 'commit', '-a', '-m', message]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def VersionCommitMessage(version, description=None, bugs=None):
    lines = ['Version %s' % version]
    if description:
        lines.append('')
        lines.append(description)
    lines.append('')
    if bugs:
        for bug in sorted(bugs):
            bug_str = str(bug).strip()
            if not bug_str.startswith('b/'):
                bug_str = 'b/%s' % bug_str
            lines.append('Bug: %s' % bug_str)
    lines.append('Cq-Exclude-Trybots: luci.r8.try:presubmit')
    return '\n'.join(lines)


version_commit_message = VersionCommitMessage
