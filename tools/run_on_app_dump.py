#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import adb
import apk_masseur
import as_utils
import compiledump
import gradle
import optparse
import os
import shutil
import sys
import time
import update_prebuilds_in_android
import utils
import zipfile

from datetime import datetime

SHRINKERS = ['r8', 'r8-full', 'r8-nolib', 'r8-nolib-full']

class AttrDict(dict):
  def __getattr__(self, name):
    return self.get(name, None)

# To generate the files for a new app, navigate to the app source folder and
# run:
# ./gradlew clean :app:assembleRelease -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# and store the dump and the apk.
# If the app has instrumented tests, adding `testBuildType "release"` and
# running:
# ./gradlew assembleAndroidTest -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# will also generate dumps and apk for tests.

class App(object):
  def __init__(self, fields):
    defaults = {
      'id': None,
      'name': None,
      'collections': [],
      'dump_app': None,
      'apk_app': None,
      'dump_test': None,
      'apk_test': None,
      'skip': False,
      'url': None,  # url is not used but nice to have for updating apps
      'revision': None,
      'folder': None,
      'skip_recompilation': False,
      'compiler_properties': [],
    }
    # This below does not work in python3
    defaults.update(fields.items())
    self.__dict__ = defaults


class AppCollection(object):
  def __init__(self, fields):
    defaults = {
      'name': None
    }
    # This below does not work in python3
    defaults.update(fields.items())
    self.__dict__ = defaults


