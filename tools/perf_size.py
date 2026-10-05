#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import json
import math
import os
import subprocess
import sys
import time
import zipfile

import archive
import compiledump
import dex2oat
import git_utils
import gradle
import jdk
import thread_utils
from thread_utils import print_thread
import utils

BUCKET = 'perf-size-results'
MAX_SUMMARY_BYTES = 4000

SIZE_BENCHMARKS = {
    'NowInAndroidAppWithResourceShrinking': 'android/nowinandroid',
    'NowInAndroidAppPartial': 'android/nowinandroid',
    'JetNewsApp': 'android/compose-samples/jetnews',
    'ReplyApp': 'android/compose-samples/reply',
    'TiviApp': 'tivi',
    'ChromeApp': 'chrome',
    'FeederApp': 'android/feeder',
    'FeederAppPartial': 'android/feeder',
    'BookStoryApp': 'android/bookstory',
    'BookStoryAppPartial': 'android/bookstory',
    'ReadYouApp': 'android/readyou',
    'ReadYouAppPartial': 'android/readyou',
    'FossifyFileManagerApp': 'android/fossify-filemanager',
    'FossifyFileManagerAppPartial': 'android/fossify-filemanager',
    'NewPipeKotlinApp': 'android/newpipe',
    'NewPipeKotlinAppPartial': 'android/newpipe',
    'TuskyApp': 'android/tusky',
    'TuskyAppPartial': 'android/tusky',
    'KeePassDXApp': 'android/keepassdx',
    'KeePassDXAppPartial': 'android/keepassdx',
    'OmniNotesApp': 'android/omninotes',
    'OmniNotesAppPartial': 'android/omninotes',
    'OpenTracksApp': 'android/opentracks',
    'OpenTracksAppPartial': 'android/opentracks',
}

SIZE_COMPILEDUMPS = {
    'Tachiyomi': 'tachiyomi',
    'Signal': 'signal-android',
    'NewPipe': 'newpipe',
}

COMPILEDUMP_COMPILERS = ('r8full', 'd8-debug', 'd8-release')


def get_compiledump_key(dump_name, compiler):
    return f'{dump_name}:{compiler}'


def get_expected_result_keys(options):
    return list(options.benchmarks) + [
        get_compiledump_key(c, compiler)
        for c in options.compiledumps
        for compiler in COMPILEDUMP_COMPILERS
    ]


def parse_options(argv=None):
    parser = argparse.ArgumentParser(
        description='Run size-focused benchmarks and compiledumps for presubmit.'
    )
    parser.add_argument('--benchmark',
                        help='Specific benchmark(s) to measure.',
                        action='append')
    parser.add_argument('--compiledump',
                        help='Specific compiledump(s) to measure.',
                        action='append')
    parser.add_argument('--workers',
                        help='Number of parallel workers (default: 4).',
                        type=int,
                        default=4)
    parser.add_argument('--use-prebuilt-lib',
                        '--use_prebuilt_lib',
                        help='Use prebuilt artifacts from compile-only CAS.',
                        action='store_true',
                        default=False)
    parser.add_argument('--no-build',
                        '--no_build',
                        help='Skip building R8 and test jars with Gradle.',
                        action='store_true',
                        default=False)
    parser.add_argument('--base-hash',
                        help='Explicit main commit hash to compare against.')
    parser.add_argument(
        '--base-jar', help='Explicit baseline r8.jar path to compare against.')
    parser.add_argument('--summary-output',
                        help='Path to write Markdown summary table.')
    parser.add_argument('--json-output',
                        help='Path to write JSON comparison results.')
    parser.add_argument(
        '--upload-baseline',
        help='Upload measured HEAD baseline to GCS (postsubmit only).',
        action='store_true',
        default=False)
    parser.add_argument('--no-upload',
                        help='Skip uploading baseline results to GCS.',
                        action='store_true',
                        default=False)
    parser.add_argument('--verbose',
                        help='Enable verbose logging.',
                        action='store_true',
                        default=False)
    options, args = parser.parse_known_args(argv)
    if options.benchmark is not None or options.compiledump is not None:
        options.benchmarks = list(dict.fromkeys(options.benchmark or []))
        options.compiledumps = list(dict.fromkeys(options.compiledump or []))
    else:
        options.benchmarks = list(SIZE_BENCHMARKS.keys())
        options.compiledumps = list(SIZE_COMPILEDUMPS.keys())
    for b in options.benchmarks:
        if b not in SIZE_BENCHMARKS:
            parser.error(f'Unknown benchmark: {b}')
    for c in options.compiledumps:
        if c not in SIZE_COMPILEDUMPS:
            parser.error(f'Unknown compiledump: {c}')
    options.is_full_suite = (
        set(options.benchmarks) == set(SIZE_BENCHMARKS.keys()) and
        set(options.compiledumps) == set(SIZE_COMPILEDUMPS.keys()))
    options.no_build = options.no_build or options.use_prebuilt_lib
    return options, args


