#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import compiledump
import datetime
import hashlib
import historic_run
import json
import os
import shutil
import subprocess
import sys
import time

import utils
if utils.is_bot():
    import upload_benchmark_data_to_google_storage

# A collection of benchmarks that should be run on the perf bot.
EXTERNAL_BENCHMARKS = {
    'ChromeApp': {
        'targets': ['r8-full']
    },
    'ChromeAppD8Merge': {
        'targets': ['d8']
    },
    'ChromeAppPartial': {
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
    'JetNewsAppPartial': {
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
    'NewPipeKotlinApp': {
        'targets': ['r8-full']
    },
    'NowInAndroidApp': {
        'targets': ['d8', 'r8-full']
    },
    'NowInAndroidAppRelease': {
        'targets': ['d8']
    },
    'NowInAndroidAppIncremental': {
        'targets': ['d8'],
        'subBenchmarks': {
            'd8': ['Dex', 'Merge']
        }
    },
    'NowInAndroidAppNoJ$': {
        'targets': ['d8']
    },
    'NowInAndroidAppNoJ$Incremental': {
        'targets': ['d8'],
        'subBenchmarks': {
            'd8': ['Dex']
        }
    },
    'NowInAndroidAppNoJ$Release': {
        'targets': ['d8']
    },
    'NowInAndroidAppPartial': {
        'targets': ['r8-full']
    },
    'OwlApp': {
        'targets': ['r8-full']
    },
    'R8': {
        'targets': ['retrace']
    },
    'R8-250MB-DISK': {
        'targets': ['retrace']
    },
    'R8-250MB-MEM': {
        'targets': ['retrace']
    },
    'R8-250MB-PARTITION': {
        'targets': ['retrace']
    },
    'R8-500MB-DISK': {
        'targets': ['retrace']
    },
    'R8-500MB-MEM': {
        'targets': ['retrace']
    },
    'R8-500MB-PARTITION': {
        'targets': ['retrace']
    },
    'R8-750MB-DISK': {
        'targets': ['retrace']
    },
    'R8-750MB-MEM': {
        'targets': ['retrace']
    },
    'R8-750MB-PARTITION': {
        'targets': ['retrace']
    },
    'R8-1GB-DISK': {
        'targets': ['retrace']
    },
    'R8-1GB-MEM': {
        'targets': ['retrace']
    },
    'R8-1GB-PARTITION': {
        'targets': ['retrace']
    },
    'ReplyApp': {
        'targets': ['r8-full']
    },
    'TiviApp': {
        'targets': ['r8-full']
    },
    'TiviAppPartial': {
        'targets': ['r8-full']
    },
    'TuskyApp': {
        'targets': ['r8-full']
    },
}
# Additional external benchmarks that should only be run on the release perf bot.
EXTERNAL_RELEASE_BENCHMARKS = {
    'BookStoryApp': {
        'targets': ['r8-full']
    },
    'BookStoryAppPartial': {
        'targets': ['r8-full']
    },
    'FeederApp': {
        'targets': ['r8-full']
    },
    'FeederAppPartial': {
        'targets': ['r8-full']
    },
    'FossifyFileManagerApp': {
        'targets': ['r8-full']
    },
    'FossifyFileManagerAppPartial': {
        'targets': ['r8-full']
    },
    'KeePassDXApp': {
        'targets': ['r8-full']
    },
    'KeePassDXAppPartial': {
        'targets': ['r8-full']
    },
    'NewPipeKotlinAppPartial': {
        'targets': ['r8-full']
    },
    'OmniNotesApp': {
        'targets': ['r8-full']
    },
    'OmniNotesAppPartial': {
        'targets': ['r8-full']
    },
    'OpenTracksApp': {
        'targets': ['r8-full']
    },
    'OpenTracksAppPartial': {
        'targets': ['r8-full']
    },
    'ReadYouApp': {
        'targets': ['r8-full']
    },
    'ReadYouAppPartial': {
        'targets': ['r8-full']
    },
    'TuskyAppPartial': {
        'targets': ['r8-full']
    },
}
# A collection of internal benchmarks that should be run on the internal bot.
INTERNAL_BENCHMARKS = {
    'SystemUIApp': {
        'targets': ['r8-full'],
        'enable_perf_size': True
    },
    'SystemUIAppGc': {
        'targets': ['r8-full']
    },
    'SystemUIAppGcNoLib': {
        'alias': 'SystemUIAppGc',
        'targets': ['r8-full'],
        'variant': 'nolib'
    },
    'SystemUIAppPartial': {
        'targets': ['r8-full'],
        'enable_perf_size': True
    },
    'SystemUIAppContainerDex': {
        'targets': ['r8-full'],
        'enable_perf_size': True
    },
}
# A collection of internal benchmarks that should be run on the internal bot.
INTERNAL_SLOW_BENCHMARKS = {
    'AGSA': {
        'targets': ['r8-full'],
        'enable_perf_size': True
    },
    'YouTubeApp': {
        'targets': ['r8-full'],
        'enable_perf_size': True
    },
    'YouTubeAppD8Merge': {
        'targets': ['d8']
    },
}
# A collection of benchmarks that should not be run on the bots, but can be used
# for running locally.
LOCAL_BENCHMARKS = {
    'ChromeAppTreeShaking': {
        'targets': ['r8-full']
    },
    'SystemUIAppTreeShaking': {
        'targets': ['r8-full']
    },
}
# A collection of build benchmarks that are tracked on CI.
# These are run using gradle_benchmark.py.
BUILD_BENCHMARKS = {
    'assemble_tests': {
        'targets': ['build']
    },
    'assemble_tests_abi_change_LambdaFactoryTest': {
        'targets': ['build']
    },
    'assemble_tests_cleaned': {
        'targets': ['build']
    },
    'compile_java_main': {
        'targets': ['build']
    },
    'compile_java_main_abi_change_InternalOptions': {
        'targets': ['build']
    },
    'compile_java_main_cleaned': {
        'targets': ['build']
    },
    'compile_java_main_non_abi_change_InternalOptions': {
        'targets': ['build']
    },
    'configuration_only': {
        'targets': ['build']
    },
    'r8_with_relocate_deps': {
        'targets': ['build']
    },
    'r8_with_relocate_deps_cleaned': {
        'targets': ['build']
    },
}

ALL_BENCHMARKS = {}
ALL_BENCHMARKS.update(EXTERNAL_BENCHMARKS)
ALL_BENCHMARKS.update(EXTERNAL_RELEASE_BENCHMARKS)
ALL_BENCHMARKS.update(INTERNAL_BENCHMARKS)
ALL_BENCHMARKS.update(INTERNAL_SLOW_BENCHMARKS)
ALL_BENCHMARKS.update(LOCAL_BENCHMARKS)
ALL_BENCHMARKS.update(BUILD_BENCHMARKS)
BUCKET = "r8-perf-results"
# Prefix of the weekly bundles that the perf page loads directly from the
# bucket, see GetWeeklyBundleLocation().
WEEKLY_PREFIX = 'weekly'
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
    result.add_argument('--internal',
                        help='Run internal benchmarks.',
                        action='store_true',
                        default=False)
    result.add_argument('--internal-slow',
                        help='Run internal slow benchmarks.',
                        action='store_true',
                        default=False)
    result.add_argument('--iterations',
                        help='How many times run_benchmark is run.',
                        type=int,
                        default=1)
    result.add_argument('--iterations-inner',
                        help='How many iterations to run inside run_benchmark.',
                        type=int,
                        default=10)
    result.add_argument('--no-upload-benchmark-data-to-google-storage',
                        help='Skip upload to GCS.',
                        action='store_true',
                        default=False)
    result.add_argument('--outdir',
                        help='Output directory for running locally.')
    result.add_argument('--patch-ref',
                        help='The patch ref for a try run. '
                        'Should only be used from rex.py.')
    result.add_argument('--release',
                        help='Run release benchmarks.',
                        action='store_true',
                        default=bool(os.environ.get('R8_BOT_RELEASE')))
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
                        help='Use R8 hash for the run (default local build)')
    result.add_argument('--version-jar',
                        help='The r8lib.jar for the given version.')
    options, args = result.parse_known_args()
    if options.benchmark:
        options.benchmarks = options.benchmark
    elif options.internal:
        options.benchmarks = INTERNAL_BENCHMARKS.keys()
    elif options.internal_slow:
        options.benchmarks = INTERNAL_SLOW_BENCHMARKS.keys()
    elif options.release:
        options.benchmarks = sorted(
            list(EXTERNAL_BENCHMARKS.keys()) +
            list(EXTERNAL_RELEASE_BENCHMARKS.keys()))
    else:
        options.benchmarks = EXTERNAL_BENCHMARKS.keys()
    options.is_try = options.patch_ref is not None
    options.quiet = not options.verbose
    del options.benchmark
    return options, args


def Build(options):
    utils.Print('Building', quiet=options.quiet)
    target = options.target or 'r8-full'
    variant = 'lib'
    build_cmd = GetRunCmd('N/A', target, variant, options,
                          ['--iterations', '0'])
    subprocess.check_call(build_cmd)


def GetRunCmd(benchmark, target, variant, options, args, r8jar=None):
    base_cmd = [
        'tools/run_benchmark.py', '--benchmark', benchmark, '--target', target
    ]
    if variant == 'nolib':
        base_cmd.append('--nolib')
    else:
        assert variant == 'lib'
    if options.verbose:
        base_cmd.append('--verbose')
    if options.version and r8jar is not None:
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


def GetArtifactLocation(benchmark,
                        target,
                        version,
                        filename,
                        branch=None,
                        is_try=False):
    if version:
        if is_try:
            assert branch is None, (
                f"Expected branch to be None for try commit, "
                f"but got '{branch}' (version: {version}, benchmark: {benchmark})"
            )
            return f'try/{benchmark}/{target}/{version}/{filename}'
        if branch and branch != 'main':
            return f'branches/{branch}/{benchmark}/{target}/{version}/{filename}'
        return f'{benchmark}/{target}/{version}/{filename}'
    else:
        commit = utils.get_HEAD_commit()
        branch = commit.branch()
        if is_try:
            assert branch is None, (
                f"Expected branch to be None for try commit, "
                f"but got '{branch}' (commit: {commit.hash()}, benchmark: {benchmark})"
            )
            return f'try/{benchmark}/{target}/{commit.hash()}/{filename}'
        if branch and branch != 'main':
            return f'branches/{branch}/{benchmark}/{target}/{commit.hash()}/{filename}'
        return f'{benchmark}/{target}/{commit.hash()}/{filename}'


def GetGSLocation(filename, bucket=BUCKET):
    return f'gs://{bucket}/{filename}'


def ArchiveBenchmarkResult(benchmark, target, benchmark_result_json_files,
                           options, temp):
    result_file = os.path.join(temp, 'result_file')
    merged_result_json = MergeBenchmarkResultJsonFiles(
        benchmark_result_json_files)
    with open(result_file, 'w') as f:
        json.dump(merged_result_json, f)
    ArchiveOutputFile(result_file,
                      GetArtifactLocation(benchmark,
                                          target,
                                          options.version,
                                          'result.json',
                                          is_try=options.is_try),
                      outdir=options.outdir)
    return merged_result_json


# Returns the ISO week of the given timestamp as WwwYyy, e.g. W43Y26.
def GetIsoWeekFolder(timestamp):
    year, week, _ = datetime.datetime.fromtimestamp(
        timestamp, tz=datetime.timezone.utc).isocalendar()
    return f'W{week:02d}Y{year % 100:02d}'


# Returns a name for this perf.py run that is unique per build, target, and set
# of benchmarks, since a build can run perf.py more than once.
def GetWeeklyBundleRunName(benchmarks, target=None):
    run_id = (os.environ.get('SWARMING_TASK_ID') or
              os.environ.get('KOKORO_BUILD_ID') or f'local-{int(time.time())}')
    key = f"{target or ''}:{','.join(sorted(set(benchmarks)))}"
    benchmarks_hash = hashlib.sha1(key.encode('utf-8')).hexdigest()[:8]
    return f'{run_id}-{benchmarks_hash}'


# Weekly bundles hold all results of one target from one perf.py run on one
# commit:
#   weekly/TARGET/WwwYyy/HASH/RUN.json for main commits, in the ISO week of the
#       commit,
#   weekly/TARGET/WwwYyy/try/HASH/RUN.json for try commits, in the ISO week of
#       their merge base with main,
#   weekly/TARGET/releases/BRANCH/HASH/RUN.json for release branch commits.
def GetWeeklyBundleLocation(commit, target, run_name, is_try):
    if is_try:
        merge_base = subprocess.check_output(
            ['git', 'merge-base',
             commit.parent_hash(), 'origin/main']).decode('utf-8').strip()
        parent = historic_run.git_commit_from_hash(merge_base)
        week = GetIsoWeekFolder(parent.committer_timestamp())
        return (f'{WEEKLY_PREFIX}/{target}/{week}/try/{commit.hash()}/'
                f'{run_name}.json', merge_base)
    branch = commit.branch()
    if branch and branch != 'main':
        return (f'{WEEKLY_PREFIX}/{target}/releases/{branch}/{commit.hash()}/'
                f'{run_name}.json', None)
    week = GetIsoWeekFolder(commit.committer_timestamp())
    return (f'{WEEKLY_PREFIX}/{target}/{week}/{commit.hash()}/{run_name}.json',
            None)


def ArchiveWeeklyBundle(benchmark_results,
                        temp,
                        version=None,
                        is_try=False,
                        outdir=None):
    if version:
        if not compiledump.is_hash(version):
            return
        commit = historic_run.git_commit_from_hash(version)
        if commit is None:
            return
    else:
        commit = utils.get_HEAD_commit()
    base_commit_info = {
        'author': commit.author_name(),
        'hash': commit.hash(),
        'submitted': commit.committer_timestamp(),
        'title': commit.title(),
    }
    commit_version = commit.version()
    if commit_version:
        base_commit_info['version'] = commit_version
    run_time = int(time.time())
    for target, target_benchmarks in benchmark_results.items():
        if not target_benchmarks:
            continue
        run_name = GetWeeklyBundleRunName(target_benchmarks.keys(),
                                          target=target)
        location, parent_hash = GetWeeklyBundleLocation(commit, target,
                                                        run_name, is_try)
        commit_info = dict(base_commit_info)
        if parent_hash:
            commit_info['parent_hash'] = parent_hash
        bundle = {
            'commit': commit_info,
            'run': {
                'name': run_name,
                'time': run_time,
            },
            'target': target,
            # Results by benchmark, in the format of result.json.
            'benchmarks': target_benchmarks,
        }
        bundle_file = os.path.join(temp, f'weekly_bundle_{target}.json')
        with open(bundle_file, 'w') as f:
            json.dump(bundle, f)
        ArchiveOutputFile(bundle_file, location, outdir=outdir)


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
    # Results of this run by target and benchmark, for the weekly bundle.
    weekly_bundle_results = {}
    with utils.TempDir() as temp:
        if options.version:
            # Download r8.jar once instead of once per run_benchmark.py invocation.
            download_options = argparse.Namespace(no_build=True, nolib=True)
            r8jar = options.version_jar or compiledump.download_distribution(
                options.version, download_options, temp)
        else:
            r8jar = None
        for benchmark in options.benchmarks:
            benchmark_info = ALL_BENCHMARKS[benchmark]
            benchmark_to_run = benchmark_info.get('alias', benchmark)
            targets = [options.target
                      ] if options.target else benchmark_info['targets']
            variant = benchmark_info.get('variant', 'lib')
            for target in targets:
                sub_benchmarks = benchmark_info.get('subBenchmarks', {})
                sub_benchmarks_for_target = sub_benchmarks.get(target, [])

                if options.skip_if_output_exists:
                    assert len(sub_benchmarks_for_target) == 0, 'Unimplemented'
                    if options.outdir:
                        raise NotImplementedError
                    output = GetGSLocation(
                        GetArtifactLocation(benchmark,
                                            target,
                                            options.version,
                                            'result.json',
                                            is_try=options.is_try))
                    if utils.cloud_storage_exists(output):
                        print(f'Skipping run, {output} already exists.')
                        continue

                # Run benchmark.
                if sub_benchmarks_for_target:
                    benchmark_result_json_files = {}
                    for sub_benchmark in sub_benchmarks_for_target:
                        benchmark_result_json_files[sub_benchmark] = []
                else:
                    benchmark_result_json_files = []

                # Prepare out dir.
                temp_benchmark_target = os.path.join(temp, benchmark, target)
                os.makedirs(temp_benchmark_target)

                failed = False
                for i in range(options.iterations):
                    utils.Print(
                        f'Benchmarking {benchmark} ({i+1}/{options.iterations})',
                        quiet=options.quiet)
                    if sub_benchmarks_for_target:
                        benchmark_result_file = os.path.join(
                            temp_benchmark_target, f'result_{i}')
                        os.makedirs(benchmark_result_file)
                    else:
                        benchmark_result_file = os.path.join(
                            temp_benchmark_target, f'result_file_{i}')
                    iteration_cmd = GetRunCmd(
                        benchmark_to_run, target, variant, options, [
                            '--iterations',
                            str(options.iterations_inner), '--output',
                            benchmark_result_file, '--no-build'
                        ], r8jar)
                    try:
                        subprocess.check_call(iteration_cmd)
                        if sub_benchmarks_for_target:
                            for sub_benchmark in sub_benchmarks_for_target:
                                sub_benchmark_result_file = os.path.join(
                                    benchmark_result_file,
                                    benchmark + sub_benchmark)
                                benchmark_result_json_files[
                                    sub_benchmark].append(
                                        sub_benchmark_result_file)
                        else:
                            benchmark_result_json_files.append(
                                benchmark_result_file)
                    except subprocess.CalledProcessError as e:
                        failed = True
                        any_failed = True
                        break

                if failed:
                    continue

                # Merge results and write output.
                target_results = weekly_bundle_results.setdefault(target, {})
                if sub_benchmarks_for_target:
                    for sub_benchmark in sub_benchmarks_for_target:
                        target_results[
                            benchmark + sub_benchmark] = ArchiveBenchmarkResult(
                                benchmark + sub_benchmark, target,
                                benchmark_result_json_files[sub_benchmark],
                                options, temp)
                else:
                    target_results[benchmark] = ArchiveBenchmarkResult(
                        benchmark, target, benchmark_result_json_files, options,
                        temp)

                # Write metadata.
                if utils.is_bot():
                    meta_file = os.path.join(temp, "meta")
                    with open(meta_file, 'w') as f:
                        f.write("Produced by: " +
                                (os.environ.get('SWARMING_BOT_ID') or
                                 os.environ.get('KOKORO_BUILD_ID')))
                    ArchiveOutputFile(meta_file,
                                      GetArtifactLocation(
                                          benchmark,
                                          target,
                                          options.version,
                                          'meta',
                                          is_try=options.is_try),
                                      outdir=options.outdir)

        # The weekly bundles are a new pipeline that runs next to the existing
        # one, so failing to write them must not fail the run.
        if weekly_bundle_results:
            try:
                ArchiveWeeklyBundle(weekly_bundle_results,
                                    temp,
                                    version=options.version,
                                    is_try=options.is_try,
                                    outdir=options.outdir)
            except Exception as e:
                print(f'Warning: failed to write weekly bundle: {e}',
                      file=sys.stderr)

    # Only upload benchmark data when running on the perf bot.
    if utils.is_bot(
    ) and not options.no_upload_benchmark_data_to_google_storage:
        upload_benchmark_data_to_google_storage.run_bucket()

    if any_failed:
        return 1


if __name__ == '__main__':
    sys.exit(main())
