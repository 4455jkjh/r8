#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
import argparse
import os
import sys
import zipfile


# Proguard lookup program classes before library classes. In R8 this is
# not the behaviour (it used to be) R8 will check library classes before
# program classes. Some apps have duplicate classes in the library and program.
# To make these apps work with R8 simulate program classes before library
# classes by creating a new library jar which have all the provided library
# classes which are not also in program classes.
def sanitize_libraries_in_pgconf(sanitized_lib_path,
                                 sanitized_pgconf_path,
                                 pgconfs,
                                 injars=None,
                                 libraryjars=None):
    with open(sanitized_pgconf_path, 'w') as sanitized_pgconf:
        injars = [] if injars is None else injars
        libraryjars = [] if libraryjars is None else libraryjars
        for pgconf in pgconfs:
            pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
            first_library_jar = True
            with open(pgconf) as pgconf_file:
                for line in pgconf_file:
                    trimmed = line.strip()
                    if trimmed.startswith('-injars'):
                        # Collect -injars and leave them in the configuration.
                        injar = os.path.join(pgconf_dirname,
                                             trimmed[len('-injars'):].strip())
                        injars.append(injar)
                        sanitized_pgconf.write('-injars {}\n'.format(injar))
                    elif trimmed.startswith('-libraryjars'):
                        # Collect -libraryjars and replace them with the sanitized library.
                        libraryjar = os.path.join(
                            pgconf_dirname,
                            trimmed[len('-libraryjars'):].strip())
                        libraryjars.append(libraryjar)
                        if first_library_jar:
                            sanitized_pgconf.write(
                                '-libraryjars {}\n'.format(sanitized_lib_path))
                            first_library_jar = False
                        sanitized_pgconf.write('# {}'.format(line))
                    else:
                        sanitized_pgconf.write(line)

    sanitize_libraries(sanitized_lib_path, libraryjars, injars)


def sanitize_libraries(sanitized_lib_path, libraryjars, injars):
    program_entries = set()
    library_entries = set()

    for injar in injars:
        with zipfile.ZipFile(injar, 'r') as injar_zf:
            for zipinfo in injar_zf.infolist():
                program_entries.add(zipinfo.filename)

    with zipfile.ZipFile(sanitized_lib_path, 'w') as output_zf:
        for libraryjar in libraryjars:
            with zipfile.ZipFile(libraryjar, 'r') as input_zf:
                for zipinfo in input_zf.infolist():
                    if (zipinfo.filename not in program_entries and
                            zipinfo.filename not in library_entries):
                        library_entries.add(zipinfo.filename)
                        output_zf.writestr(zipinfo, input_zf.read(zipinfo))


def parse_options(argv=None):
    parser = argparse.ArgumentParser(
        description='Create a sanitized library jar and Proguard configuration.'
    )
    parser.add_argument('sanitized_lib',
                        help='Output path for the sanitized library jar.')
    parser.add_argument(
        'sanitized_pgconf',
        help='Output path for the sanitized Proguard configuration.')
    parser.add_argument('--injar',
                        dest='injars',
                        action='append',
                        default=[],
                        metavar='<existing_injar>',
                        help='Program jar input.')
    parser.add_argument('--libraryjar',
                        dest='libraryjars',
                        action='append',
                        default=[],
                        metavar='<existing_library_jar>',
                        help='Library jar input.')
    parser.add_argument('--pgconf',
                        dest='pgconfs',
                        action='append',
                        default=[],
                        metavar='<existing_pgconf>',
                        help='Proguard configuration input.')
    options = parser.parse_args(argv)
    if not (options.pgconfs or options.injars or options.libraryjars):
        parser.error(
            'Expected at least one of --pgconf, --injar, or --libraryjar.')
    return options


def main(argv=None):
    options = parse_options(argv)
    sanitize_libraries_in_pgconf(options.sanitized_lib,
                                 options.sanitized_pgconf, options.pgconfs,
                                 options.injars, options.libraryjars)


if __name__ == '__main__':
    sys.exit(main())