def ensure_build_artifacts(options):
    gradle.ensure_jdk()
    required_files = [
        utils.R8_JAR,
        utils.R8_TESTS_JAR,
        utils.R8_TESTS_DEPS_JAR,
        utils.R8_TESTBASE_JAR,
        utils.BUILD_JAVA_MAIN_CLASSPATH,
        os.path.join(utils.REPO_ROOT, 'd8_r8', 'keepanno', 'build', 'classes',
                     'java', 'main'),
        os.path.join(utils.REPO_ROOT, 'd8_r8', 'test_modules', 'tests_java_8',
                     'build', 'classes', 'java', 'test'),
        os.path.join(utils.REPO_ROOT, 'd8_r8', 'test_modules', 'testbase',
                     'build', 'classes', 'java', 'main'),
    ]
    if options.no_build and all(os.path.exists(p) for p in required_files):
        print('Using prebuilt R8 and test artifacts.')
        return
    build_targets = [
        utils.GRADLE_TASK_R8,
        utils.GRADLE_TASK_KEEP_ANNO_JAR,
        utils.GRADLE_TASK_TEST_DEPS_JAR,
        utils.GRADLE_TASK_TEST_UNZIP_TESTBASE,
        ':test:unzipTests',
        '-Pno_internal',
    ]
    print('Building R8 and test artifacts for size presubmit...')
    gradle.run_gradle(build_targets)


def ensure_dependencies(options):
    deps_to_download = {
        os.path.join(utils.OPENSOURCE_DUMPS_DIR, SIZE_BENCHMARKS[b])
        for b in options.benchmarks
    } | {
        os.path.join(utils.OPENSOURCE_DUMPS_DIR, SIZE_COMPILEDUMPS[c])
        for c in options.compiledumps
    }
    dex2oat_host_dir = os.path.join(utils.TOOLS_DIR, 'linux',
                                    dex2oat.DIRS[dex2oat.LATEST])
    deps_to_download.add(dex2oat_host_dir)

    for dep_path in sorted(deps_to_download):
        if os.path.exists(dep_path + '.tar.gz.sha1'):
            utils.ensure_google_download(dep_path, quiet=not options.verbose)


def tag_local_r8_jar(src_jar, commit_sha, temp_dir, label='patch'):
    """Ensure r8.jar has a 40-char version.sha in r8-version.properties.

    Local r8.jar builds lack r8-version.properties (or have version.sha=engineering),
    which causes R8 to emit 'engineering' (11 chars) instead of a 40-char SHA in
    every DEX marker (+29 bytes per DEX file compared to archived main builds).
    """
    with zipfile.ZipFile(src_jar, 'r') as zf:
        if 'r8-version.properties' in zf.namelist():
            raw_props = zf.read('r8-version.properties')
            content = raw_props.decode('utf-8', errors='replace')
            for line in content.splitlines():
                if line.startswith('version.sha=') and len(
                        line.split('=', 1)[1].strip()) == 40:
                    return src_jar
    tagged_jar = os.path.join(temp_dir, f'r8_tagged_{label}.jar')
    sha = commit_sha if commit_sha and len(commit_sha) == 40 else '0' * 40
    props = f'version.sha={sha}\nversion-file.version.code=1\n'
    with zipfile.ZipFile(src_jar, 'r') as zin:
        with zipfile.ZipFile(tagged_jar, 'w') as zout:
            for item in zin.infolist():
                if item.filename != 'r8-version.properties':
                    zout.writestr(item, zin.read(item.filename))
            zout.writestr('r8-version.properties', props)
    return tagged_jar


