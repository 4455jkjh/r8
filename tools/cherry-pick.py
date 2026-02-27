#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import git_utils
import utils

VERSION_FILE = 'src/main/java/com/android/tools/r8/Version.java'
VERSION_PREFIX = 'String LABEL = "'


def sed(old, new, file):
    subprocess.run(['sed', '-i', 's/%s/%s/' % (old, new), file])


def git_new_branch(name, upstream=None):
    cmd = ['git', 'new-branch', name]
    if upstream:
        cmd += ['--upstream', upstream]
    else:
        cmd += ['--upstream-current']
    subprocess.run(cmd)


def git_commit(message):
    subprocess.run(['git', 'commit', '-a', '-m', message])


def parse_options():
    parser = argparse.ArgumentParser(description='Release r8')
    parser.add_argument('--branch',
                        metavar=('<branch>'),
                        default=[],
                        action='append',
                        help='Branch(es) to cherry-pick to')
    parser.add_argument('--current-checkout',
                        '--current_checkout',
                        default=False,
                        action='store_true',
                        help='Perform cherry picks into the current checkout')
    parser.add_argument('--no-upload',
                        '--no_upload',
                        default=False,
                        action='store_true',
                        help='Do not upload to Gerrit')
    parser.add_argument('--remote',
                        default='origin',
                        help='The remote name (defaults to "origin")')
    parser.add_argument('--yes',
                        default=False,
                        action='store_true',
                        help='Answer "yes" to all questions')
    parser.add_argument('--reviewer',
                        metavar=('<reviewer(s)>'),
                        default=[],
                        action='append',
                        help='Rewiever(s) for the cherry-pick(s) '
                        '(adds @google.com if missing)')
    parser.add_argument('--send-mail',
                        '--send_mail',
                        default=False,
                        action='store_true',
                        help='Send Gerrit review request right away')
    exclusive = parser.add_mutually_exclusive_group(required=True)
    exclusive.add_argument('hashes',
                           default=[],
                           metavar='<hash>',
                           nargs='*',
                           help='Hashes to merge')
    exclusive.add_argument('--promote-dev',
                           '--promote_dev',
                           default=False,
                           action='store_true',
                           help='Promote a -dev branch to stable')
    return parser.parse_args()


def version_from_version_file():
    for line in open(VERSION_FILE, 'r'):
        index = line.find(VERSION_PREFIX)
        if index > 0:
            index += len(VERSION_PREFIX)
            subline = line[index:]
            return subline[:subline.index('"')]
    return "unknown"


def run(args, branch):
    if args.current_checkout:
        for i in range(len(args.hashes) + 1):
            local_branch_name = 'cherry-%s-%d' % (branch, i + 1)
            print('Deleting branch %s' % local_branch_name)
            subprocess.run(['git', '--delete', '--force', 'branch', local_branch_name]) 

    bugs = set()

    count = 1
    for hash in args.hashes:
        local_branch_name = 'cherry-%s-%d' % (branch, count)
        print('Cherry-picking %s in %s' % (hash, local_branch_name))
        if count == 1:
            git_new_branch(local_branch_name, '%s/%s' % (args.remote, branch))
        else:
            git_new_branch(local_branch_name)

        subprocess.run(['git', 'cherry-pick', hash])
        confirm_and_upload(local_branch_name, args, bugs)
        count = count + 1

    local_branch_name = 'cherry-%s-%d' % (branch, count)
    git_new_branch(local_branch_name)

    old_version = version_from_version_file()
    new_version = 'unknown'
    if old_version.find('.') > 0 and old_version.find('-') == -1:
        split_version = old_version.split('.')
        new_version = '.'.join(split_version[:-1] +
                               [str(int(split_version[-1]) + 1)])
        sed(old_version, new_version, VERSION_FILE)
    else:
        editor = os.environ.get('VISUAL')
        if not editor:
            editor = os.environ.get('EDITOR')
        if not editor:
            editor = 'vi'
        input("\nCannot automatically determine the new version.\n"
            + "Press [enter] to edit %s for version update with editor '%s'." %
              (VERSION_FILE, editor))
        subprocess.run([editor, VERSION_FILE])
        new_version = input('Please enter the new version for the commit message: ')

    message = ("Version %s\n\n" % new_version)
    for bug in sorted(bugs):
        message += 'Bug: b/%s\n' % bug

    git_commit(message)
    confirm_and_upload(branch, args, None)
    if not args.current_checkout and not args.yes:
        while True:
            try:
                answer = input(
                    "Type 'delete' to finish and delete checkout in %s: " %
                    os.getcwd())
                if answer == 'delete':
                    break
            except KeyboardInterrupt:
                pass


