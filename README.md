# gradle-j2cl-plugin

This plugin provides tasks and project conventions for building [J2CL] projects with Gradle.

[J2CL]: https://github.com/google/j2cl

## Requirements

This plugin requires using at least Gradle 6.6.

## Building the project

Building the project currently requires that you separately checkout and build [J2CL] itself.

You need to build the following targets:
```sh
bazel build //transpiler/java/com/google/j2cl/transpiler:J2clCommandLineRunner_deploy.jar
bazel build //tools/java/com/google/j2cl/tools/gwtincompatible:GwtIncompatibleStripper_deploy.jar
bazel build //jre/java:jre //jre/java:jre_bootclasspath
bazel build //junit/emul/java:junit_emul //junit/generator/java/com/google/j2cl/junit/...
# Must be last, as it actually only sets up the execroot
bazel build $(bazel query 'filter('closure_.*', //third_party/...)')
```

Once this is done, you can build the plugin with Gradle,
passing the path to the J2CL project root as a `j2cl-project-root` Gradle property:

```sh
./gradlew -Pj2cl-project-root="<path to J2CL project root>" build
```

Instead of passing it to each Gradle execution,
you can set it up once and for all in your `~/.gradle/gradle.properties`:
```properties
j2cl-project-root=<path to J2CL project root>
```

The plugin is currently tested/exercized using sample projects in the `samples/` folder.
Switch to a sample's folder then build it,
this will automatically build the plugin as a [composite build].

[composite build]: https://docs.gradle.org/current/userguide/composite_builds.html