def pos_or_none(val):
    return val if val is not None and val > 0 else None


def is_feature_out_jar(fname):
    return fname.startswith('feature-') and fname.endswith('.out.jar')


def is_feature_out_ap(fname):
    return fname.startswith('feature-') and fname.endswith('.out.ap_')


def run_single_benchmark(benchmark_name, r8_jar, version_label, temp_dir,
                         options, results_dict, worker_id):
    bench_temp = os.path.join(temp_dir, f'bench_{benchmark_name}')
    os.makedirs(bench_temp, exist_ok=True)
    result_json_path = os.path.join(bench_temp, 'result.json')

    cmd = [
        sys.executable,
        os.path.join(utils.TOOLS_DIR, 'run_benchmark.py'),
        '--benchmark',
        benchmark_name,
        '--target',
        'r8-full',
        '--nolib',
        '--no-build',
        '--warmup',
        '0',
        '--iterations',
        '1',
        '--version',
        version_label,
        '--version-jar',
        r8_jar,
        '--temp',
        bench_temp,
        '--output',
        result_json_path,
    ]
    if options.verbose:
        cmd.append('--verbose')

    start = time.time()
    print_thread(f'Running benchmark {benchmark_name} ({version_label})...',
                 worker_id)
    subprocess.check_call(cmd)
    duration = time.time() - start

    with open(result_json_path, 'r') as f:
        iter_res = json.load(f)['results'][0]
    dex_size = pos_or_none(iter_res.get('code_size'))
    oat_size = pos_or_none(iter_res.get('oat_code_size'))
    resource_size = pos_or_none(iter_res.get('resource_size'))

    results_dict[benchmark_name] = {
        'name': benchmark_name,
        'kind': 'benchmark (r8-full)',
        'dex_size': dex_size,
        'oat_size': oat_size,
        'resource_size': resource_size,
        'duration_s': round(duration, 1),
    }
    print_thread(
        f'Finished benchmark {benchmark_name} ({version_label}) in {duration:.1f}s: '
        f'dex={dex_size}, oat={oat_size}, res={resource_size}', worker_id)
    return 0


