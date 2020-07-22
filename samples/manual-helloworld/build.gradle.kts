import net.ltgt.gradle.j2cl.tasks.ClosureCompile
import net.ltgt.gradle.j2cl.tasks.GenerateTests
import net.ltgt.gradle.j2cl.tasks.GwtIncompatibleStrip
import net.ltgt.gradle.j2cl.tasks.J2clTranspile

plugins {
    id("net.ltgt.j2cl-base")
    id("org.jlleitschuh.gradle.ktlint")
}
ktlint {
    version.set("0.37.2")
    enableExperimentalRules.set(true)
}

val j2clProjectRoot = layout.dir(providers.gradleProperty("j2cl-project-root").forUseAtConfigurationTime().map { file(it) })
fun j2clBin(vararg paths: String) = j2clProjectRoot.map { it.dir("bazel-bin").files(*paths) }
fun j2clOut(vararg paths: String) = j2clProjectRoot.map { it.dir("bazel-${it.asFile.name}").files(*paths) }

repositories {
    mavenCentral()
}
val compileClasspath by configurations.creating
val closureCompile by configurations.creating
val testCompileClasspath by configurations.creating {
    extendsFrom(compileClasspath)
}
val testClosureCompile by configurations.creating {
    extendsFrom(closureCompile)
}
dependencies {
    gwtIncompatibleStripper(j2clBin("tools/java/com/google/j2cl/tools/gwtincompatible/GwtIncompatibleStripper_deploy.jar"))
    j2clTranspiler(j2clBin("transpiler/java/com/google/j2cl/transpiler/J2clCommandLineRunner_deploy.jar"))
    closureCompiler("com.google.javascript:closure-compiler:v20200628")
    j2clBootstrapClasspath(j2clBin("jre/java/libjre_bootclasspath.jar"))
    j2clTestingAnnotationClasspath(j2clBin("junit/generator/java/com/google/j2cl/junit/apt/libinternal_junit_annotations.jar"))
    j2clTestingAnnotationProcessor(
        j2clBin(
            "junit/generator/java/com/google/j2cl/junit/apt/libjunit_processor.jar",
            "junit/generator/java/com/google/j2cl/junit/async/libasync.jar"
        )
    )
    arrayOf(
        "com.google.auto:auto-common:0.9",
        "org.apache.velocity:velocity-engine-core:2.1",
        "com.google.guava:guava:25.1-jre",
        "junit:junit:4.12"
    ).forEach {
        j2clTestingAnnotationProcessor(it) { because("Transitive dependency of libjunit_processor.jar") }
    }

    compileClasspath(
        j2clBin(
            "jre/java/libjre-hjar.jar",
            "external/org_gwtproject_gwt/user/libgwt-javaemul-internal-annotations.jar"
        )
    )
    compileClasspath("com.google.jsinterop:jsinterop-annotations:2.0.0")
    closureCompile(
        files(
            j2clBin("jre/java/jre.js.zip"),
            j2clOut(
                // Generated with:
                // bazel query --noimplicit_deps 'kind("source file", deps(kind(closure_js_library, deps(//jre/java:jre))))' | cut -f2 -d:
                "external/com_google_javascript_closure_library/closure/goog/base.js",
                "external/com_google_javascript_closure_library/closure/goog/debug/error.js",
                "external/com_google_javascript_closure_library/closure/goog/dom/nodetype.js",
                "external/com_google_javascript_closure_library/closure/goog/asserts/asserts.js",
                "external/com_google_javascript_closure_library/closure/goog/reflect/reflect.js",
                "external/com_google_javascript_closure_library/closure/goog/math/long.js"
            )
        )
    )
    testCompileClasspath(j2clBin("junit/emul/java/libjunit_emul-hjar.jar"))
    testClosureCompile(j2clBin("junit/emul/java/junit_emul.js.zip"))
    testClosureCompile(
        j2clOut(
            // Generated with:
            // bazel query --noimplicit_deps 'kind("source file", deps(@io_bazel_rules_closure//closure/library/testing:testrunner + @io_bazel_rules_closure//closure/library/testing:testsuite))' | cut -f2 -d:
            "external/com_google_javascript_closure_library/closure/goog/useragent/useragent.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/testsuite.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/testrunner.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/testcase.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/strictmock.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/stacktrace.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/propertyreplacer.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/mockmatchers.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/mockinterface.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/mockcontrol.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/mockclock.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/mock.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/loosemock.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/jsunitexception.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/jsunit.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/functionmock.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/events/events.js",
            "external/com_google_javascript_closure_library/closure/goog/testing/asserts.js",
            "external/com_google_javascript_closure_library/closure/goog/style/style.js",
            "external/com_google_javascript_closure_library/closure/goog/structs/structs.js",
            "external/com_google_javascript_closure_library/closure/goog/structs/set.js",
            "external/com_google_javascript_closure_library/closure/goog/structs/map.js",
            "external/com_google_javascript_closure_library/closure/goog/structs/collection.js",
            "external/com_google_javascript_closure_library/closure/goog/string/typedstring.js",
            "external/com_google_javascript_closure_library/closure/goog/string/string.js",
            "external/com_google_javascript_closure_library/closure/goog/string/internal.js",
            "external/com_google_javascript_closure_library/closure/goog/string/const.js",
            "external/com_google_javascript_closure_library/closure/goog/reflect/reflect.js",
            "external/com_google_javascript_closure_library/closure/goog/promise/thenable.js",
            "external/com_google_javascript_closure_library/closure/goog/promise/resolver.js",
            "external/com_google_javascript_closure_library/closure/goog/promise/promise.js",
            "external/com_google_javascript_closure_library/closure/goog/object/object.js",
            "external/com_google_javascript_closure_library/closure/goog/math/size.js",
            "external/com_google_javascript_closure_library/closure/goog/math/rect.js",
            "external/com_google_javascript_closure_library/closure/goog/math/math.js",
            "external/com_google_javascript_closure_library/closure/goog/math/irect.js",
            "external/com_google_javascript_closure_library/closure/goog/math/coordinate.js",
            "external/com_google_javascript_closure_library/closure/goog/math/box.js",
            "external/com_google_javascript_closure_library/closure/goog/labs/useragent/util.js",
            "external/com_google_javascript_closure_library/closure/goog/labs/useragent/platform.js",
            "external/com_google_javascript_closure_library/closure/goog/labs/useragent/engine.js",
            "external/com_google_javascript_closure_library/closure/goog/labs/useragent/browser.js",
            "external/com_google_javascript_closure_library/closure/goog/labs/testing/environment.js",
            "external/com_google_javascript_closure_library/closure/goog/json/json.js",
            "external/com_google_javascript_closure_library/closure/goog/iter/iter.js",
            "external/com_google_javascript_closure_library/closure/goog/i18n/bidi.js",
            "external/com_google_javascript_closure_library/closure/goog/html/uncheckedconversions.js",
            "external/com_google_javascript_closure_library/closure/goog/html/trustedtypes.js",
            "external/com_google_javascript_closure_library/closure/goog/html/trustedresourceurl.js",
            "external/com_google_javascript_closure_library/closure/goog/html/safeurl.js",
            "external/com_google_javascript_closure_library/closure/goog/html/safestylesheet.js",
            "external/com_google_javascript_closure_library/closure/goog/html/safestyle.js",
            "external/com_google_javascript_closure_library/closure/goog/html/safescript.js",
            "external/com_google_javascript_closure_library/closure/goog/html/safehtml.js",
            "external/com_google_javascript_closure_library/closure/goog/functions/functions.js",
            "external/com_google_javascript_closure_library/closure/goog/fs/url.js",
            "external/com_google_javascript_closure_library/closure/goog/events/listenermap.js",
            "external/com_google_javascript_closure_library/closure/goog/events/listener.js",
            "external/com_google_javascript_closure_library/closure/goog/events/listenable.js",
            "external/com_google_javascript_closure_library/closure/goog/events/keycodes.js",
            "external/com_google_javascript_closure_library/closure/goog/events/eventtype.js",
            "external/com_google_javascript_closure_library/closure/goog/events/eventtarget.js",
            "external/com_google_javascript_closure_library/closure/goog/events/events.js",
            "external/com_google_javascript_closure_library/closure/goog/events/eventid.js",
            "external/com_google_javascript_closure_library/closure/goog/events/event.js",
            "external/com_google_javascript_closure_library/closure/goog/events/browserfeature.js",
            "external/com_google_javascript_closure_library/closure/goog/events/browserevent.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/vendor.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/tags.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/tagname.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/safe.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/nodetype.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/htmlelement.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/dom.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/browserfeature.js",
            "external/com_google_javascript_closure_library/closure/goog/dom/asserts.js",
            "external/com_google_javascript_closure_library/closure/goog/disposable/idisposable.js",
            "external/com_google_javascript_closure_library/closure/goog/disposable/disposable.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/relativetimeprovider.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/logrecord.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/logger.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/logbuffer.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/formatter.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/errorcontext.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/error.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/entrypointregistry.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/debug.js",
            "external/com_google_javascript_closure_library/closure/goog/debug/console.js",
            "external/com_google_javascript_closure_library/closure/goog/async/workqueue.js",
            "external/com_google_javascript_closure_library/closure/goog/async/run.js",
            "external/com_google_javascript_closure_library/closure/goog/async/nexttick.js",
            "external/com_google_javascript_closure_library/closure/goog/async/freelist.js",
            "external/com_google_javascript_closure_library/closure/goog/asserts/asserts.js",
            "external/com_google_javascript_closure_library/closure/goog/array/array.js"
        )
    )
}

