#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from datetime import datetime, timezone
from glob import glob
import argparse
import copy
import os
import sys
import threading
import time
import xml.etree.ElementTree as ET

import archive
import gradle
import nest_data
import perfetto_utils
from sanitize_libraries import sanitize_libraries, sanitize_libraries_in_pgconf
import thread_utils
from thread_utils import print_thread
import toolhelper
import utils

TYPES = ['dex', 'deploy', 'proguarded']
APPS = ['nest']
COMPILERS = ['d8', 'r8']
COMPILER_BUILDS = ['full', 'lib']

# We use this magic exit code to signal that the program OOM'ed
OOM_EXIT_CODE = 42
# According to Popen.returncode doc:
# A negative value -N indicates that the child was terminated by signal N.
TIMEOUT_KILL_CODE = -9


def parse_options(argv):
    result = argparse.ArgumentParser()
    result.add_argument('--compiler',
                        help='The compiler to use',
                        choices=COMPILERS)
    result.add_argument('--compiler-build',
                        help='Compiler build to use',
                        choices=COMPILER_BUILDS,
                        default='lib')
    result.add_argument('--no-fail-fast',
                        help='Whether run_on_app.py should report all failures '
                        'and not just the first one',
                        default=False,
                        action='store_true')
    result.add_argument('--hash', help='The version of D8/R8 to use')
    result.add_argument('--app', help='What app to run on', choices=APPS)
    result.add_argument('--run-all',
                        help='Compile all possible combinations',
                        default=False,
                        action='store_true')
    result.add_argument('--expect-oom',
                        help='Expect that compilation will fail with an OOM',
                        default=False,
                        action='store_true')
    result.add_argument('--type',
                        help='Default for R8: deploy, for D8: proguarded',
                        choices=TYPES)
    result.add_argument('--out',
                        help='Where to place the output',
                        default=utils.BUILD)
    result.add_argument('--no-build',
                        help='Run without building first',
                        default=False,
                        action='store_true')
    result.add_argument('--max-memory',
                        help='The maximum memory in MB to run with',
                        type=int)
    result.add_argument('--find-min-xmx',
                        help='Find the minimum amount of memory we can run in',
                        default=False,
                        action='store_true')
    result.add_argument('--find-min-xmx-min-memory',
                        help='Setting the minimum memory baseline to run in',
                        type=int)
    result.add_argument('--find-min-xmx-max-memory',
                        help='Setting the maximum memory baseline to run in',
                        type=int)
    result.add_argument('--find-min-xmx-range-size',
                        help='Setting the size of the acceptable memory range',
                        type=int,
                        default=32)
    result.add_argument('--no-extra-pgconf',
                        '--no_extra_pgconf',
                        help='Build without the following extra rules: ' +
                        '-printconfiguration, -printmapping, -printseeds, ' +
                        '-printusage',
                        default=False,
                        action='store_true')
    result.add_argument('--timeout',
                        type=int,
                        default=0,
                        help='Set timeout instead of waiting for OOM.')
    result.add_argument('--ignore-java-version',
                        help='Do not check java version',
                        default=False,
                        action='store_true')
    result.add_argument(
        '--no-libraries',
        help='Do not pass in libraries, even if they exist in conf',
        default=False,
        action='store_true')
    result.add_argument(
        '--disable-assertions',
        '--disable_assertions',
        '-da',
        help='Disable Java assertions when running the compiler '
        '(default enabled)',
        default=False,
        action='store_true')
    result.add_argument('--debug-agent',
                        help='Run with debug agent.',
                        default=False,
                        action='store_true')
    result.add_argument('--version', help='The version of the app to run')
    result.add_argument('-k', help='Override the default ProGuard keep rules')
    result.add_argument('--compiler-flags',
                        help='Additional option(s) for the compiler. ' +
                        'If passing several options use a quoted string.')
    result.add_argument('--r8-flags',
                        help='Additional option(s) for the compiler. ' +
                        'Same as --compiler-flags, keeping it for backward'
                        ' compatibility. ' +
                        'If passing several options use a quoted string.')
    result.add_argument('--track-memory-to-file',
                        help='Track how much memory the jvm is using while ' +
                        ' compiling. Output to the specified file.')
    result.add_argument('--profile',
                        help='Profile R8 run.',
                        default=False,
                        action='store_true')
    result.add_argument(
        '--dump-args-file',
        help='Dump a file with the arguments for the specified ' +
        'configuration. For use as a @<file> argument to perform ' + 'the run.')
    result.add_argument('--print-runtimeraw',
                        metavar='BENCHMARKNAME',
                        help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                        ' <elapsed> ms\' at the end where <elapsed> is' +
                        ' the elapsed time in milliseconds.')
    result.add_argument('--print-memoryuse',
                        metavar='BENCHMARKNAME',
                        help='Print the line \'<BENCHMARKNAME>(MemoryUse):' +
                        ' <mem>\' at the end where <mem> is the peak' +
                        ' peak resident set size (VmHWM) in bytes.')
    result.add_argument('--print-dexsegments',
                        metavar='BENCHMARKNAME',
                        help='Print the sizes of individual dex segments as ' +
                        '\'<BENCHMARKNAME>-<segment>(CodeSize): <bytes>\'')
    result.add_argument(
        '--track-time-in-memory',
        help='Plot the times taken from memory starting point to '
        'end-point with defined memory increment',
        default=False,
        action='store_true')
    result.add_argument('--track-time-in-memory-max',
                        help='Setting the maximum memory baseline to run in',
                        type=int)
    result.add_argument('--track-time-in-memory-min',
                        help='Setting the minimum memory baseline to run in',
                        type=int)
    result.add_argument('--track-time-in-memory-increment',
                        help='Setting the increment',
                        type=int,
                        default=32)
    result.add_argument('--cpu-list',
                        help='Run under \'taskset\' with these CPUs. See '
                        'the \'taskset\' -c option for the format')
    result.add_argument('--quiet',
                        help='Disable compiler logging',
                        default=False,
                        action='store_true')
    result.add_argument('--trace-lite',
                        '--trace_lite',
                        help='Enable lite Perfetto tracing (>= 2ms slices).',
                        default=False,
                        action='store_true')
    result.add_argument('--upload-traces',
                        '--upload_traces',
                        help='Upload Perfetto traces to GCS.',
                        default=False,
                        action='store_true')
    result.add_argument('--workers',
                        help='Number of workers to use',
                        default=1,
                        type=int)
    (options, args) = result.parse_known_args(argv)
    assert not options.hash or options.no_build, (
        'Argument --no-build is required when using --hash')
    assert not options.hash or options.compiler_build == 'full', (
        'Compiler build lib not yet supported with --hash')
    return (options, args)


