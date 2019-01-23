#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from distutils.version import LooseVersion
from HTMLParser import HTMLParser
import os
import shutil

import utils

def add_r8_dependency(checkout_dir, temp_dir, minified):
  build_file = os.path.join(checkout_dir, 'build.gradle')
  assert os.path.isfile(build_file), (
      'Expected a file to be present at {}'.format(build_file))

  with open(build_file) as f:
    lines = f.readlines()

  added_r8_dependency = False
  is_inside_dependencies = False

  with open(build_file, 'w') as f:
    gradle_version = None
    for line in lines:
      stripped = line.strip()
      if stripped == 'dependencies {':
        assert not is_inside_dependencies, (
            'Unexpected line with \'dependencies {\'')
        is_inside_dependencies = True
      if is_inside_dependencies:
        if '/r8.jar' in stripped:
          if minified:
            # Skip line to avoid dependency on r8.jar
            continue
          added_r8_dependency = True
        elif '/r8lib.jar' in stripped:
          if not minified:
            # Skip line to avoid dependency on r8lib.jar
            continue
          added_r8_dependency = True
        elif 'com.android.tools.build:gradle:' in stripped:
          gradle_version = stripped[stripped.rindex(':')+1:-1]
          if not added_r8_dependency:
            indent = ''.ljust(line.index('classpath'))
            jar = os.path.join(temp_dir, 'r8lib.jar' if minified else 'r8.jar')
            f.write('{}classpath files(\'{}\')\n'.format(indent, jar))
            added_r8_dependency = True
        elif stripped == '}':
          is_inside_dependencies = False
      f.write(line)

  assert added_r8_dependency, 'Unable to add R8 as a dependency'
  assert gradle_version
  assert LooseVersion(gradle_version) >= LooseVersion('3.2'), (
      'Unsupported gradle version: {} (must use at least gradle '
          + 'version 3.2)').format(gradle_version)

def remove_r8_dependency(checkout_dir):
  build_file = os.path.join(checkout_dir, 'build.gradle')
  assert os.path.isfile(build_file), (
      'Expected a file to be present at {}'.format(build_file))
  with open(build_file) as f:
    lines = f.readlines()
  with open(build_file, 'w') as f:
    for line in lines:
      if ('/r8.jar' not in line) and ('/r8lib.jar' not in line):
        f.write(line)

def GetMinAndCompileSdk(app, config, checkout_dir, apk_reference):

  compile_sdk = config.get('compile_sdk', None)
  min_sdk = config.get('min_sdk', None)

  if not compile_sdk or not min_sdk:
    app_module = config.get('app_module', 'app')
    build_gradle_file = os.path.join(checkout_dir, app_module, 'build.gradle')
    assert os.path.isfile(build_gradle_file), (
        'Expected to find build.gradle file at {}'.format(build_gradle_file))

    # Attempt to find the sdk values from build.gradle.
    with open(build_gradle_file) as f:
      for line in f.readlines():
        stripped = line.strip()
        if stripped.startswith('compileSdkVersion '):
          if 'compile_sdk' not in config:
            assert not compile_sdk
            compile_sdk = int(stripped[len('compileSdkVersion '):])
        elif stripped.startswith('minSdkVersion '):
          if 'min_sdk' not in config:
            assert not min_sdk
            min_sdk = int(stripped[len('minSdkVersion '):])

  assert min_sdk, (
      'Expected to find `minSdkVersion` in {}'.format(build_gradle_file))
  assert compile_sdk, (
      'Expected to find `compileSdkVersion` in {}'.format(build_gradle_file))

  return (min_sdk, compile_sdk)

def IsGradleTaskName(x):
  # Check that it is non-empty.
  if not x:
    return False
  # Check that there is no whitespace.
  for c in x:
    if c.isspace():
      return False
  # Check that the first character following an optional ':' is a lower-case
  # alphabetic character.
  c = x[0]
  if c == ':' and len(x) >= 2:
    c = x[1]
  return c.isalpha() and c.islower()

def IsGradleCompilerTask(x, shrinker):
  if 'r8' in shrinker:
    assert 'transformClassesWithDexBuilderFor' not in x
    assert 'transformDexArchiveWithDexMergerFor' not in x
    return 'transformClassesAndResourcesWithR8For' in x

  assert shrinker == 'proguard'
  return ('transformClassesAndResourcesWithProguard' in x
      or 'transformClassesWithDexBuilderFor' in x
      or 'transformDexArchiveWithDexMergerFor' in x)

