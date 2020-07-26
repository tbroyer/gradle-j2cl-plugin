package net.ltgt.gradle.j2cl

import net.ltgt.gradle.j2cl.tasks.ClosureCompile
import net.ltgt.gradle.j2cl.tasks.ClosureCompileTests
import net.ltgt.gradle.j2cl.tasks.GenerateTests
import net.ltgt.gradle.j2cl.tasks.GwtIncompatibleStrip
import net.ltgt.gradle.j2cl.tasks.J2clTranspile
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType

/**
 * A simple 'hello world' plugin.
 */
class J2clBasePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        pluginManager.apply("java-base")

        configure<JavaPluginExtension> {
            // FIXME: remove when J2CL supports JDK11+
            sourceCompatibility = JavaVersion.VERSION_1_8
        }

        val gwtIncompatibleStripperConfiguration = configurations.create(GWT_INCOMPATIBLE_STRIPPER_CONFIGURATION_NAME) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The GwtIncompatibleStripper libraries to be used by this project"
        }
        val j2clTranspilerConfiguration = configurations.create(J2CL_TRANSPILER_CONFIGURATION_NAME) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The J2clTranspiler libraries to be used by this project"
        }
        val closureCompilerConfiguration = configurations.create(CLOSURE_COMPILER_CONFIGURATION_NAME) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The Closure Compiler libraries to be used by this project"
        }
        val j2clBootstrapClasspathConfiguration = configurations.create(J2CL_BOOTSTRAP_CLASSPATH_CONFIGURATION_NAME) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The bootstrap classpath for J2CL to be used by this project"
        }
        val j2clTestingAnnotationClasspathConfiguration = configurations.create(J2CL_TESTING_ANNOTATION_CLASSPATH) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The J2clTestInput libraries to be used by this project"
        }
        val j2clTestingAnnotationProcessorConfiguration = configurations.create(J2CL_TESTING_ANNOTATION_PROCESSOR) {
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "The J2clTestingProcessor libraries to be used by this project"
        }

        tasks.withType<GwtIncompatibleStrip>().configureEach {
            stripperClasspath.from(gwtIncompatibleStripperConfiguration)
        }
        tasks.withType<J2clTranspile>().configureEach {
            transpilerClasspath.from(j2clTranspilerConfiguration)
        }
        tasks.withType<ClosureCompile>().configureEach {
            compilerClasspath.from(closureCompilerConfiguration)
            options {
                debug.convention(false)
                exportTestFunctions.convention(false)
                rewritePolyfills.convention(false)
                compilationLevel.convention("ADVANCED")
                jreLoggingLogLevel.convention("OFF")
                jreChecksCheckLevel.convention("NORMAL")
                jreClassMetadata.convention("STRIPPED")
            }
        }
        tasks.withType<GenerateTests>().configureEach {
            sourceCompatibility.convention(provider { the<JavaPluginExtension>().sourceCompatibility })
            bootstrapClasspath.from(j2clBootstrapClasspathConfiguration)
            annotationClasspath.from(j2clTestingAnnotationClasspathConfiguration)
            annotationProcessorPath.from(j2clTestingAnnotationProcessorConfiguration)
        }
        tasks.withType<ClosureCompileTests>().configureEach {
            compilerClasspath.from(closureCompilerConfiguration)
            options {
                debug.convention(true)
                exportTestFunctions.convention(true)
                rewritePolyfills.convention(false)
                compilationLevel.convention("SIMPLE")
                jreLoggingLogLevel.convention("OFF")
                jreChecksCheckLevel.convention("NORMAL")
                jreClassMetadata.convention("STRIPPED")
            }
        }
    }
}