APPS = [
  App({
    'id': 'com.numix.calculator',
    'name': 'Calculator',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    # Compiling tests fail: Library class android.content.res.XmlResourceParser
    # implements program class org.xmlpull.v1.XmlPullParser. Nothing to really
    # do about that.
    'id_test': 'com.numix.calculator.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/numixproject/android-suite/tree/master/Calculator',
    'revision': 'f58e1b53f7278c9b675d5855842c6d8a44cccb1f',
    'folder': 'android-suite-calculator',
  }),
  App({
    'id': 'dev.dworks.apps.anexplorer.pro',
    'name': 'AnExplorer',
    'dump_app': 'dump_app.zip',
    'apk_app': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
    'url': 'https://github.com/christofferqa/AnExplorer',
    'revision': '365927477b8eab4052a1882d5e358057ae3dee4d',
    'folder': 'anexplorer',
  }),
  App({
    'id': 'de.danoeh.antennapod',
    'name': 'AntennaPod',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-free-release.apk',
    # TODO(b/172452102): Tests and monkey do not work
    'id_test': 'de.danoeh.antennapod.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-free-release-androidTest.apk',
    'url': 'https://github.com/christofferqa/AntennaPod.git',
    'revision': '77e94f4783a16abe9cc5b78dc2d2b2b1867d8c06',
    'folder': 'antennapod',
  }),
  App({
    'id': 'com.example.applymapping',
    'name': 'applymapping',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'id_test': 'com.example.applymapping.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/mkj-gram/applymapping',
    'revision': 'e3ae14b8c16fa4718e5dea8f7ad00937701b3c48',
    'folder': 'applymapping',
  }),
  App({
    'id': 'com.chanapps.four.activity',
    'name': 'chanu',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/mkj-gram/chanu.git',
    'revision': '6e53458f167b6d78398da60c20fd0da01a232617',
    'folder': 'chanu',
    # The app depends on a class file that has access flags interface but
    # not abstract
    'compiler_properties': ['-Dcom.android.tools.r8.allowInvalidCfAccessFlags=true']
  }),
  # TODO(b/172539375): Monkey runner fails on recompilation.
  App({
    'id': 'com.google.firebase.example.fireeats',
    'name': 'FriendlyEats',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/firebase/friendlyeats-android',
    'revision': '7c6dd016fc31ea5ecb948d5166b8479efc3775cc',
    'folder': 'friendlyeats',
  }),
  App({
    'id': 'com.google.samples.apps.sunflower',
    'name': 'Sunflower',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-debug.apk',
    # TODO(b/172549283): Compiling tests fails
    'id_test': 'com.google.samples.apps.sunflower.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-debug-androidTest.apk',
    'url': 'https://github.com/android/sunflower',
    'revision': '0c4c88fdad2a74791199dffd1a6559559b1dbd4a',
    'folder': 'sunflower',
  }),
  # TODO(b/172565385): Monkey runner fails on recompilation
  App({
    'id': 'com.google.samples.apps.iosched',
    'name': 'iosched',
    'dump_app': 'dump_app.zip',
    'apk_app': 'mobile-release.apk',
    'url': 'https://github.com/christofferqa/iosched.git',
    'revision': '581cbbe2253711775dbccb753cdb53e7e506cb02',
    'folder': 'iosched',
  }),
  App({
    'id': 'fr.neamar.kiss',
    'name': 'KISS',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    # TODO(b/172569220): Running tests fails due to missing keep rules
    'id_test': 'fr.neamar.kiss.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/Neamar/KISS',
    'revision': '8ccffaadaf0d0b8fc4418ed2b4281a0935d3d971',
    'folder': 'kiss',
  }),
  # TODO(b/172577344): Monkey runner not working.
  App({
    'id': 'io.github.hidroh.materialistic',
    'name': 'materialistic',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/materialistic.git',
    'revision': '2b2b2ee25ce9e672d5aab1dc90a354af1522b1d9',
    'folder': 'materialistic',
  }),
  App({
    'id': 'com.avjindersinghsekhon.minimaltodo',
    'name': 'MinimalTodo',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/Minimal-Todo',
    'revision': '9d8c73746762cd376b718858ec1e8783ca07ba7c',
    'folder': 'minimal-todo',
  }),
  App({
    'id': 'net.nurik.roman.muzei',
    'name': 'muzei',
    'dump_app': 'dump_app.zip',
    'apk_app': 'muzei-release.apk',
    'url': 'https://github.com/romannurik/muzei',
    'revision': '9eac6e98aebeaf0ae40bdcd85f16dd2886551138',
    'folder': 'muzei',
  }),
  # TODO(b/172806281): Monkey runner does not work.
  App({
    'id': 'org.schabi.newpipe',
    'name': 'NewPipe',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/TeamNewPipe/NewPipe',
    'revision': 'f4435f90313281beece70c544032f784418d85fa',
    'folder': 'newpipe',
  }),
  # TODO(b/172806808): Monkey runner does not work.
  App({
    'id': 'io.rover.app.debug',
    'name': 'Rover',
    'dump_app': 'dump_app.zip',
    'apk_app': 'example-app-release-unsigned.apk',
    'url': 'https://github.com/RoverPlatform/rover-android',
    'revision': '94342117097770ea3ca2c6df6ab496a1a55c3ce7',
    'folder': 'rover-android',
  }),
  # TODO(b/172808159): Monkey runner does not work
  App({
    'id': 'com.google.android.apps.santatracker',
    'name': 'SantaTracker',
    'dump_app': 'dump_app.zip',
    'apk_app': 'santa-tracker-release.apk',
    'url': 'https://github.com/christofferqa/santa-tracker-android',
    'revision': '8dee74be7d9ee33c69465a07088c53087d24a6dd',
    'folder': 'santa-tracker',
  }),
  App({
    'id': 'org.thoughtcrime.securesms',
    'name': 'Signal',
    'dump_app': 'dump_app.zip',
    'apk_app': 'Signal-Android-play-prod-universal-release-4.76.2.apk',
    # TODO(b/172812839): Instrumentation test fails.
    'id_test': 'org.thoughtcrime.securesms.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'Signal-Android-play-prod-release-androidTest.apk',
    'url': 'https://github.com/signalapp/Signal-Android',
    'revision': '91ca19f294362ccee2c2b43c247eba228e2b30a1',
    'folder': 'signal-android',
  }),
  # TODO(b/172815827): Monkey runner does not work
  App({
    'id': 'com.simplemobiletools.calendar.pro',
    'name': 'Simple-Calendar',
    'dump_app': 'dump_app.zip',
    'apk_app': 'calendar-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Calendar',
    'revision': '906209874d0a091c7fce5a57972472f272d6b068',
    'folder': 'simple-calendar',
  }),
  # TODO(b/172815534): Monkey runner does not work
  App({
    'id': 'com.simplemobiletools.camera.pro',
    'name': 'Simple-Camera',
    'dump_app': 'dump_app.zip',
    'apk_app': 'camera-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Camera',
    'revision': 'ebf9820c51e960912b3238287e30a131244fdee6',
    'folder': 'simple-camera',
  }),
  App({
    'id': 'com.simplemobiletools.filemanager.pro',
    'name': 'Simple-File-Manager',
    'dump_app': 'dump_app.zip',
    'apk_app': 'file-manager-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-File-Manager',
    'revision': '2b7fa68ea251222cc40cf6d62ad1de260a6f54d9',
    'folder': 'simple-file-manager',
  }),
  App({
    'id': 'com.simplemobiletools.gallery.pro',
    'name': 'Simple-Gallery',
    'dump_app': 'dump_app.zip',
    'apk_app': 'gallery-326-foss-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Gallery',
    'revision': '564e56b20d33b28d0018c8087ec705beeb60785e',
    'folder': 'simple-gallery',
  }),
  App({
    'id': 'com.example.sqldelight.hockey',
    'name': 'SQLDelight',
    'dump_app': 'dump_app.zip',
    'apk_app': 'android-release.apk',
    'url': 'https://github.com/christofferqa/sqldelight',
    'revision': '2e67a1126b6df05e4119d1e3a432fde51d76cdc8',
    'folder': 'sqldelight',
  }),
  # TODO(b/172824096): Monkey runner does not work.
  App({
    'id': 'eu.kanade.tachiyomi',
    'name': 'Tachiyomi',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-dev-release.apk',
    'url': 'https://github.com/inorichi/tachiyomi',
    'revision': '8aa6486bf76ab9a61a5494bee284b1a5e9180bf3',
    'folder': 'tachiyomi',
  }),
  # TODO(b/172862042): Monkey runner does not work.
  App({
    'id': 'app.tivi',
    'name': 'Tivi',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/chrisbanes/tivi',
    'revision': '5c6d9ed338885c59b1fc64050d92d056417bb4de',
    'folder': 'tivi',
  }),
  App({
    'id': 'com.keylesspalace.tusky',
    'name': 'Tusky',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-blue-release.apk',
    'url': 'https://github.com/tuskyapp/Tusky',
    'revision': '814a9b8f9bacf8d26f712b06a0313a3534a2be95',
    'folder': 'tusky',
  }),
  App({
    'id': 'org.wikipedia',
    'name': 'Wikipedia',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-prod-release.apk',
    'url': 'https://github.com/wikimedia/apps-android-wikipedia',
    'revision': '0fa7cad843c66313be8e25790ef084cf1a1fa67e',
    'folder': 'wikipedia',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'androidx.compose.samples.crane',
    'name': 'compose-crane',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/crane',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetcaster',
    'name': 'compose-jetcaster',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetcaster',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.jetchat',
    'name': 'compose-jetchat',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetchat',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetnews',
    'name': 'compose-jetnews',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetnews',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetsnack',
    'name': 'compose-jetsnack',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetsnack',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.jetsurvey',
    'name': 'compose-jetsurvey',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetsurvey',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.owl',
    'name': 'compose-owl',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/owl',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.rally',
    'name': 'compose-rally',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/rally',
    # TODO(b/173176042): Fix recompilation
    'skip_recompilation': True,
  }),
]


