#!/usr/bin/env lucicfg

lucicfg.check_version("1.28.0", "Please use newer `lucicfg` binary")

# Enable LUCI Realms support.
lucicfg.enable_experiment("crbug.com/1085650")

# Launch 0% of Builds in "realms-aware mode"
luci.builder.defaults.experiments.set({"luci.use_realms": 100})


luci.project(
    name = "r8",
    buildbucket = "cr-buildbucket.appspot.com",
    logdog = "luci-logdog.appspot.com",
    milo = "luci-milo.appspot.com",
    notify = "luci-notify.appspot.com",
    scheduler = "luci-scheduler.appspot.com",
    swarming = "chrome-swarming.appspot.com",
    acls = [
        acl.entry(
            [
                acl.BUILDBUCKET_READER,
                acl.LOGDOG_READER,
                acl.PROJECT_CONFIGS_READER,
                acl.SCHEDULER_READER,
            ],
            groups = ["all"],
        ),
        acl.entry(
            [
                acl.BUILDBUCKET_TRIGGERER,
                acl.SCHEDULER_OWNER,

            ],
            groups = [
                "project-r8-committers"
            ],
            users = [
                "luci-scheduler@appspot.gserviceaccount.com"
            ]
        ),
        acl.entry(
            [
                acl.LOGDOG_WRITER,
            ],
            groups = [
                "luci-logdog-r8-writers"
            ],
        ),
    ],
    bindings = [
        luci.binding(
            roles = "role/swarming.poolOwner",
            groups = "mdb/r8-team",
        ),
        luci.binding(
            roles = "role/swarming.poolViewer",
            groups = "googlers",
        ),
    ],
)

luci.logdog(gs_bucket = "logdog-r8-archive")


# Allow the given users to use LUCI `led` tool and "Debug" button
# inside the given bucket & pool security realms.
def led_users(*, pool_realm, builder_realm, groups):
    luci.realm(
        name = pool_realm,
        bindings = [
            luci.binding(
                roles = "role/swarming.poolUser",
                groups = groups,
            ),
        ],
    )
    luci.binding(
        realm = builder_realm,
        roles = "role/swarming.taskTriggerer",
        groups = groups,
    )
led_users(
    pool_realm="pools/ci",
    builder_realm="ci",
    groups=[
        "mdb/r8-team",
        "mdb/chrome-troopers",
    ],
)

luci.bucket(name = "ci")

luci.milo()

luci.notifier(
  name = "r8-failures",
  on_failure = True,
  on_new_failure = True,
  notify_blamelist = True
)

luci.gitiles_poller(
  name = "main-gitiles-trigger",
  bucket = "ci",
  repo = "https://r8.googlesource.com/r8"
)

luci.gitiles_poller(
  name = "branch-gitiles-trigger",
  bucket = "ci",
  repo = "https://r8.googlesource.com/r8",
  # Version branches are named d8-x.y (up until d8-1.5) or just x.y (from 1.6)
  refs = ["refs/heads/(?:d8-)?[0-9]+\\.[0-9]+(\\.[0-9]+)?"],
  path_regexps = ["src/main/java/com/android/tools/r8/Version.java"]
)

luci.console_view(
    name = "main",
    title = "R8 Main Console",
    repo = "https://r8.googlesource.com/r8",
    refs = ["refs/heads/.*"]
)

def builder_view(name, category, short_name):
    return luci.console_view_entry(
        console_view = "main",
        builder = name,
        category = category,
        short_name = short_name,
    )

luci.recipe(
      name="rex",
      cipd_package = "infra_internal/recipe_bundles/" +
          "chrome-internal.googlesource.com/chrome/" +
	  "tools/build_limited/scripts/slave",
      cipd_version = "refs/heads/master"
)

common_test_options = [
    "--tool=r8",
    "--no_internal",
    "--one_line_per_test",
    "--archive_failures"
]

def get_dimensions(windows=False, jctf=False, internal=False, normal=False):
  dimensions = {
    "cores" : "2" if internal else "8",
    "cpu" : "x86-64",
    "pool" : "luci.r8.ci"
  }
  if windows:
    dimensions["os"] = "windows-10"
  else:
    dimensions["os"] = "Ubuntu-16.04"
  if jctf:
    dimensions["jctf"] = "true"
  if internal:
    dimensions["internal"] = "true"
  if normal:
    dimensions["normal"] = "true"
  return dimensions

def r8_builder(name, priority=26, trigger=True, category=None, **kwargs):
  release = name.endswith("release")
  triggered = None if not trigger else ["branch-gitiles-trigger"] if release\
      else ["main-gitiles-trigger"]

  luci.builder(
    name = name,
    bucket = "ci",
    service_account = "r8-ci-builder@chops-service-accounts." +
        "iam.gserviceaccount.com",
    build_numbers = True,
    swarming_tags = ["vpython:native-python-wrapper"],
    notifies = ["r8-failures"] if trigger else None,
    priority = priority,
    triggered_by = triggered,
    executable = "rex",
    **kwargs
  )
  category = category if category else "R8 release" if release else "R8"
  builder_view(name, category, name.split("-")[-1].replace("_release", ""))

