#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import statistics
import sys
import time

try:
  from perfetto.trace_processor import TraceProcessor
except ImportError:
  sys.exit(
      'Unable to analyze perfetto trace without the perfetto library. '
      'Install instructions:\n'
      '    sudo apt install python3-pip\n'
      '    pip3 install perfetto')

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import adb_utils
import apk_utils
import perfetto_utils
import utils

def setup(options):
  # Increase screen off timeout to avoid device screen turns off.
  twenty_four_hours_in_millis = 24 * 60 * 60 * 1000
  previous_screen_off_timeout = adb_utils.get_screen_off_timeout(
      options.device_id)
  adb_utils.set_screen_off_timeout(
      twenty_four_hours_in_millis, options.device_id)

  # Unlock device.
  adb_utils.unlock(options.device_id, options.device_pin)

  teardown_options = {
    'previous_screen_off_timeout': previous_screen_off_timeout
  }
  return teardown_options

def teardown(options, teardown_options):
  # Reset screen off timeout.
  adb_utils.set_screen_off_timeout(
      teardown_options['previous_screen_off_timeout'],
      options.device_id)

def run_all(apk, options, tmp_dir):
  # Launch app while collecting information.
  data_total = {}
  for iteration in range(1, options.iterations + 1):
    print('Starting iteration %i' % iteration)
    out_dir = os.path.join(options.out_dir, str(iteration))
    teardown_options = setup_for_run(apk, out_dir, options)
    data = run(out_dir, options, tmp_dir)
    teardown_for_run(options, teardown_options)
    add_data(data_total, data)
    print('Result:')
    print(data)
    print(compute_data_summary(data_total))
    print('Done')
  print('Average result:')
  data_summary = compute_data_summary(data_total)
  print(data_summary)
  write_data(options.out_dir, data_summary)

def compute_data_summary(data_total):
  data_summary = {}
  for key, value in data_total.items():
    if not isinstance(value, list):
      data_summary[key] = value
      continue
    data_summary['%s_avg' % key] = round(statistics.mean(value), 1)
    data_summary['%s_med' % key] = statistics.median(value)
  return data_summary

def setup_for_run(apk, out_dir, options):
  adb_utils.root(options.device_id)

  print('Installing')
  adb_utils.uninstall(options.app_id, options.device_id)
  adb_utils.install(apk, options.device_id)
  os.makedirs(out_dir, exist_ok=True)

  # AOT compile.
  if options.aot:
    print('AOT compiling')
    if options.baseline_profile:
      adb_utils.clear_profile_data(options.app_id, options.device_id)
      adb_utils.install_profile(options.app_id, options.device_id)
    else:
      adb_utils.force_compilation(options.app_id, options.device_id)

  # Cooldown and then unlock device.
  if options.cooldown > 0:
    print('Cooling down for %i seconds' % options.cooldown)
    assert adb_utils.get_screen_state(options.device_id).is_off()
    time.sleep(options.cooldown)
    teardown_options = adb_utils.prepare_for_interaction_with_device(
        options.device_id, options.device_pin)
  else:
    teardown_options = None

  # Prelaunch for hot startup.
  if options.hot_startup:
    print('Prelaunching')
    adb_utils.launch_activity(
        options.app_id,
        options.main_activity,
        options.device_id,
        wait_for_activity_to_launch=False)
    time.sleep(options.startup_duration)
    adb_utils.navigate_to_home_screen(options.device_id)
    time.sleep(1)

  # Drop caches before run.
  adb_utils.drop_caches(options.device_id)
  return teardown_options

def teardown_for_run(options, teardown_options):
  assert adb_utils.get_screen_state(options.device_id).is_on_and_unlocked()

  if options.cooldown > 0:
    adb_utils.teardown_after_interaction_with_device(
        teardown_options, options.device_id)
    adb_utils.ensure_screen_off(options.device_id)
  else:
    assert teardown_options is None

def run(out_dir, options, tmp_dir):
  assert adb_utils.get_screen_state(options.device_id).is_on_and_unlocked()

  # Start perfetto trace collector.
  perfetto_process = None
  perfetto_trace_path = None
  if options.perfetto:
    perfetto_process, perfetto_trace_path = perfetto_utils.record_android_trace(
        out_dir, tmp_dir, options.device_id)

  # Launch main activity.
  launch_activity_result = adb_utils.launch_activity(
      options.app_id,
      options.main_activity,
      options.device_id,
      wait_for_activity_to_launch=True)

  # Wait for perfetto trace collector to stop.
  if options.perfetto:
    perfetto_utils.stop_record_android_trace(perfetto_process, out_dir)

  # Get minor and major page faults from app process.
  data = compute_data(launch_activity_result, perfetto_trace_path, options)
  write_data(out_dir, data)
  return data

def add_data(data_total, data):
  for key, value in data.items():
    if key == 'app_id':
      assert data_total.get(key, value) == value
      data_total[key] = value
    if key == 'time':
      continue
    if key in data_total:
      if key == 'app_id':
        assert data_total[key] == value
      else:
        existing_value = data_total[key]
        assert isinstance(value, int)
        assert isinstance(existing_value, list)
        existing_value.append(value)
    else:
      assert isinstance(value, int), key
      data_total[key] = [value]

