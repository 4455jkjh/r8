#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import re
import subprocess
import utils


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


CQ_EXCLUDE_PRESUBMIT = 'Cq-Exclude-Trybots: luci.r8.try:presubmit'


def GitCommit(message):
    cmd = ['git', 'commit', '-a', '-m', message]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def GitAmendCommitMessage(message):
    cmd = ['git', 'commit', '--amend', '-m', message]
    utils.PrintCmd(cmd)
    return subprocess.check_call(cmd)


def AddPresubmitExcludeToCommitMessage(message):
    if CQ_EXCLUDE_PRESUBMIT in message:
        return message
    stripped = message.rstrip()
    paragraphs = stripped.split('\n\n')
    if len(paragraphs) > 1:
        last_paragraph_lines = paragraphs[-1].splitlines()
        if all(
                re.match(r'^\s*[\w-]+:\s', line)
                for line in last_paragraph_lines):
            return stripped + '\n' + CQ_EXCLUDE_PRESUBMIT + '\n'
    return stripped + '\n\n' + CQ_EXCLUDE_PRESUBMIT + '\n'


def VersionCommitMessage(version,
                         description=None,
                         bugs=None,
                         exclude_presubmit=False):
    lines = ['Version %s' % version]
    if description:
        lines.append('')
        lines.append(description)
    trailers = []
    if bugs:
        for bug in sorted(bugs):
            bug_str = str(bug).strip()
            if not bug_str.startswith('b/'):
                bug_str = 'b/%s' % bug_str
            trailers.append('Bug: %s' % bug_str)
    if exclude_presubmit:
        trailers.append(CQ_EXCLUDE_PRESUBMIT)
    if trailers:
        lines.append('')
        lines.extend(trailers)
    return '\n'.join(lines)


version_commit_message = VersionCommitMessage