APP_COLLECTIONS = [
  AppCollection({
    'name': 'compose-samples',
  })
]


def remove_print_lines(file):
  with open(file) as f:
    lines = f.readlines()
  with open(file, 'w') as f:
    for line in lines:
      if '-printconfiguration' not in line:
        f.write(line)


def download_app(app_sha):
  utils.DownloadFromGoogleCloudStorage(app_sha)


def is_logging_enabled_for(app, options):
  if options.no_logging:
    return False
  if options.app_logging_filter and app.name not in options.app_logging_filter:
    return False
  return True


def is_minified_r8(shrinker):
  return '-nolib' not in shrinker


def is_full_r8(shrinker):
  return '-full' in shrinker


def version_is_built_jar(version):
  return version != 'master' and version != 'source'


def compute_size_of_dex_files_in_package(path):
  dex_size = 0
  z = zipfile.ZipFile(path, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size


def dump_for_app(app_dir, app):
  return os.path.join(app_dir, app.dump_app)


def dump_test_for_app(app_dir, app):
  return os.path.join(app_dir, app.dump_test)


def get_r8_jar(options, temp_dir, shrinker):
  if (options.version == 'source'):
    return None
  return os.path.join(
      temp_dir, 'r8lib.jar' if is_minified_r8(shrinker) else 'r8.jar')


def get_results_for_app(app, options, temp_dir):
  app_folder = app.folder if app.folder else app.name + "_" + app.revision
  app_dir = os.path.join(utils.OPENSOURCE_DUMPS_DIR, app_folder)

  if not os.path.exists(app_dir) and not options.golem:
    # Download the app from google storage.
    download_app(app_dir + ".tar.gz.sha1")

  # Ensure that the dumps are in place
  assert os.path.isfile(dump_for_app(app_dir, app)), "Could not find dump " \
                                                     "for app " + app.name

  result = {}
  result['status'] = 'success'
  result_per_shrinker = build_app_with_shrinkers(
    app, options, temp_dir, app_dir)
  for shrinker, shrinker_result in result_per_shrinker.items():
    result[shrinker] = shrinker_result
  return result


def build_app_with_shrinkers(app, options, temp_dir, app_dir):
  result_per_shrinker = {}
  for shrinker in options.shrinker:
    results = []
    build_app_and_run_with_shrinker(
      app, options, temp_dir, app_dir, shrinker, results)
    result_per_shrinker[shrinker] = results
  if len(options.apps) > 1:
    print('')
    log_results_for_app(app, result_per_shrinker, options)
    print('')

  return result_per_shrinker


def is_last_build(index, compilation_steps):
  return index == compilation_steps - 1


def build_app_and_run_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                                    results):
  print('[{}] Building {} with {}'.format(
    datetime.now().strftime("%H:%M:%S"),
    app.name,
    shrinker))
  print('To compile locally: '
        'tools/run_on_app_dump.py --shrinker {} --r8-compilation-steps {} '
        '--app {}'.format(
    shrinker,
    options.r8_compilation_steps,
    app.name))
  print('HINT: use --shrinker r8-nolib --no-build if you have a local R8.jar')
  recomp_jar = None
  status = 'success'
  if options.r8_compilation_steps < 1:
    return
  compilation_steps = 1 if app.skip_recompilation else options.r8_compilation_steps
  for compilation_step in range(0, compilation_steps):
    if status != 'success':
      break
    print('Compiling {} of {}'.format(compilation_step + 1, compilation_steps))
    result = {}
    try:
      start = time.time()
      (app_jar, mapping, new_recomp_jar) = \
        build_app_with_shrinker(
          app, options, temp_dir, app_dir, shrinker, compilation_step,
          compilation_steps, recomp_jar)
      end = time.time()
      dex_size = compute_size_of_dex_files_in_package(app_jar)
      result['build_status'] = 'success'
      result['recompilation_status'] = 'success'
      result['output_jar'] = app_jar
      result['output_mapping'] = mapping
      result['dex_size'] = dex_size
      result['duration'] = int((end - start) * 1000)  # Wall time
      if (new_recomp_jar is None
          and not is_last_build(compilation_step, compilation_steps)):
        result['recompilation_status'] = 'failed'
        warn('Failed to build {} with {}'.format(app.name, shrinker))
      recomp_jar = new_recomp_jar
    except Exception as e:
      warn('Failed to build {} with {}'.format(app.name, shrinker))
      if e:
        print('Error: ' + str(e))
      result['build_status'] = 'failed'
      status = 'failed'

    original_app_apk = os.path.join(app_dir, app.apk_app)
    app_apk_destination = os.path.join(
      temp_dir,"{}_{}.apk".format(app.id, compilation_step))

    if result.get('build_status') == 'success' and options.monkey:
      # Make a copy of the given APK, move the newly generated dex files into the
      # copied APK, and then sign the APK.
      apk_masseur.masseur(
        original_app_apk, dex=app_jar, resources='META-INF/services/*',
        out=app_apk_destination,
        quiet=options.quiet, logging=is_logging_enabled_for(app, options),
        keystore=options.keystore)

      result['monkey_status'] = 'success' if adb.run_monkey(
        app.id, options.emulator_id, app_apk_destination, options.monkey_events,
        options.quiet, is_logging_enabled_for(app, options)) else 'failed'

    if (result.get('build_status') == 'success'
        and options.run_tests and app.dump_test):
      if not os.path.isfile(app_apk_destination):
        apk_masseur.masseur(
          original_app_apk, dex=app_jar, resources='META-INF/services/*',
          out=app_apk_destination,
          quiet=options.quiet, logging=is_logging_enabled_for(app, options),
          keystore=options.keystore)

      # Compile the tests with the mapping file.
      test_jar = build_test_with_shrinker(
        app, options, temp_dir, app_dir,shrinker, compilation_step,
        result['output_mapping'])
      if not test_jar:
        result['instrumentation_test_status'] = 'compilation_failed'
      else:
        original_test_apk = os.path.join(app_dir, app.apk_test)
        test_apk_destination = os.path.join(
          temp_dir,"{}_{}.test.apk".format(app.id_test, compilation_step))
        apk_masseur.masseur(
          original_test_apk, dex=test_jar, resources='META-INF/services/*',
          out=test_apk_destination,
          quiet=options.quiet, logging=is_logging_enabled_for(app, options),
          keystore=options.keystore)
        result['instrumentation_test_status'] = 'success' if adb.run_instrumented(
          app.id, app.id_test, options.emulator_id, app_apk_destination,
          test_apk_destination, options.quiet,
          is_logging_enabled_for(app, options)) else 'failed'

    results.append(result)
    if result.get('recompilation_status') != 'success':
      break


