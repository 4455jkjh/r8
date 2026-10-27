#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import base64
import gzip
import html
import json
import os
import shutil

import utils

PERFETTO_TRACES_BUCKET = 'r8-perfetto-traces'


def _js_str(value):
    return json.dumps(value).replace('</', '<\\/')


def _build_perfetto_html(gz_bytes, title, file_name):
    b64 = base64.b64encode(gz_bytes).decode('ascii')
    return ('<!doctype html><html><head><meta charset="utf-8">'
            f'<title>{html.escape(title)}</title></head>'
            '<body style="margin:0;overflow:hidden">'
            '<iframe id="pf" src="https://ui.perfetto.dev" '
            'style="position:fixed;inset:0;width:100%;height:100%;border:0">'
            '</iframe><script>'
            'const ORIGIN = "https://ui.perfetto.dev";'
            f'const B64 = {_js_str(b64)};'
            f'const TITLE = {_js_str(title)};'
            f'const FILE_NAME = {_js_str(file_name)};'
            'const bin = atob(B64);'
            'const bytes = new Uint8Array(bin.length);'
            'for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);'
            'const iframe = document.getElementById("pf");'
            'const timer = setInterval(() => {'
            '  if (iframe.contentWindow) {'
            '    iframe.contentWindow.postMessage("PING", ORIGIN);'
            '  }'
            '}, 200);'
            'function onMessage(evt) {'
            '  if (evt.origin !== ORIGIN || evt.data !== "PONG") return;'
            '  clearInterval(timer);'
            '  window.removeEventListener("message", onMessage);'
            '  iframe.contentWindow.postMessage({'
            '    perfetto: {'
            '      buffer: bytes.buffer,'
            '      title: TITLE,'
            '      fileName: FILE_NAME,'
            '      downloadable: true,'
            '      shareable: false,'
            '    }'
            '  }, ORIGIN, [bytes.buffer]);'
            '}'
            'window.addEventListener("message", onMessage);'
            '</script></body></html>')


def upload_perfetto_trace(trace_file, gcs_prefix, slug, title, temp_dir):
    if not os.path.isfile(trace_file) or os.path.getsize(trace_file) <= 0:
        return None
    gcs_prefix = gcs_prefix.strip('/')
    file_name = f'{slug}.perfetto-trace.gz'
    gz_path = os.path.join(temp_dir, file_name)
    html_path = os.path.join(temp_dir, f'{slug}.perfetto.html')
    with open(trace_file, 'rb') as f_in:
        gz_bytes = gzip.compress(f_in.read(), mtime=0)
    with open(gz_path, 'wb') as f_gz:
        f_gz.write(gz_bytes)
    with open(html_path, 'w', encoding='utf-8') as f_html:
        f_html.write(_build_perfetto_html(gz_bytes, title, file_name))
    gcs_dir = f'gs://{PERFETTO_TRACES_BUCKET}/{gcs_prefix}'
    utils.upload_file_to_cloud_storage(
        html_path,
        f'{gcs_dir}/{slug}.perfetto.html',
        header='Content-Type:text/html; charset=utf-8')
    utils.upload_file_to_cloud_storage(
        gz_path,
        f'{gcs_dir}/{file_name}',
        header='Content-Type:application/octet-stream')
    return (f'https://storage.cloud.google.com/{PERFETTO_TRACES_BUCKET}/'
            f'{gcs_prefix}/{slug}.perfetto.html')


def run_with_perfetto_trace(action,
                            enable_trace,
                            upload_traces,
                            temp_dir,
                            slug,
                            title,
                            gcs_prefix=None,
                            local_trace_path=None,
                            warn_fn=print):
    if not enable_trace:
        return action(None), None
    trace_file = os.path.join(temp_dir, f'{slug}.perfetto-trace')
    exit_code = action(trace_file)
    if exit_code != 0:
        return exit_code, None
    if upload_traces and gcs_prefix:
        try:
            url = upload_perfetto_trace(trace_file, gcs_prefix, slug, title,
                                        temp_dir)
            return exit_code, url
        except Exception as e:
            warn_fn(f'Failed to upload Perfetto trace for {slug}: {e}')
            return exit_code, None
    if local_trace_path and os.path.isfile(trace_file):
        shutil.copyfile(trace_file, local_trace_path)
    return exit_code, None
