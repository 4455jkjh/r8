#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import compiledump
import json
import os
import shutil
import subprocess
import sys

import utils
if utils.is_bot():
    import upload_benchmark_data_to_google_storage

BENCHMARKS = {
    'ChromeApp': {
        'targets': ['r8-full']
    },
    'CraneApp': {
        'targets': ['r8-full']
    },
    'HelloWorld': {
        'targets': ['d8']
    },
    'HelloWorldNoLib': {
        'targets': ['d8']
    },
    'HelloWorldCf': {
        'targets': ['d8']
    },
    'HelloWorldCfNoLib': {
        'targets': ['d8']
    },
    'JetLaggedApp': {
        'targets': ['r8-full']
    },
    'JetNewsApp': {
        'targets': ['r8-full']
    },
    'JetCasterApp': {
        'targets': ['r8-full']
    },
    'JetChatApp': {
        'targets': ['r8-full']
    },
    'JetSnackApp': {
        'targets': ['r8-full']
    },
    'NowInAndroidApp': {
        'targets': ['r8-full']
    },
    'OwlApp': {
        'targets': ['r8-full']
    },
    'R8': {
        'targets': ['retrace']
    },
    'ReplyApp': {
        'targets': ['r8-full']
    },
    'TiviApp': {
        'targets': ['r8-full']
    },
}
BUCKET = "r8-perf-results"
SAMPLE_BENCHMARK_RESULT_JSON = {
    'benchmark_name': '<benchmark_name>',
    'results': [{
        'code_size': 0,
        'runtime': 0
    }]
}


# Result structure on cloud storage
# gs://bucket/benchmark_results/APP/TARGET/GIT_HASH/result.json
#                                                   meta
# where results simply contains the result lines and
# meta contains information about the execution (machine)
def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--benchmark',
                        help='Specific benchmark(s) to measure.',
                        action='append')
    result.add_argument('--iterations',
                        help='How many times run_benchmark is run.',
                        type=int,
                        default=1)
    result.add_argument('--iterations-inner',
                        help='How many iterations to run inside run_benchmark.',
                        type=int,
                        default=10)
    result.add_argument('--outdir',
                        help='Output directory for running locally.')
    result.add_argument('--skip-if-output-exists',
                        help='Skip if output exists.',
                        action='store_true',
                        default=False)
    result.add_argument(
        '--target',
        help='Specific target to run on.',
        choices=['d8', 'r8-full', 'r8-force', 'r8-compat', 'retrace'])
    result.add_argument('--verbose',
                        help='To enable verbose logging.',
                        action='store_true',
                        default=False)
    result.add_argument('--version',
                        '-v',
                        help='Use R8 hash for the run (default local build)',
                        default=None)
    options, args = result.parse_known_args()
    options.benchmarks = options.benchmark or BENCHMARKS.keys()
    options.quiet = not options.verbose
    del options.benchmark
    return options, args


def Build(options):
    utils.Print('Building', quiet=options.quiet)
    target = options.target or 'r8-full'
    build_cmd = GetRunCmd('N/A', target, options, ['--iterations', '0'])
    subprocess.check_call(build_cmd)


def GetRunCmd(benchmark, target, options, args):
    base_cmd = [
        'tools/run_benchmark.py', '--benchmark', benchmark, '--target', target
    ]
    if options.verbose:
        base_cmd.append('--verbose')
    if options.version:
        base_cmd.extend(
            ['--version', options.version, '--version-jar', r8jar, '--nolib'])
    return base_cmd + args


def MergeBenchmarkResultJsonFiles(benchmark_result_json_files):
    merged_benchmark_result_json = None
    for benchmark_result_json_file in benchmark_result_json_files:
        benchmark_result_json = ParseBenchmarkResultJsonFile(
            benchmark_result_json_file)
        if merged_benchmark_result_json is None:
            merged_benchmark_result_json = benchmark_result_json
        else:
            MergeBenchmarkResultJsonFile(merged_benchmark_result_json,
                                         benchmark_result_json)
    return merged_benchmark_result_json


