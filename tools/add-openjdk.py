#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import sys
import subprocess
import tarfile
import zipfile

import utils

# This script is a rewrite of scripts/add-openjdk.sh.
# It is used to add a new OpenJDK version to third_party by:
# 1. Cleaning up existing platform directories and archives.
# 2. Extracting platform-specific JDK archives from ~/Downloads.
# 3. Dereferencing symlinks and copying README.google into the platform directory.
# 4. Uploading to Google Storage using upload_to_google_storage.py.
# 5. Cleaning up temporary files and adding generated .sha1 files to git.


def parse_options():
    parser = argparse.ArgumentParser(
        description='Add a new OpenJDK version to third_party')
    parser.add_argument('--jdk-version',
                        '--jdk_version',
                        required=True,
                        help='JDK version (e.g., 25)')
    parser.add_argument(
        '--jdk-version-full',
        '--jdk_version_full',
        help='Full JDK version name (e.g., 25-ea+33). Defaults to --jdk-version.'
    )
    return parser.parse_args()


def run_command(command, cwd=None):
    print('Running: %s' % ' '.join(command))
    subprocess.check_call(command, cwd=cwd)


def main():
    args = parse_options()
    jdk_version = args.jdk_version
    jdk_version_full = args.jdk_version_full if args.jdk_version_full else jdk_version

    # The script expects to be run in third_party/openjdk/jdk-XX
    target_dir = os.path.join(utils.REPO_ROOT, 'third_party', 'openjdk',
                              'jdk-%s' % jdk_version)
    if not os.path.exists(target_dir):
        print('Error: Target directory does not exist: %s' % target_dir)
        print('Please create it first.')
        return 1

    if not os.path.exists(os.path.join(target_dir, 'README.google')):
        print('Error: README.google not found in %s' % target_dir)
        print(
            'Prepare README.google by copying from a previous JDK version and update it.'
        )
        return 1

    os.chdir(target_dir)

    downloads = os.path.expanduser('~/Downloads')

    platforms = [('linux', 'linux-x64_bin.tar.gz', 'jdk-%s' % jdk_version),
                 ('osx', 'macos-aarch64_bin.tar.gz',
                  'jdk-%s.jdk' % jdk_version),
                 ('windows', 'windows-x64_bin.zip', 'jdk-%s' % jdk_version)]

    for platform, suffix, extract_dir in platforms:
        print('Processing %s...' % platform)

        # Clean up existing files and directories
        if os.path.exists(platform):
            shutil.rmtree(platform)
        for ext in ['', '.sha1']:
            filename = platform + '.tar.gz' + ext
            if os.path.exists(filename):
                os.remove(filename)

        # Extract
        archive_name = 'openjdk-%s_%s' % (jdk_version_full, suffix)
        archive_path = os.path.join(downloads, archive_name)
        if not os.path.exists(archive_path):
            print('Error: Archive not found: %s' % archive_path)
            return 1

        print('Extracting %s' % archive_path)
        if archive_path.endswith('.tar.gz'):
            with tarfile.open(archive_path, 'r:gz') as tar:
                tar.extractall()
        elif archive_path.endswith('.zip'):
            with zipfile.ZipFile(archive_path, 'r') as zip_ref:
                zip_ref.extractall()

        if not os.path.exists(extract_dir):
            print('Error: Extraction did not create expected directory: %s' %
                  extract_dir)
            return 1

        # Move and prepare
        # Use copytree(symlinks=False) to dereference symlinks (mimics cp -rL)
        shutil.copytree(extract_dir, platform, symlinks=False)
        shutil.copy('README.google', platform)

        # Upload
        run_command([
            'upload_to_google_storage.py', '-a', '--bucket', 'r8-deps', platform
        ])

        # Cleanup
        shutil.rmtree(extract_dir)
        shutil.rmtree(platform)
        if os.path.exists(platform + '.tar.gz'):
            os.remove(platform + '.tar.gz')

    # git add *.sha1
    run_command(['git', 'add', '*.sha1'])

    print('\nJDK version %s added successfully.' % jdk_version)
    print(
        'Update additional files, see https://r8-review.googlesource.com/c/r8/+/111040'
    )
    return 0


if __name__ == '__main__':
    sys.exit(main())
