# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os

import defines

JDK_DIRS = os.path.join(defines.THIRD_PARTY, 'openjdk')
GRADLE_DAEMON_JVM_PROPERTIES = os.path.join(defines.REPO_ROOT, 'gradle',
                                            'gradle-daemon-jvm.properties')

ALL_JDKS = ['openjdk-9.0.4', 'jdk-11', 'jdk-17', 'jdk-21', 'jdk-25']

DEFAULT_JDK_HOME = None


# This JDK is used for everything except the compilation of the compiler.
def get_default_jdk_home():
    if DEFAULT_JDK_HOME is None:
        with open(GRADLE_DAEMON_JVM_PROPERTIES) as f:
            for line in f:
                line = line.strip()
                if line.startswith('toolchainVersion='):
                    version = line.split('=', 1)[1].strip()
                    return get_jdk_home(f'jdk-{version}')
        raise Exception('No toolchainVersion found in ' +
                        GRADLE_DAEMON_JVM_PROPERTIES)
    return DEFAULT_JDK_HOME


# This JDK is used for compilation and tasks related to compilation of the compiler.
def get_compilation_jdk_home():
    return get_jdk11_home()


def get_jdk_home(name):
    if name == 'jdk8':
        return get_jdk8_home()
    third_party_jdk_root = os.path.join(JDK_DIRS, name)
    if not os.path.exists(third_party_jdk_root):
        raise Exception('No JDKs found in ' + third_party_jdk_root)
    os_root = get_os_java_home(third_party_jdk_root)
    if not os.path.exists(os_root):
        raise Exception('No platform JDK found in ' + os_root)
    return os_root


def get_os_java_home(root):
    if defines.is_linux():
        return os.path.join(root, 'linux')
    elif defines.is_osx():
        return os.path.join(root, 'osx', 'Contents', 'Home')
    elif defines.is_windows():
        return os.path.join(root, 'windows')
    else:
        raise Exception('Unsupported platform'
                        ' (not detected as either of Linux, macOS or Windows)')


def get_all_jdk_dirs():
    dirs = []
    for jdk in ALL_JDKS:
        root = os.path.join(JDK_DIRS, jdk)
        if defines.is_linux():
            root = os.path.join(root, 'linux')
        elif defines.is_osx():
            root = os.path.join(root, 'osx')
        elif defines.is_windows():
            root = os.path.join(root, 'windows')
        else:
            raise Exception(
                'Unsupported platform'
                ' (not detected as either of Linux, macOS or Windows)')
        # Some jdks are not available on windows, don't try to get these.
        if os.path.exists(root + '.tar.gz.sha1'):
            dirs.append(root)
    return dirs


def get_jdk21_home():
    return get_jdk_home('jdk-21')


def get_jdk17_home():
    return get_jdk_home('jdk-17')


def get_jdk11_home():
    return get_jdk_home('jdk-11')


def get_jdk8_home():
    root = os.path.join(JDK_DIRS, 'jdk8')
    if defines.is_linux():
        return os.path.join(root, 'linux-x86')
    elif defines.is_osx():
        return os.path.join(root, 'darwin-x86')
    else:
        return os.environ['JAVA_HOME']


def get_java_executable(jdk_home):
    executable = 'java.exe' if defines.is_windows() else 'java'
    return os.path.join(jdk_home, 'bin', executable)


def get_javac_executable(jdk_home):
    executable = 'javac.exe' if defines.is_windows() else 'javac'
    return os.path.join(jdk_home, 'bin', executable)


def get_jstack_executable(jdk_home):
    executable = 'jstack.exe' if defines.is_windows() else 'jstack'
    return os.path.join(jdk_home, 'bin', executable)