def build_app_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                            compilation_step_index, compilation_steps,
                            prev_recomp_jar):

  args = AttrDict({
    'dump': dump_for_app(app_dir, app),
    'r8_jar': get_r8_jar(options, temp_dir, shrinker),
    'ea': False if options.disable_assertions else True,
    'version': options.version,
    'compiler': 'r8full' if is_full_r8(shrinker) else 'r8',
    'debug_agent': options.debug_agent,
    'program_jar': prev_recomp_jar,
    'nolib': not is_minified_r8(shrinker),
    'config_file_consumer': remove_print_lines,
    'properties': app.compiler_properties,
    'disable_desugared_lib': False,
  })

  app_jar = os.path.join(
    temp_dir, '{}_{}_{}_dex_out.jar'.format(
      app.name, shrinker, compilation_step_index))
  app_mapping = os.path.join(
    temp_dir, '{}_{}_{}_dex_out.jar.map'.format(
      app.name, shrinker, compilation_step_index))
  recomp_jar = None

  with utils.TempDir() as compile_temp_dir:
    compile_result = compiledump.run1(compile_temp_dir, args, [])
    out_jar = os.path.join(compile_temp_dir, "out.jar")
    out_mapping = os.path.join(compile_temp_dir, "out.jar.map")

    if compile_result != 0 or not os.path.isfile(out_jar):
      assert False, 'Compilation of {} failed'.format(dump_for_app(app_dir, app))
    shutil.move(out_jar, app_jar)
    shutil.move(out_mapping, app_mapping)

    if compilation_step_index < compilation_steps - 1:
      args['classfile'] = True
      args['min_api'] = "10000"
      args['disable_desugared_lib'] = True
      compile_result = compiledump.run1(compile_temp_dir, args, [])
      if compile_result == 0:
        recomp_jar = os.path.join(
          temp_dir, '{}_{}_{}_cf_out.jar'.format(
            app.name, shrinker, compilation_step_index))
        shutil.move(out_jar, recomp_jar)

  return (app_jar, app_mapping, recomp_jar)


