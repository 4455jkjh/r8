#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import datetime
import os.path
import re
import shutil
import subprocess
import sys
import tempfile
import update_prebuilds_in_android
import urllib
import utils

def update_prebuilds(version, checkout):
  update_prebuilds_in_android.main_download('', True, 'lib', checkout, version)


def release_studio_or_aosp(path, options, git_message):
  with utils.ChangedWorkingDirectory(path):
    subprocess.call(['repo', 'abandon', 'update-r8'])
    if not options.no_sync:
      subprocess.check_call(['repo', 'sync'])

    prebuilts_r8 = os.path.join(path, 'prebuilts', 'r8')

    with utils.ChangedWorkingDirectory(prebuilts_r8):
      subprocess.check_call(['repo', 'start', 'update-r8'])

    update_prebuilds(options.version, path)

    with utils.ChangedWorkingDirectory(prebuilts_r8):
      subprocess.check_call(['git', 'commit', '-a', '-m', git_message])
      process = subprocess.Popen(['repo', 'upload', '.', '--verify'],
                                 stdin=subprocess.PIPE)
      return process.communicate(input='y\n')[0]


def prepare_aosp(args):
  aosp = raw_input('Input the path for the AOSP checkout:\n')
  assert os.path.exists(aosp), "Could not find AOSP path %s" % aosp

  def release_aosp(options):
    print "Releasing for AOSP"
    git_message = ("""Update D8 and R8 to %s

Version: master %s
This build IS NOT suitable for preview or public release.

Built here: go/r8-releases/raw/%s

Test: TARGET_PRODUCT=aosp_arm64 m -j core-oj"""
                   % (args.version, args.version, args.version))
    return release_studio_or_aosp(aosp, options, git_message)

  return release_aosp


def git_message_dev(version):
  return """Update D8 R8 master to %s

This is a development snapshot, it's fine to use for studio canary build, but
not for BETA or release, for those we would need a release version of R8
binaries.
This build IS suitable for preview release but IS NOT suitable for public release.

Built here: go/r8-releases/raw/%s
Test: ./gradlew check
Bug: """ % (version, version)


def prepare_studio(args):
  studio = raw_input('Input the path for the STUDIO checkout:\n')
  assert os.path.exists(studio), "Could not find STUDIO path %s" % studio

  def release_studio(options):
    print "Releasing for STUDIO"
    return release_studio_or_aosp(studio, options, git_message_dev(options.version))

  return release_studio


def g4_cp(old, new, file):
  subprocess.check_call('g4 cp {%s,%s}/%s' % (old, new, file), shell=True)


def g4_open(file):
  subprocess.check_call('g4 open %s' % file, shell=True)


def g4_add(files):
  subprocess.check_call(' '.join(['g4', 'add'] + files), shell=True)


def g4_change(version, r8version):
  return subprocess.check_output(
      'g4 change --desc "Update R8 to version %s %s"' % (version, r8version), shell=True)


def sed(pattern, replace, path):
  with open(path, "r") as sources:
    lines = sources.readlines()
  with open(path, "w") as sources:
    for line in lines:
      sources.write(re.sub(pattern, replace, line))


def download_file(version, file, dst):
  urllib.urlretrieve(
      ('http://storage.googleapis.com/r8-releases/raw/%s/%s' % (version, file)),
      dst)


def blaze_run(target):
  return subprocess.check_output(
      'blaze run %s' % target, shell=True, stderr=subprocess.STDOUT)


