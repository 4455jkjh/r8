#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import json
import os
import shutil
import stat
import sys
import tempfile
import urllib.request
import zipfile

import utils

VERSION = "148.0.7778.96"  # Default version, but can be overridden
API_URL = "https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json"


def get_latest_stable_version():
    print("Fetching latest stable Chrome version from googlechromelabs...")
    try:
        req = urllib.request.Request(API_URL,
                                     headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode('utf-8'))
        channel_data = data.get("channels", {}).get("Stable")
        if channel_data:
            return channel_data.get("version")
    except Exception as e:
        print(f"Failed to fetch latest version: {e}")
    return None


def parse_options():
    parser = argparse.ArgumentParser(
        description=
        'Update chrome-headless-shell binaries for Playwright in third_party.')
    parser.add_argument(
        '--version',
        help=
        f'Chrome version to download. If not specified and --latest is not used, defaults to {VERSION}.'
    )
    parser.add_argument(
        '--latest',
        action='store_true',
        help='Download the latest stable version of chrome-headless-shell.')
    return parser.parse_args()


def download_and_extract(name, url, extract_to):
    with tempfile.TemporaryDirectory() as tmpdir:
        zip_path = os.path.join(tmpdir, f"{name}.zip")
        print(f"Downloading {url} to {zip_path}...")
        try:
            urllib.request.urlretrieve(url, zip_path)
        except Exception as e:
            print(f"Failed to download {url}: {e}")
            return False

        print(f"Extracting {zip_path} to {tmpdir}...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(tmpdir)

        # Chrome zip usually contains a single top-level directory
        zip_dirs = [
            d for d in os.listdir(tmpdir)
            if os.path.isdir(os.path.join(tmpdir, d))
        ]
        if not zip_dirs:
            print(f"No directory found in zip for {name}")
            return False

        src_dir = os.path.join(tmpdir, zip_dirs[0])
        print(f"Moving contents of {src_dir} to {extract_to}...")
        os.makedirs(extract_to, exist_ok=True)
        for item in os.listdir(src_dir):
            s = os.path.join(src_dir, item)
            d = os.path.join(extract_to, item)
            if os.path.exists(d):
                if os.path.isdir(d):
                    shutil.rmtree(d)
                else:
                    os.remove(d)
            shutil.move(s, d)
    return True


def set_executable(file_path):
    if os.path.exists(file_path):
        print(f"Making {file_path} executable...")
        st = os.stat(file_path)
        os.chmod(file_path,
                 st.st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    else:
        print(f"Warning: File {file_path} not found to set executable.")


def main():
    args = parse_options()
    version = args.version
    if args.latest:
        version = get_latest_stable_version()
        if not version:
            print("Error: Could not retrieve latest stable version.")
            sys.exit(1)
    elif not version:
        version = VERSION

    print(f"Using Chrome version: {version}")
    base_url = f"https://storage.googleapis.com/chrome-for-testing-public/{version}"

    urls = {
        "linux":
            f"{base_url}/linux64/chrome-headless-shell-linux64.zip",
        "mac-arm64":
            f"{base_url}/mac-arm64/chrome-headless-shell-mac-arm64.zip",
        "mac-x64":
            f"{base_url}/mac-x64/chrome-headless-shell-mac-x64.zip",
        "windows":
            f"{base_url}/win64/chrome-headless-shell-win64.zip"
    }

    chrome_headless_dir = os.path.join(utils.THIRD_PARTY, 'chrome_headless')

    linux_dir = os.path.join(chrome_headless_dir, 'linux')
    mac_dir = os.path.join(chrome_headless_dir, 'mac')
    mac_arm_dir = os.path.join(mac_dir, 'mac-arm64')
    mac_x64_dir = os.path.join(mac_dir, 'mac-x64')
    windows_dir = os.path.join(chrome_headless_dir, 'windows')

    # Clean up existing directories if they exist
    for d in [linux_dir, mac_dir, windows_dir]:
        if os.path.exists(d):
            print(f"Removing existing directory {d}...")
            shutil.rmtree(d)

    # 1. Linux
    if download_and_extract("linux", urls["linux"], linux_dir):
        set_executable(os.path.join(linux_dir, "chrome-headless-shell"))

    # 2. Mac
    download_arm = download_and_extract("mac-arm64", urls["mac-arm64"],
                                        mac_arm_dir)
    download_x64 = download_and_extract("mac-x64", urls["mac-x64"], mac_x64_dir)
    if download_arm and download_x64:
        set_executable(os.path.join(mac_arm_dir, "chrome-headless-shell"))
        set_executable(os.path.join(mac_x64_dir, "chrome-headless-shell"))

    # 3. Windows
    download_and_extract("windows", urls["windows"], windows_dir)

    print("\nLocal setup complete.")
    print("If you want to upload these to Google Storage, run:")
    print(
        f"  (cd {chrome_headless_dir}; upload_to_google_storage.py -a --bucket r8-deps linux)"
    )
    print(
        f"  (cd {chrome_headless_dir}; upload_to_google_storage.py -a --bucket r8-deps mac)"
    )
    print(
        f"  (cd {chrome_headless_dir}; upload_to_google_storage.py -a --bucket r8-deps windows)"
    )


if __name__ == '__main__':
    sys.exit(main())