def build_test_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                             compilation_step_index, mapping):

  def rewrite_file(file):
    remove_print_lines(file)
    with open(file) as f:
      lines = f.readlines()
    with open(file, 'w') as f:
      for line in lines:
        if '-applymapping' not in line:
          f.write(line + '\n')
      f.write("-applymapping " + mapping + '\n')

  args = AttrDict({
    'dump': dump_test_for_app(app_dir, app),
    'r8_jar': get_r8_jar(options, temp_dir, shrinker),
    'ea': False if options.disable_assertions else True,
    'version': options.version,
    'compiler': 'r8full' if is_full_r8(shrinker) else 'r8',
    'debug_agent': options.debug_agent,
    'nolib': not is_minified_r8(shrinker),
    # The config file will have an -applymapping reference to an old map.
    # Update it to point to mapping file build in the compilation of the app.
    'config_file_consumer': rewrite_file
  })

  test_jar = os.path.join(
    temp_dir, '{}_{}_{}_test_out.jar'.format(
      app.name, shrinker, compilation_step_index))

  with utils.TempDir() as compile_temp_dir:
    compile_result = compiledump.run1(compile_temp_dir, args, [])
    out_jar = os.path.join(compile_temp_dir, "out.jar")
    if compile_result != 0 or not os.path.isfile(out_jar):
      return None
    shutil.move(out_jar, test_jar)

  return test_jar


