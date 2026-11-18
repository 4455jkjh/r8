#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
"""Selective JaCoCo class discovery and Zoss Bifrost C2P coverage upload."""

import json
import os
import struct
import subprocess
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

import utils

COVERAGE_INCLUDES_FILE = os.path.join(utils.BUILD, 'coverage_includes.txt')
JACOCO_NO_R8LIB_XML = os.path.join(utils.BUILD, 'reports', 'jacoco',
                                   'testNoR8Lib', 'jacocoTestReport.xml')

ZOSS_MAX_CHUNK_BYTES = 3 * 1024 * 1024
ZOSS_JACOCO_XML_FORMAT_ENUM = 5
ZOSS_CLOUD_PLATFORM_SCOPE = 'https://www.googleapis.com/auth/cloud-platform'
ZOSS_IAM_CREDENTIALS_URL = (
    'https://iamcredentials.googleapis.com/v1/projects/-/'
    'serviceAccounts/zoss-coverage-uploader@r8-service-accounts.iam.gserviceaccount.com:generateAccessToken'
)
ZOSS_UPLOAD_URL = (
    'https://bifrost-zossclcoveragestorage-c2p.googleapis.com:443'
    '/zapfhahn.zoss.proto.services.CoverageStorage/UploadCoverage')
ZOSS_GERRIT_HOST = 'r8'
ZOSS_GERRIT_PROJECT = 'r8'
ZOSS_UPLOADER_NAME = 'LUCI'

COVERED_SOURCE_ROOTS = (
    'src/assistant/java',
    'src/keepanno/java',
    'src/keepradius/java',
    'src/libanalyzer/java',
    'src/main/java',
    'src/utils/java',
)

EXCLUDED_REL_SUBPATHS = (
    'com/android/tools/r8/keepanno/proto/',
    'com/android/tools/r8/keepradius/proto/',
    'com/android/tools/r8/libanalyzer/proto/',
)


def get_covered_source_root(repo_rel_path):
    norm = repo_rel_path.replace('\\', '/')
    if not (norm.endswith('.java') or norm.endswith('.kt')):
        return None
    if any(sub in norm for sub in EXCLUDED_REL_SUBPATHS):
        return None
    for src_root in COVERED_SOURCE_ROOTS:
        if norm.startswith(src_root + '/'):
            return src_root
    return None


def compute_changed_jacoco_includes():
    """Returns deduplicated JaCoCo class patterns (`pkg.Cls`, `pkg.Cls$*`) for touched compiler files."""
    with utils.ChangedWorkingDirectory(utils.REPO_ROOT, quiet=True):
        head_commit = utils.get_head_commit()
        changed_files = head_commit.changed_files() if head_commit else []

    jacoco_includes = []
    for rel_path in changed_files:
        rel_path = rel_path.strip().replace('\\', '/')
        src_root = get_covered_source_root(rel_path)
        if not src_root:
            continue
        if not os.path.isfile(os.path.join(utils.REPO_ROOT, rel_path)):
            continue
        stem, ext = os.path.splitext(rel_path[len(src_root) + 1:])
        class_name = stem.replace('/', '.')
        jacoco_includes.append(class_name)
        jacoco_includes.append(f'{class_name}$*')
        if ext == '.kt':
            jacoco_includes.append(f'{class_name}Kt')
            jacoco_includes.append(f'{class_name}Kt$*')

    return list(dict.fromkeys(jacoco_includes))


def prepare_jacoco_includes():
    """Writes `build/coverage_includes.txt` when compiler files changed."""
    if os.path.isfile(JACOCO_NO_R8LIB_XML):
        os.remove(JACOCO_NO_R8LIB_XML)
    includes = compute_changed_jacoco_includes()
    if includes:
        utils.makedirs_if_needed(os.path.dirname(COVERAGE_INCLUDES_FILE))
        with open(COVERAGE_INCLUDES_FILE, 'w', encoding='utf-8') as f:
            f.write('\n'.join(includes) + '\n')
    elif os.path.isfile(COVERAGE_INCLUDES_FILE):
        os.remove(COVERAGE_INCLUDES_FILE)
    return includes