def SetPrintConfigurationDirective(app, config, checkout_dir, destination):
  proguard_config_file = FindProguardConfigurationFile(
      app, config, checkout_dir)
  with open(proguard_config_file) as f:
    lines = f.readlines()
  with open(proguard_config_file, 'w') as f:
    for line in lines:
      if '-printconfiguration' not in line:
        f.write(line)
    f.write('-printconfiguration {}\n'.format(destination))

def FindProguardConfigurationFile(app, config, checkout_dir):
  app_module = config.get('app_module', 'app')
  candidates = ['proguard-rules.pro', 'proguard-rules.txt', 'proguard.cfg']
  for candidate in candidates:
    proguard_config_file = os.path.join(checkout_dir, app_module, candidate)
    if os.path.isfile(proguard_config_file):
      return proguard_config_file
  # Currently assuming that the Proguard configuration file can be found at
  # one of the predefined locations.
  assert False

def Move(src, dst):
  print('Moving `{}` to `{}`'.format(src, dst))
  dst_parent = os.path.dirname(dst)
  if not os.path.isdir(dst_parent):
    os.makedirs(dst_parent)
  elif os.path.isdir(dst):
    shutil.rmtree(dst)
  elif os.path.isfile(dst):
    os.remove(dst)
  os.rename(src, dst)

def MoveDir(src, dst):
  assert os.path.isdir(src)
  Move(src, dst)

def MoveFile(src, dst):
  assert os.path.isfile(src)
  Move(src, dst)

def MoveProfileReportTo(dest_dir, build_stdout):
  html_file = None
  profile_message = 'See the profiling report at: '
  for line in build_stdout:
    if profile_message in line:
      html_file = line[len(profile_message):]
      if html_file.startswith('file://'):
        html_file = html_file[len('file://'):]
      break

  if not html_file:
    return

  assert os.path.isfile(html_file), 'Expected to find HTML file at {}'.format(
      html_file)
  MoveFile(html_file, os.path.join(dest_dir, 'index.html'))

  html_dir = os.path.dirname(html_file)
  for dir_name in ['css', 'js']:
    MoveDir(os.path.join(html_dir, dir_name), os.path.join(dest_dir, dir_name))

def ParseProfileReport(profile_dir):
  html_file = os.path.join(profile_dir, 'index.html')
  assert os.path.isfile(html_file)

  parser = ProfileReportParser()
  with open(html_file) as f:
    for line in f.readlines():
      parser.feed(line)
  return parser.result

# A simple HTML parser that recognizes the following pattern:
#
# <tr>
# <td class="indentPath">:app:transformClassesAndResourcesWithR8ForRelease</td>
# <td class="numeric">3.490s</td>
# <td></td>
# </tr>
class ProfileReportParser(HTMLParser):
  def __init__(self):
    HTMLParser.__init__(self)
    self.entered_table_row = False
    self.entered_task_name_cell = False
    self.entered_duration_cell = False

    self.current_task_name = None
    self.current_duration = None

    self.result = {}

  def handle_starttag(self, tag, attrs):
    entered_table_row_before = self.entered_table_row
    entered_task_name_cell_before = self.entered_task_name_cell

    self.entered_table_row = (tag == 'tr')
    self.entered_task_name_cell = (tag == 'td' and entered_table_row_before)
    self.entered_duration_cell = (
        self.current_task_name
            and tag == 'td'
            and entered_task_name_cell_before)

  def handle_endtag(self, tag):
    if tag == 'tr':
      if self.current_task_name and self.current_duration:
        self.result[self.current_task_name] = self.current_duration
      self.current_task_name = None
      self.current_duration = None
    self.entered_table_row = False

  def handle_data(self, data):
    stripped = data.strip()
    if not stripped:
      return
    if self.entered_task_name_cell:
      if IsGradleTaskName(stripped):
        self.current_task_name = stripped
    elif self.entered_duration_cell and stripped.endswith('s'):
      duration = stripped[:-1]
      if 'm' in duration:
        tmp = duration.split('m')
        minutes = int(tmp[0])
        seconds = float(tmp[1])
      else:
        minutes = 0
        seconds = float(duration)
      self.current_duration = 60 * minutes + seconds
    self.entered_table_row = False