def MergeBenchmarkResultJsonFile(merged_benchmark_result_json,
                                 benchmark_result_json):
    assert benchmark_result_json.keys() == SAMPLE_BENCHMARK_RESULT_JSON.keys()
    assert merged_benchmark_result_json[
        'benchmark_name'] == benchmark_result_json['benchmark_name']
    merged_benchmark_result_json['results'].extend(
        benchmark_result_json['results'])


def ParseBenchmarkResultJsonFile(result_json_file):
    with open(result_json_file, 'r') as f:
        lines = f.readlines()
        return json.loads(''.join(lines))


def GetArtifactLocation(benchmark, target, version, filename):
    version_or_head = version or utils.get_HEAD_sha1()
    return f'{benchmark}/{target}/{version_or_head}/{filename}'


def GetGSLocation(filename, bucket=BUCKET):
    return f'gs://{bucket}/{filename}'


def ArchiveOutputFile(file, dest, bucket=BUCKET, header=None, outdir=None):
    if outdir:
        dest_in_outdir = os.path.join(outdir, dest)
        os.makedirs(os.path.dirname(dest_in_outdir), exist_ok=True)
        shutil.copyfile(file, dest_in_outdir)
    else:
        utils.upload_file_to_cloud_storage(file,
                                           GetGSLocation(dest, bucket=bucket),
                                           header=header)


# Usage with historic_run.py:
# ./tools/historic_run.py
#     --cmd "perf.py --skip-if-output-exists --version"
#     --timeout -1
#     --top 3373fd18453835bf49bff9f02523a507a2ebf317
#     --bottom 7486f01e0622cb5935b77a92b59ddf1ca8dbd2e2
def main():
    options, args = ParseOptions()
    Build(options)
    any_failed = False
    with utils.TempDir() as temp:
        if options.version:
            # Download r8.jar once instead of once per run_benchmark.py invocation.
            download_options = argparse.Namespace(no_build=True, nolib=True)
            r8jar = compiledump.download_distribution(options.version,
                                                      download_options, temp)
        for benchmark in options.benchmarks:
            targets = [options.target
                      ] if options.target else BENCHMARKS[benchmark]['targets']
            for target in targets:
                if options.skip_if_output_exists:
                    if options.outdir:
                        raise NotImplementedError
                    output = GetGSLocation(
                        GetArtifactLocation(benchmark, target, options.version,
                                            'result.json'))
                    if utils.cloud_storage_exists(output):
                        print(f'Skipping run, {output} already exists.')
                        continue

                # Run benchmark.
                benchmark_result_json_files = []
                failed = False
                for i in range(options.iterations):
                    utils.Print(
                        f'Benchmarking {benchmark} ({i+1}/{options.iterations})',
                        quiet=options.quiet)
                    benchhmark_result_file = os.path.join(
                        temp, f'result_file_{i}')
                    iteration_cmd = GetRunCmd(benchmark, target, options, [
                        '--iterations',
                        str(options.iterations_inner), '--output',
                        benchhmark_result_file, '--no-build'
                    ])
                    try:
                        subprocess.check_call(iteration_cmd)
                        benchmark_result_json_files.append(
                            benchhmark_result_file)
                    except subprocess.CalledProcessError as e:
                        failed = True
                        any_failed = True
                        break

                if failed:
                    continue

                # Merge results and write output.
                result_file = os.path.join(temp, 'result_file')
                with open(result_file, 'w') as f:
                    json.dump(
                        MergeBenchmarkResultJsonFiles(
                            benchmark_result_json_files), f)
                ArchiveOutputFile(result_file,
                                  GetArtifactLocation(benchmark, target,
                                                      options.version,
                                                      'result.json'),
                                  outdir=options.outdir)

                # Write metadata.
                if utils.is_bot():
                    meta_file = os.path.join(temp, "meta")
                    with open(meta_file, 'w') as f:
                        f.write("Produced by: " +
                                os.environ.get('SWARMING_BOT_ID'))
                    ArchiveOutputFile(meta_file,
                                      GetArtifactLocation(
                                          benchmark, target, options.version,
                                          'meta'),
                                      outdir=options.outdir)

    if utils.is_bot():
        upload_benchmark_data_to_google_storage.run()

    if any_failed:
        return 1


if __name__ == '__main__':
    sys.exit(main())
