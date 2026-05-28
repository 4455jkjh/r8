#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import subprocess
import argparse
import tempfile
import zipfile
from multiprocessing import Pool, cpu_count


def decompile_file(args):
    """Decompiles a single class file using javap -v -p."""
    class_file, output_file = args

    # Ensure the output directory exists. os.makedirs(exist_ok=True) is process-safe
    # in Python 3.2+, so multiple workers can call this without a race condition.
    os.makedirs(os.path.dirname(output_file), exist_ok=True)

    try:
        with open(output_file, 'w') as f:
            subprocess.run(['javap', '-v', '-p', class_file],
                           stdout=f,
                           stderr=subprocess.PIPE,
                           check=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Error decompiling {class_file}: {e.stderr.decode().strip()}")
        return False
    except Exception as e:
        print(f"Unexpected error with {class_file}: {e}")
        return False


def process_classes(classes_dir, output_dir, jobs):
    """Processes all class files in the given directory."""
    tasks = []
    for root, _, files in os.walk(classes_dir):
        for file in files:
            if file.endswith(".class"):
                class_path = os.path.join(root, file)
                rel_path = os.path.relpath(class_path, classes_dir)
                output_path = os.path.join(
                    output_dir,
                    os.path.splitext(rel_path)[0] + ".javap")
                tasks.append((class_path, output_path))

    if not tasks:
        print("No .class files found.")
        return

    print(
        f"Found {len(tasks)} files. Decompiling using {jobs} parallel jobs...")

    with Pool(jobs) as pool:
        results = pool.map(decompile_file, tasks)

    success_count = sum(results)
    print(
        f"Finished. Successfully decompiled {success_count} / {len(tasks)} files."
    )


def main():
    parser = argparse.ArgumentParser(
        description="Decompile Java class files using javap -v -p.")
    parser.add_argument(
        "classes", help="Directory containing .class files or a .jar file.")
    parser.add_argument("output",
                        help="Directory to save decompiled .javap files.")
    parser.add_argument("-j",
                        "--jobs",
                        type=int,
                        default=cpu_count(),
                        help="Number of parallel jobs (default: all CPUs).")

    args = parser.parse_args()

    input_path = os.path.abspath(args.classes)
    output_dir = os.path.abspath(args.output)

    if not os.path.exists(input_path):
        print(f"Error: Input path '{input_path}' does not exist.")
        return

    if os.path.isfile(input_path) and input_path.endswith(".jar"):
        print(f"Input is a JAR file. Extracting {input_path}...")
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                with zipfile.ZipFile(input_path, 'r') as jar:
                    jar.extractall(tmp_dir)
                process_classes(tmp_dir, output_dir, args.jobs)
            except zipfile.BadZipFile:
                print(f"Error: '{input_path}' is not a valid JAR/ZIP file.")
    elif os.path.isdir(input_path):
        process_classes(input_path, output_dir, args.jobs)
    else:
        print(f"Error: '{input_path}' is neither a directory nor a .jar file.")


if __name__ == "__main__":
    main()
