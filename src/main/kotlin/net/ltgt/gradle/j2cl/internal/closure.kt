package net.ltgt.gradle.j2cl.internal

import com.google.javascript.jscomp.CommandLineRunner
import com.google.javascript.jscomp.CompilationLevel
import com.google.javascript.jscomp.CompilerOptions
import net.ltgt.gradle.j2cl.tasks.ClosureOptions
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getValue
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

internal interface ClosureCompilerParameters : WorkParameters {
    val entrypoints: ListProperty<String>
    val files: ConfigurableFileCollection
    val destinationDirectory: DirectoryProperty
    val initialScriptFilename: Property<String>
    val options: Property<ClosureOptions>
    val temporaryDirectory: DirectoryProperty
}

internal abstract class ClosureCompilerWorker : WorkAction<ClosureCompilerParameters> {

    override fun execute() {
        val entrypoints = parameters.entrypoints.get()
        val destinationDirectory by parameters.destinationDirectory
        val initialScriptFilename by parameters.initialScriptFilename
        val options by parameters.options
        val compilationLevel =
            CompilationLevel.fromString(options.compilationLevel.get())

        val configFile = defineJs(parameters.temporaryDirectory.get(), options)

        val args = mutableListOf<String>()
        entrypoints.flatMapTo(args) {
            listOf("--entry_point", "goog:$it")
        }
        val jsOutputFile = destinationDirectory.file(initialScriptFilename).asFile
        args.addAll(
            arrayOf(
                // from closure_js_binary
                "--js_output_file", jsOutputFile.path,
                "--create_source_map", "${jsOutputFile.path}.map",
                "--language_in", "ECMASCRIPT_2017",
                "--language_out", "ECMASCRIPT5",
                "--compilation_level", compilationLevel.toString(),
                "--dependency_mode", "PRUNE_LEGACY",
                "--warning_level", "VERBOSE",
                "--generate_exports",
                "--process_closure_primitives",
                "--define=goog.json.USE_NATIVE_JSON",
                "--hide_warnings_for=closure/goog/base.js",
                "--source_map_location_mapping", "${jsOutputFile.path}|${jsOutputFile.name}",
                if (options.debug.get()) "--debug" else "--define=goog.DEBUG=false",
                // from j2cl_application
                "--jscomp_off", "analyzerChecks",
                "--jscomp_off", "reportUnknownTypes"
            )
        )
        if (compilationLevel == CompilationLevel.ADVANCED_OPTIMIZATIONS) {
            args.add("--use_types_for_optimization")
        }
        args.add(configFile.path)
        parameters.files.flatMapTo(args) {
            when {
                it.isZip -> listOf("--jszip", it.path)
                it.isJs -> listOf(it.path)
                else -> listOf()
            }
        }
        args.add("--rewrite_polyfills=${options.rewritePolyfills.get()}")

        val runner = JsCompRunner(
            args.toTypedArray(),
            exportTestFunctions = options.exportTestFunctions.get()
        )
        if (runner.shouldRunCompiler()) {
            runner.run()
        }
        if (runner.exitCode != 0) {
            throw ClosureCompilerException(runner.exitCode)
        }
        if (runner.hasErrors()) {
            throw ClosureCompilerException()
        }
    }

    private fun defineJs(temporaryDir: Directory, options: ClosureOptions): File {
        val config = temporaryDir.asFile.resolve("config.js")

        val defines = mutableMapOf(
            "jre.checks.checkLevel" to options.jreChecksCheckLevel.get(),
            "jre.logging.logLevel" to options.jreLoggingLogLevel.get(),
            "jre.classMetadata" to options.jreClassMetadata.get()
        )
        defines.putAll(options.defines.get())

        config.writeText("var CLOSURE_DEFINES = ${MOSHI.adapter<Any>(Object::class.java).indent("  ").toJson(defines)};")

        return config
    }
}

private class JsCompRunner(
    args: Array<String>,
    private val exportTestFunctions: Boolean
) : CommandLineRunner(args) {
    var exitCode: Int = 0

    init {
        setExitCodeReceiver { this.exitCode = it!!; null }
    }

    override fun createOptions(): CompilerOptions = super.createOptions().apply {
        exportTestFunctions = this@JsCompRunner.exportTestFunctions
    }
}

private class ClosureCompilerException(exitCode: Int? = null) :
    RuntimeException("Compilation failed${exitCode?.let { " with exit code $it" } ?: ""}; see the compiler error output for details.")

private val File.isZip: Boolean
    get() = name.endsWith(".zip", ignoreCase = true) || name.endsWith(".jszip", ignoreCase = true)
private val File.isJs: Boolean
    get() = name.endsWith(".js", ignoreCase = true)