def prepare_google3(args):
  utils.check_prodacces()

  # Check if an existing client exists.
  if ':update-r8:' in subprocess.check_output('g4 myclients', shell=True):
    print "Remove the existing 'update-r8' client before continuing."
    sys.exit(1)

  def release_google3(options):
    print "Releasing for Google 3"

    google3_base = subprocess.check_output(
        ['p4', 'g4d', '-f', 'update-r8']).rstrip()
    third_party_r8 = os.path.join(google3_base, 'third_party', 'java', 'r8')

    # Check if new version folder is already created
    today = datetime.date.today()
    new_version='v%d%02d%02d' % (today.year, today.month, today.day)
    new_version_path = os.path.join(third_party_r8, new_version)

    if os.path.exists(new_version_path):
      shutil.rmtree(new_version_path)

    # Remove old version
    old_versions = []
    for name in os.listdir(third_party_r8):
      if os.path.isdir(os.path.join(third_party_r8, name)):
        old_versions.append(name)
    old_versions.sort()

    if len(old_versions) >= 2:
      shutil.rmtree(os.path.join(third_party_r8, old_versions[0]))

    # Take current version to copy from
    old_version=old_versions[-1]

    # Create new version
    assert not os.path.exists(new_version_path)
    os.mkdir(new_version_path)

    with utils.ChangedWorkingDirectory(third_party_r8):
      g4_cp(old_version, new_version, 'BUILD')
      g4_cp(old_version, new_version, 'LICENSE')
      g4_cp(old_version, new_version, 'METADATA')

      with utils.ChangedWorkingDirectory(new_version_path):
        # update METADATA
        g4_open('METADATA')
        sed(r'[1-9]\.[0-9]{1,2}\.[0-9]{1,3}-dev',
            options.version,
            os.path.join(new_version_path, 'METADATA'))
        sed(r'\{ year.*\}',
            ('{ year: %i month: %i day: %i }'
             % (today.year, today.month, today.day))
            , os.path.join(new_version_path, 'METADATA'))

        # update BUILD (is not necessary from v20190923)
        g4_open('BUILD')
        sed(old_version, new_version, os.path.join(new_version_path, 'BUILD'))

        # download files
        download_file(options.version, 'r8-full-exclude-deps.jar', 'r8.jar')
        download_file(options.version, 'r8-src.jar', 'r8-src.jar')
        download_file(options.version, 'r8lib-exclude-deps.jar', 'r8lib.jar')
        download_file(
            options.version, 'r8lib-exclude-deps.jar.map', 'r8lib.jar.map')
        g4_add(['r8.jar', 'r8-src.jar', 'r8lib.jar', 'r8lib.jar.map'])

      subprocess.check_output('chmod u+w %s/*' % new_version, shell=True)

      g4_open('BUILD')
      sed(old_version, new_version, os.path.join(third_party_r8, 'BUILD'))

    with utils.ChangedWorkingDirectory(google3_base):
      blaze_result = blaze_run('//third_party/java/r8:d8 -- --version')

      assert options.version in blaze_result

      return g4_change(new_version, options.version)

  return release_google3


def parse_options():
  result = argparse.ArgumentParser(description='Release r8')
  result.add_argument('--targets',
                      help='Where to release a new version.',
                      choices=['all', 'aosp', 'studio', 'google3'],
                      required=True,
                      action='append')
  result.add_argument('--version',
                      required=True,
                      help='The new version of R8 (e.g., 1.4.51)')
  result.add_argument('--no_sync',
                      default=False,
                      action='store_true',
                      help='Do not sync repos before uploading')
  args = result.parse_args()
  if 'all' in args.targets:
    args.targets = ['aosp', 'studio', 'google3']
  return args


def main():
  args = parse_options()
  targets_to_run = []
  if 'google3' in args.targets:
    targets_to_run.append(prepare_google3(args))
  if 'studio' in args.targets:
    targets_to_run.append(prepare_studio(args))
  if 'aosp' in args.targets:
    targets_to_run.append(prepare_aosp(args))

  final_results = []
  for target_closure in targets_to_run:
    final_results.append(target_closure(args))

  print '\n\n**************************************************************'
  print 'PRINTING SUMMARY'
  print '**************************************************************\n\n'

  for result in final_results:
    if result is not None:
      print result


if __name__ == '__main__':
  sys.exit(main())

