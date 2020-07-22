import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("org.jlleitschuh.gradle.ktlint")
}

group = "net.ltgt.gradle"

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

tasks.withType<KotlinCompile>().configureEach {
    // This is the version used in Gradle 6.6, for backwards compatibility when we'll upgrade
    kotlinOptions.apiVersion = "1.3"

    kotlinOptions.allWarningsAsErrors = true
}

val j2clProjectRoot = layout.dir(providers.gradleProperty("j2cl-project-root").forUseAtConfigurationTime().map { file(it) })

repositories {
    mavenCentral()
}
dependencies {
    compileOnly(
        j2clProjectRoot.map {
            it.files(
                "bazel-bin/transpiler/java/com/google/j2cl/transpiler/libcommandlinerunner_lib.jar",
                "bazel-bin/tools/java/com/google/j2cl/tools/gwtincompatible/libgwtincompatible_lib.jar",
                // needed by the above two JARs
                "bazel-bin/transpiler/java/com/google/j2cl/common/libcommon-hjar.jar"
            )
        }
    )
    compileOnly("com.google.javascript:closure-compiler:v20200628@jar")
    compileOnly("org.seleniumhq.selenium:selenium-java:3.141.59")
    compileOnly("org.seleniumhq.selenium:htmlunit-driver:2.42.0")

    implementation("com.squareup.moshi:moshi:1.9.3")
    implementation("com.squareup.moshi:moshi-kotlin:1.9.3")
}

gradlePlugin {
    plugins {
        register("base") {
            id = "net.ltgt.j2cl-base"
            implementationClass = "net.ltgt.gradle.j2cl.J2clBasePlugin"
        }
    }
}

ktlint {
    version.set("0.37.2")
    enableExperimentalRules.set(true)
}
