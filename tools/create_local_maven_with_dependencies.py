#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os.path
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path

import utils

# The local_maven_repository_generator orders the repositories by name, so
# prefix with X- to control the order, as many dependencies are present
# in several repositories. Save A- for additional local repository.
REPOSITORIES = [
    'B-Google=https://maven.google.com/',
    'C-Maven Central=https://repo1.maven.org/maven2/',
    "D-Gradle Plugins=https://plugins.gradle.org/m2/",
]

with open(os.path.join(utils.REPO_ROOT, 'gradle', 'libs.versions.toml'), "rb") as f:
  data = tomllib.load(f)
versions = data.get("versions", {})
libraries = data.get("libraries", {})

BUILD_DEPENDENCIES = []
PLUGIN_DEPENDENCIES = []
for library, details in libraries.items():
    artifact = details["module"] + ":" + versions[details["version"]["ref"]]
    if library.endswith("GradlePlugin" ):
        PLUGIN_DEPENDENCIES.append(artifact)
    else:
        BUILD_DEPENDENCIES.append(artifact)

def dependencies_tar(dependencies_path):
    return os.path.join(os.path.dirname(dependencies_path),
                        os.path.basename(dependencies_path) + '.tar.gz')


def dependencies_tar_sha1(dependencies_path):
    return os.path.join(os.path.dirname(dependencies_path),
                        os.path.basename(dependencies_path) + '.tar.gz.sha1')


def remove_local_maven_repository(dependencies_path):
    if os.path.exists(dependencies_path):
        shutil.rmtree(dependencies_path)
    tar = dependencies_tar(dependencies_path)
    if os.path.exists(tar):
        os.remove(tar)
    sha1 = dependencies_tar_sha1(dependencies_path)
    if os.path.exists(sha1):
        os.remove(sha1)


def create_local_maven_repository(args, dependencies_path, repositories,
                                  dependencies):
    with utils.ChangedWorkingDirectory(args.studio):
        cmd = [
            os.path.join('tools', 'base', 'bazel', 'bazel'), 'run',
            '//tools/base/bazel:local_maven_repository_generator_cli', '--',
            '--repo-path', dependencies_path, '--fetch'
        ]
        for repository in repositories:
            cmd.extend(['--remote-repo', repository])
        for dependency in dependencies:
            cmd.append(dependency)
        subprocess.check_call(cmd)
        clean_remote_repositories_files(dependencies_path)


def clean_remote_repositories_files(directory_path):
    """
    Recursively finds and deletes all '_remote.repositories' files
    in the specified directory. These files include timestamps making
    the dependencies archive creation non-reproducible.
    """
    # rglob performs a recursive search for the given pattern
    for file_path in Path(directory_path).rglob("_remote.repositories"):
        if file_path.is_file():
            try:
                file_path.unlink()
            except PermissionError:
                print(f"Permission denied: {file_path}")
            except Exception as e:
                print(f"Error deleting {file_path}: {e}")


def parse_options():
    result = argparse.ArgumentParser(
        description='Create local Maven repository with dependencies')
    result.add_argument(
        '--studio',
        metavar=('<path>'),
        required=True,
        help='Path to a studio-main checkout (to get the tool '
        '//tools/base/bazel:local_maven_repository_generator_cli)')
    result.add_argument('--plugin-deps',
                        '--plugin_deps',
                        default=False,
                        action='store_true',
                        help='Build repository Gradle plugin dependncies')
    result.add_argument(
        '--include-maven-local',
        '--include_maven_local',
        metavar=('<path>'),
        default=False,
        help='Path to maven local repository to include as dependency source')
    result.add_argument('--no-upload',
                        '--no_upload',
                        default=False,
                        action='store_true',
                        help="Don't upload to Google CLoud Storage")
    return result.parse_args()


def set_utime(path):
    os.utime(path, (0, 0))
    for root, dirs, files in os.walk(path):
        for f in files:
            os.utime(os.path.join(root, f), (0, 0))
        for d in dirs:
            os.utime(os.path.join(root, d), (0, 0))


def main():
    args = parse_options()

    repositories = REPOSITORIES
    if args.include_maven_local:
        # Add the local repository as the first for it to take precedence.
        # Use A- prefix as current local_maven_repository_generator orderes by name.
        repositories = ['A-Local=file://%s' % args.include_maven_local
                       ] + REPOSITORIES

    dependencies = []

    if args.plugin_deps:
        dependencies_plugin_path = os.path.join(utils.THIRD_PARTY,
                                                'dependencies_plugin')
        remove_local_maven_repository(dependencies_plugin_path)
        print("Downloading to " + dependencies_plugin_path)
        create_local_maven_repository(args, dependencies_plugin_path,
                                      repositories, PLUGIN_DEPENDENCIES)
        set_utime(dependencies_plugin_path)
        dependencies.append('dependencies_plugin')
    else:
        dependencies_path = os.path.join(utils.THIRD_PARTY, 'dependencies')
        remove_local_maven_repository(dependencies_path)
        print("Downloading to " + dependencies_path)
        create_local_maven_repository(args, dependencies_path, repositories,
                                      BUILD_DEPENDENCIES)
        set_utime(dependencies_path)
        dependencies.append('dependencies')

    upload_cmds = []
    for dependency in dependencies:
        upload_cmds.append([
            'upload_to_google_storage.py', '-a', '--bucket', 'r8-deps',
            dependency
        ])

    if not args.no_upload:
        print("Uploading to Google Cloud Storage:")
        with utils.ChangedWorkingDirectory(utils.THIRD_PARTY):
            for cmd in upload_cmds:
                subprocess.check_call(cmd)
    else:
        print("Not uploading to Google Cloud Storage. " +
              "Run the following commands in %s to do so manually" %
              utils.THIRD_PARTY)
        for cmd in upload_cmds:
            print(" ".join(cmd))


if __name__ == '__main__':
    sys.exit(main())
