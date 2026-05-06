#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Fetches ART binaries and ART bootclasspath jars from AOSP head or Google Maven.
# Saves and unzips the result, and creates a boot image.

import argparse
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

import utils

URL = 'https://ci.android.com/builds/latest/branches/aosp-android-latest-release/targets/aosp_cf_x86_64_only_phone-userdebug/view/BUILD_INFO'
PATTERN = r'(.*\/submitted\/([0-9]+)\/.*)/view/BUILD_INFO$'

MAVEN_URL_PATTERN = 'https://dl.google.com/android/maven2/com/android/art/art/{version}/art-{version}.zip'
MAVEN_INDEX_URL = 'https://dl.google.com/android/maven2/com/android/art/group-index.xml'


def parse_options():
    parser = argparse.ArgumentParser(description='Fetch ART binaries.')
    parser.add_argument('--maven',
                        help='Download from Google Maven.',
                        action='store_true')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--head',
                       help='Download from AOSP head.',
                       action='store_true')
    group.add_argument(
        '--version',
        help='Version to download from Google Maven (e.g., 36.0, 33.10).')
    parser.add_argument('--list-versions',
                        '--list_versions',
                        help='List versions available on Google Maven.',
                        action='store_true')
    parser.add_argument(
        '--output',
        help='Output directory to unzip into (default: %(default)s).',
        default='third_party/dex2oat')
    parser.add_argument(
        '--old',
        help='Exclude riscv64 and CMC GC (for versions 33.10 and before).',
        action='store_true')
    return parser.parse_args()


def list_maven_versions():
    response = urllib.request.urlopen(MAVEN_INDEX_URL)
    content = response.read().decode('utf-8')
    response.close()
    # Basic XML parsing to find <art versions="..."/>
    match = re.search(r'<art versions="([^"]+)"', content)
    if match:
        versions = match.group(1).split(',')
        print('Available versions on Google Maven:')
        for version in versions:
            print('  %s' % version)
    else:
        print('Failed to parse versions from %s' % MAVEN_INDEX_URL)
        return 1
    return 0


def download_from_head(temp_dir):
    response = urllib.request.urlopen(URL)
    redirected_url = response.geturl()
    response.close()

    match = re.match(PATTERN, redirected_url)
    if not match:
        print('Got unexpected URL %s' % redirected_url)
        return None

    download_url = '%s/raw/art_release.zip' % match.group(1)
    dest = os.path.join(temp_dir, 'art_release.zip')
    print('Downloading: %s to: %s' % (download_url, dest))
    urllib.request.urlretrieve(download_url, dest)
    return dest


def download_from_maven(version, temp_dir):
    download_url = MAVEN_URL_PATTERN.format(version=version)
    dest = os.path.join(temp_dir, 'art_release.zip')
    print('Downloading: %s to: %s' % (download_url, dest))
    try:
        urllib.request.urlretrieve(download_url, dest)
    except Exception as e:
        print('Failed to download from Maven: %s' % e)
        return None
    return dest


def unzip(zip_file, out_dir):
    print('Unzipping %s to %s' % (zip_file, out_dir))
    if os.path.exists(out_dir):
        shutil.rmtree(out_dir)
    os.makedirs(out_dir)
    with zipfile.ZipFile(zip_file, 'r') as zip_ref:
        zip_ref.extractall(out_dir)

    # Ensure binaries are executable
    bin_dir = os.path.join(out_dir, 'x86_64', 'bin')
    if os.path.exists(bin_dir):
        for f in os.listdir(bin_dir):
            os.chmod(os.path.join(bin_dir, f), 0o755)


def create_boot_image(out_dir, old=False):
    # Constants from create_boot_image.py
    DIR_FOR_BOOT_IMAGE = 'app/system/framework'
    instruction_sets = ['arm', 'arm64', 'x86', 'x86_64', 'riscv64']
    if old:
        instruction_sets.remove('riscv64')

    BOOTJARS = [
        'core-oj.jar', 'core-libart.jar', 'okhttp.jar', 'bouncycastle.jar',
        'apache-xml.jar'
    ]
    BOOT_CLASS_PATH = ':'.join(['bootjars/%s' % jar for jar in BOOTJARS])
    BOOT_CLASS_PATH_LOCATIONS = ':'.join(
        ['/apex/com.android.art/javalib/%s' % jar for jar in BOOTJARS])

    with utils.ChangedWorkingDirectory(out_dir):
        for instruction_set in instruction_sets:
            output_dir = os.path.join(DIR_FOR_BOOT_IMAGE, instruction_set)
            os.makedirs(output_dir, exist_ok=True)

            cmd = [
                'x86_64/bin/dex2oat64',
                '--runtime-arg',
                '-Xms64m',
                '--runtime-arg',
                '-Xmx512m',
            ]
            if not old:
                cmd.extend(['--runtime-arg', '-Xgc:CMC'])

            cmd.extend([
                '--runtime-arg',
                '-Xbootclasspath:%s' % BOOT_CLASS_PATH,
                '--runtime-arg',
                '-Xbootclasspath-locations:%s' % BOOT_CLASS_PATH_LOCATIONS,
                '--instruction-set=%s' % instruction_set,
                '--compiler-filter=speed',
            ])

            for jar in BOOTJARS:
                cmd.append('--dex-file=bootjars/%s' % jar)

            for jar in BOOTJARS:
                cmd.append('--dex-location=/apex/com.android.art/javalib/%s' %
                           jar)

            cmd.extend([
                '--image=%s' % os.path.join(output_dir, 'boot.art'),
                '--oat-file=%s' % os.path.join(output_dir, 'boot.oat'),
            ])
            if not old:
                cmd.append('--output-vdex=%s' %
                           os.path.join(output_dir, 'boot.vdex'))

            cmd.extend([
                '--android-root=.',
                '--abort-on-hard-verifier-error',
                '--no-abort-on-soft-verifier-error',
                '--compilation-reason=cloud',
                '--image-format=lz4',
                '--force-determinism',
                '--resolve-startup-const-strings=true',
                '--avoid-storing-invocation',
                '--generate-mini-debug-info',
            ])
            if not old:
                cmd.append('--force-allow-oj-inlines')

            cmd.append('--no-watch-dog')

            if not old:
                cmd.append('--single-image')

            cmd.append('--base=0x70000000')

            print('Creating dex2oat boot image for %s' % instruction_set)
            subprocess.check_call(cmd)


def main():
    args = parse_options()
    if args.list_versions:
        if not args.maven:
            print('--list-versions is only supported when using --maven')
            return 1
        return list_maven_versions()

    if not args.head and not args.version:
        print('Either --head or --version must be specified')
        return 1

    version_name = 'head' if args.head else args.version
    with utils.TempDir() as temp_dir:
        if args.maven:
            if args.head:
                print('--head is not supported when using --maven')
                return 1
            zip_file = download_from_maven(args.version, temp_dir)
        else:
            if args.version:
                print('--version is only supported when using --maven')
                return 1
            zip_file = download_from_head(temp_dir)

        if not zip_file:
            return 1

        out_dir = os.path.join(args.output, version_name)
        unzip(zip_file, out_dir)
        old = args.old or version_name == '33.10'
        create_boot_image(out_dir, old=old)

        print('Now run')
        print('(cd %s; upload_to_google_storage.py -a --bucket r8-deps %s)' %
              (args.output, version_name))

    return 0


if __name__ == '__main__':
    sys.exit(main())
