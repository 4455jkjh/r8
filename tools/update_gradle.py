#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import stat
import sys
import urllib.request
import zipfile

import utils


def parse_options():
    parser = argparse.ArgumentParser(
        description='Update Gradle distribution in third_party.')
    parser.add_argument('--version',
                        required=True,
                        help='Gradle version to download.')
    return parser.parse_args()


def main():
    args = parse_options()
    version = args.version
    gradle_url = f'https://services.gradle.org/distributions/gradle-{version}-bin.zip'
    zip_name = f'gradle-{version}-bin.zip'
    zip_path = os.path.join(utils.THIRD_PARTY, zip_name)
    gradle_dir = os.path.join(utils.THIRD_PARTY, 'gradle')
    unzipped_gradle_dir = os.path.join(utils.THIRD_PARTY, f'gradle-{version}')

    # Download gradle
    print(f'Downloading {gradle_url} to {zip_path}...')
    urllib.request.urlretrieve(gradle_url, zip_path)

    # Remove existing gradle directory
    if os.path.exists(gradle_dir):
        print(f'Removing existing {gradle_dir}...')
        shutil.rmtree(gradle_dir)

    # Unzip gradle
    print(f'Unzipping {zip_path} into {utils.THIRD_PARTY}...')
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(utils.THIRD_PARTY)

    # Move new gradle directory
    print(f'Moving {unzipped_gradle_dir} to {gradle_dir}...')
    shutil.move(unzipped_gradle_dir, gradle_dir)

    # Remove zip file
    print(f'Removing {zip_path}...')
    os.remove(zip_path)

    # Make gradle executable
    gradle_bin = os.path.join(gradle_dir, 'bin', 'gradle')
    print(f'Making {gradle_bin} executable...')
    st = os.stat(gradle_bin)
    os.chmod(gradle_bin, st.st_mode | stat.S_IXUSR)

    # Upload to Google Storage
    print('If you want to upload this run:')
    print(
        '  (cd {dir}; upload_to_google_storage.py -a --bucket r8-deps gradle)'.
        format(dir=utils.THIRD_PARTY))


if __name__ == '__main__':
    sys.exit(main())
