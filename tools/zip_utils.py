# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import shutil
import utils
import zipfile

def add_file_to_zip(file, destination, zip_file_path):
    with zipfile.ZipFile(zip_file_path, 'a') as zip_file:
        zip_file.write(file, destination)

def extract_all_that_matches(zip_file_path, destination, predicate):
    with zipfile.ZipFile(zip_file_path) as zip_file:
        names_to_extract = [name for name in zip_file.namelist() if predicate(name)]
        zip_file.extractall(path=destination, members=names_to_extract)
        return names_to_extract

def extract_member(zip_file_path, member, destination):
    with zipfile.ZipFile(zip_file_path) as zip_file:
        with utils.TempDir() as temp:
            zip_file.extract(member, path=temp)
            shutil.move(os.path.join(temp, member), destination)

def get_names_that_matches(zip_file_path, predicate):
    with zipfile.ZipFile(zip_file_path) as zip_file:
        return [name for name in zip_file.namelist() if predicate(name)]

def remove_files_from_zip(files, zip_file_path):
    with utils.TempDir() as temp:
        zip_out_name = os.path.join(temp, 'temp.zip')
        with zipfile.ZipFile (zip_file_path, 'r') as zip_in:
            with zipfile.ZipFile (zip_out_name, 'w', zip_in.compression) as zip_out:
                for item in zip_in.infolist():
                    buffer = zip_in.read(item.filename)
                    if not item.filename in files:
                        zip_out.writestr(item, buffer)
                    else:
                        print("Removing " + item.filename)
        shutil.move(zip_out_name, zip_file_path)