tasks {
    val stripGwtIncompatible by registering(GwtIncompatibleStrip::class) {
        source("src/main/java")
        include("**/*.java")
        destinationDirectory.set(layout.buildDirectory.dir("generated/sources/gwtIncompatibleStripper/java/main"))
    }
    val compileJava by registering(JavaCompile::class) {
        source(stripGwtIncompatible.flatMap { it.destinationDirectory })
        classpath = compileClasspath
        options.bootstrapClasspath = files(configurations.j2clBootstrapClasspath)
        destinationDirectory.set(layout.buildDirectory.dir("classes/java/main"))
        options.generatedSourceOutputDirectory.set(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main"))
    }
    val transpileJ2cl by registering(J2clTranspile::class) {
        source(stripGwtIncompatible.flatMap { it.destinationDirectory })
        source(compileJava.flatMap { it.options.generatedSourceOutputDirectory })
        nativeJsSources.from("src/main/resources")
        destinationDirectory.set(layout.buildDirectory.dir("generated/sources/j2clTranspiler/java/main"))
        classpath.from(compileJava.map { it.classpath })
    }
    val compileClosure by registering(ClosureCompile::class) {
        source(transpileJ2cl.flatMap { it.destinationDirectory })
        source(fileTree("src/main/java") { exclude("**/*.native.js") })
        source(closureCompile)
        entrypoints.add("j2cl.samples.app")
        destinationDirectory.set(layout.buildDirectory.dir("closure"))
        initialScriptFilename.set("helloworld.js")
    }
    val compileClosureDev by registering(ClosureCompile::class) {
        source(transpileJ2cl.flatMap { it.destinationDirectory })
        source(fileTree("src/main/java") { exclude("**/*.native.js") })
        source(closureCompile)
        entrypoints.add("j2cl.samples.app")
        // Same destination directory as compileClosure to easily switch between them (but means recompiling ¯\_(ツ)_/¯)
        destinationDirectory.set(layout.buildDirectory.dir("closure"))
        initialScriptFilename.set("helloworld.js")
        options {
            compilationLevel.set("BUNDLE")
        }
    }
    assemble {
        dependsOn(compileClosure)
    }

    val stripTestGwtIncompatible by registering(GwtIncompatibleStrip::class) {
        source("src/test/java")
        include("**/*.java")
        destinationDirectory.set(layout.buildDirectory.dir("generated/sources/gwtIncompatibleStripper/java/test"))
    }
    val compileTestJava by registering(JavaCompile::class) {
        source(stripTestGwtIncompatible.flatMap { it.destinationDirectory })
        classpath = files(testCompileClasspath, compileJava.flatMap { it.destinationDirectory })
        options.bootstrapClasspath = files(configurations.j2clBootstrapClasspath)
        destinationDirectory.set(layout.buildDirectory.dir("classes/java/test"))
        options.generatedSourceOutputDirectory.set(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/test"))
    }
    val generateTests by registering(GenerateTests::class) {
        testClassesDirs.from(compileTestJava.flatMap { it.destinationDirectory })
        include("**/*Test.class")
        classpath.from(compileTestJava.map { it.classpath })
        destinationDirectory.set(layout.buildDirectory.dir("generated/sources/j2clTesting/java/test"))
        sourceCompatibility.set(provider { java.sourceCompatibility })
    }
    // TODO: closureCompile tests (from generated test_summary.json) and j2clTest
    check {
        dependsOn(generateTests)
    }
}