def group_jacoco_xml_by_source_root():
    """Groups JaCoCo `<sourcefile>` elements into `(source_root, xml_bytes)` chunks for Zoss."""
    if not os.path.isfile(JACOCO_NO_R8LIB_XML):
        return []

    tree = ET.parse(JACOCO_NO_R8LIB_XML)
    by_root = {}
    for pkg_elem in tree.getroot().findall('package'):
        pkg_name = pkg_elem.attrib.get('name', '')
        for sf_elem in pkg_elem.findall('sourcefile'):
            sf_name = sf_elem.attrib.get('name', '')
            if not sf_name or sf_elem.find('line') is None:
                continue
            for src_root in COVERED_SOURCE_ROOTS:
                if os.path.isfile(
                        os.path.join(utils.REPO_ROOT, src_root, pkg_name,
                                     sf_name)):
                    by_root.setdefault(src_root, []).append((pkg_name, sf_elem))
                    break

    result = []
    for src_root in sorted(by_root):
        cur_report = ET.Element('report', {'name': 'r8'})
        cur_pkgs = {}
        cur_size = 64
        for pkg_name, sf_elem in by_root[src_root]:
            sf_bytes_len = len(ET.tostring(sf_elem, encoding='utf-8')) + 64
            if cur_pkgs and cur_size + sf_bytes_len > ZOSS_MAX_CHUNK_BYTES:
                result.append(
                    (src_root, ET.tostring(cur_report, encoding='utf-8')))
                cur_report = ET.Element('report', {'name': 'r8'})
                cur_pkgs = {}
                cur_size = 64
            if pkg_name not in cur_pkgs:
                cur_pkgs[pkg_name] = ET.SubElement(cur_report, 'package',
                                                   {'name': pkg_name})
            cur_pkgs[pkg_name].append(sf_elem)
            cur_size += sf_bytes_len
        if cur_pkgs:
            result.append((src_root, ET.tostring(cur_report, encoding='utf-8')))
    return result


def _encode_varint(value):
    out = bytearray()
    while value > 0x7F:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def _encode_varint_field(field_number, value):
    if not value:
        return b''
    return _encode_varint((field_number << 3) | 0) + _encode_varint(value)


def _encode_len_field(field_number, payload):
    if not payload:
        return b''
    return (_encode_varint((field_number << 3) | 2) +
            _encode_varint(len(payload)) + payload)


def _encode_str_field(field_number, text):
    if not text:
        return b''
    return _encode_len_field(field_number, text.encode('utf-8'))


def encode_zoss_upload_coverage_request(
    change_number,
    patchset,
    src_root,
    xml_bytes,
    uploader_id='',
):
    """Encodes `zapfhahn.zoss.proto.services.UploadCoverageRequest` for a single coverage data file."""
    gerrit_ws = b''.join([
        _encode_str_field(1, ZOSS_GERRIT_HOST),
        _encode_str_field(2, ZOSS_GERRIT_PROJECT),
        _encode_varint_field(3, int(change_number)),
        _encode_varint_field(4, int(patchset)),
    ])
    cov_data_file = b''.join([
        _encode_len_field(1, xml_bytes),
        _encode_varint_field(2, ZOSS_JACOCO_XML_FORMAT_ENUM),
        _encode_len_field(3, _encode_str_field(2, src_root)),
    ])
    cov_source = b''.join([
        _encode_str_field(1, ZOSS_UPLOADER_NAME),
        _encode_str_field(2,
                          str(uploader_id) if uploader_id else ''),
    ])
    return b''.join([
        _encode_len_field(1, _encode_len_field(3, gerrit_ws)),
        _encode_len_field(3, _encode_len_field(1, cov_data_file)),
        _encode_len_field(5, cov_source),
    ])