# Most apps have -printmapping, -printseeds, -printusage and
# -printconfiguration in the Proguard configuration. However we don't
# want to write these files in the locations specified.
# Instead generate an auxiliary Proguard configuration placing these
# output files together with the dex output.
def generate_additional_proguard_configuration(temp, outdir):
    name = "output.config"
    with open(os.path.join(temp, name), 'w') as f:
        f.write('-printmapping ' + os.path.join(outdir, 'proguard.map') + "\n")
        f.write('-printseeds ' + os.path.join(outdir, 'proguard.seeds') + "\n")
        f.write('-printusage ' + os.path.join(outdir, 'proguard.usage') + "\n")
        f.write('-printconfiguration ' +
                os.path.join(outdir, 'proguard.config') + "\n")
        return os.path.abspath(f.name)


# Please add bug number for disabled permutations and please explicitly
# do Bug: #BUG in the commit message of disabling to ensure re-enabling
DISABLED_PERMUTATIONS = [
    # (app, version, type), e.g., ('gmail', '180826.15', 'deploy')
]


def get_permutations():
    data_providers = {
        'nest': nest_data,
    }
    # Check to ensure that we add all variants here.
    assert len(APPS) == len(data_providers)
    for app, data in data_providers.items():
        for version in data.VERSIONS:
            for type in data.VERSIONS[version]:
                if (app, version, type) not in DISABLED_PERMUTATIONS:
                    # Only run with R8 lib to reduce cycle times.
                    for use_r8lib in [True]:
                        yield app, version, type, use_r8lib