def run_single_compiledump(dump_name, compiler, r8_jar, version_label, temp_dir,
                           options, results_dict, worker_id):
    folder = SIZE_COMPILEDUMPS[dump_name]
    dump_path = os.path.join(utils.OPENSOURCE_DUMPS_DIR, folder, 'dump_app.zip')
    dump_temp = os.path.join(temp_dir, f'dump_{dump_name}_{compiler}')
    os.makedirs(dump_temp, exist_ok=True)

    if compiler.startswith('d8-'):
        actual_compiler = 'd8'
        compilation_mode = compiler.split('-', 1)[1]
    else:
        actual_compiler = compiler
        compilation_mode = None

    raw_args = [
        '-d',
        dump_path,
        '--compiler',
        actual_compiler,
        '--r8-jar',
        r8_jar,
        '--version',
        version_label,
        '--nolib',
        '--no-build',
        '--disable-assertions',
        '--optimized-resource-shrinking',
        '--xmx',
        '8g',
    ]
    if actual_compiler == 'd8':
        raw_args.extend(['--min-api', '21'])
    if compilation_mode:
        raw_args.extend(['--compilation-mode', compilation_mode])
    args = compiledump.make_parser().parse_args(raw_args)

    start = time.time()
    print_thread(
        f'Running compiledump {dump_name} ({compiler}, {version_label})...',
        worker_id)
    ret = compiledump.run1(dump_temp,
                           args, [],
                           jdkhome=jdk.GetDefaultJdkHome(),
                           worker_id=worker_id)
    out_jar = os.path.join(dump_temp, 'out.jar')
    if ret != 0 or not os.path.isfile(out_jar):
        raise RuntimeError(
            f'compiledump failed for {dump_name} ({compiler}, {version_label})')

    dex_size = utils.compute_dex_size_in_zip(out_jar)
    for fname in os.listdir(dump_temp):
        if is_feature_out_jar(fname):
            dex_size += utils.compute_dex_size_in_zip(
                os.path.join(dump_temp, fname))
    dex_size = pos_or_none(dex_size)

    out_oat = os.path.join(dump_temp, 'out.oat')
    # Run dex2oat.py in a subprocess to avoid process-global os.chdir() across threads.
    subprocess.check_call([
        sys.executable,
        os.path.join(utils.TOOLS_DIR, 'dex2oat.py'),
        '--output',
        out_oat,
        '--version',
        dex2oat.LATEST,
        out_jar,
    ])
    oat_size = pos_or_none(
        os.path.getsize(out_oat) if os.path.isfile(out_oat) else None)

    res_files = [
        os.path.join(dump_temp, f)
        for f in os.listdir(dump_temp)
        if f == 'app-res-out.ap_' or is_feature_out_ap(f)
    ]
    resource_size = pos_or_none(
        sum(os.path.getsize(p) for p in res_files) if res_files else None)

    duration = time.time() - start
    key = get_compiledump_key(dump_name, compiler)
    results_dict[key] = {
        'name': dump_name,
        'kind': f'compiledump ({compiler})',
        'dex_size': dex_size,
        'oat_size': oat_size,
        'resource_size': resource_size,
        'duration_s': round(duration, 1),
    }
    print_thread(
        f'Finished compiledump {dump_name} ({compiler}, {version_label}) in {duration:.1f}s: '
        f'dex={dex_size}, oat={oat_size}, res={resource_size}', worker_id)
    return 0


def run_suite(r8_jar, version_label, temp_dir, options):
    suite_temp = os.path.join(temp_dir, f'suite_{version_label[:12]}')
    os.makedirs(suite_temp, exist_ok=True)
    results_dict = {}
    jobs = []
    for b in options.benchmarks:
        jobs.append(lambda wid, b=b: run_single_benchmark(
            b, r8_jar, version_label, suite_temp, options, results_dict, wid))
    for c in options.compiledumps:
        for comp in COMPILEDUMP_COMPILERS:
            jobs.append(lambda wid, c=c, comp=comp: run_single_compiledump(
                c, comp, r8_jar, version_label, suite_temp, options,
                results_dict, wid))

    if jobs:
        exit_code = thread_utils.run_in_parallel(
            jobs, number_of_workers=options.workers, stop_on_first_failure=True)
        if exit_code != 0:
            raise RuntimeError(f'Size suite failed for {version_label}')

    return {k: results_dict[k] for k in get_expected_result_keys(options)}


def get_baseline_gs_location(commit_hash):
    return f'gs://{BUCKET}/{commit_hash}/baseline.json'


def try_load_baseline_from_gcs(commit_hash, required_keys, temp_dir):
    gs_loc = get_baseline_gs_location(commit_hash)
    if not utils.cloud_storage_exists(gs_loc):
        return None
    local_path = os.path.join(temp_dir, f'baseline_{commit_hash}.json')
    try:
        utils.download_file_from_cloud_storage(gs_loc, local_path, quiet=True)
        with open(local_path, 'r') as f:
            items = json.load(f).get('items', {})
        if all(k in items for k in required_keys):
            print(
                f'Loaded cached size baseline for {commit_hash} from {gs_loc}')
            return items
    except Exception as e:
        print(f'Warning: failed to load cached baseline from {gs_loc}: {e}')
    return None