def log_results_for_apps(result_per_shrinker_per_app, options):
  print('')
  app_errors = 0
  for (app, result_per_shrinker) in result_per_shrinker_per_app:
    app_errors += (1 if log_results_for_app(app, result_per_shrinker, options)
                   else 0)
  return app_errors


def log_results_for_app(app, result_per_shrinker, options):
  if options.print_dexsegments:
    log_segments_for_app(app, result_per_shrinker, options)
    return False
  else:
    return log_comparison_results_for_app(app, result_per_shrinker, options)


def log_segments_for_app(app, result_per_shrinker, options):
  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    for result in result_per_shrinker.get(shrinker):
      benchmark_name = '{}-{}'.format(options.print_dexsegments, app.name)
      utils.print_dexsegments(benchmark_name, [result.get('output_jar')])
      duration = result.get('duration')
      print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration))
      print('%s-Total(CodeSize): %s' % (benchmark_name, result.get('dex_size')))


def percentage_diff_as_string(before, after):
  if after < before:
    return '-' + str(round((1.0 - after / before) * 100)) + '%'
  else:
    return '+' + str(round((after - before) / before * 100)) + '%'


def log_comparison_results_for_app(app, result_per_shrinker, options):
  print(app.name + ':')
  app_error = False
  if result_per_shrinker.get('status', 'success') != 'success':
    error_message = result_per_shrinker.get('error_message')
    print('  skipped ({})'.format(error_message))
    return

  proguard_result = result_per_shrinker.get('pg', {})
  proguard_dex_size = float(proguard_result.get('dex_size', -1))

  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    compilation_index = 1
    for result in result_per_shrinker.get(shrinker):
      build_status = result.get('build_status')
      if build_status != 'success' and build_status is not None:
        app_error = True
        warn('  {}-#{}: {}'.format(shrinker, compilation_index, build_status))
        continue

      print('  {}-#{}:'.format(shrinker, compilation_index))
      dex_size = result.get('dex_size')
      msg = '    dex size: {}'.format(dex_size)
      if options.print_runtimeraw:
        print('    run time raw: {} ms'.format(result.get('duration')))
      if dex_size != proguard_dex_size and proguard_dex_size >= 0:
        msg = '{} ({}, {})'.format(
          msg, dex_size - proguard_dex_size,
          percentage_diff_as_string(proguard_dex_size, dex_size))
        success(msg) if dex_size < proguard_dex_size else warn(msg)
      else:
        print(msg)

      if options.monkey:
        monkey_status = result.get('monkey_status')
        if monkey_status != 'success':
          app_error = True
          warn('    monkey: {}'.format(monkey_status))
        else:
          success('    monkey: {}'.format(monkey_status))

      if options.run_tests and 'instrumentation_test_status' in result:
        test_status = result.get('instrumentation_test_status')
        if test_status != 'success':
          warn('    instrumentation_tests: {}'.format(test_status))
        else:
          success('    instrumentation_tests: {}'.format(test_status))

      recompilation_status = result.get('recompilation_status', '')
      if recompilation_status == 'failed':
        app_error = True
        warn('    recompilation {}-#{}: failed'.format(shrinker,
                                                       compilation_index))
        continue

      compilation_index += 1

  return app_error