def resolve_zoss_oauth_token():
    base_token = subprocess.check_output(
        ['luci-auth', 'token', '-scopes', ZOSS_CLOUD_PLATFORM_SCOPE],
        text=True,
    ).strip()
    req = urllib.request.Request(
        ZOSS_IAM_CREDENTIALS_URL,
        data=json.dumps({
            'scope': [ZOSS_CLOUD_PLATFORM_SCOPE]
        }).encode('utf-8'),
        headers={
            'Authorization': f'Bearer {base_token}',
            'Content-Type': 'application/json',
        },
        method='POST',
    )
    with urllib.request.urlopen(req, timeout=15.0) as resp:
        data = json.loads(resp.read().decode('utf-8'))
    access_token = data.get('accessToken', '').strip()
    if not access_token:
        raise RuntimeError('IAM generateAccessToken returned empty token')
    return access_token


def frame_grpc_message(payload_bytes):
    """Wraps a protobuf message into a 5-byte gRPC length-prefixed frame."""
    return b'\x00' + struct.pack('>I', len(payload_bytes)) + payload_bytes


def upload_jacoco_report_to_zoss(change_number, patchset, uploader_id=''):
    """Streams JaCoCo XML coverage to Zoss `CoverageStorage.UploadCoverage` over Bifrost C2P HTTP/2."""
    root_xml_pairs = group_jacoco_xml_by_source_root()
    if not root_xml_pairs:
        print(
            'No JaCoCo XML entries for covered compiler sources; skipping Zoss upload.'
        )
        return

    grpc_body = b''.join(
        frame_grpc_message(
            encode_zoss_upload_coverage_request(
                change_number=change_number,
                patchset=patchset,
                src_root=src_root,
                xml_bytes=xml_bytes,
                uploader_id=uploader_id,
            )) for src_root, xml_bytes in root_xml_pairs)

    try:
        token = resolve_zoss_oauth_token()
    except Exception as e:
        print(f'Failed to resolve OAuth token for Zoss upload: {e}',
              file=sys.stderr)
        return

    try:
        with utils.TempDir() as temp_dir:
            payload_path = os.path.join(temp_dir, 'zoss_upload.bin')
            with open(payload_path, 'wb') as f:
                f.write(grpc_body)
            # Pass the token in a config file to keep it out of the curl argv.
            config_path = os.path.join(temp_dir, 'zoss_curl.cfg')
            with open(config_path, 'w', encoding='utf-8') as f:
                f.write(f'header = "authorization: Bearer {token}"\n')

            curl_cmd = [
                'curl',
                '-sS',
                '--http2',
                '--max-time',
                '30',
                '-D',
                '-',
                '-o',
                '/dev/null',
                '-X',
                'POST',
                '-H',
                'content-type: application/grpc',
                '-H',
                'te: trailers',
                '-K',
                config_path,
                '--data-binary',
                f'@{payload_path}',
                ZOSS_UPLOAD_URL,
            ]
            proc = subprocess.run(
                curl_cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )

        if proc.returncode != 0:
            print(
                f'Zoss curl upload failed (exit {proc.returncode}): {proc.stderr.strip()}',
                file=sys.stderr)
            return

        http_status = None
        grpc_status = None
        grpc_message = None
        for raw_line in proc.stdout.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if line.startswith('HTTP/'):
                parts = line.split(None, 2)
                if len(parts) >= 2 and parts[1].isdigit():
                    http_status = int(parts[1])
            elif ':' in line:
                key, val = line.split(':', 1)
                key_lower = key.strip().lower()
                val_strip = val.strip()
                if key_lower == 'grpc-status':
                    grpc_status = val_strip
                elif key_lower == 'grpc-message':
                    grpc_message = val_strip

        if http_status != 200 or grpc_status != '0':
            decoded_msg = urllib.parse.unquote(
                grpc_message) if grpc_message else proc.stdout.strip()
            print(
                f'Zoss upload failed (http={http_status}, grpc={grpc_status}): {decoded_msg}',
                file=sys.stderr)
            return

        print(f'Uploaded JaCoCo XML coverage ({len(root_xml_pairs)} chunk(s)) '
              f'to Zoss for {ZOSS_GERRIT_HOST}/{ZOSS_GERRIT_PROJECT} '
              f'CL {change_number}/{patchset}.')
    except Exception as e:
        print(f'Zoss upload failed: {e}', file=sys.stderr)