def compute_data(launch_activity_result, perfetto_trace_path, options):
  minfl, majfl = adb_utils.get_minor_major_page_faults(
      options.app_id, options.device_id)
  data = {
    'app_id': options.app_id,
    'time': time.ctime(time.time()),
    'minfl': minfl,
    'majfl': majfl
  }
  startup_data = compute_startup_data(
      launch_activity_result, perfetto_trace_path, options)
  return data | startup_data

def compute_startup_data(launch_activity_result, perfetto_trace_path, options):
  startup_data = {
    'adb_startup': launch_activity_result.get('total_time')
  }
  perfetto_startup_data = {}
  if options.perfetto:
    trace_processor = TraceProcessor(file_path=perfetto_trace_path)

    # Compute time to first frame according to the builtin android_startup
    # metric.
    startup_metric = trace_processor.metric(['android_startup'])
    time_to_first_frame_ms = \
        startup_metric.android_startup.startup[0].to_first_frame.dur_ms
    perfetto_startup_data['perfetto_startup'] = round(time_to_first_frame_ms)

    if not options.hot_startup:
      # Compute time to first and last doFrame event.
      bind_application_slice = perfetto_utils.find_unique_slice_by_name(
          'bindApplication', options, trace_processor)
      activity_start_slice = perfetto_utils.find_unique_slice_by_name(
          'activityStart', options, trace_processor)
      do_frame_slices = perfetto_utils.find_slices_by_name(
          'Choreographer#doFrame', options, trace_processor)
      first_do_frame_slice = next(do_frame_slices)
      *_, last_do_frame_slice = do_frame_slices

      perfetto_startup_data.update({
        'time_to_first_choreographer_do_frame_ms':
            round(perfetto_utils.get_slice_end_since_start(
                first_do_frame_slice, bind_application_slice)),
        'time_to_last_choreographer_do_frame_ms':
            round(perfetto_utils.get_slice_end_since_start(
                last_do_frame_slice, bind_application_slice))
      })

  # Return combined startup data.
  return startup_data | perfetto_startup_data

def write_data(out_dir, data):
  data_path = os.path.join(out_dir, 'data.txt')
  with open(data_path, 'w') as f:
    for key, value in data.items():
      f.write('%s=%s\n' % (key, str(value)))

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Generate a perfetto trace file.')
  result.add_argument('--app-id',
                      help='The application ID of interest',
                      required=True)
  result.add_argument('--aot',
                      help='Enable force compilation',
                      default=False,
                      action='store_true')
  result.add_argument('--aot-profile',
                      help='Enable force compilation using profiles',
                      default=False,
                      action='store_true')
  result.add_argument('--apk',
                      help='Path to the APK',
                      required=True)
  result.add_argument('--cooldown',
                      help='Seconds to wait before running each iteration',
                      default=0,
                      type=int)
  result.add_argument('--device-id',
                      help='Device id (e.g., emulator-5554).')
  result.add_argument('--device-pin',
                      help='Device pin code (e.g., 1234)')
  result.add_argument('--hot-startup',
                      help='Measure hot startup instead of cold startup',
                      default=False,
                      action='store_true')
  result.add_argument('--iterations',
                      help='Number of traces to generate',
                      default=1,
                      type=int)
  result.add_argument('--main-activity',
                      help='Main activity class name',
                      required=True)
  result.add_argument('--no-perfetto',
                      help='Disables perfetto trace generation',
                      action='store_true',
                      default=False)
  result.add_argument('--out-dir',
                      help='Directory to store trace files in',
                      required=True)
  result.add_argument('--baseline-profile',
                      help='Baseline profile to install')
  result.add_argument('--startup-duration',
                      help='Duration in seconds before shutting down app',
                      default=15,
                      type=int)
  options, args = result.parse_known_args(argv)
  setattr(options, 'perfetto', not options.no_perfetto)
  # Profile is only used with --aot.
  assert options.aot or not options.baseline_profile
  return options, args

def global_setup(options):
  # If there is no cooldown then unlock the screen once. Otherwise we turn off
  # the screen during the cooldown and unlock the screen before each iteration.
  teardown_options = None
  if options.cooldown == 0:
    teardown_options = adb_utils.prepare_for_interaction_with_device(
        options.device_id, options.device_pin)
    assert adb_utils.get_screen_state(options.device_id).is_on()
  else:
    adb_utils.ensure_screen_off(options.device_id)
  return teardown_options

def global_teardown(options, teardown_options):
  if options.cooldown == 0:
    adb_utils.teardown_after_interaction_with_device(
        teardown_options, options.device_id)
  else:
    assert teardown_options is None

def main(argv):
  (options, args) = parse_options(argv)
  with utils.TempDir() as tmp_dir:
    apk = apk_utils.add_baseline_profile_to_apk(
        options.apk, options.baseline_profile, tmp_dir)
    teardown_options = global_setup(options)
    run_all(apk, options, tmp_dir)
    global_teardown(options, teardown_options)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))