#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import sys

import compiledump
import gradle
import utils


def parse_options(argv):
    result = argparse.ArgumentParser(description='Run test-based benchmarks.')
    result.add_argument('--benchmark',
                        help='The test benchmark to run',
                        required=True)
    result.add_argument(
        '--target',
        help='The test target to run',
        required=True,
        # These should 1:1 with benchmarks/BenchmarkTarget.java
        choices=['d8', 'r8-full', 'r8-force', 'r8-compat', 'retrace'])
    result.add_argument(
        '--debug-agent',
        '--debug_agent',
        help=
        'Enable Java debug agent and suspend compilation (default disabled)',
        default=False,
        action='store_true')
    result.add_argument(
        '--dump-jfr-to-file',
        '--dump_jfr_to_file',
        nargs='?',
        const='profile.jfr',
        help=
        'Enable Java Flight Recorder (JFR) and dump to the given file (default: profile.jfr)'
    )
    result.add_argument(
        '--dump-gc-log-to-file',
        '--dump_gc_log_to_file',
        nargs='?',
        const='gc.log',
        help='Enable GC logging and dump to the given file (default: gc.log)')
    result.add_argument(
        '--dump-heap-to-directory',
        '--dump_heap_to_directory',
        help='Dump heap(s) to the given directory (default disabled)')
    result.add_argument('--dump-keep-radius-to-directory',
                        '--dump_keep_radius_to_directory',
                        help='Dump keep radius .pb file to the given directory')
    result.add_argument(
        '--dump-keep-radius-html-to-directory',
        '--dump_keep_radius_html_to_directory',
        help='Dump keep radius .html file to the given directory')
    result.add_argument('--dump-trace-to-directory',
                        '--dump_trace_to_directory',
                        help='Dump perfetto trace to the given directory')
    result.add_argument('--nolib',
                        '--no-lib',
                        '--no-r8lib',
                        help='Run the non-lib R8 build (default false)',
                        default=False,
                        action='store_true')
    result.add_argument('--no-build',
                        '--no_build',
                        help='Run without building first (default false)',
                        default=False,
                        action='store_true')
    result.add_argument('--enable-assertions',
                        '--enable_assertions',
                        '-ea',
                        help='Enable assertions when running',
                        default=False,
                        action='store_true')
    result.add_argument('--heap-size',
                        '--heap_size',
                        help='Heap size to use for the JVM (e.g. 4g)')
    result.add_argument('--iterations',
                        '-i',
                        help='Number of iterations to run',
                        type=int)
    result.add_argument('--output',
                        help='Output path where to write the result')
    result.add_argument('--build-output',
                        '--build_output',
                        help='Output path where to write output from the build')
    result.add_argument(
        '--version',
        '-v',
        help='Use R8 version/hash for the run (default local build)',
        default=None)
    result.add_argument(
        '--version-jar',
        help='The r8.jar corresponding to the version given at --version.',
        default=None)
    result.add_argument('--temp',
                        help='A directory to use for temporaries and outputs.',
                        default=None)
    result.add_argument('--verbose',
                        help='To enable verbose logging.',
                        action='store_true',
                        default=False)
    result.add_argument('--warmup',
                        '-w',
                        help='Number of warmup iterations to run',
                        type=int)
    options, args = result.parse_known_args(argv)
    options.quiet = not options.verbose
    # We must download the non-lib distribution when running with a specific
    # version, since BenchmarkMainEntryRunner is using R8 internals.
    # TODO(b/346477461): Look into removing this limitation.
    assert options.version is None or options.nolib
    return options, args


def is_internal_benchmark(benchmark):
    return any(name in benchmark for name in ('AGSA', 'SystemUI', 'YouTube'))


def build_gradle_args(options, temp):
    if options.version or options.version_jar:
        base_task = ':test:runBenchmarkWithCustomJar'
    elif options.nolib:
        base_task = ':test:runBenchmarkNoLib'
    else:
        base_task = ':test:runBenchmark'
    task = f'{base_task}Prebuilt' if options.no_build else base_task
    gradle_args = [
        task,
        f'-Pbenchmark={options.benchmark}',
        f'-Ptarget={options.target}',
    ]
    if options.version or options.version_jar:
        r8jar = options.version_jar or compiledump.download_distribution(
            options.version, options, temp)
        gradle_args.append(f'-Pr8_jar={os.path.abspath(r8jar)}')

    if not is_internal_benchmark(options.benchmark):
        gradle_args.append('-Pno_internal')

    for flag in ('enable_assertions', 'debug_agent', 'verbose'):
        if getattr(options, flag):
            gradle_args.append(f'-P{flag}')

    for prop, val in (('benchmark_iterations', options.iterations),
                      ('warmup', options.warmup), ('heap_size',
                                                   options.heap_size)):
        if val is not None:
            gradle_args.append(f'-P{prop}={val}')

    for path_opt in ('output', 'build_output', 'temp', 'dump_jfr_to_file',
                     'dump_gc_log_to_file', 'dump_heap_to_directory',
                     'dump_keep_radius_to_directory',
                     'dump_keep_radius_html_to_directory',
                     'dump_trace_to_directory'):
        val = getattr(options, path_opt)
        if val:
            gradle_args.append(f'-P{path_opt}={os.path.abspath(val)}')

    utils.append_gradle_user_home_for_worktree(False, False, gradle_args)
    return gradle_args


def main(argv, temp):
    options, _ = parse_options(argv)
    gradle_args = build_gradle_args(options, temp)
    return gradle.run_gradle(gradle_args, quiet=options.quiet)


if __name__ == '__main__':
    with utils.TempDir() as temp:
        sys.exit(main(sys.argv[1:], temp))