def try_upload_baseline_to_gcs(commit_hash, items, temp_dir, options):
    if (not options.upload_baseline or options.no_upload or
            not options.is_full_suite or not utils.is_bot()):
        return
    local_path = os.path.join(temp_dir, f'upload_baseline_{commit_hash}.json')
    payload = {
        'hash': commit_hash,
        'timestamp': int(time.time()),
        'items': items,
    }
    with open(local_path, 'w') as f:
        json.dump(payload, f, indent=2)
    gs_loc = get_baseline_gs_location(commit_hash)
    try:
        utils.upload_file_to_cloud_storage(local_path, gs_loc)
        print(f'Uploaded size baseline to {gs_loc}')
    except Exception as e:
        print(f'Warning: could not upload size baseline to {gs_loc}: {e}')


def resolve_baseline_results(options,
                             temp_dir,
                             exclude_sha=None,
                             cache_only=False):
    required_keys = get_expected_result_keys(options)
    if options.base_jar:
        base_hash = options.base_hash or 'custom-jar'
        tagged_base = tag_local_r8_jar(os.path.abspath(options.base_jar),
                                       options.base_hash,
                                       temp_dir,
                                       label='base')
        items = run_suite(tagged_base, base_hash, temp_dir, options)
        return base_hash, items

    candidates = git_utils.get_candidate_main_commits(options.base_hash,
                                                      exclude_sha=exclude_sha)
    # Check GCS cache for any candidate commit.
    for commit_hash in candidates:
        cached = try_load_baseline_from_gcs(commit_hash, required_keys,
                                            temp_dir)
        if cached is not None:
            return commit_hash, cached

    if cache_only:
        return candidates[0], None

    # Download prebuilt r8.jar from gs://r8-releases/raw/main/<hash>/r8.jar.
    selected_hash = None
    downloaded_jar = None
    for commit_hash in candidates:
        gs_r8_jar = archive.get_upload_destination(commit_hash, 'r8.jar', True)
        if utils.file_exists_on_cloud_storage(gs_r8_jar):
            dest_jar = os.path.join(temp_dir, f'r8_{commit_hash}.jar')
            print(
                f'Downloading baseline r8.jar for {commit_hash} from {gs_r8_jar}'
            )
            utils.download_file_from_cloud_storage(gs_r8_jar,
                                                   dest_jar,
                                                   quiet=True)
            selected_hash = commit_hash
            downloaded_jar = dest_jar
            break

    if downloaded_jar is None:
        print('Warning: no cached baseline or archived r8.jar found in GCS')
        return candidates[0], None

    try:
        items = run_suite(downloaded_jar, selected_hash, temp_dir, options)
        return selected_hash, items
    except Exception as e:
        print(
            f'Warning: live baseline fallback failed for {selected_hash}: {e}')
        return selected_hash, None


def build_binary_sizes_dict(items):
    binary_sizes = {}
    for key, entry in (items or {}).items():
        dex_size = entry.get('dex_size')
        if dex_size is None:
            continue
        name = entry.get('name', key)
        kind = entry.get('kind', '')
        if 'd8-debug' in kind or key.endswith(':d8-debug'):
            tool = 'd8-debug'
        elif 'd8-release' in kind or key.endswith(':d8-release'):
            tool = 'd8-release'
        elif 'd8' in kind or key.endswith(':d8'):
            tool = 'd8'
        else:
            tool = 'r8'
        binary_sizes[f'{name} ({tool})'] = int(dex_size)
    return binary_sizes


def format_bytes(num_bytes):
    abs_bytes = abs(num_bytes)
    if abs_bytes < 1024:
        return f'{num_bytes}B'
    if abs_bytes < 1024 * 1024:
        return f'{num_bytes / 1024.0:.1f}KB'
    return f'{num_bytes / (1024.0 * 1024.0):.2f}MB'