def r8_tester(name,
    test_options,
    dimensions = None,
    execution_timeout = time.hour * 6,
    expiration_timeout = time.hour * 35,
    category=None):
  dimensions = dimensions if dimensions else get_dimensions(normal=True)
  for name in [name, name + "_release"]:
    r8_builder(
        name = name,
        category = category,
        execution_timeout = execution_timeout,
        expiration_timeout = expiration_timeout,
        dimensions = dimensions,
        properties = {
            "test_options" : test_options,
            "builder_group" : "internal.client.r8"
        }
    )

def r8_tester_with_default(name, test_options, dimensions=None, category=None):
  r8_tester(name, test_options + common_test_options,
            dimensions = dimensions, category = category)

def archivers():
  for name in ["archive", "archive_release", "lib_desugar-archive"]:
    desugar = "desugar" in name
    properties = {
        "archive": "true",
        "builder_group" : "internal.client.r8"
    }
    if desugar:
      properties["sdk_desugar"] = "true"
    r8_builder(
        name,
        category = "library_desugar" if desugar else name,
        dimensions = get_dimensions(),
        triggering_policy = scheduler.policy(
            kind = scheduler.GREEDY_BATCHING_KIND,
            max_batch_size = 1,
            max_concurrent_invocations = 3
        ),
        priority = 25,
        trigger = not desugar,
        properties = properties,
        execution_timeout = time.hour * 1 if desugar else time.minute * 30 ,
        expiration_timeout = time.hour * 35,
    )
archivers()

r8_tester_with_default("linux-dex_default", ["--runtimes=dex-default"])
r8_tester_with_default("linux-none", ["--runtimes=none"])
r8_tester_with_default("linux-jdk8", ["--runtimes=jdk8"])
r8_tester_with_default("linux-jdk9", ["--runtimes=jdk9"])
r8_tester_with_default("linux-jdk11", ["--runtimes=jdk11"])

r8_tester_with_default("linux-android-4.0.4",
    ["--dex_vm=4.0.4", "--all_tests"])
r8_tester_with_default("linux-android-4.4.4",
    ["--dex_vm=4.4.4", "--all_tests"])
r8_tester_with_default("linux-android-5.1.1",
    ["--dex_vm=5.1.1", "--all_tests"])
r8_tester_with_default("linux-android-6.0.1",
    ["--dex_vm=6.0.1", "--all_tests"])
r8_tester_with_default("linux-android-7.0.0",
    ["--dex_vm=7.0.0", "--all_tests"])
r8_tester_with_default("linux-android-8.1.0",
    ["--dex_vm=8.1.0", "--all_tests"])
r8_tester_with_default("linux-android-9.0.0",
    ["--dex_vm=9.0.0", "--all_tests"])
r8_tester_with_default("linux-android-10.0.0",
    ["--dex_vm=10.0.0", "--all_tests"])
r8_tester_with_default("linux-android-12.0.0",
    ["--dex_vm=12.0.0", "--all_tests"])

r8_tester_with_default("windows", ["--all_tests"], category = "windows",
    dimensions=get_dimensions(windows=True))

def internal():
  for name in ["linux-internal", "linux-internal_release"]:
    r8_builder(
        name,
        dimensions = get_dimensions(internal=True),
        triggering_policy = scheduler.policy(
            kind = scheduler.GREEDY_BATCHING_KIND,
            max_batch_size = 1,
            max_concurrent_invocations = 1
        ),
        priority = 25,
        properties = {
            "internal": "true",
            "builder_group" : "internal.client.r8"
        },
        execution_timeout = time.hour * 12,
        expiration_timeout = time.hour * 35,
    )
internal()

def app_dump():
  for release in ["", "_release"]:
      properties = {
          "builder_group" : "internal.client.r8",
          "test_options" : ["--bot"],
          "test_wrapper" : "tools/run_on_app_dump.py"
      }
      name = "linux-run-on-app-dump" + release
      r8_builder(
          name,
          dimensions = get_dimensions(),
          execution_timeout = time.hour * 12,
          expiration_timeout = time.hour * 35,
          properties = properties,
      )
app_dump()

def desugared_library():
  for name in ["head", "jdk11_head"]:
    test_options = ["--no_internal", "--desugared-library", "HEAD"]
    if "jdk11" in name:
      test_options = test_options + ["--desugared-library-configuration", "jdk11"]
    properties = {
       "builder_group" : "internal.client.r8",
       "test_options" : test_options,
    }
    name = "desugared_library-" + name
    r8_builder(
        name,
        category = "library_desugar",
        dimensions = get_dimensions(),
        execution_timeout = time.hour * 12,
        expiration_timeout = time.hour * 35,
        properties = properties,
    )
desugared_library()

r8_builder(
    "linux-kotlin-dev",
    dimensions = get_dimensions(),
    execution_timeout = time.hour * 12,
    expiration_timeout = time.hour * 35,
    properties = {
      "builder_group" : "internal.client.r8",
      "test_options" : ["--runtimes=dex-default:jdk11", "--kotlin-compiler-dev", "--one_line_per_test", "--archive_failures", "--no-internal", "*kotlin*"]
    }
)

def jctf():
  for release in ["", "_release"]:
    for tool in ["d8", "r8cf"]:
      properties = {
          "tool": tool,
          "builder_group" : "internal.client.r8",
          "dex_vm" : "all",
          "only_jctf" : "true",
      }
      name = "linux-" + tool + "_jctf"
      name = name + release
      r8_builder(
          name,
          dimensions = get_dimensions(jctf=True),
          execution_timeout = time.hour * 12,
          expiration_timeout = time.hour * 35,
          properties = properties,
      )
jctf()

