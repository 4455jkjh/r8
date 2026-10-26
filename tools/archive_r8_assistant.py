#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import json
import os
try:
    import resource
except ImportError:
    pass
import shutil
import subprocess
import sys
import zipfile

import create_maven_release
import gradle
from timing import Timing
import utils

ARCHIVE_BUCKET = 'r8-releases'


def parse_options():
    parser = argparse.ArgumentParser(description='Archive R8 Assistant')
    parser.add_argument(
        '--version',
        metavar='<version>',
        help='Expected R8 Assistant version in R8AssistantVersion.java')
    parser.add_argument('--dry-run',
                        '--dry_run',
                        default=False,
                        action='store_true',
                        help='Build only, no upload to Cloud Storage.')
    parser.add_argument('--dry-run-output',
                        '--dry_run_output',
                        metavar='<output directory>',
                        default=None,
                        help='Output directory for dry run.')
    parser.add_argument(
        '--skip-gradle-build',
        '--skip_gradle_build',
        default=False,
        action='store_true',
        help='Skip Gradle build (only supported with --dry-run).')
    parser.add_argument('--archive-details',
                        metavar='<file>',
                        default=utils.ARCHIVE_DETAILS,
                        help='Path to write archive_details.json.')
    return parser.parse_args()


def set_r_limit_to_max():
    if utils.IsWindows():
        return
    (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
    resource.setrlimit(resource.RLIMIT_NOFILE, (hard, hard))


def get_git_hash():
    return subprocess.check_output(['git', 'rev-parse',
                                    'HEAD']).decode('utf-8').strip()


def get_storage_path(version):
    return f'raw/r8-assistant/{version}'


def get_version_destination(storage_prefix, version):
    return f'{storage_prefix}{ARCHIVE_BUCKET}/{get_storage_path(version)}'


def get_upload_destination(version, file_name):
    return f'{get_version_destination("gs://", version)}/{file_name}'


def get_url(version, file_name):
    return f'{get_version_destination(utils.CLOUD_STORAGE_URL + "/", version)}/{file_name}'


def upload_file(src_file, version, dst_file, options):
    destination = get_upload_destination(version, dst_file)
    print(f'Uploading {src_file} to {destination}')
    if options.dry_run:
        if options.dry_run_output:
            dry_run_destination = os.path.join(options.dry_run_output, version,
                                               dst_file)
            print(
                f'Dry run, not actually uploading. Copying to {dry_run_destination}'
            )
            os.makedirs(os.path.dirname(dry_run_destination), exist_ok=True)
            shutil.copyfile(src_file, dry_run_destination)
        else:
            print('Dry run, not actually uploading')
    else:
        utils.upload_file_to_cloud_storage(src_file, destination)
        print(f'File available at: {get_url(version, dst_file)}')


def run_gradle_build(options, timing):
    if options.skip_gradle_build:
        return
    timing.begin('Download deps')
    gradle.run_gradle([utils.GRADLE_TASK_DOWNLOAD_DEPS, '-Pno_internal'])
    timing.end()
    timing.begin('Build consolidated license')
    gradle.run_gradle([utils.GRADLE_TASK_CONSOLIDATED_LICENSE, '-Pno_internal'])
    timing.end()
    timing.begin('Build R8 assistant')
    gradle.run_gradle([utils.GRADLE_TASK_R8_ASSISTANT, '-Pno_internal'])
    timing.end()


def run(options):
    if not utils.is_bot() and not options.dry_run:
        raise Exception("You are not a bot, don't archive builds. "
                        "Use --dry-run to test locally")
    if (options.dry_run_output and
        (not os.path.exists(options.dry_run_output) or
         not os.path.isdir(options.dry_run_output))):
        raise Exception(
            f'{options.dry_run_output} does not exist or is not a directory')
    if options.skip_gradle_build and not options.dry_run:
        raise Exception(
            'Using --skip-gradle-build only supported with --dry-run')

    version = create_maven_release.determine_r8_assistant_version()
    if options.version and version != options.version:
        raise Exception(
            f'R8AssistantVersion.LABEL is {version}, expected {options.version}'
        )

    timing = Timing()
    set_r_limit_to_max()
    run_gradle_build(options, timing)

    with utils.TempDir() as temp:
        timing.begin('Generate r8-assistant maven zip')
        version_file = os.path.join(temp, 'r8-version.properties')
        with open(version_file, 'w') as version_writer:
            version_writer.write('version.sha=' + get_git_hash() + '\n')
            version_writer.write('version.version=' + version + '\n')
            bot_id = os.environ.get('SWARMING_BOT_ID') or os.environ.get(
                'KOKORO_BUILD_ID')
            if not bot_id and not options.dry_run:
                raise Exception(
                    'Environment variable SWARMING_BOT_ID or KOKORO_BUILD_ID not set'
                )
            releaser = ('releaser=<local developer build>\n' if
                        (options.dry_run and not bot_id) else
                        'releaser=go/r8bot (' + (bot_id or 'foo') + ')\n')
            version_writer.write(releaser)
            version_writer.write('version-file.version.code=1\n')

        create_maven_release.generate_r8_assistant_maven_zip(
            utils.R8_ASSISTANT_MAVEN_ZIP_LIB,
            version_file=version_file,
            skip_gradle_build=True)
        timing.end()

        destination = get_version_destination('gs://', version)
        if not options.dry_run and utils.cloud_storage_exists(destination):
            raise Exception(
                f'Target archive directory {destination} already exists')

        timing.begin('Upload files')
        for_archiving = [
            utils.R8_ASSISTANT_LIB_JAR,
            utils.R8_ASSISTANT_LIB_JAR + '.map',
            utils.R8_ASSISTANT_LIB_JAR + '_map.zip',
            utils.R8_ASSISTANT_MAVEN_ZIP_LIB,
        ]
        for file in for_archiving:
            file_name = os.path.basename(file)
            tagged_file = os.path.join(temp, file_name)
            shutil.copyfile(file, tagged_file)
            if file_name.endswith('.jar'):
                with zipfile.ZipFile(tagged_file, 'a') as f:
                    f.write(version_file, os.path.basename(version_file))
            upload_file(tagged_file, version, file_name, options)

        os.makedirs(os.path.dirname(os.path.abspath(options.archive_details)),
                    exist_ok=True)
        archive_details = {
            'version': version,
            'bucket': ARCHIVE_BUCKET,
            'bucket_path': get_storage_path(version),
        }
        with open(options.archive_details, 'w') as f:
            json.dump(archive_details, f, indent=4)
        timing.end()
        timing.report()


def main():
    options = parse_options()
    run(options)


if __name__ == '__main__':
    sys.exit(main())