def format_diff(base_val, patch_val):
    if base_val is None or patch_val is None or base_val <= 0:
        return '—'
    diff_bytes = patch_val - base_val
    if diff_bytes == 0:
        return '0B'
    pct = abs(diff_bytes / float(base_val)) * 100.0
    sign = '+' if diff_bytes > 0 else ''
    text = f'{sign}{format_bytes(diff_bytes)} ({pct:.2f}%)'
    badge = '🟢' if diff_bytes < 0 else '🔴'
    return f'{badge} **{text}**'


def geomean_ratio(ratios):
    if not ratios:
        return None
    return math.exp(sum(math.log(r) for r in ratios) / len(ratios))


def display_name_and_type(key, entry):
    name = entry.get('name', key)
    kind = entry.get('kind', '')
    if 'd8-debug' in kind:
        return name, 'd8-debug'
    if 'd8-release' in kind:
        return name, 'd8-rel'
    if name.endswith('Partial'):
        return name[:-len('Partial')], 'r8-part'
    return name, 'r8'


def max_abs_pct_change(base_entry, patch_entry, metrics):
    result = 0.0
    for m in metrics:
        b_val = base_entry.get(m)
        p_val = patch_entry.get(m)
        if b_val and p_val:
            result = max(result, abs(float(p_val) - b_val) / b_val)
    return result


def generate_markdown_summary(base_hash, base_items, patch_items):
    short_base = base_hash[:8] if len(base_hash) >= 8 else base_hash
    headers = ['Target', 'Type', 'DEX Δ', 'OAT Δ', 'Res Δ']
    metrics = ('dex_size', 'oat_size', 'resource_size')
    ratios = {m: [] for m in metrics}
    changed_rows = []

    for key, patch_entry in patch_items.items():
        base_entry = (base_items or {}).get(key, {})
        display_name, display_type = display_name_and_type(key, patch_entry)
        row_cells = [f'`{display_name}`', display_type]
        row_has_diff = False
        for m in metrics:
            b_val = base_entry.get(m)
            p_val = patch_entry.get(m)
            if p_val and b_val:
                ratios[m].append(float(p_val) / float(b_val))
                if p_val != b_val:
                    row_has_diff = True
            row_cells.append(format_diff(b_val, p_val))
        if row_has_diff:
            changed_rows.append((max_abs_pct_change(base_entry, patch_entry,
                                                    metrics), row_cells))

    # Only list targets with a size change, largest relative change first.
    changed_rows.sort(key=lambda r: r[0], reverse=True)
    data_rows = [row for _, row in changed_rows]

    def fmt_geomean(r_list):
        gm = geomean_ratio(r_list)
        if gm is None:
            return '—'
        pct = (gm - 1.0) * 100.0
        if abs(pct) < 0.005:
            pct = 0.0
        sign = '+' if pct > 0 else ''
        badge = '🟢 ' if pct < 0 else ('🔴 ' if pct > 0 else '')
        return f'{badge}**{sign}{pct:.2f}%**'

    dex_gm = fmt_geomean(ratios['dex_size'])
    oat_gm = fmt_geomean(ratios['oat_size'])
    lines = [
        f'### R8 Size Presubmit Report (vs. `main` @ `{short_base}`) — '
        f'DEX: {dex_gm}, OAT: {oat_gm}'
    ]
    if not base_items:
        lines.append('')
        lines.append(
            f'*(No cached baseline available for `main` @ `{short_base}`)*')
        return '\n'.join(lines)
    if not data_rows:
        lines.append('')
        lines.append('No size difference.')
        return '\n'.join(lines)

    footer_row = ['**Geomean**', '']
    for m in metrics:
        footer_row.append(fmt_geomean(ratios[m]))

    # PolyGerrit's <gr-formatted-text> renders GFM pipe tables into HTML
    # <table>/<th align=...>/<td align=...>, but its Shadow DOM stylesheet
    # defines no cell padding on th/td. Adding non-breaking spaces (\u00a0)
    # provides horizontal column spacing in HTML while ASCII space padding
    # keeps the raw Markdown pipes aligned in plain text logs.
    pad = '\u00a0\u00a0\u00a0'

    def pad_cells(row):
        return [
            f'{cell}{pad}' if i < 2 else f'{pad}{cell}'
            for i, cell in enumerate(row)
        ]

    def display_width(s):
        return sum(2 if ord(c) > 0xFFFF else 1 for c in s)

    def render(rows):
        padded_headers = pad_cells(headers)
        padded_data = [pad_cells(r) for r in rows]
        padded_footer = pad_cells(footer_row)
        all_rows = [padded_headers] + padded_data + [padded_footer]
        widths = [
            max(display_width(r[i])
                for r in all_rows)
            for i in range(len(headers))
        ]

        # Gerrit's email CommentFormatter only renders a paragraph as <pre>
        # (monospace, whitespace preserved) if a line starts with whitespace.
        # GFM still parses pipe tables indented by up to 3 spaces.
        def fmt_row(row):
            cells = []
            for i, cell in enumerate(row):
                extra = widths[i] - display_width(cell)
                cells.append(cell + (' ' * extra) if i < 2 else (' ' * extra) +
                             cell)
            return ' | ' + ' | '.join(cells) + ' |'

        align_row = ' | ' + ' | '.join(':' + '-' * (w - 1) if i < 2 else '-' *
                                       (w - 1) + ':'
                                       for i, w in enumerate(widths)) + ' |'

        table_lines = lines + [
            '',
            fmt_row(padded_headers),
            align_row,
            *(fmt_row(r) for r in padded_data),
            fmt_row(padded_footer),
            '',
        ]
        omitted = len(data_rows) - len(rows)
        if omitted:
            table_lines.append(f'*…and {omitted} more changed targets*')
        return '\n'.join(table_lines)

    # The summary is posted as a Gerrit comment and must stay within
    # MAX_SUMMARY_BYTES, so include as many of the largest changes as fit.
    for count in range(len(data_rows), -1, -1):
        markdown = render(data_rows[:count])
        if len(markdown.encode('utf-8')) <= MAX_SUMMARY_BYTES:
            return markdown
    return markdown