def run_all(options, args):
    # Build first so that each job won't.
    if should_build(options):
        gradle.run_gradle([utils.GRADLE_TASK_R8LIB])
        options.no_build = True
    assert not should_build(options)

    # Args will be destroyed
    assert len(args) == 0
    jobs = []
    for name, version, type, use_r8lib in get_permutations():
        compiler = 'r8' if type == 'deploy' else 'd8'
        compiler_build = 'lib' if use_r8lib else 'full'
        fixed_options = copy.copy(options)
        fixed_options.app = name
        fixed_options.version = version
        fixed_options.compiler = compiler
        fixed_options.compiler_build = compiler_build
        fixed_options.type = type
        jobs.append(
            create_job(compiler, compiler_build, name, fixed_options, type,
                       version))
    exit_code = thread_utils.run_in_parallel(
        jobs,
        number_of_workers=options.workers,
        stop_on_first_failure=not options.no_fail_fast)
    return exit_code


def create_job(compiler, compiler_build, name, options, type, version):
    return lambda worker_id: run_job(compiler, compiler_build, name, options,
                                     type, version, worker_id)


def run_job(compiler, compiler_build, name, options, type, version, worker_id):
    print_thread(
        'Executing %s/%s with %s %s %s' %
        (compiler, compiler_build, name, version, type), worker_id)
    if worker_id is not None:
        options.out = os.path.join(options.out, str(worker_id))
        os.makedirs(options.out, exist_ok=True)
    exit_code = run_with_options(options, [], worker_id=worker_id)
    if exit_code:
        print_thread(
            'Failed %s %s %s with %s/%s' %
            (name, version, type, compiler, compiler_build), worker_id)
    return exit_code


def find_min_xmx(options, args):
    # Args will be destroyed
    assert len(args) == 0
    # If we can run in 128 MB then we are good (which we can for small examples
    # or D8 on medium sized examples)
    if options.find_min_xmx_min_memory:
        not_working = options.find_min_xmx_min_memory
    elif options.compiler == 'd8':
        not_working = 128
    else:
        not_working = 1024
    if options.find_min_xmx_max_memory:
        working = options.find_min_xmx_max_memory
    else:
        working = 1024 * 8
    exit_code = 0
    range = int(options.find_min_xmx_range_size)
    while working - not_working > range:
        next_candidate = int(working - ((working - not_working) / 2))
        print('working: %s, non_working: %s, next_candidate: %s' %
              (working, not_working, next_candidate))
        extra_args = ['-Xmx%sM' % next_candidate]
        t0 = time.time()
        exit_code = run_with_options(options, [], extra_args)
        t1 = time.time()
        print('Running took: %s ms' % (1000.0 * (t1 - t0)))
        if exit_code != 0:
            if exit_code not in [OOM_EXIT_CODE, TIMEOUT_KILL_CODE]:
                print('Non OOM/Timeout error executing, exiting')
                return 2
        if exit_code == 0:
            working = next_candidate
        elif exit_code == TIMEOUT_KILL_CODE:
            print('Timeout. Continue to the next candidate.')
            not_working = next_candidate
        else:
            assert exit_code == OOM_EXIT_CODE
            not_working = next_candidate

    assert working - not_working <= range
    print('Found range: %s - %s' % (not_working, working))
    return 0


def track_time_in_memory(options, args):
    # Args will be destroyed
    assert len(args) == 0
    if not options.track_time_in_memory_min:
        raise Exception(
            'You have to specify --track_time_in_memory_min when running with '
            '--track-time-in-memory')
    if not options.track_time_in_memory_max:
        raise Exception(
            'You have to specify --track_time_in_memory_max when running with '
            '--track-time-in-memory')
    if not options.track_time_in_memory_increment:
        raise Exception(
            'You have to specify --track_time_in_memory_increment when running '
            'with --track-time-in-memory')
    current = options.track_time_in_memory_min
    print('Memory (KB)\tTime (ms)')
    with utils.TempDir() as temp:
        stdout = os.path.join(temp, 'stdout')
        stdout_fd = open(stdout, 'w')
        while current <= options.track_time_in_memory_max:
            extra_args = ['-Xmx%sM' % current]
            t0 = time.time()
            exit_code = run_with_options(options, [],
                                         extra_args,
                                         stdout_fd,
                                         quiet=True)
            t1 = time.time()
            total = (1000.0 * (t1 - t0)) if exit_code == 0 else -1
            print('%s\t%s' % (current, total))
            current += options.track_time_in_memory_increment

    return 0


class _TraceRecorder(object):

    def __init__(self, gcs_prefix):
        self.gcs_prefix = gcs_prefix
        self._lock = threading.Lock()
        self.entries = []

    def record(self, app, slug, elapsed_sec, passed, failure_message,
               perfetto_url):
        with self._lock:
            self.entries.append({
                'app': app,
                'slug': slug,
                'time': elapsed_sec,
                'passed': passed,
                'failure_message': failure_message,
                'perfetto_url': perfetto_url,
            })


