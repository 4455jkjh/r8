# R8 Keep Radius

The "keep radius" of a keep rule refers to the negative impact that the rule has on the final application.

R8 provides a tool to analyze and visualize this keep radius. This helps developers:

* **Identify expensive rules**: Find keep rules that prevent R8 from
optimizing large parts of the application.
* **Debug dependencies**: See which libraries introduce broad keep rules.
* **Remove redundancy**: Discover rules that are not needed because they are
covered by other rules.

> **Note**: This feature is currently **experimental** and requires R8 version
> 9.2.14-dev or higher.

## Generating the R8 Configuration Analysis Report

R8 supports outputting a Configuration Analysis Report as an HTML file.

### Gradle

To generate the HTML report from a Gradle build, pass the
`com.android.tools.r8.dumpkeepradiushtmltodirectory` system property.

See [Replacing R8 in AGP](../README.md#replacing-r8-in-agp) for instructions on
how to update the R8 version in your build.

```bash
./gradlew assembleRelease \
    --no-daemon \
    -Dcom.android.tools.r8.dumpkeepradiushtmltodirectory=<output_directory>
```

**Example**:
```bash
./gradlew assembleRelease \
    --no-daemon \
    -Dcom.android.tools.r8.dumpkeepradiushtmltodirectory=/tmp/keepradius
```

### Android Platform

For builds within the Android Platform, set the `R8_DUMP_KEEP_RADIUS` environment variable to `true`.

```bash
R8_DUMP_KEEP_RADIUS=true m
```

This will generate an `r8keepradius.pb` file for each R8 build target within
`out/soong/.intermediates`.

## Visualizing the Data

The raw `.pb` file can be converted into an interactive HTML report using the
`KeepRadiusHtmlReportGenerator`.

### Converting a single file

Use the following command to convert a specific `.pb` file to HTML:

```bash
java -cp r8.jar com.android.tools.r8.keepradius.KeepRadiusHtmlReportGenerator \
  <path_to_input_pb> <path_to_output_html>
```

**Example**:
```bash
java -cp r8.jar com.android.tools.r8.keepradius.KeepRadiusHtmlReportGenerator \
  /tmp/keepradius/keepradius.pb /tmp/keepradius/report.html
```

### Converting multiple files (Android Platform)

If you have multiple keep radius files (e.g., from an Android Platform build),
you can generate a summary report for the directory:

```bash
java -cp prebuilts/r8/r8.jar com.android.tools.r8.keepradius.KeepRadiusHtmlReportGenerator \
  <path_to_input_directory> <path_to_output_directory>
```

**Example**:
```bash
java -cp prebuilts/r8/r8.jar com.android.tools.r8.keepradius.KeepRadiusHtmlReportGenerator \
  out/soong/.intermediates /tmp/keepradiusreport
```

This will scan the input directory for `.pb` files and generate an HTML report
in the output directory.
