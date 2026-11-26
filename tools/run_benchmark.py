#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import sys

import compiledump
import gradle
import run_stable_benchmark
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
        choices=['d8', 'r8-full', 'retrace'])
    result.add_argument(
        '--runner',
        help='The benchmark runner to use. The stable runner records HEAD vs '
        'HEAD~1 compile timings in the same JVM (default: default)',
        default='default',
        choices=['default', 'stable'])
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
    result.add_argument('--dump-trace-to-file',
                        '--dump_trace_to_file',
                        help='Dump perfetto trace to the given file')
    result.add_argument('--trace-lite',
                        '--trace_lite',
                        help='Enable lite perfetto tracing mode',
                        default=False,
                        action='store_true')
    result.add_argument(
        '--trace-min-value-ms',
        '--trace_min_value_ms',
        help='Minimum slice duration in ms to record in perfetto trace',
        type=int,
        default=None)
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
    result.add_argument('--quartets',
                        help='Number of ABBA quartets per JVM (stable runner)',
                        type=int)
    result.add_argument('--warmup',
                        '-w',
                        help='Number of warmup iterations to run',
                        type=int)
    options, args = result.parse_known_args(argv)
    options.quiet = not options.verbose
    if options.runner == 'stable':
        unsupported_stable_flags = (
            ('--version-jar', options.version_jar),
            ('--nolib', options.nolib),
            ('--debug-agent', options.debug_agent),
            ('--dump-jfr-to-file', options.dump_jfr_to_file),
            ('--dump-gc-log-to-file', options.dump_gc_log_to_file),
            ('--dump-heap-to-directory', options.dump_heap_to_directory),
            ('--dump-keep-radius-to-directory',
             options.dump_keep_radius_to_directory),
            ('--dump-keep-radius-html-to-directory',
             options.dump_keep_radius_html_to_directory),
            ('--dump-trace-to-directory', options.dump_trace_to_directory),
            ('--dump-trace-to-file', options.dump_trace_to_file),
            ('--trace-lite', options.trace_lite),
            ('--trace-min-value-ms', options.trace_min_value_ms is not None),
            ('--build-output', options.build_output),
        )
        for flag, value in unsupported_stable_flags:
            if value:
                result.error(f'{flag} is not supported with --runner stable')
        if options.iterations is None or options.iterations < 2 or options.iterations % 2 != 0:
            result.error('--runner stable requires an even --iterations >= 2')
        if options.warmup is None or options.warmup < 0:
            result.error('--runner stable requires --warmup >= 0')
        if options.quartets is None or options.quartets < 1:
            result.error('--runner stable requires --quartets >= 1')
    elif options.quartets is not None:
        result.error('--quartets is only supported with --runner stable')
    # We must download the non-lib distribution when running with a specific
    # version, since BenchmarkMainEntryRunner is using R8 internals, except for
    # the stable runner, which builds matching r8lib + rewritten test JARs
    # locally.
    # TODO(b/346477461): Look into removing this limitation.
    assert (options.version is None or options.nolib or
            options.runner == 'stable')
    return options, args


def is_internal_benchmark(benchmark):
    return any(name in benchmark
               for name in ('AGSA', 'ChromeInternal', 'SystemUI', 'YouTube'))


def build_gradle_args(options, temp):
    if options.runner == 'stable':
        # With --version the head classpath is prebuilt (-Phead_classpath), so
        # the non-lib build suffices for launching the runner.
        base_task = (':test:runBenchmarkNoLib'
                     if options.version else ':test:runBenchmark')
    elif options.version or options.version_jar:
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
    if options.runner == 'stable':
        gradle_args.append('-Prunner=stable')
        gradle_args.extend(
            run_stable_benchmark.resolve_stable_gradle_args(options))
    elif options.version or options.version_jar:
        r8jar = options.version_jar or compiledump.download_distribution(
            options.version, options, temp)
        gradle_args.append(f'-Pr8_jar={os.path.abspath(r8jar)}')

    if not is_internal_benchmark(options.benchmark):
        gradle_args.append('-Pno_internal')

    for flag in ('enable_assertions', 'debug_agent', 'trace_lite', 'verbose'):
        if getattr(options, flag):
            gradle_args.append(f'-P{flag}')

    for prop, val in (('benchmark_iterations',
                       options.iterations), ('warmup', options.warmup),
                      ('quartets', options.quartets), ('heap_size',
                                                       options.heap_size),
                      ('trace_min_value_ms', options.trace_min_value_ms)):
        if val is not None:
            gradle_args.append(f'-P{prop}={val}')

    for path_opt in ('output', 'build_output', 'temp', 'dump_jfr_to_file',
                     'dump_gc_log_to_file', 'dump_heap_to_directory',
                     'dump_keep_radius_to_directory',
                     'dump_keep_radius_html_to_directory',
                     'dump_trace_to_directory', 'dump_trace_to_file'):
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