def _get_kokoro_gcs_prefix():
    job_name = os.environ.get('KOKORO_JOB_NAME', '').lower()
    build_id = (os.environ.get('KOKORO_BUILD_ID') or
                datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    if 'presubmit' in job_name:
        return f'kokoro/presubmit/{build_id}'
    if 'release' in job_name:
        return f'kokoro/release/{build_id}'
    return f'kokoro/ci/{build_id}'


def _write_junit_xml(entries):
    if not entries:
        return
    try:
        sorted_entries = sorted(entries, key=lambda e: e['slug'])
        failures = sum(0 if e['passed'] else 1 for e in sorted_entries)
        total_time = sum(e['time'] for e in sorted_entries)
        suite = ET.Element(
            'testsuite', {
                'name': 'run_on_app',
                'tests': str(len(sorted_entries)),
                'failures': str(failures),
                'errors': '0',
                'time': f'{total_time:.3f}',
            })
        for entry in sorted_entries:
            case = ET.SubElement(
                suite, 'testcase', {
                    'classname': f"run_on_app.{entry['app']}",
                    'name': entry['slug'],
                    'time': f"{entry['time']:.3f}",
                })
            if entry.get('perfetto_url'):
                props = ET.SubElement(case, 'properties')
                ET.SubElement(props, 'property', {
                    'name': 'perfetto_trace',
                    'value': entry['perfetto_url'],
                })
            if not entry['passed']:
                ET.SubElement(
                    case, 'failure', {
                        'message':
                            entry.get('failure_message')
                            or 'Compilation failed',
                    })
        out_dir = os.path.join(utils.BUILD, 'test-results', 'run_on_app')
        os.makedirs(out_dir, exist_ok=True)
        out_file = os.path.join(out_dir, 'TEST-run_on_app.xml')
        ET.ElementTree(suite).write(out_file,
                                    encoding='utf-8',
                                    xml_declaration=True)
    except Exception as e:
        print(f'WARNING: Failed to write JUnit XML for Perfetto traces: {e}',
              flush=True)


def _print_perfetto_summary(entries):
    traced = [
        e for e in sorted(entries, key=lambda e: e['slug'])
        if e.get('perfetto_url')
    ]
    if not traced:
        return
    print('', flush=True)
    print('Perfetto traces:', flush=True)
    for entry in traced:
        print(f"  {entry['slug']}: {entry['perfetto_url']}", flush=True)


def main(argv):
    (options, args) = parse_options(argv)
    if options.track_memory_to_file or options.print_memoryuse:
        raise Exception(
            'The --track-memory-to-file and --print-memoryuse options have '
            'been marked as unused and will be deleted unless somebody is '
            'against it.')
    if options.expect_oom and not options.max_memory:
        raise Exception(
            'You should only use --expect-oom if also specifying --max-memory')
    if options.expect_oom and options.timeout:
        raise Exception(
            'You should not use --timeout when also specifying --expect-oom')
    if options.find_min_xmx and options.track_time_in_memory:
        raise Exception(
            'You cannot both find the min xmx and track time at the same time')
    if options.find_min_xmx:
        return find_min_xmx(options, args)
    if options.track_time_in_memory:
        return track_time_in_memory(options, args)
    trace_recorder = (_TraceRecorder(_get_kokoro_gcs_prefix()) if
                      (options.trace_lite or options.upload_traces) else None)
    options._trace_recorder = trace_recorder
    if options.run_all:
        exit_code = run_all(options, args)
    else:
        exit_code = run_with_options(options, args, quiet=options.quiet)
        if options.expect_oom:
            exit_code = 0 if exit_code == OOM_EXIT_CODE else 1
            if trace_recorder and trace_recorder.entries:
                trace_recorder.entries[-1]['passed'] = (exit_code == 0)
                if exit_code == 0:
                    trace_recorder.entries[-1]['failure_message'] = None
                elif not trace_recorder.entries[-1]['failure_message']:
                    trace_recorder.entries[-1][
                        'failure_message'] = 'Expected OOM did not occur'
    if trace_recorder:
        if options.upload_traces:
            _write_junit_xml(trace_recorder.entries)
        _print_perfetto_summary(trace_recorder.entries)
    return exit_code


def get_version_and_data(options):
    if options.app == 'nest':
        version = options.version or '20180926'
        data = nest_data
    else:
        raise Exception("You need to specify '--app={}'".format('|'.join(APPS)))
    return version, data


def get_type(options):
    if not options.type:
        return 'deploy' if options.compiler == 'r8' else 'proguarded'
    return options.type


def has_injars_and_libraryjars(pgconfs):
    # Check if there are -injars and -libraryjars in the configuration.
    has_injars = False
    has_libraryjars = False
    for pgconf in pgconfs:
        pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
        with open(pgconf) as pgconf_file:
            for line in pgconf_file:
                trimmed = line.strip()
                if trimmed.startswith('-injars'):
                    has_injars = True
                elif trimmed.startswith('-libraryjars'):
                    has_libraryjars = True
                if has_injars and has_libraryjars:
                    return True
    return False


def check_no_injars_and_no_libraryjars(pgconfs):
    # Ensure that there are no -injars or -libraryjars in the configuration.
    for pgconf in pgconfs:
        pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
        with open(pgconf) as pgconf_file:
            for line in pgconf_file:
                trimmed = line.strip()
                if trimmed.startswith('-injars'):
                    raise Exception("Unexpected -injars found in " + pgconf)
                elif trimmed.startswith('-libraryjars'):
                    raise Exception("Unexpected -libraryjars found in " +
                                    pgconf)


def should_build(options):
    return not options.no_build


def run_with_options(options,
                     args,
                     extra_args=None,
                     stdout=None,
                     quiet=False,
                     worker_id=None):
    if extra_args is None:
        extra_args = []
    app_provided_pg_conf = False
    # todo(121018500): remove when memory is under control
    if not any('-Xmx' in arg for arg in extra_args):
        if options.max_memory:
            extra_args.append('-Xmx%sM' % options.max_memory)
        else:
            extra_args.append('-Xmx8G')
    if not options.ignore_java_version:
        utils.check_java_version()

    if not options.disable_assertions:
        extra_args.append('-Dcom.android.tools.r8.enableTestAssertions=1')

    outdir = options.out
    (version_id, data) = get_version_and_data(options)

    if options.compiler not in COMPILERS:
        raise Exception("You need to specify '--compiler={}'".format(
            '|'.join(COMPILERS)))

    if options.compiler_build not in COMPILER_BUILDS:
        raise Exception("You need to specify '--compiler-build={}'".format(
            '|'.join(COMPILER_BUILDS)))

    if version_id not in data.VERSIONS.keys():
        print('No version {} for application {}'.format(version_id,
                                                        options.app))
        print('Valid versions are {}'.format(data.VERSIONS.keys()))
        return 1

    version = data.VERSIONS[version_id]

    type = get_type(options)

    if type not in version:
        print('No type {} for version {}'.format(type, version))
        print('Valid types are {}'.format(version.keys()))
        return 1
    values = version[type]

    args.extend(['--output', outdir])
    if 'min-api' in values:
        args.extend(['--min-api', values['min-api']])

    if 'main-dex-list' in values:
        args.extend(['--main-dex-list', values['main-dex-list']])

    inputs = values['inputs']
    libraries = values['libraries'] if 'libraries' in values else []

    if options.compiler == 'r8':
        if 'pgconf' in values and not options.k:
            sanitized_lib_path = os.path.join(os.path.abspath(outdir),
                                              'sanitized_lib.jar')
            if has_injars_and_libraryjars(values['pgconf']):
                sanitized_pgconf_path = os.path.join(os.path.abspath(outdir),
                                                     'sanitized.config')
                sanitize_libraries_in_pgconf(sanitized_lib_path,
                                             sanitized_pgconf_path,
                                             values['pgconf'])
                libraries = [sanitized_lib_path]
                args.extend(['--pg-conf', sanitized_pgconf_path])
                inputs = []
            else:
                # -injars without -libraryjars or vice versa is not supported.
                check_no_injars_and_no_libraryjars(values['pgconf'])
                for pgconf in values['pgconf']:
                    args.extend(['--pg-conf', pgconf])
                if 'sanitize_libraries' in values and values[
                        'sanitize_libraries']:
                    sanitize_libraries(sanitized_lib_path, values['libraries'],
                                       values['inputs'])
                    libraries = [sanitized_lib_path]
            app_provided_pg_conf = True
            if 'pgconf_extra' in values:
                extra_conf = os.path.join(os.path.abspath(outdir),
                                          'pgconf_extra')
                with open(extra_conf, 'w') as extra_f:
                    extra_f.write(values['pgconf_extra'])
                args.extend(['--pg-conf', extra_conf])
        if options.k:
            args.extend(['--pg-conf', options.k])
        if 'maindexrules' in values:
            for rules in values['maindexrules']:
                args.extend(['--main-dex-rules', rules])
        if 'allow-type-errors' in values:
            extra_args.append('-Dcom.android.tools.r8.allowTypeErrors=1')
        extra_args.append(
            '-Dcom.android.tools.r8.disallowClassInlinerGracefulExit=1')
        if 'system-properties' in values:
            for system_property in values['system-properties']:
                extra_args.append(system_property)

    if options.debug_agent:
        if not options.compiler_build == 'full':
            print(
                'WARNING: Running debugging agent on r8lib is questionable...')
        extra_args.append(
            '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005'
        )

    if not options.no_libraries:
        for lib in libraries:
            args.extend(['--lib', lib])

    if not outdir.endswith('.zip') and not outdir.endswith('.jar') \
        and not os.path.exists(outdir):
        os.makedirs(outdir)

    if options.hash:
        # Download r8-<hash>.jar from
        # https://storage.googleapis.com/r8-releases/raw/<hash>/.
        download_path = archive.get_upload_destination(options.hash, 'r8.jar',
                                                       True)
        assert utils.file_exists_on_cloud_storage(download_path), (
            'Could not find r8.jar file from provided hash: %s' % options.hash)
        destination = os.path.join(utils.LIBS, 'r8-' + options.hash + '.jar')
        utils.download_file_from_cloud_storage(download_path,
                                               destination,
                                               printcmd=not quiet,
                                               quiet=quiet)

    # Additional flags for the compiler from the configuration file.
    if 'flags' in values:
        args.extend(values['flags'].split(' '))
    if options.compiler == 'r8':
        if 'r8-flags' in values:
            args.extend(values['r8-flags'].split(' '))

    # Additional flags for the compiler from the command line.
    if options.compiler_flags:
        args.extend(options.compiler_flags.split(' '))
    if options.r8_flags:
        args.extend(options.r8_flags.split(' '))

    # Feature jars.
    features = values['features'] if 'features' in values else []
    for i, feature in enumerate(features, start=1):
        feature_out = os.path.join(outdir, 'feature-%d.zip' % i)
        for feature_jar in feature['inputs']:
            args.extend(['--feature', feature_jar, feature_out])

    args.extend(inputs)

    t0 = None
    if options.dump_args_file:
        with open(options.dump_args_file, 'w') as args_file:
            args_file.writelines([arg + os.linesep for arg in args])
    else:
        slug = f'{options.app}_{version_id}_{type}_{options.compiler}'
        enable_trace = bool(
            getattr(options, 'trace_lite', False) or
            getattr(options, 'upload_traces', False))
        trace_recorder = getattr(options, '_trace_recorder', None)
        with utils.TempDir() as temp:
            if options.print_memoryuse and not options.track_memory_to_file:
                options.track_memory_to_file = os.path.join(
                    temp, utils.MEMORY_USE_TMP_FILE)
            if options.compiler == 'r8' and app_provided_pg_conf:
                # Ensure that output of -printmapping and -printseeds go to the output
                # location and not where the app Proguard configuration places them.
                if outdir.endswith('.zip') or outdir.endswith('.jar'):
                    pg_outdir = os.path.dirname(outdir)
                else:
                    pg_outdir = outdir
                if not options.no_extra_pgconf:
                    additional_pg_conf = generate_additional_proguard_configuration(
                        temp, os.path.abspath(pg_outdir))
                    args.extend(['--pg-conf', additional_pg_conf])
            stdout_path = os.path.join(temp, 'stdout')
            stderr_path = os.path.join(temp, 'stderr')
            with open(stdout_path, 'w') as stdout_workers:
                with open(stderr_path, 'w') as stderr:
                    jar = None
                    main = None
                    if options.compiler_build == 'full':
                        tool = options.compiler
                    else:
                        assert (options.compiler_build == 'lib')
                        tool = 'r8lib-' + options.compiler
                    if options.hash:
                        jar = os.path.join(utils.LIBS,
                                           'r8-' + options.hash + '.jar')
                        main = 'com.android.tools.r8.' + options.compiler.upper(
                        )
                    if should_build(options):
                        gradle.run_gradle([
                            utils.GRADLE_TASK_R8LIB if tool.startswith('r8lib')
                            else utils.GRADLE_TASK_R8
                        ])
                    t0 = time.time()

                    def run_tool(trace_file):
                        run_extra_args = extra_args
                        if trace_file:
                            run_extra_args = extra_args + [
                                f'-Dcom.android.tools.r8.dumptracetofile={trace_file}',
                                '-Dcom.android.tools.r8.trace.lite',
                            ]
                        return toolhelper.run(
                            tool,
                            args,
                            build=False,
                            disable_assertions=options.disable_assertions,
                            profile=options.profile,
                            track_memory_file=options.track_memory_to_file,
                            extra_args=run_extra_args,
                            stdout=stdout
                            if worker_id is None else stdout_workers,
                            stderr=stderr,
                            timeout=options.timeout,
                            quiet=quiet,
                            cmd_prefix=['taskset', '-c', options.cpu_list]
                            if options.cpu_list else [],
                            jar=jar,
                            main=main,
                            worker_id=worker_id)

                    trace_out_dir = (os.path.dirname(os.path.abspath(outdir))
                                     if outdir.endswith('.zip') or
                                     outdir.endswith('.jar') else outdir)
                    exit_code, perfetto_url = (
                        perfetto_utils.run_with_perfetto_trace(
                            run_tool,
                            enable_trace,
                            getattr(options, 'upload_traces', False),
                            temp,
                            slug,
                            f'{options.app} {version_id} ({type}, {options.compiler})',
                            gcs_prefix=(trace_recorder.gcs_prefix
                                        if trace_recorder else
                                        _get_kokoro_gcs_prefix()),
                            local_trace_path=os.path.join(
                                trace_out_dir, f'{slug}.perfetto-trace'),
                            warn_fn=lambda msg: print_thread(
                                f'WARNING: {msg}', worker_id)))
                    elapsed_sec = time.time() - t0
                    if perfetto_url:
                        print_thread(f'  perfetto: {perfetto_url}', worker_id)
            if trace_recorder is not None:
                failure_msg = None
                if exit_code != 0:
                    is_oom = False
                    if os.path.isfile(stderr_path):
                        with open(stderr_path) as err_f:
                            is_oom = ('java.lang.OutOfMemoryError'
                                      in err_f.read())
                    if is_oom:
                        failure_msg = (
                            f'OutOfMemoryError (exit code {exit_code})')
                    elif exit_code == TIMEOUT_KILL_CODE:
                        failure_msg = f'Timed out (exit code {exit_code})'
                    else:
                        failure_msg = (
                            f'Compilation failed with exit code {exit_code}')
                trace_recorder.record(options.app, slug, elapsed_sec,
                                      exit_code == 0, failure_msg, perfetto_url)
            if worker_id is not None:
                with open(stdout_path) as stdout:
                    stdout_text = stdout.read()
                    print_thread(stdout_text, worker_id)
            if exit_code != 0:
                with open(stderr_path) as stderr:
                    stderr_text = stderr.read()
                    if not quiet:
                        print_thread(stderr_text, worker_id)
                    if 'java.lang.OutOfMemoryError' in stderr_text:
                        if not quiet:
                            print('Failure was OOM')
                        return OOM_EXIT_CODE
                    return exit_code

            if options.print_memoryuse:
                print('{}(MemoryUse): {}'.format(
                    options.print_memoryuse,
                    utils.grep_memoryuse(options.track_memory_to_file)))

    if options.print_runtimeraw:
        print('{}(RunTimeRaw): {} ms'.format(options.print_runtimeraw,
                                             1000.0 * (time.time() - t0)))

    if options.print_dexsegments:
        dex_files = glob(os.path.join(outdir, '*.dex'))
        utils.print_dexsegments(options.print_dexsegments, dex_files)
        print('{}-Total(CodeSize): {}'.format(
            options.print_dexsegments, compute_size_of_dex_files(dex_files)))
    return 0


def compute_size_of_dex_files(dex_files):
    dex_size = 0
    for dex_file in dex_files:
        dex_size += os.path.getsize(dex_file)
    return dex_size


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