def main(argv=None):
    options, _ = parse_options(argv)
    ensure_build_artifacts(options)
    ensure_dependencies(options)

    head_hash = utils.get_HEAD_sha1()
    with utils.TempDir() as temp_dir:
        tagged_r8_jar = tag_local_r8_jar(utils.R8_JAR,
                                         head_hash,
                                         temp_dir,
                                         label='patch')
        print(f'Running size suite for HEAD ({head_hash[:8]})...')
        patch_items = run_suite(tagged_r8_jar, 'patch', temp_dir, options)

        print('Resolving main baseline sizes...')
        base_hash, base_items = resolve_baseline_results(
            options,
            temp_dir,
            exclude_sha=head_hash,
            cache_only=options.upload_baseline)

        if options.upload_baseline:
            try_upload_baseline_to_gcs(head_hash, patch_items, temp_dir,
                                       options)

        markdown = generate_markdown_summary(base_hash, base_items, patch_items)
        print('\n' + markdown)

        if options.summary_output:
            summary_path = os.path.abspath(options.summary_output)
            os.makedirs(os.path.dirname(summary_path), exist_ok=True)
            with open(summary_path, 'w') as f:
                f.write(markdown)

        if options.json_output:
            json_path = os.path.abspath(options.json_output)
            os.makedirs(os.path.dirname(json_path), exist_ok=True)
            got_revision = head_hash if options.upload_baseline else base_hash
            with open(json_path, 'w') as f:
                json.dump(
                    {
                        'got_revision': got_revision,
                        'binary_sizes': build_binary_sizes_dict(patch_items),
                        'base_hash': base_hash,
                        'head_hash': head_hash,
                        'base': base_items,
                        'patch': patch_items,
                        'summary_markdown': markdown,
                    },
                    f,
                    indent=2)

    return 0


if __name__ == '__main__':
    sys.exit(main())
