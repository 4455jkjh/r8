#!/usr/bin/env python3
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
"""
Wrapper script for running gradle.
Will make sure we pulled down gradle before running, and will use the pulled
down version to have a consistent developer experience.
"""

import argparse
import os
import subprocess
import sys

import jdk
import utils

GRADLE8_SHA1 = os.path.join(utils.THIRD_PARTY, 'gradle.tar.gz.sha1')


def get_gradle_dir():
    return os.path.join(utils.THIRD_PARTY, 'gradle')


def get_gradle_executable():
    if utils.IsWindows():
        return os.path.join(get_gradle_dir(), 'bin', 'gradle.bat')
    else:
        return os.path.join(get_gradle_dir(), 'bin', 'gradle')


def parse_options():
    parser = argparse.ArgumentParser(description='Call gradle.')
    parser.add_argument('--exclude-deps',
                        '--exclude_deps',
                        help='Build without internalized dependencies.',
                        default=False,
                        action='store_true')
    parser.add_argument(
        '--no-internal',
        '--no_internal',
        help='Do not build with support for Google internal tests.',
        default=False,
        action='store_true')
    parser.add_argument('--java-home',
                        '--java_home',
                        help='Use a custom java version to run gradle.')
    parser.add_argument('--worktree',
                        help='Gradle is running in a worktree and may lock up '
                        'the gradle caches.',
                        action='store_true',
                        default=False)
    parser.add_argument('--quiet',
                        '-q',
                        help='Only print warnings and errors from gradle.',
                        action='store_true',
                        default=False)
    return parser.parse_known_args()


def get_java_env(env):
    java_env = dict(env if env else os.environ,
                    JAVA_HOME=jdk.GetGradleJdkHome())
    java_env['PATH'] = java_env['PATH'] + os.pathsep + os.path.join(
        jdk.GetGradleJdkHome(), 'bin')
    java_env['GRADLE_OPTS'] = '-Xmx1g'
    return java_env


def print_cmd(s):
    if type(s) is list:
        s = ' '.join(s)
    print('Running: %s' % s)
    sys.stdout.flush()


def ensure_gradle():
    utils.EnsureDepFromGoogleCloudStorage(GRADLE8_SHA1,
                                          'Gradle binary',
                                          dep=get_gradle_executable())


def ensure_gradle_repositories():
    dependencies_sha1 = os.path.join(utils.THIRD_PARTY,
                                     'dependencies.tar.gz.sha1')
    utils.EnsureDepFromGoogleCloudStorage(dependencies_sha1,
                                          'Gradle dependencies')
    dependencies_plugin_sha1 = os.path.join(utils.THIRD_PARTY,
                                            'dependencies_plugin.tar.gz.sha1')
    utils.EnsureDepFromGoogleCloudStorage(dependencies_plugin_sha1,
                                          'Gradle plugin dependencies')


def ensure_jdk():
    # Gradle in the new setup will use the jdks in the evaluation - fetch
    # all beforehand.
    for root in jdk.GetAllJdkDirs():
        jdk_sha1 = root + '.tar.gz.sha1'
        utils.EnsureDepFromGoogleCloudStorage(jdk_sha1, root)


def ensure_deps():
    ensure_gradle()
    ensure_gradle_repositories()
    ensure_jdk()


def run_gradle_in(gradle_cmd,
                  args,
                  cwd,
                  throw_on_failure=True,
                  env=None,
                  quiet=False):
    ensure_deps()
    cmd = [gradle_cmd]
    # Changes to these flags should be copied to gradle_benchmark.scenarios.
    args.extend(['--offline'])
    if not any(
            arg.startswith('-Porg.gradle.java.installations.paths=')
            for arg in args):
        args.append('-Porg.gradle.java.installations.paths=' +
                    get_java_installations_paths())
    if not any(
            arg.startswith('-Porg.gradle.java.installations.auto-detect=')
            for arg in args):
        args.append('-Porg.gradle.java.installations.auto-detect=false')
    if not any(
            arg.startswith('-Porg.gradle.java.installations.auto-download=')
            for arg in args):
        args.append('-Porg.gradle.java.installations.auto-download=false')
    if quiet:
        args.append('--quiet')
    cmd.extend(args)
    with utils.ChangedWorkingDirectory(cwd):
        if not quiet:
            utils.PrintCmd(cmd)
        return_value = subprocess.call(cmd, env=get_java_env(env))
        if throw_on_failure and return_value != 0:
            raise Exception('Failed to execute gradle')
        return return_value


def run_gradle(args, throw_on_failure=True, env=None, quiet=False):
    return run_gradle_in(get_gradle_executable(),
                         args,
                         utils.REPO_ROOT,
                         throw_on_failure,
                         env=env,
                         quiet=quiet)


def get_java_installations_paths():
    paths = []
    openjdk_dir = os.path.join(utils.THIRD_PARTY, 'openjdk')
    if os.path.exists(openjdk_dir):
        for jdk in os.listdir(openjdk_dir):
            jdk_dir = os.path.join(openjdk_dir, jdk)
            if not os.path.isdir(jdk_dir):
                continue
            for platform in ['linux', 'osx', 'windows']:
                platform_dir = os.path.join(jdk_dir, platform)
                if os.path.exists(platform_dir):
                    paths.append(platform_dir)
    return ','.join(paths)


def main():
    (options, args) = parse_options()
    if options.java_home:
        args.append('-Dorg.gradle.java.home=' + options.java_home)
    if options.no_internal:
        args.append('-Pno_internal')
    if options.exclude_deps:
        args.append('-Pexclude_deps')
    if options.worktree:
        args.append('-g=' + os.path.join(utils.REPO_ROOT, ".gradle_user_home"))
    return run_gradle(args, quiet=options.quiet)


if __name__ == '__main__':
    sys.exit(main())