def parse_options(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=[app.name for app in APPS],
                    action='append')
  result.add_option('--app-collection', '--app_collection',
                    help='What app collection to run',
                    choices=[collection.name for collection in APP_COLLECTIONS],
                    action='append')
  result.add_option('--bot',
                    help='Running on bot, use third_party dependency.',
                    default=False,
                    action='store_true')
  result.add_option('--debug-agent',
                    help='Enable Java debug agent and suspend compilation '
                         '(default disabled)',
                    default=False,
                    action='store_true')
  result.add_option('--disable-assertions', '--disable_assertions',
                    help='Disable assertions when compiling',
                    default=False,
                    action='store_true')
  result.add_option('--emulator-id', '--emulator_id',
                    help='Id of the emulator to use',
                    default='emulator-5554')
  result.add_option('--golem',
                    help='Running on golem, do not download',
                    default=False,
                    action='store_true')
  result.add_option('--hash',
                    help='The commit of R8 to use')
  result.add_option('--keystore',
                    help='Path to app.keystore',
                    default=os.path.join(utils.TOOLS_DIR, 'debug.keystore'))
  result.add_option('--keystore-password', '--keystore_password',
                    help='Password for app.keystore',
                    default='android')
  result.add_option('--app-logging-filter', '--app_logging_filter',
                    help='The apps for which to turn on logging',
                    action='append')
  result.add_option('--monkey',
                    help='Whether to install and run app(s) with monkey',
                    default=False,
                    action='store_true')
  result.add_option('--monkey-events', '--monkey_events',
                    help='Number of events that the monkey should trigger',
                    default=250,
                    type=int)
  result.add_option('--no-build', '--no_build',
                    help='Run without building ToT first (only when using ToT)',
                    default=False,
                    action='store_true')
  result.add_option('--no-logging', '--no_logging',
                    help='Disable logging except for errors',
                    default=False,
                    action='store_true')
  result.add_option('--print-dexsegments',
                    metavar='BENCHMARKNAME',
                    help='Print the sizes of individual dex segments as ' +
                         '\'<BENCHMARKNAME>-<APP>-<segment>(CodeSize): '
                         '<bytes>\'')
  result.add_option('--print-runtimeraw',
                    metavar='BENCHMARKNAME',
                    help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                        ' <elapsed> ms\' at the end where <elapsed> is' +
                        ' the elapsed time in milliseconds.')
  result.add_option('--quiet',
                    help='Disable verbose logging',
                    default=False,
                    action='store_true')
  result.add_option('--r8-compilation-steps', '--r8_compilation_steps',
                    help='Number of times R8 should be run on each app',
                    default=2,
                    type=int)
  result.add_option('--run-tests', '--run_tests',
                    help='Whether to run instrumentation tests',
                    default=False,
                    action='store_true')
  result.add_option('--sign-apks', '--sign_apks',
                    help='Whether the APKs should be signed',
                    default=False,
                    action='store_true')
  result.add_option('--shrinker',
                    help='The shrinkers to use (by default, all are run)',
                    action='append')
  result.add_option('--version',
                    default='master',
                    help='The version of R8 to use (e.g., 1.4.51)')
  (options, args) = result.parse_args(argv)

  if options.app or options.app_collection:
    if not options.app:
      options.app = []
    if not options.app_collection:
      options.app_collection = []
    options.apps = [
        app
        for app in APPS
        if app.name in options.app
           or any(collection in options.app_collection
                  for collection in app.collections)]
    del options.app
    del options.app_collection
  else:
    options.apps = APPS

  if options.app_logging_filter:
    for app_name in options.app_logging_filter:
      assert any(app.name == app_name for app in options.apps)
  if options.shrinker:
    for shrinker in options.shrinker:
      assert shrinker in SHRINKERS, (
          'Shrinker must be one of %s' % ', '.join(SHRINKERS))
  else:
    options.shrinker = [shrinker for shrinker in SHRINKERS]

  if options.hash or version_is_built_jar(options.version):
    # No need to build R8 if a specific version should be used.
    options.no_build = True
    if 'r8-nolib' in options.shrinker:
      warn('Skipping shrinker r8-nolib because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib')
    if 'r8-nolib-full' in options.shrinker:
      warn('Skipping shrinker r8-nolib-full because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib-full')
  return (options, args)