def confirm_and_upload(local_branch_name, args, bugs):
    if not args.yes:
      question = ('Ready to continue (cwd %s, will not upload to Gerrit)' %
                  os.getcwd() if args.no_upload else
                  'Ready to upload %s (cwd %s)' % (local_branch_name, os.getcwd()))

      while True:
          try:
              answer = input(question + ' [yes/abort]? ')
              if answer == 'yes':
                 break
              if answer == 'abort':
                  print('Aborting new branch for %s' % local_branch_name)
                  sys.exit(1)
          except KeyboardInterrupt:
              pass

    # Compute the set of bug refs from the commit message after confirmation.
    # If done before a conflicting cherry-pick status will potentially include
    # references that are orthogonal to the pick.
    if bugs != None:
        commit_message = subprocess.check_output(
            ['git', 'log', '--format=%B', '-n', '1', 'HEAD'])
        commit_lines = [
            l.strip() for l in commit_message.decode('UTF-8').split('\n')
        ]
        for line in commit_lines:
            bug = None
            if line.startswith('Bug: '):
                bug = line.replace('Bug: ', '')
            elif line.startswith('Fixed: '):
                bug = line.replace('Fixed: ', '')
            elif line.startswith('Fixes: '):
                bug = line.replace('Fixes: ', '')
            if bug:
                bugs.add(bug.replace('b/', '').strip())

    cmd = ['git', 'cl', 'upload', '--bypass-hooks']
    if args.yes:
        cmd.append('-f')
    git_utils.GitClAppendReviewers(cmd, args.reviewer, args.send_mail)
    if not args.no_upload:
        subprocess.run(cmd)
    else:
        print('Not uploading, upload command was "%s"' % cmd)


def promote_dev(args):
    if len(args.branch) > 1:
        print("Only one branch can be specified for --promote-dev.")
        sys.exit(1)
    branch = args.branch[0]

    if args.current_checkout:
        print("--current-checkout not supported for --promote-dev.")
        sys.exit(1)

    with utils.TempDir() as temp:
        print("Performing promote dev in %s" % temp)
        subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
        with utils.ChangedWorkingDirectory(temp):
            local_branch_name = 'promote-dev-%s' % branch
            subprocess.run(['git', 'branch', local_branch_name, '-D'])
            git_new_branch(local_branch_name, '%s/%s' % (args.remote, branch))

            dev_version = version_from_version_file()
            print(dev_version)
            if not dev_version.endswith('-dev'):
                print("Not a -dev version branch")
                sys.exit(1)
            version = dev_version[0:len(dev_version) - 4]

            print("Promoting %s to %s" % (dev_version, version))
            sed(dev_version, version, VERSION_FILE)

            message = ('Version %s\n\n'
                'Promoting version %s to version %s' % (version, dev_version, version))

            git_commit(message)
            confirm_and_upload(branch, args, None)


def main():
    args = parse_options()

    if len(args.branch) == 0:
        print("No branches specified.")
        sys.exit(1)

    if args.promote_dev:
        promote_dev(args)
        sys.exit(0)

    # At least one hash even though it is optional, as it is in an exclusive group
    # with --promote-dev which is checked above.
    assert len(args.hashes) > 0
    branches = args.branch
    args.branch = None
    for branch in branches:
        print("Performing cherry-picking to %s" % branch)
        if not args.current_checkout:
            with utils.TempDir() as temp:
                print("Performing cherry-picking in %s" % temp)
                subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
                with utils.ChangedWorkingDirectory(temp):
                    run(args, branch)
        else:
            # Run in current directory.
            print("Performing cherry-picking in %s" % os.getcwd())
            subprocess.check_output(['git', 'fetch', 'origin'])
            run(args, branch)


if __name__ == '__main__':
    sys.exit(main())