def main(argv):
  (options, args) = parse_options(argv)

  if options.bot:
    options.no_logging = True
    options.shrinker = ['r8', 'r8-full']
    print(options.shrinker)

  if options.golem:
    golem.link_third_party()
    options.disable_assertions = True
    options.no_build = True
    options.r8_compilation_steps = 1
    options.quiet = True
    options.no_logging = True

  with utils.TempDir() as temp_dir:
    if options.hash:
      # Download r8-<hash>.jar from
      # https://storage.googleapis.com/r8-releases/raw/.
      target = 'r8-{}.jar'.format(options.hash)
      update_prebuilds_in_android.download_hash(
        temp_dir, 'com/android/tools/r8/' + options.hash, target)
      as_utils.MoveFile(
        os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
        quiet=options.quiet)
    elif version_is_built_jar(options.version):
        # Download r8-<version>.jar from
        # https://storage.googleapis.com/r8-releases/raw/.
        target = 'r8-{}.jar'.format(options.version)
        update_prebuilds_in_android.download_version(
          temp_dir, 'com/android/tools/r8/' + options.version, target)
        as_utils.MoveFile(
          os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
          quiet=options.quiet)
    elif options.version == 'master':
      if not (options.no_build or options.golem):
        gradle.RunGradle(['r8', '-Pno_internal'])
        build_r8lib = False
        for shrinker in options.shrinker:
          if is_minified_r8(shrinker):
            build_r8lib = True
        if build_r8lib:
          gradle.RunGradle(['r8lib', '-Pno_internal'])
      # Make a copy of r8.jar and r8lib.jar such that they stay the same for
      # the entire execution of this script.
      if 'r8-nolib' in options.shrinker or 'r8-nolib-full' in options.shrinker:
        assert os.path.isfile(utils.R8_JAR), 'Cannot build without r8.jar'
        shutil.copyfile(utils.R8_JAR, os.path.join(temp_dir, 'r8.jar'))
      if 'r8' in options.shrinker or 'r8-full' in options.shrinker:
        assert os.path.isfile(utils.R8LIB_JAR), 'Cannot build without r8lib.jar'
        shutil.copyfile(utils.R8LIB_JAR, os.path.join(temp_dir, 'r8lib.jar'))

    result_per_shrinker_per_app = []
    for app in options.apps:
      if app.skip:
        continue
      result_per_shrinker_per_app.append(
        (app, get_results_for_app(app, options, temp_dir)))
    errors = log_results_for_apps(result_per_shrinker_per_app, options)
    if errors > 0:
      dest = 'gs://r8-test-results/r8-libs/' + str(int(time.time()))
      utils.upload_file_to_cloud_storage(os.path.join(temp_dir, 'r8lib.jar'), dest)
      print('R8lib saved to %s' % dest)
    return errors


def success(message):
  CGREEN = '\033[32m'
  CEND = '\033[0m'
  print(CGREEN + message + CEND)


def warn(message):
  CRED = '\033[91m'
  CEND = '\033[0m'
  print(CRED + message + CEND)


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
